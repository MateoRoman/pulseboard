---

description: "Task list for 001-foro-jerarquico"
---

# Tasks: Foro con conversaciones jerárquicas

**Input**: Design documents from `/specs/001-foro-jerarquico/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/rest-api.md](./contracts/rest-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidos. Los pide `plan.md` (Technical Context) y `research.md` R8, concentrados donde está el riesgo real: ensamblado del árbol, derivación de profundidad, aplicación del límite y escritura atómica.

**Organization**: agrupadas por historia de usuario, para poder implementar y validar cada una por separado.

**Revisión aplicada tras `/speckit-analyze` (2026-09-11)**: se corrigieron un hallazgo CRITICAL, dos HIGH y tres MEDIUM. Ver [Correcciones aplicadas](#correcciones-aplicadas) al final.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: historia a la que pertenece la tarea (US1..US5)
- Cada descripción lleva la ruta exacta del archivo

## Path Conventions

Aplicación web con dos proyectos hermanos, según `plan.md`:

- Back-end: `backend/src/main/java/com/pulseboard/forum/`, pruebas en `backend/src/test/java/com/pulseboard/forum/`
- Front-end: `frontend/src/app/`

---

## Phase 1: Setup e instalación de dependencias

**Purpose**: dejar ambos proyectos generados, con todas sus dependencias descargadas y **arrancando**, antes de escribir una sola línea de lógica.

- [ ] T001 Generar el scaffold del back-end en `backend/` con Spring Initializr: Spring Boot 4.1.1, Java 21, build Maven, dependencia `spring-boot-starter-web`, incluyendo Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`)
- [ ] T002 Verificar que `.\mvnw.cmd -v` funciona en `backend/`. `JAVA_HOME` **ya quedó definido** a nivel de usuario en `C:\Program Files\Java\jdk-21` el 2026-09-11: se comprobó que el `mvnw.cmd` oficial aborta sin esa variable y no cae al `java` del `PATH` (`research.md` R3). Si la terminal es anterior a ese cambio, abrir una nueva
- [ ] T003 Fijar en `backend/pom.xml` el `groupId` `com.pulseboard`, el `artifactId` `forum` y el paquete base `com.pulseboard.forum`; añadir `spring-boot-starter-test` como dependencia de prueba
- [ ] T004 **Instalar las dependencias del back-end**: ejecutar `.\mvnw.cmd -q dependency:go-offline` en `backend/` para descargar Maven y todo el árbol de dependencias, dejando la construcción reproducible sin red
- [ ] T005 Verificar que el back-end arranca: `.\mvnw.cmd spring-boot:run` en `backend/` responde en `http://localhost:8080`
- [ ] T006 [P] Generar el scaffold del front-end en `frontend/` con Angular CLI 21.2.x invocado por `npx`, sin instalación global
- [ ] T007 [P] Fijar en `frontend/package.json` la versión exacta de Angular 21.2.x y declarar `engines.node: ">=22.12.0"` — Angular 22 exige `^22.22.3` y el entorno tiene 22.15.1 (`research.md` R1)
- [ ] T008 **Instalar las dependencias del front-end**: ejecutar `npm install` en `frontend/` y verificar que no quedan avisos de incompatibilidad de `engines`
- [ ] T009 [P] Crear `frontend/proxy.conf.json` redirigiendo `/api` a `http://localhost:8080` y referenciarlo desde el script `start` de `frontend/package.json` (`research.md` R7)
- [ ] T010 Verificar que el front-end arranca: `npm start` en `frontend/` sirve en `http://localhost:4200`

**Checkpoint**: ambos proyectos arrancan con sus dependencias instaladas. Si algo falla aquí, no continuar.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: dominio, persistencia, contrato y **el armazón de la aplicación Angular** compartidos por todas las historias.

**⚠️ CRITICAL**: ninguna historia puede empezar hasta cerrar esta fase.

### Back-end: dominio y persistencia

