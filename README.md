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

---

# Parte 1 — Generación de la solución mediante IA

## Herramientas utilizadas

- **Claude Code (Anthropic), modelo Opus 5** — agente de IA en terminal, con acceso al
  sistema de archivos, ejecución de comandos y consulta web. Única herramienta de
  generación de código.
- **GitHub Spec Kit v1.0.6** — toolkit de *Spec-Driven Development*, integrado a Claude Code
  como skills.

La elección de Spec Kit determinó el método. En lugar de pedir "hacé un foro" y corregir lo
que saliera, el desarrollo pasó por seis etapas con un artefacto revisable entre cada una:

```
/speckit-constitution → principios no negociables del proyecto
/speckit-specify      → especificación funcional, sin tecnología
/speckit-plan         → arquitectura, stack y contrato de API
/speckit-tasks        → 77 tareas ejecutables
/speckit-analyze      → auditoría de consistencia entre artefactos
/speckit-implement    → implementación
```

Cada documento se revisó y corrigió **antes** de dejar avanzar a la siguiente etapa. Ahí
está el valor: los errores se detectan cuando cuestan un párrafo, no cuando cuestan un
refactor.

## Prompts principales

| # | Prompt | Para qué |
|---|--------|----------|
| 1 | *"Instalar Spec Kit y arrancar el proyecto. Si falla algún paso, pará y mostrame el error exacto — no asumas que ya tengo nada."* | Forzar verificación del entorno en lugar de suposiciones |
| 2 | La premisa completa + stack + estrategia de ramas `dev`/`main` | Constitución del proyecto |
| 3 | Alcance AL-01..AL-04 + lista explícita de lo que queda **fuera** | Especificación |
| 4 | *"Validá que las tareas produzcan un aplicativo funcional, que se respete la persistencia en archivo JSON, y que cubra toda la premisa"* | Auditoría con criterios explícitos |
| 5 | *"Hacé las correcciones para que dé como resultado un aplicativo funcional que se pueda probar"* | Corrección e implementación |


## Cómo se refinaron los resultados

**Se acotó el contexto.** La IA abrió por su cuenta el PDF del enunciado y empezó a
incorporar los criterios de evaluación a la constitución del proyecto. Se cortó: la
constitución gobierna el producto, no el proceso de evaluación. Un documento de gobierno
con ruido se vuelve decorativo.

**Se podó la constitución.** De cinco principios a cuatro. Al reescribirla con el criterio
de "solo alcance y parámetros" apareció el hueco real: **no decía en ningún lado qué hace
la aplicación**. Se agregó la sección de alcance con AL-01..AL-04 y un bloque explícito de
*fuera de alcance*, que después fue lo que impidió que se colara funcionalidad "porque es
barata".

**Se resolvieron las ambigüedades a mano.** La especificación se frenó con dos marcadores
`[NEEDS CLARIFICATION]`. Ambos afectaban el alcance, así que se decidieron en lugar de
delegarlos: el modelo de identidad (nombre + avatar al entrar) y la existencia de un tope
de anidación.

**Se auditó antes de implementar.** Con las 77 tareas listas se pidió `/speckit-analyze`.
Encontró un bloqueante: **ninguna tarea ensamblaba la aplicación Angular**. Se creaban
cinco componentes pero nada tocaba el bootstrap ni las rutas; siguiendo las tareas al pie
de la letra, `npm start` habría levantado la página por defecto de Angular. Es el error
clásico de la generación asistida: cada pieza correcta y nadie las conecta. También salieron
una dependencia hacia adelante entre historias y unos tests de front que habrían pasado en
verde sin probar nada.

**Se verificó contra el artefacto real, no contra el reporte.** Con los 50 tests en verde,
se abrió `data/messages.json` a mano y apareció un campo `"root"` que no estaba en el
esquema documentado: Jackson serializaba el método `isRoot()` como propiedad. Un dato
derivado de `parentId` capaz de contradecirlo. Se corrigió y se agregaron las aserciones
que lo habrían detectado.

## Decisiones para adaptar o corregir el código generado

**El árbol no se almacena: se deriva.** Lista plana con `parentId` en disco, sin campos
`depth` ni `replies`. Un `depth` almacenado puede desincronizarse; derivándolo, eso es
imposible por construcción.

**El límite de anidación vive en un solo lugar.** La alternativa natural —una constante en
Java y otra en TypeScript— son dos verdades que divergen. Se creó `GET /api/config` para
eliminar la duplicación. Verificado: el número **solo aparece en `application.yml`**.

**Ocultar el botón no es una garantía.** El front oculta la acción al llegar al máximo
**y** el servidor rechaza con `422`. Si solo se ocultara, cualquier `curl` crearía un nivel
de más.

**`422` y no `400` para la profundidad.** La petición es válida; lo que falla es una regla
sobre el estado del árbol. Distinguirlo permite al front refrescar la vista en vez de
mostrar un error de formulario.

**MockMvc en lugar de agregar una dependencia.** `TestRestTemplate` exigía un artefacto
extra. MockMvc ya venía en el starter y cubre lo que se quería verificar.

**Se separó el límite de producto del límite visual.** El componente recursivo tiene su
propia constante de sangrado, independiente de `maxDepth`. Gracias a eso la anidación
ilimitada no desborda la pantalla.

**Correcciones forzadas por el entorno**, ninguna detectable leyendo documentación:

| Suposición del plan | Realidad |
|---|---|
| Angular 22 (última) | Exige Node `^22.22.3`; el entorno tiene 22.15.1 → Angular 21.2.24 |
| `spring-boot-starter-web` | Spring Boot 4 renombró los starters → `-webmvc` |
| Jackson en `com.fasterxml` | Boot 4 usa Jackson 3 → `tools.jackson` |
| `npm install` funciona | Falla con el grafo de peers de vitest → `legacy-peer-deps` |

---

## Ramas

- `dev` — desarrollo
- `main` — producción; no recibe commits directos, solo integra desde `dev`
