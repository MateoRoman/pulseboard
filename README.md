# Pulseboard

Foro de discusión con conversaciones jerárquicas: mensajes principales, respuestas a
cualquier mensaje y anidación en múltiples niveles, con la relación padre-hijo visible al
leer.

**Stack**: Angular 21 · Spring Boot 4 sobre Java 21 · persistencia en un único archivo JSON,
sin base de datos ni ORM.

---

## Prerrequisitos

| Requisito | Versión | Nota |
|-----------|---------|------|
| JDK | 21 o superior | `java -version` |
| Node.js | **≥ 22.12.0** | Angular 21 lo exige. No hace falta actualizar a Angular 22 |
| npm | ≥ 8 | |
| Maven | *no se requiere* | Se usa el wrapper `mvnw` versionado en el repositorio |
| Angular CLI | *no se requiere global* | Se usa la dependencia local del proyecto |

No hace falta definir `JAVA_HOME`: el wrapper incluido es de tipo *only-script* y localiza
el JDK por sí mismo.

## Ejecutar en local

Hacen falta **dos terminales**: el back-end y el front-end corren a la vez.

### Terminal 1 — back-end

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

Queda escuchando en `http://localhost:8080`. La primera ejecución descarga Maven y las
dependencias, así que tarda más.

Comprobar que responde:

```bash
curl http://localhost:8080/api/config
```

### Terminal 2 — front-end

```bash
cd frontend
npm install
npm start
```

Abrir **http://localhost:4200**. El servidor de desarrollo redirige `/api` al back-end
mediante `proxy.conf.json`, así que no hace falta configurar CORS.

### Pruebas

```bash
cd backend  && ./mvnw test     # 59 pruebas
cd frontend && npm test        # 26 pruebas
```

---

## Arquitectura

Dos aplicaciones independientes que se comunican por una API REST acordada de antemano.

```
┌──────────────────────────┐         ┌──────────────────────────────┐
│  Angular (4200)          │  HTTP   │  Spring Boot (8080)          │
│                          │ ──────► │                              │
│  IdentityService         │  /api   │  MessageController           │
│    └ localStorage        │         │  ConfigController            │
│  ForumConfigService ─────┼─────────┤    └ GET /api/config         │
│  ForumApiService         │         │  MessageService (validación) │
│  MessageNodeComponent    │         │  MessageTree (ensamblado)    │
│    └ recursivo           │         │  MessageRepository ──────────┼──► data/messages.json
└──────────────────────────┘         └──────────────────────────────┘
```

### El árbol no se almacena

Ésta es la decisión central. En disco hay una **lista plana** de mensajes, cada uno con su
`parentId`:

```json
{
  "schemaVersion": 1,
  "messages": [
    { "id": "…", "content": "Hola", "parentId": null,  "authorName": "Mateo", … },
    { "id": "…", "content": "Qué tal", "parentId": "…", "authorName": "Ana",   … }
  ]
}
```

El árbol y la profundidad de cada mensaje se **derivan** de esas referencias en cada
lectura (`MessageTree`). No se guarda ningún campo `depth`, `replies` ni `children`.

Por qué: un dato almacenado puede desincronizarse de la estructura real. Si la profundidad
estuviera guardada y alguien editara un `parentId`, la aplicación mostraría un árbol que
contradice sus propios datos. Derivándola, eso es imposible por construcción.

### El límite de anidación es configurable y vive en un solo lugar

`backend/src/main/resources/application.yml`:

```yaml
forum:
  max-depth: 5      # el mensaje principal es el nivel 1
```

| Valor | Comportamiento |
|-------|----------------|
| `3` | tres niveles |
| `5` | cinco niveles (por defecto) |
| `0` o negativo | **ilimitado** |

Cambiar el comportamiento es editar esa línea y reiniciar. No hay código que tocar.

El back-end lo lee para validar; el front-end lo consulta por `GET /api/config` para
decidir si ofrece la acción de responder. **Ningún componente escribe ese número.** Con
anidación ilimitada, `GET /api/config` devuelve `maxDepth: null` — `null` significa «sin
límite», no «desconocido».

Ocultar el botón de responder es una mejora de experiencia, no la garantía: el servidor
rechaza con `422 MAX_DEPTH_EXCEEDED` cualquier intento de superar el límite, aunque la
petición esquive la interfaz.

### Persistencia segura sobre un archivo plano

Un archivo no ofrece transacciones. `JsonMessageRepository` compensa con dos garantías:

- **Escritura atómica**: se escribe a `messages.json.tmp` y se reemplaza con `ATOMIC_MOVE`.
  Una interrupción deja intacto el archivo anterior, nunca uno truncado a medias.
- **Acceso serializado**: un `ReentrantReadWriteLock` permite lecturas simultáneas pero
  ninguna se solapa con una escritura.

Es la única clase que conoce la ruta y el formato del archivo. Reemplazar el JSON por otro
mecanismo no requeriría tocar nada fuera de ese paquete.

---

## API

Contrato completo en
[`specs/001-foro-jerarquico/contracts/rest-api.md`](specs/001-foro-jerarquico/contracts/rest-api.md).

| Método | Ruta | Qué hace |
|--------|------|----------|
| `GET` | `/api/config` | Límites y conjunto de avatares |
| `GET` | `/api/conversations` | Todas las conversaciones, como árboles |
| `GET` | `/api/conversations/{id}` | Una conversación |
| `POST` | `/api/messages` | Crea un mensaje principal (`parentId` nulo) o una respuesta |

Todos los errores comparten la misma forma, y el cliente decide sobre `code`, nunca sobre
el texto:

```json
{ "code": "MAX_DEPTH_EXCEEDED", "message": "…", "field": "parentId", "timestamp": "…" }
```

| HTTP | Cuándo |
|------|--------|
| `400` | Petición mal formada o campo inválido (`CONTENT_EMPTY`, `AVATAR_INVALID`, …) |
| `404` | `PARENT_NOT_FOUND`, `CONVERSATION_NOT_FOUND` |
| `422` | `MAX_DEPTH_EXCEEDED` — la petición es válida; falla una regla sobre el estado del árbol |
| `500` | `STORE_UNREADABLE` — el archivo existe pero no se puede interpretar |

---

## Identidad

No hay autenticación. Al entrar se declara un nombre y se elige un avatar de un conjunto
cerrado; queda en `localStorage`.

Cada mensaje guarda **copiados** el nombre y el avatar vigentes al publicarlo, en lugar de
referenciar a un participante. Así la autoría de lo ya publicado no depende de una sesión
posterior, y cambiar de identidad no reescribe el pasado.

---

## Estructura

```
backend/
  src/main/java/com/pulseboard/forum/
    config/      ForumProperties          límites leídos de application.yml
    domain/      Message, MessageTree     modelo y ensamblado del árbol
    repository/  JsonMessageRepository    única puerta al archivo
    service/     MessageService           validaciones y límite
    web/         controladores, DTO y manejo de errores
  data/messages.json                      almacén (no versionado; se crea solo)

frontend/src/app/
  core/          modelos, servicios y guard de identidad
  shared/        avatar
  features/
    identity/            alta y cambio de identidad
    conversation-list/   lista de conversaciones
    conversation/        nodo recursivo y formulario

specs/001-foro-jerarquico/    especificación, plan y contrato
.specify/memory/constitution.md   principios que gobiernan el proyecto
```

## Ramas

- `dev` — desarrollo
- `main` — producción; no recibe commits directos, solo integra desde `dev`