- [ ] T011 [P] Crear `ForumProperties` en `backend/src/main/java/com/pulseboard/forum/config/ForumProperties.java` con `maxDepth`, `maxContentLength`, `maxAuthorNameLength` y el conjunto de avatares
- [ ] T012 [P] Declarar en `backend/src/main/resources/application.yml` los valores `forum.max-depth: 5`, `forum.max-content-length: 2000`, `forum.max-author-name-length: 40` y los avatares `avatar-01`..`avatar-08` — fuente única del límite (FR-018)
- [ ] T013 [P] Crear el record `Message` en `backend/src/main/java/com/pulseboard/forum/domain/Message.java` con `id` (UUID v4, estable y único — **FR-012**), `content`, `authorName`, `authorAvatar`, `createdAt` (ISO-8601 UTC con milisegundos — **FR-011**) y `parentId` (UUID o `null`). SIN campo `depth` ni `children`: prohibidos por el Principio I y FR-015
- [ ] T014 Crear la interfaz `MessageRepository` en `backend/src/main/java/com/pulseboard/forum/repository/MessageRepository.java` como única puerta al archivo (Principio III)
- [ ] T015 Implementar `JsonMessageRepository` en `backend/src/main/java/com/pulseboard/forum/repository/JsonMessageRepository.java`: leer y escribir el envoltorio `{"schemaVersion": 1, "messages": [...]}` sobre `backend/data/messages.json`, con escritura a `messages.json.tmp` y `Files.move(tmp, destino, ATOMIC_MOVE, REPLACE_EXISTING)`, y todo el acceso serializado por un `ReentrantReadWriteLock` (Principio III, FR-030)
- [ ] T016 Implementar en `backend/src/main/java/com/pulseboard/forum/repository/JsonMessageRepository.java` el arranque sin archivo (foro vacío, creación perezosa al primer mensaje, FR-028) y el fallo explícito ante JSON malformado, `schemaVersion` desconocida o mensaje individual inválido, indicando ruta y problema (FR-029). No descartar mensajes en silencio
- [ ] T017 Implementar `MessageTree` en `backend/src/main/java/com/pulseboard/forum/domain/MessageTree.java`: indexar por `id`, enlazar cada mensaje a su padre por `parentId`, ordenar hermanos por `createdAt` ascendente y raíces por `createdAt` descendente con desempate por `id` ascendente, y calcular la profundidad recorriendo desde cada raíz (raíz = 1). Omitir del árbol y registrar los mensajes cuyo `parentId` apunte a un `id` inexistente (FR-019, FR-021)

### Back-end: contrato y errores

- [ ] T018 [P] Crear los DTO de la API en `backend/src/main/java/com/pulseboard/forum/web/dto/` (`MessageResponse` con `replies` anidadas y `depth` calculado, `CreateMessageRequest`, `ConfigResponse`, `ConversationsResponse`), distintos del esquema persistido; la traducción entre ambos la exige el Principio III
- [ ] T019 [P] Crear `ApiError` y `ApiExceptionHandler` en `backend/src/main/java/com/pulseboard/forum/web/` con la forma estable `{code, message, field, timestamp}` y el mapeo a `400`, `404`, `422` y `500` del contrato (Principio II)
- [ ] T020 Implementar `ConfigController` en `backend/src/main/java/com/pulseboard/forum/web/ConfigController.java` con `GET /api/config`, devolviendo `maxDepth`, `maxContentLength`, `maxAuthorNameLength` y `avatars` desde `ForumProperties`

### Back-end: pruebas del núcleo

- [ ] T021 [P] Escribir `MessageTreeTest` en `backend/src/test/java/com/pulseboard/forum/domain/MessageTreeTest.java`: ensamblado desde lista desordenada, determinismo del resultado, profundidad derivada, orden de hermanos y desempate por `id`
- [ ] T022 [P] Escribir `JsonMessageRepositoryTest` en `backend/src/test/java/com/pulseboard/forum/repository/JsonMessageRepositoryTest.java`: arranque sin archivo, creación perezosa, ida y vuelta de lectura/escritura, fallo explícito ante JSON corrupto y ante `schemaVersion` desconocida

### Front-end: núcleo y armazón de la aplicación

