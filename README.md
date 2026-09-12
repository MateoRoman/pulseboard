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

# Parte 2 — Análisis del código generado

## Arquitectura propuesta

### Componentes principales

**Back-end** — cinco paquetes, una responsabilidad cada uno:

| Paquete | Clase | Responsabilidad |
|---------|-------|-----------------|
| `config` | `ForumProperties` | Límites leídos de `application.yml`. Fuente única de `max-depth` |
| `domain` | `Message` | El mensaje tal como se persiste. Record inmutable |
| | `MessageTree` | Ensambla el árbol desde la lista plana y calcula profundidades |
| | `MessageNode` | Un mensaje ya situado en el árbol, con su profundidad y sus hijos |
| `repository` | `MessageRepository` | Interfaz. Única puerta al almacenamiento |
| | `JsonMessageRepository` | Implementación sobre archivo: escritura atómica y bloqueo |
| `service` | `MessageService` | Validaciones y reglas de negocio |
| `web` | `MessageController` · `ConfigController` | Endpoints |
| | `ApiExceptionHandler` | Traduce excepciones a la forma estable de error |
| | `dto/` | Forma de la API, distinta del esquema persistido |

**Front-end** — Angular 21 con componentes standalone y *signals*:

| Capa | Pieza | Responsabilidad |
|------|-------|-----------------|
| `core/services` | `ForumApiService` | Único punto de acceso HTTP |
| | `ForumConfigService` | Guarda los límites que decide el servidor |
| | `IdentityService` | Identidad local en `localStorage` |
| `core/guards` | `identityGuard` | Impide entrar sin identidad declarada |
| `features` | `IdentityComponent` | Alta y cambio de nombre/avatar |
| | `ConversationListComponent` | Lista de conversaciones y composición |
| | `ConversationComponent` | Una conversación |
| | **`MessageNodeComponent`** | **Renderizador recursivo** |
| | `MessageFormComponent` | Publicar y responder |

### Flujo Front-end ↔ Back-end

```
ARRANQUE
  provideAppInitializer ──── GET /api/config ─────────────►  ConfigController
  ForumConfigService    ◄─── {maxDepth, avatars, límites} ──┘ lee ForumProperties

LECTURA
  ConversationList      ──── GET /api/conversations ──────►  MessageController
                                                                  │ Repository.findAll()
                                                                  │ MessageTree.from(lista)
                        ◄─── {conversations:[{replies:[…]}]} ─────┘ ensambla el árbol
  MessageNode (recursivo) dibuja

ESCRITURA
  MessageForm           ──── POST /api/messages ──────────►  MessageController
                                                                  │ valida contenido y autor
                                                                  │ valida padre y profundidad
                                                                  │ Repository.append()
                        ◄─── 201 + mensaje con depth ────────────┘ temporal + ATOMIC_MOVE
  published → reload()  ──── GET /api/conversations ──────►  (vuelve a leer)
```

Dos decisiones de este flujo:

- **La configuración se carga antes del primer render.** Así ningún componente ve un estado
  donde `maxDepth` es desconocido, que es justo cuando resultaría tentador escribir el
  número a mano.
- **Tras publicar se recarga la conversación** en lugar de insertar el mensaje en el estado
  local. Más simple y sin riesgo de que la vista y el servidor diverjan.

### Manejo de datos

Tres representaciones del mismo mensaje, cada una con su forma:

| | En disco (`Message`) | En dominio (`MessageNode`) | En la API (`MessageResponse`) |
|---|---|---|---|
| Estructura | plana, con `parentId` | árbol enlazado | árbol anidado en `replies[]` |
| Profundidad | **no existe** | calculada | calculada, expuesta como `depth` |
| Hijos | **no existen** | `replies[]` en memoria | `replies[]` en el JSON |

La traducción es deliberada. Reutilizar la misma clase en las tres capas ataría el formato
de almacenamiento al contrato público: cualquier cambio en uno rompería el otro.

### Estrategia de persistencia

