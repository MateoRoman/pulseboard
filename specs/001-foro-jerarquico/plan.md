# Implementation Plan: Foro con conversaciones jerárquicas

**Branch**: `dev` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-foro-jerarquico/spec.md`

## Summary

Primera versión funcional y ejecutable en local de un foro de discusión con conversaciones
jerárquicas: publicar mensajes principales, responder a cualquier mensaje, y leer la
conversación con su anidación visible hasta 5 niveles.

El enfoque técnico separa dos aplicaciones que se comunican por una API REST acordada de
antemano. El back-end en Java expone la API y es el único que toca el almacenamiento: guarda
los mensajes como **lista plana con referencia al padre** en un archivo JSON, y escribe de
forma atómica. El árbol no se almacena: se **ensambla en memoria al leer** y la profundidad
se deriva de la cadena de ancestros, de modo que la estructura nunca puede desincronizarse
de los datos. El front-end en Angular renderiza esa jerarquía de forma recursiva y consulta
al back-end el límite de anidación en lugar de declararlo por su cuenta.

## Technical Context

**Language/Version**: Java 21 (LTS, JDK 21.0.6 verificado en el entorno) · TypeScript 5.x
sobre Node.js ≥22.12.0 (v22.15.1 verificado)

**Primary Dependencies**: Spring Boot 4.1.1 (`spring-boot-starter-web`, incluye Jackson) ·
Angular 21.2.x

**Storage**: archivo JSON único en disco (`data/messages.json`). Sin base de datos, sin ORM,
sin almacén embebido (TC-03)

**Testing**: JUnit 5 vía `spring-boot-starter-test`, concentrado en ensamblado del árbol,
derivación de profundidad, aplicación del límite y escritura atómica. En el front-end, el
arnés que genera `ng new` por defecto

**Target Platform**: ejecución local en Windows 10+ (desarrollo), portable a Linux y macOS.
Navegadores de escritorio y móvil desde 360 px de ancho

**Project Type**: aplicación web con front-end y back-end separados

**Performance Goals**: mensaje publicado visible en menos de 2 s (SC-006) · conversación de
200 mensajes con profundidad 5 renderizada completa y legible (SC-005)

**Constraints**: ejecución completamente local sin servicios de terceros (TC-06) ·
anidación limitada a 5 niveles declarados en un único lugar (FR-016, FR-018) · escrituras
atómicas y acceso serializado sobre el archivo (Principio III, FR-030)

**Scale/Scope**: cientos de mensajes por conversación, no decenas de miles · un único
usuario interactuando a la vez · 5 historias de usuario, 30 requisitos funcionales

**Decisiones condicionadas por el entorno** (detalle en [research.md](./research.md)):

- Angular se fija en **21.2.x**, no en la 22.1.8 más reciente: esta última exige Node
  `^22.22.3` y el entorno tiene 22.15.1. Angular 21.2.x pide `^22.12.0` y sí se cumple.
- El back-end se construye con **Maven Wrapper** versionado, porque no hay Maven ni Gradle
  instalados. Riesgo abierto: `JAVA_HOME` no está definido y `mvnw.cmd` lo consulta antes
  de recurrir al `PATH`; debe verificarse en el primer arranque.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Restricciones tecnológicas

| Restricción | Cumplimiento | Evidencia |
|-------------|--------------|-----------|
| TC-01 Angular | PASS | Front-end íntegramente en Angular 21.2.x; sin frameworks adicionales de UI |
| TC-02 Java | PASS | Back-end en Java 21 |
| TC-03 JSON sin BD ni ORM | PASS | Único almacén: `data/messages.json`. Sin dependencias de base de datos ni de mapeo objeto-relacional, tampoco en pruebas |
| TC-04 GitHub | PASS | `github.com/MateoRoman/pulseboard` |
| TC-05 Referencia funcional | PASS | `react-discussions` orienta la interacción; las divergencias se justifican |
| TC-06 Ejecución local | PASS | Ambas aplicaciones corren en local; ningún servicio de terceros en tiempo de ejecución |

### Principios

| Principio | Cumplimiento | Cómo lo satisface el diseño |
|-----------|--------------|------------------------------|
| I. Jerarquía Primero | PASS | Cada mensaje lleva `id` estable y `parentId` explícito (`null` en las raíces). La profundidad se calcula al ensamblar el árbol y no se persiste. El árbol se reconstruye indexando por `id`, sin depender del orden de lectura. El componente de renderizado es recursivo y no incorpora el número 5 |
| II. Contrato API Explícito | PASS | El contrato se escribe en [contracts/](./contracts/) antes de implementar cualquiera de los dos lados. El front-end nunca recibe rutas de archivo ni estructuras de almacenamiento. Los errores viajan con forma estable y código HTTP significativo |
| III. Persistencia Encapsulada | PASS | Interfaz `MessageRepository` como única puerta al archivo. Escritura a temporal y `ATOMIC_MOVE`. Acceso serializado por `ReentrantReadWriteLock`. El esquema persistido se traduce a los DTO de la API; no se reutiliza la misma clase |
| IV. Simplicidad Justificada | PASS con justificación | Spring Boot es una dependencia grande y su incorporación se justifica en [research.md](./research.md) R2. No se añaden starters adicionales. Sin capas especulativas: no hay caché, ni eventos, ni abstracción de almacenamiento más allá del repositorio que el Principio III ya exige |

### Alcance

El diseño cubre AL-01..AL-04 y no introduce nada de la lista de fuera de alcance: no hay
autenticación, ni edición o borrado, ni votos, ni moderación, ni notificaciones, ni
búsqueda, ni paginación. La identidad local (nombre y avatar) no es autenticación: no tiene
credenciales, no se verifica y no se persiste en el servidor como entidad propia.

**Resultado del gate: PASS.** Sin violaciones que requieran justificación en Complexity
Tracking.

### Re-evaluación posterior al diseño (Phase 1)

Revisados `data-model.md`, `contracts/` y `quickstart.md`: el diseño mantiene los seis
cumplimientos tecnológicos y los cuatro principios. Dos puntos merecen registro explícito:

- El endpoint `GET /api/config` existe únicamente para que el front-end conozca el límite
  de anidación sin declararlo. Es la pieza que hace cumplible FR-018 y no habilita ninguna
  capacidad fuera de alcance.
- Los DTO de la API (`MessageResponse`, con hijos anidados) tienen forma distinta al
  esquema persistido (lista plana). La traducción entre ambos es deliberada y la exige el
  Principio III.

## Project Structure

### Documentation (this feature)

```text
specs/001-foro-jerarquico/
├── plan.md              # Este archivo
├── spec.md              # Especificación funcional
├── research.md          # Phase 0 — decisiones técnicas
├── data-model.md        # Phase 1 — entidades y reglas
├── quickstart.md        # Phase 1 — guía de ejecución y validación
├── contracts/
│   └── rest-api.md      # Phase 1 — contrato REST
├── checklists/
│   └── requirements.md  # Checklist de calidad de la spec
└── tasks.md             # Phase 2 — generado por /speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── mvnw, mvnw.cmd, .mvn/          # Maven Wrapper versionado (no hay Maven global)
├── pom.xml
├── data/
│   └── messages.json              # Único almacén. Se crea al primer mensaje
└── src/
    ├── main/java/com/pulseboard/forum/
    │   ├── ForumApplication.java
    │   ├── config/
    │   │   └── ForumProperties.java      # forum.max-depth — fuente única del límite
    │   ├── domain/
    │   │   ├── Message.java              # id, content, authorName, authorAvatar,
    │   │   │                             # createdAt, parentId
    │   │   └── MessageTree.java          # ensamblado del árbol y cálculo de profundidad
    │   ├── repository/
    │   │   ├── MessageRepository.java    # interfaz — única puerta al archivo
    │   │   └── JsonMessageRepository.java# lectura, escritura atómica, bloqueo
    │   ├── service/
    │   │   └── MessageService.java       # validaciones, límite de profundidad
    │   └── web/
    │       ├── MessageController.java    # endpoints de mensajes
    │       ├── ConfigController.java     # GET /api/config
    │       ├── dto/                      # DTO de la API, distintos del esquema persistido
    │       └── ApiExceptionHandler.java  # forma estable de error
    └── test/java/com/pulseboard/forum/
        ├── domain/MessageTreeTest.java        # ensamblado, profundidad, orden
        ├── service/MessageServiceTest.java    # validaciones y límite
        └── repository/JsonMessageRepositoryTest.java  # atomicidad, arranque vacío