- [ ] T023 [P] Crear los modelos del front-end en `frontend/src/app/core/models/` (`message.model.ts`, `participant.model.ts`, `forum-config.model.ts`) espejando el contrato REST
- [ ] T024 [P] Implementar `ForumApiService` en `frontend/src/app/core/services/forum-api.service.ts` como único punto de acceso a la API, decidiendo sobre el campo `code` de los errores y nunca sobre `message` (Principio II)
- [ ] T025 Implementar `ForumConfigService` en `frontend/src/app/core/services/forum-config.service.ts` consumiendo `GET /api/config` al arrancar y exponiendo los límites. MUST NOT declarar ninguno de esos números por su cuenta (FR-018)
- [ ] T026 Configurar el arranque de la aplicación en `frontend/src/app/app.config.ts`: registrar `provideHttpClient()` y `provideRouter(routes)`, y cargar la configuración del foro antes del primer render
- [ ] T027 Definir las rutas en `frontend/src/app/app.routes.ts`: ruta raíz a la lista de conversaciones y ruta de identidad, con redirección a identidad mientras no haya una establecida
- [ ] T028 Crear el armazón en `frontend/src/app/app.component.ts|html|css` con el `<router-outlet>`, la cabecera de la aplicación y el indicador de identidad activa; verificar que `frontend/src/main.ts` arranca contra `app.config.ts`
- [ ] T029 **Esqueleto vertical**: verificar con el back-end levantado que `http://localhost:4200` carga el armazón y que `ForumConfigService` obtiene `GET /api/config` **a través del proxy**, mostrando el `maxDepth` recibido. Es la primera prueba de que los dos lados se hablan

**Checkpoint**: dominio, persistencia, contrato y aplicación arrancable listos. Las historias pueden empezar.

---

## Phase 3: User Story 1 - Identificarse para participar (Priority: P1)

**Goal**: la persona declara nombre y avatar antes de poder publicar, y esa identidad se conserva.

**Independent Test**: abrir la aplicación sin identidad previa, completar nombre y avatar, y verificar que quedan asociados y visibles. No requiere que exista ningún mensaje.

- [ ] T030 [P] [US1] Implementar `IdentityService` en `frontend/src/app/core/services/identity.service.ts` persistiendo `{name, avatar}` en `localStorage` y exponiendo si hay identidad establecida (`research.md` R9, FR-004)
- [ ] T031 [US1] Crear el componente de identidad en `frontend/src/app/features/identity/identity.component.ts|html|css` con campo de nombre y selector de avatar, que bloquea publicar mientras no haya identidad (FR-001)
- [ ] T032 [US1] Aplicar en `frontend/src/app/features/identity/identity.component.ts` la validación del nombre: rechazar vacío o solo espacios, y limitar a `maxAuthorNameLength` (40) leído de `ForumConfigService`, nunca escrito a mano (FR-002)
- [ ] T033 [US1] Renderizar en `frontend/src/app/features/identity/identity.component.html` el conjunto de avatares que devuelve `GET /api/config`, con la selección reflejada visualmente antes de confirmar (FR-003)
- [ ] T034 [US1] Permitir cambiar nombre y avatar desde `frontend/src/app/features/identity/identity.component.ts`, sin alterar la autoría de lo ya publicado (FR-005)
- [ ] T035 [US1] Mostrar el nombre como texto literal, sin interpretar marcado incrustado, en `frontend/src/app/features/identity/identity.component.html` (FR-024)

**Checkpoint**: US1 funciona y se valida sola.

---

## Phase 4: User Story 2 - Publicar un mensaje principal (Priority: P1)

**Goal**: publicar un mensaje que inicia una conversación y verlo aparecer sin recargar, con su autoría.

**Independent Test**: con identidad establecida y el foro vacío, publicar un mensaje y verificar que aparece en la lista con su autor.

### Back-end

- [ ] T036 [P] [US2] Escribir `MessageServiceTest` en `backend/src/test/java/com/pulseboard/forum/service/MessageServiceTest.java` cubriendo las validaciones de contenido y nombre, y la de avatar contra el conjunto configurado
- [ ] T037 [US2] Implementar `MessageService` en `backend/src/main/java/com/pulseboard/forum/service/MessageService.java` con la creación de mensajes: generar `id` UUID v4 (**FR-012**) y `createdAt` (**FR-011**) en el servidor, ignorando los que envíe el cliente
- [ ] T038 [US2] Añadir en `backend/src/main/java/com/pulseboard/forum/service/MessageService.java` las validaciones de contenido: `1..2000 caracteres tras recortar espacios`, con `CONTENT_EMPTY` (400) y `CONTENT_TOO_LONG` (400) (FR-009, FR-010, **SC-007**)
- [ ] T039 [US2] Añadir en `backend/src/main/java/com/pulseboard/forum/service/MessageService.java` las validaciones de autoría: nombre `1..40 caracteres tras recortar espacios` con `AUTHOR_NAME_EMPTY` y `AUTHOR_NAME_TOO_LONG` (400), y avatar perteneciente al conjunto con `AVATAR_INVALID` (400) (FR-002, FR-003)
- [ ] T040 [US2] Copiar en cada mensaje el `authorName` y el `authorAvatar` vigentes al publicar, en `backend/src/main/java/com/pulseboard/forum/service/MessageService.java`, para que la autoría no dependa de una sesión posterior (FR-006)
- [ ] T041 [US2] Implementar `POST /api/messages` con `parentId` ausente o `null` en `backend/src/main/java/com/pulseboard/forum/web/MessageController.java`, respondiendo `201` con cabecera `Location: /api/messages/{id}` (FR-007)
- [ ] T042 [US2] Implementar `GET /api/conversations` en `backend/src/main/java/com/pulseboard/forum/web/MessageController.java` devolviendo los árboles completos; foro vacío responde `200` con `"conversations": []`, nunca un error (FR-025)