Archivo único `backend/data/messages.json`, con tres garantías (detalle en
[Persistencia segura sobre un archivo plano](#persistencia-segura-sobre-un-archivo-plano)):

- **Escritura atómica** — temporal + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`. Una
  interrupción deja intacto el archivo anterior, nunca uno a medio escribir.
- **Acceso serializado** — `ReentrantReadWriteLock`: varias lecturas se solapan entre sí,
  ninguna se solapa con una escritura.
- **`schemaVersion`** — permite detectar un formato desconocido y fallar explícitamente en
  lugar de leerlo mal en silencio.

---

## Funcionamiento del código

### Cómo se crean los comentarios

`MessageService.create()` — el servidor controla lo que el cliente no debe controlar:

```java
public MessageNode create(CreateMessageRequest request) {
    String content      = validatedContent(request.content());      // 1..2000, sin espacios
    String authorName   = validatedAuthorName(request.authorName()); // 1..40
    String authorAvatar = validatedAvatar(request.authorAvatar());   // del conjunto
    UUID   parentId     = validatedParent(request.parentId());       // existe y cabe

    Message message = new Message(
            UUID.randomUUID(),   // el id lo asigna el servidor
            content, authorName, authorAvatar,
            Instant.now(),       // la fecha también
            parentId);

    repository.append(message);
    return tree().findById(message.id()).orElseThrow();
}
```

- **`CreateMessageRequest` no tiene campos `id` ni `createdAt`.** Si el cliente los envía se
  ignoran por no existir en el DTO: el tipo lo impide, sin código defensivo.
- **Se relee el árbol para devolver el mensaje.** Calcular la profundidad aparte daría dos
  cálculos que pueden discrepar; releer garantiza que el `depth` devuelto es el mismo que
  verá cualquier lectura posterior.

La validación del padre es donde vive el límite de anidación:

```java
private UUID validatedParent(UUID parentId) {
    if (parentId == null) return null;                    // mensaje principal

    MessageNode parent = tree().findById(parentId)
        .orElseThrow(ForumException::parentNotFound);     // 404 PARENT_NOT_FOUND

    if (properties.hasDepthLimit() && parent.depth() >= properties.maxDepth()) {
        throw ForumException.maxDepthExceeded(properties.maxDepth());  // 422
    }
    return parentId;
}
```

### Cómo se almacenan

```java
public Message append(Message message) {
    lock.writeLock().lock();
    try {
        List<Message> messages = new ArrayList<>(read().messages());
        messages.add(message);
        write(new MessageStore(CURRENT_SCHEMA_VERSION, messages));
        return message;
    } finally {
        lock.writeLock().unlock();
    }
}

private void write(MessageStore store) {
    Files.write(tempFile, objectMapper.writeValueAsBytes(store));
    Files.move(tempFile, dataFile, ATOMIC_MOVE, REPLACE_EXISTING);
}
```

`append` es el nombre del método —«agregar un mensaje a la colección»—, no una escritura al
final del archivo: se **reescribe el archivo completo** en cada publicación.
`StandardOpenOption.APPEND` no aparece en ninguna línea del proyecto. El archivo es siempre
un documento JSON válido y completo.

Es O(n) por escritura. Aceptable para el volumen asumido (cientos de mensajes) y es el
precio de esa garantía.

### Cómo se renderizan los niveles de respuestas

El componente **se invoca a sí mismo** en su propia plantilla:

```html
<article class="node" [style.--indent]="indentLevel()">
  <div class="message"> … avatar, autor, contenido … </div>

  @if (message().replies.length) {
    <div class="replies">
      @for (reply of message().replies; track reply.id) {
        <app-message-node [message]="reply" (replied)="replied.emit()" />
      }
    </div>
  }
</article>
```

No hay bucle con un número de niveles ni `switch` por profundidad. La recursión termina
sola cuando `replies` está vacío.

La separación clave está en el TypeScript:

```typescript
protected canReply(): boolean {
  return this.config.canReplyTo(this.message().depth);            // límite del SERVIDOR
}

protected indentLevel(): number {
  return Math.min(this.message().depth - 1, MAX_VISUAL_INDENT);   // límite VISUAL
}
```

**Son dos límites distintos y deliberadamente independientes.** `maxDepth` es una regla de
producto que vive en el servidor; `MAX_VISUAL_INDENT` es cuántos escalones de sangrado
caben en pantalla. Gracias a esa separación, la anidación ilimitada no desborda la vista: el
sangrado deja de crecer, pero la guía vertical mantiene visible la relación padre-hijo.

Verificado con `grep`: el número del límite **solo aparece en `application.yml`**. Ni en
Java, ni en TypeScript, ni en HTML.

### Cómo se manejan las relaciones padre-hijo

La relación se guarda **una sola vez, en el hijo**, como `parentId`. El padre no tiene lista
de hijos en disco: duplicarla abriría la puerta a que ambas copias divergieran.

El árbol se ensambla en dos pasadas, coste lineal:

```java
public static MessageTree from(List<Message> messages) {
    // Pasada 1 — indexar por id y calcular profundidad
    Map<UUID, Message> index = new HashMap<>();
    for (Message m : messages) index.put(m.id(), m);

    Map<UUID, MessageNode> nodes = new HashMap<>();
    for (Message m : messages) {
        int depth = depthOf(m, index);
        if (depth < 0) { log.warn("huérfano, se omite"); continue; }
        nodes.put(m.id(), new MessageNode(m, depth));
    }

    // Pasada 2 — enlazar cada nodo con su padre
    List<MessageNode> roots = new ArrayList<>();
    for (MessageNode node : nodes.values()) {
        UUID parentId = node.message().parentId();
        if (parentId == null) roots.add(node);
        else nodes.get(parentId).addReply(node);
    }

    roots.sort(ROOTS_NEWEST_FIRST);
    roots.forEach(MessageNode::sortReplies);
    return new MessageTree(roots, nodes);
}
```

Y la profundidad se **deriva** recorriendo la cadena de ancestros:

```java
private static int depthOf(Message message, Map<UUID, Message> index) {
    int depth = 1;                     // el mensaje principal es nivel 1
    UUID parentId = message.parentId();
    int guard = index.size() + 1;      // cota contra referencias circulares

    while (parentId != null) {
        if (guard-- <= 0) return -1;
        Message parent = index.get(parentId);
        if (parent == null) return -1; // cadena rota: huérfano
        depth++;
        parentId = parent.parentId();
    }
    return depth;
}
```

Tres propiedades que esto garantiza:

- **Determinismo.** El resultado no depende del orden de lectura del archivo, porque el
  ordenamiento se aplica sobre `createdAt` con desempate por `id`. Hay una prueba que
  baraja la lista diez veces y compara la estructura resultante.
- **Imposible desincronizar.** Al no almacenar `depth`, no puede contradecir a `parentId`.
- **Huérfanos explícitos.** Un `parentId` que apunta a nada se registra y se omite, en vez
  de colgarse como raíz falsa: mostrarlo como mensaje principal sería mentir sobre su
  origen.

---

# Parte 3 — Identificación de mejoras

Cinco oportunidades de mejora sobre la solución entregada. **Ninguna está implementada**:
son propuestas.

---

## 1. La aplicación no percibe cambios externos

**Eje**: diseño de componentes · manejo de estado

### Problema

No existe ningún canal por el que la aplicación se entere de que el estado cambió fuera de
ella. Verificado por ausencia total de:

| Búsqueda en `frontend/src/app` | Resultado |
|---|---|
| `addEventListener` · `'storage'` · `BroadcastChannel` | ninguno |
| `setInterval` · `EventSource` · `WebSocket` | ninguno |

`IdentityService` lee `localStorage` **una sola vez**, al instanciarse:

```typescript
private readonly current = signal<Participant | null>(read());
```

Y `reload()` se dispara solo al arrancar y tras **la propia** publicación del usuario.

### Riesgo o impacto actual

Dos síntomas, de gravedad muy distinta:

- **Identidad** — con dos pestañas abiertas, cambiar de usuario en una no actualiza la
  otra. Se publica con la identidad equivocada, sin error ni aviso.
- **Contenido** — los mensajes publicados por otra persona **nunca aparecen** hasta
  recargar la página. En una aplicación cuyo propósito es que varios conversen, esto
  significa que dos personas no pueden mantener una conversación.

El segundo es el defecto de producto más serio del proyecto.

> **Matiz**: los mensajes ya publicados deben seguir mostrando a su autor original. Eso es
> correcto y deliberado (FR-006). El defecto es solo la identidad *activa*.

### Solución propuesta

Escalonada:

1. **Identidad entre pestañas** — escuchar el evento `storage` en `IdentityService` y
   actualizar la señal. ~10 líneas.
2. **Contenido, versión simple** — *polling* condicional con `If-None-Match`; el servidor
   responde `304` si no hubo cambios.
3. **Contenido, versión correcta** — *Server-Sent Events*: el servidor notifica y el
   cliente recarga solo entonces. Unidireccional, que es lo que se necesita; un WebSocket
   sería sobredimensionado.

### Beneficio esperado

El foro funciona como foro. Se elimina la publicación con identidad equivocada. El punto 2
además obliga a calcular una versión del estado, que es la base de la mejora 2.

---

## 2. Lectura y reconstrucción completa en cada operación

**Eje**: rendimiento

### Problema

`MessageService.create()` invoca `tree()` **tres veces** por publicación: en
`validatedParent()`, dentro de `repository.append()` al leer, y al final para devolver el
mensaje con su profundidad. Cada llamada lee el archivo completo, lo deserializa y
reconstruye el árbol desde cero. Cada escritura reserializa el archivo entero.

### Riesgo o impacto actual

Coste **O(n) por operación, O(n²) al poblar el foro**. Con cientos de mensajes es
imperceptible (200 mensajes concurrentes pasan en 1,3 s). A partir de unos miles, publicar
se degrada de forma visible, y el `ReentrantReadWriteLock` convierte esa lentitud en
contención: cada escritura bloquea todas las lecturas.

Hoy es una decisión consciente dentro del volumen asumido, no un defecto. Se vuelve uno
cuando ese supuesto cambie.

### Solución propuesta

Estado en memoria como fuente de lectura, archivo solo para durabilidad:

- Cargar el archivo una vez al arrancar y conservar el `Map<UUID, Message>`.
- Cachear el árbol ensamblado e invalidarlo al escribir.
- Si reescribir molesta, pasar a un registro de operaciones con compactación periódica.

El cambio queda **contenido en `JsonMessageRepository`**: ninguna otra clase se entera,
porque el Principio III ya puso esa frontera.

### Beneficio esperado

Ensamblar el árbol una vez por *cambio* en lugar de una vez por *petición*. Publicar deja
de escalar con el tamaño del foro.

---

## 3. Sin paginación ni carga incremental

**Eje**: escalabilidad

### Problema

`GET /api/conversations` devuelve **todas** las conversaciones con **todos** sus mensajes
anidados. El front las renderiza completas y, tras cada publicación, vuelve a pedir el
conjunto entero.

### Riesgo o impacto actual

La respuesta crece linealmente y sin tope. Con 5.000 mensajes son varios MB de JSON por
petición, y Angular construye un componente por mensaje: el desplazamiento se entrecorta.
El peor momento de carga coincide con el instante en que el usuario acaba de publicar y
espera respuesta inmediata.

La paginación está **explícitamente fuera del alcance** declarado: es una restricción
aceptada, no un descuido. Pero es la primera barrera real de escalabilidad.

### Solución propuesta

1. Paginar las conversaciones raíz (`?page=0&size=20`), sin tocar la anidación interna.
2. Cargar subárboles bajo demanda. `GET /api/conversations/{id}` ya existe y es la mitad
   del camino.
3. Sustituir la recarga completa tras publicar por la inserción local del mensaje que el
   `201` **ya devuelve** con su `depth` calculado.

El punto 3 es el de mejor relación coste/beneficio y no requiere cambiar el contrato.

### Beneficio esperado

El tiempo de carga inicial deja de depender del tamaño del foro. Se elimina un viaje de red
por publicación, aprovechando datos que hoy se descartan.

---

## 4. Sin protección contra abuso

**Eje**: seguridad

### Problema

`POST /api/messages` no tiene límite de frecuencia, ni de tamaño total del almacén, ni
forma alguna de identificar a quien publica. No hay autenticación —está fuera de alcance—
pero tampoco ningún otro control.

### Riesgo o impacto actual

Un script externo puede publicar miles de mensajes por segundo hasta llenar el disco. Cada
uno dispara además una reescritura completa del archivo, así que el servicio se degrada
mucho antes de agotar el almacenamiento. En ejecución local el riesgo es teórico; expuesto
en red, es la vulnerabilidad más directa del sistema.

### Solución propuesta

- *Rate limiting* por IP: N publicaciones por minuto, respondiendo `429 Too Many Requests`.
- Tope configurable de mensajes totales o tamaño del archivo, con error explícito.
- Registrar la IP de origen junto al mensaje, para rastrear abusos sin introducir cuentas.

### Beneficio esperado

El coste de un abuso deja de ser cero. El `429` le da al cliente una señal accionable,
coherente con el manejo de errores que ya existe.

---

## 5. Las respuestas no se pueden plegar

**Eje**: diseño de componentes · UI

### Problema

`MessageNodeComponent` renderiza **todas** las respuestas de un mensaje, siempre y
completas. No hay forma de contraer una rama:

```html
@if (message().replies.length) {
  <div class="replies">
    @for (reply of message().replies; track reply.id) {
      <app-message-node [message]="reply" … />   <!-- sin condición de visibilidad -->
    }
  </div>
}
```

El único control existente es `toggleReply()`, que muestra u oculta el **formulario** de
respuesta. No existe nada equivalente para las respuestas en sí.

### Riesgo o impacto actual

Una conversación con muchas respuestas empuja al resto fuera de la pantalla: para llegar a
la siguiente conversación hay que recorrer la anterior entera. No se puede ojear el foro.

Se agrava con la profundidad y con el ancho: a 360 px, un hilo activo de cinco niveles es
prácticamente innavegable. Y se compone con la mejora 3 — sin paginación **y** sin plegado,
el foro completo es un único muro continuo.

Es el patrón que cualquier usuario da por sentado en un hilo de comentarios, y su ausencia
se nota de inmediato.

### Solución propuesta

Un control de plegado por nodo, con contador de lo que se oculta:

```
▾ Ana · hace 2 h
  Me parece bien.
  ▸ 12 respuestas          ← plegado: un clic las expande
```

- **Estado local en el componente**, con el mismo patrón que ya usa `replying`:
  `collapsed = signal(false)`. El componente ya es recursivo, así que cada nodo gestiona el
  suyo sin coordinación externa.
- **Contador de descendientes** — recorrido recursivo sobre `replies`, barato y calculable
  en el cliente con los datos que ya llegan.
- **Criterio de plegado inicial** — expandido por defecto; plegado automático al superar un
  umbral de respuestas o de profundidad. El umbral debería seguir el mismo camino que
  `maxDepth`: configurable y consultado, nunca escrito en el componente.
- **Accesibilidad** — `aria-expanded` y `aria-controls` en el control, para que el estado
  sea legible por lectores de pantalla.
- El plegado es **estado de vista**, no del dominio: no viaja al servidor. Si conviene
  recordarlo entre recargas, `localStorage` por conversación alcanza.

### Beneficio esperado

Se puede ojear el foro y saltar de una conversación a otra sin recorrerlas. La aplicación
se vuelve usable en pantallas angostas, que es donde hoy más sufre.

Además, un nodo plegado **no renderiza su subárbol**: los nodos del DOM bajan de forma
proporcional a lo que esté contraído, lo que ataca por el lado del cliente el mismo
problema de volumen que la mejora 3 ataca por el lado del servidor. Y el control de
plegado es exactamente la superficie donde después encaja un «cargar más respuestas» si se
implementa la carga bajo demanda.

---

## Ramas

- `dev` — desarrollo
- `main` — producción; no recibe commits directos, solo integra desde `dev`