frontend/
├── package.json                   # Angular 21.2.x fijado explícitamente
├── proxy.conf.json                # dev server → back-end, evita CORS en desarrollo
└── src/app/
    ├── core/
    │   ├── models/                # Message, Participant, ForumConfig
    │   └── services/
    │       ├── forum-api.service.ts     # único punto de acceso a la API
    │       ├── identity.service.ts      # nombre y avatar en localStorage
    │       └── forum-config.service.ts  # límite consultado, nunca declarado
    └── features/
        ├── identity/               # alta de nombre y avatar (US1)
        ├── conversation-list/      # lista de conversaciones (US2)
        └── conversation/
            ├── conversation.component.*   # vista de una conversación
            ├── message-node.component.*   # recursivo — se invoca a sí mismo
            └── message-form.component.*   # publicar y responder
```

**Structure Decision**: aplicación web con dos proyectos hermanos, `backend/` y
`frontend/`, en la raíz del repositorio. La separación la impone el stack (TC-01 y TC-02 son
dos ecosistemas de construcción distintos) y refuerza el Principio II: cada lado se
construye y se prueba por separado contra el contrato acordado, no contra la
implementación del otro.

Dentro del back-end, las carpetas siguen la frontera que exige el Principio III:
`repository/` es el único paquete que conoce el archivo, y `web/dto/` mantiene la forma de
la API separada del esquema persistido. En el front-end, `message-node.component` es
recursivo por diseño: es lo que permite renderizar profundidad arbitraria sin que ningún
componente asuma un número fijo de niveles (Principio I).

## Complexity Tracking

> Sin violaciones del Constitution Check. No se requiere justificación en esta tabla.

La única dependencia de peso, Spring Boot, queda justificada en
[research.md](./research.md) R2 conforme al Principio IV, que exige registrar la razón de
cada dependencia añadida — no que no se añada ninguna.