### Front-end

- [ ] T043 [P] [US2] Crear `message-node.component` **recursivo** en `frontend/src/app/features/conversation/message-node.component.ts|html|css`, que renderiza un mensaje (autor, avatar, contenido, fecha) y **se invoca a sí mismo** sobre `replies`. MUST NOT contener el número de niveles (Principio I). Es el renderizador que US2, US3 y US4 comparten
- [ ] T044 [US2] Crear `conversation.component` en `frontend/src/app/features/conversation/conversation.component.ts|html|css` que renderiza un árbol completo delegando en `message-node.component`
- [ ] T045 [US2] Crear `conversation-list.component` en `frontend/src/app/features/conversation-list/conversation-list.component.ts|html|css` consumiendo `GET /api/conversations` y delegando cada árbol en `conversation.component` (FR-020)
- [ ] T046 [US2] Crear `message-form.component` en `frontend/src/app/features/conversation/message-form.component.ts|html|css` para publicar mensajes principales, conservando lo escrito cuando la publicación se rechaza (FR-009)
- [ ] T047 [US2] Enlazar las rutas de `frontend/src/app/app.routes.ts` con los componentes reales: identidad y lista de conversaciones, con la redirección a identidad operativa
- [ ] T048 [US2] Reflejar el mensaje recién publicado en la vista sin recarga manual desde `frontend/src/app/features/conversation-list/conversation-list.component.ts` (FR-026, SC-006)
- [ ] T049 [US2] Mostrar el estado vacío comprensible cuando no hay mensajes, en `frontend/src/app/features/conversation-list/conversation-list.component.html` (FR-025)

**Checkpoint**: US1 y US2 funcionan. **Esto es el MVP: la aplicación se abre, se declara identidad, se publica y se lee.**

---

## Phase 5: User Story 3 - Responder a un mensaje (Priority: P2)

**Goal**: responder a cualquier mensaje por debajo del nivel máximo, con la respuesta vinculada a su padre.

**Independent Test**: con al menos un mensaje publicado, responder y verificar que queda vinculado a él y no a otro.

- [ ] T050 [P] [US3] Ampliar `MessageServiceTest` en `backend/src/test/java/com/pulseboard/forum/service/MessageServiceTest.java` con: padre inexistente, respuesta en nivel 4 aceptada, respuesta sobre nivel 5 rechazada, y que ningún rechazo persista el mensaje
- [ ] T051 [US3] Validar en `backend/src/main/java/com/pulseboard/forum/service/MessageService.java` que `parentId` referencia un mensaje existente, respondiendo `404` con `PARENT_NOT_FOUND` (FR-014)
- [ ] T052 [US3] Validar en `backend/src/main/java/com/pulseboard/forum/service/MessageService.java` que la profundidad resultante no excede `forum.max-depth`, respondiendo `422` con `MAX_DEPTH_EXCEEDED`. Leer el límite de `ForumProperties`, nunca escribir el número (FR-016, FR-017, FR-018, **SC-008**)
- [ ] T053 [US3] Extender `POST /api/messages` en `backend/src/main/java/com/pulseboard/forum/web/MessageController.java` para aceptar `parentId` con valor y crear respuestas (FR-008, FR-013)
- [ ] T054 [US3] Añadir la acción de responder por mensaje en `frontend/src/app/features/conversation/message-node.component.ts|html`, reutilizando `message-form.component`
- [ ] T055 [US3] Ocultar la acción de responder cuando `depth` del mensaje alcanza el `maxDepth` de `ForumConfigService`, en `frontend/src/app/features/conversation/message-node.component.ts`. Comparar contra el valor consultado, nunca contra un número propio (FR-017, FR-018)
- [ ] T056 [US3] Manejar en `frontend/src/app/core/services/forum-api.service.ts` la respuesta `422 MAX_DEPTH_EXCEEDED` refrescando la conversación, en lugar de mostrarla como error de validación de formulario
- [ ] T057 [US3] Manejar en `frontend/src/app/core/services/forum-api.service.ts` la respuesta `404 PARENT_NOT_FOUND` informando que el mensaje destino no está disponible (FR-014)

**Checkpoint**: US1, US2 y US3 funcionan por separado.

---

## Phase 6: User Story 4 - Leer la conversación en su jerarquía (Priority: P3)

**Goal**: la anidación es visible y la relación padre-hijo es evidente al leer, hasta el nivel 5.

**Independent Test**: cargar una conversación que llegue al nivel 5 y verificar que cada mensaje se muestra bajo su padre con la profundidad distinguible.

> El renderizador recursivo ya existe desde T043. Esta fase le da el tratamiento visual que vuelve legible la jerarquía.

- [ ] T058 [US4] Aplicar la subordinación visual por sangrado en `frontend/src/app/features/conversation/message-node.component.css`, de modo que la relación padre-hijo sea evidente sin inferirla del orden de lectura (FR-020)
- [ ] T059 [US4] Dejar de aumentar el sangrado a partir del nivel en que dejaría de ser legible, conservando la relación padre-hijo de forma inequívoca, en `frontend/src/app/features/conversation/message-node.component.css` (FR-022)
- [ ] T060 [US4] Garantizar que no hay desbordamiento horizontal desde 360 px de ancho, en `frontend/src/app/features/conversation/message-node.component.css` (**SC-005**)
- [ ] T061 [P] [US4] Mostrar nombre y avatar del autor junto a cada mensaje en `frontend/src/app/features/conversation/message-node.component.html` (FR-023, **SC-004**)
- [ ] T062 [P] [US4] Renderizar el contenido y el nombre como texto literal, sin interpretar marcado incrustado, en `frontend/src/app/features/conversation/message-node.component.html` (FR-024)
- [ ] T063 [P] [US4] Distinguir visualmente dónde termina una rama y empieza otra entre hermanos, en `frontend/src/app/features/conversation/message-node.component.css`

**Checkpoint**: la jerarquía se lee correctamente hasta el nivel máximo.

---

## Phase 7: User Story 5 - Recuperar la conversación al volver (Priority: P3)

**Goal**: el contenido, su jerarquía y su autoría sobreviven a un reinicio.

**Independent Test**: publicar contenido anidado, reiniciar la aplicación y verificar que todo sigue presente.

- [ ] T064 [P] [US5] Escribir en `backend/src/test/java/com/pulseboard/forum/repository/JsonMessageRepositoryTest.java` la prueba de persistencia entre reinicios: escribir un árbol, releer desde disco y comparar contenido, jerarquía y autoría (FR-027, **SC-003**)
- [ ] T065 [P] [US5] Escribir en `backend/src/test/java/com/pulseboard/forum/repository/JsonMessageRepositoryTest.java` la prueba de escrituras concurrentes: publicaciones solapadas conservan todos los mensajes, sin que una descarte o sobrescriba otra (FR-030, **SC-009**)
- [ ] T066 [US5] Implementar `GET /api/conversations/{id}` en `backend/src/main/java/com/pulseboard/forum/web/MessageController.java`, respondiendo `404 CONVERSATION_NOT_FOUND` si no existe o si el `id` corresponde a una respuesta y no a una raíz
- [ ] T067 [US5] Verificar el arranque con `backend/data/messages.json` ausente: foro vacío que invita a publicar, sin error (FR-028)
- [ ] T068 [US5] Verificar el arranque con `backend/data/messages.json` corrupto (JSON malformado y `schemaVersion` desconocida): fallo explícito indicando ruta y problema, sin arrancar en silencio con datos vacíos o parciales (FR-029)

**Checkpoint**: las cinco historias funcionan por separado.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T069 [P] Escribir pruebas de front-end en `frontend/src/app/core/services/identity.service.spec.ts` y `frontend/src/app/features/conversation/message-node.component.spec.ts`: persistencia de la identidad en `localStorage` y renderizado recursivo de un árbol de 5 niveles. Reemplazan el spec de ejemplo que genera `ng new`, para que `npm test` pruebe algo real
- [ ] T070 [P] Escribir el `README.md` en la raíz con instrucciones de ejecución verificadas: prerrequisitos, los dos comandos de arranque y las notas sobre `JAVA_HOME` y la versión de Node
- [ ] T071 [P] Documentar la arquitectura y el flujo front-end ↔ back-end en `README.md`
- [ ] T072 Ejecutar los escenarios V1..V6 de `specs/001-foro-jerarquico/quickstart.md` de principio a fin y corregir lo que falle (**SC-001, SC-002**)
- [ ] T073 Ejecutar `.\mvnw.cmd test` en `backend/` y `npm test` en `frontend/`, y dejar ambos en verde
- [ ] T074 Verificar que `backend/data/messages.json` no quedó versionado y que el Maven Wrapper sí lo está
- [ ] T075 Revisar que ningún componente del front-end ni validación del back-end incorpora el número 5 por su cuenta: el único lugar es `backend/src/main/resources/application.yml` (FR-018, Principio I)
- [ ] T076 Revisar que ninguna capa fuera de `backend/src/main/java/com/pulseboard/forum/repository/` accede al archivo (Principio III)
- [ ] T077 Verificar que el contrato de `specs/001-foro-jerarquico/contracts/rest-api.md` coincide con lo implementado; si divergió, actualizarlo en el mismo cambio (Principio II)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. T002 es crítica y temprana: valida el riesgo abierto de `JAVA_HOME`. T004 y T008 instalan dependencias; T005 y T010 confirman que ambos proyectos arrancan
- **Foundational (Phase 2)**: depende del Setup. **Bloquea todas las historias**. Cierra con T029, el esqueleto vertical que prueba que front y back se hablan
- **US1 (Phase 3)** y **US2 (Phase 4)**: ambas P1. Juntas forman el MVP
- **US3 (Phase 5)**: depende de que exista contenido al que responder (US2)
- **US4 (Phase 6)**: da tratamiento visual al renderizador que ya creó T043
- **US5 (Phase 7)**: la persistencia ya la aporta la fase Foundational; aquí se verifica y se completa
- **Polish (Phase 8)**: depende de todas las anteriores

### Dependencias internas relevantes

- T004 y T008 deben completarse antes de T005 y T010 respectivamente
- T015 y T016 comparten archivo: **secuenciales**, no paralelizables
- T017 depende de T013 (`Message`) y de T014 (interfaz del repositorio)
- T020 depende de T011 y T012
- T025 depende de T020: sin `GET /api/config` no hay límites que consultar
- T026, T027 y T028 son el armazón: T029 no se puede verificar sin las tres
- T032, T033 y T055 dependen de T025
- T037..T040 comparten `MessageService.java`: **secuenciales**
- T051 y T052 también tocan `MessageService.java`: secuenciales entre sí y posteriores a T040
- T044 y T045 dependen de T043 (el renderizador recursivo debe existir)
- T054, T055 y las tareas de US4 dependen de T043

### Parallel Opportunities

- **Setup**: T006, T007 y T009 en paralelo con la rama del back-end
- **Foundational**: T011, T012, T013 en paralelo; luego T018 y T019; T021 y T022; T023 y T024
- **US1**: T030 en paralelo con el back-end de otras fases
- **US2**: T036 en paralelo con T043
- **US4**: T061, T062 y T063 en paralelo entre sí
- **US5**: T064 y T065 en paralelo
- **Polish**: T069, T070 y T071 en paralelo

---

## Parallel Example: Phase 2 Foundational

```bash
# Primer bloque paralelo: configuración y dominio
Task: "Crear ForumProperties en backend/src/main/java/com/pulseboard/forum/config/ForumProperties.java"
Task: "Declarar los valores en backend/src/main/resources/application.yml"
Task: "Crear el record Message en backend/src/main/java/com/pulseboard/forum/domain/Message.java"

# Segundo bloque paralelo: DTO y manejo de errores
Task: "Crear los DTO de la API en backend/src/main/java/com/pulseboard/forum/web/dto/"
Task: "Crear ApiError y ApiExceptionHandler en backend/src/main/java/com/pulseboard/forum/web/"

# Tercer bloque paralelo: pruebas del núcleo y núcleo del front
Task: "Escribir MessageTreeTest en backend/src/test/java/com/pulseboard/forum/domain/MessageTreeTest.java"
Task: "Escribir JsonMessageRepositoryTest en backend/src/test/java/com/pulseboard/forum/repository/JsonMessageRepositoryTest.java"
Task: "Crear los modelos del front-end en frontend/src/app/core/models/"
Task: "Implementar ForumApiService en frontend/src/app/core/services/forum-api.service.ts"
```

---

## Implementation Strategy

### MVP: US1 + US2

Las dos historias P1 juntas son el mínimo que entrega valor. US1 sola solo establece una
identidad y no permite hacer nada con ella; US2 sin US1 no puede atribuir la autoría que
FR-006 exige.

1. Phase 1: Setup, con dependencias instaladas y ambos proyectos arrancando
2. Phase 2: Foundational — cierra con el esqueleto vertical (T029)
3. Phase 3 + Phase 4: US1 y US2
4. **PARAR Y VALIDAR**: escenarios V1 y V2 de `quickstart.md`
5. En este punto la aplicación **se abre, pide identidad, publica y lee mensajes**

### Entrega incremental

1. Setup + Foundational → base lista y comunicándose
2. + US1 + US2 → **MVP probable en navegador** (V1, V2)
3. + US3 → responder y anidar hasta 5 niveles (V3)
4. + US4 → la jerarquía se lee correctamente (V4)
5. + US5 → persistencia verificada entre reinicios (V5)
6. + Polish → tests de front, README, V6 y revisiones de principios

Cada paso añade valor sin romper el anterior.

---

## Correcciones aplicadas

Resultado de `/speckit-analyze` del 2026-09-11. La numeración cambió: 67 → **77 tareas**.

| Hallazgo | Severidad | Corrección |
|----------|-----------|------------|
| **C1** — Ninguna tarea ensamblaba la aplicación Angular: cinco componentes huérfanos sin `app.component`, rutas ni bootstrap | CRITICAL | **T026** (`app.config.ts` con `provideHttpClient` y `provideRouter`), **T027** (`app.routes.ts` con redirección a identidad), **T028** (`app.component` con `<router-outlet>` y `main.ts`), **T029** (esqueleto vertical verificado) y **T047** (enlazar rutas con los componentes reales) |
| **C2** — Dependencia hacia adelante: T043/T044 de US3 (P2) requerían `message-node.component`, creado en US4 (P3) | HIGH | `message-node.component` se crea ahora en **T043**, dentro de US2. US4 pasa a darle tratamiento visual, sin crearlo |
| **C3** — En el MVP ningún componente renderizaba un mensaje | HIGH | Resuelto por lo mismo: **T043**, **T044** y **T045** dejan la cadena de renderizado completa dentro de US2 |
| **C4** — Tests de front sin tarea que los escriba; `npm test` pasaría con el spec de ejemplo | MEDIUM | **T069** escribe pruebas reales de `IdentityService` y del renderizado recursivo de 5 niveles |
| **C5** — SC-008 sin trazabilidad | MEDIUM | Citado en **T052** |
| **C6** — FR-011 y FR-012 sin tarea que los cite | MEDIUM | Citados en **T013** y **T037** |
| *Petición adicional* | — | **T004** (`dependency:go-offline`) y **T008** (`npm install`) instalan dependencias; **T005** y **T010** verifican que cada proyecto arranca antes de escribir lógica |

---

## Notas

- La tarea **T002** es la más temprana con riesgo real: si `mvnw` no arranca sin `JAVA_HOME`,
  todo el back-end queda bloqueado. Resolverla antes de avanzar.
- La tarea **T029** es el primer punto donde se comprueba que la aplicación existe como tal
  y no como dos mitades sueltas. No saltarla.
- Las tareas **T075** y **T076** no son cosmética: verifican los dos principios que más
  fácilmente se violan durante la implementación — el número 5 filtrándose a un componente,
  y una capa accediendo al archivo por atajo.
- Las restricciones citadas entre comillas (`1..2000 caracteres tras recortar espacios`,
  `1..40 caracteres`) vienen literales de `data-model.md`, para no dejarlas al criterio del
  momento de implementar.
- Commitear por tarea o por grupo lógico, con los prefijos que fija la constitución.
