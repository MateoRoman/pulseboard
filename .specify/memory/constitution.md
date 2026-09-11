# Pulseboard Constitution

## Alcance del Aplicativo

Pulseboard es una aplicación de foro construida alrededor de una única idea: la
conversación se lee y se escribe como un árbol.

**Capacidades comprometidas**

- **AL-01 — Crear mensajes principales.** Un usuario puede iniciar una conversación
  nueva publicando un mensaje raíz.
- **AL-02 — Responder mensajes.** Un usuario puede responder a cualquier mensaje
  existente, sea raíz o respuesta.
- **AL-03 — Visualización jerárquica.** Una conversación se presenta con su estructura
  de anidación visible; la relación entre un mensaje y su padre debe ser evidente al
  leer, no inferida por el usuario.
- **AL-04 — Anidación multinivel.** La aplicación soporta respuestas anidadas en
  múltiples niveles, no un único nivel de respuesta plana.

**Fuera de alcance**

Las siguientes capacidades NO forman parte del producto salvo enmienda explícita a esta
constitución: autenticación y gestión de cuentas, edición y borrado de mensajes,
votación o reacciones, moderación, notificaciones, búsqueda y paginación de
conversaciones.

Ampliar el alcance es legítimo, pero MUST hacerse por enmienda y no por acumulación
silenciosa durante la implementación.

## Core Principles

### I. Jerarquía Primero

La conversación anidada es el dominio, no una característica de presentación. Toda
decisión de modelo, API o UI se evalúa primero por su efecto sobre el árbol de mensajes.

- Todo mensaje MUST tener una identidad estable y una referencia explícita a su padre.
  Un mensaje raíz se representa con padre nulo; no se admiten convenciones implícitas
  (cadenas vacías, índices posicionales, orden de inserción) para inferir parentesco.
- La relación padre-hijo MUST ser la única fuente de verdad de la estructura. La
  profundidad de un mensaje se DERIVA de la cadena de ancestros; nunca se almacena como
  dato editable que pueda desincronizarse.
- El árbol MUST poder reconstruirse de forma determinista desde los datos persistidos,
  sin depender del orden de lectura del archivo.
- Ningún componente MUST asumir un número fijo de niveles. El renderizado jerárquico se
  resuelve de forma recursiva o iterativa sobre profundidad arbitraria.

**Razón**: la anidación multinivel es el requisito diferenciador del producto. Un modelo
que ate la profundidad a la estructura de almacenamiento o a la plantilla de UI vuelve
costoso cualquier cambio sobre los niveles.

### II. Contrato API Explícito

Angular y Java se desarrollan contra un contrato acordado, no uno descubierto.

- El contrato REST (rutas, verbos, forma de request/response, códigos de error) MUST
  definirse y escribirse antes de implementar cualquiera de los dos lados.
- El front-end MUST NOT conocer detalles de la persistencia. Nunca recibe rutas de
  archivo, offsets ni estructuras internas del almacenamiento.
- Los cambios de contrato MUST actualizarse en la documentación en el mismo cambio que
  los introduce. Un contrato desactualizado se trata como defecto, no como deuda.
- Los errores MUST viajar con forma estable y código HTTP significativo; el front-end no
  infiere fallos a partir de cuerpos vacíos o respuestas 200 ambiguas.

**Razón**: son dos stacks distintos evolucionando en paralelo. Sin un contrato escrito, la
integración se convierte en el cuello de botella y el origen de los defectos más caros.

### III. Persistencia Encapsulada

El archivo JSON es un detalle de implementación detrás de una frontera, no un formato que
atraviese el sistema.

- Todo acceso al archivo MUST pasar por una capa de repositorio con interfaz explícita.
  Ninguna capa de dominio, servicio de aplicación o controlador lee o escribe el archivo
  directamente.
- Las escrituras MUST ser atómicas: escribir a temporal y reemplazar, nunca truncar y
  reescribir en sitio. Una escritura interrumpida no puede dejar el archivo corrupto.
- El acceso concurrente MUST estar serializado explícitamente. El modelo de concurrencia
  elegido se documenta junto a la implementación del repositorio.
- El esquema del JSON persistido MUST ser independiente de la forma de la respuesta HTTP.
  Se traduce entre ambos; no se reutiliza la misma clase por comodidad.
- Reemplazar el archivo JSON por otro mecanismo de persistencia MUST NOT requerir cambios
  fuera de la capa de repositorio.

**Razón**: un archivo plano no ofrece transacciones, bloqueo ni integridad referencial. Si
esa fragilidad se filtra al resto del sistema, deja de ser una decisión reversible.

### IV. Simplicidad Justificada

El proyecto se defiende por lo que hace, no por lo que incorpora.

- Toda dependencia añadida MUST tener una justificación registrada. Ante capacidad
  equivalente, se prefiere la biblioteca estándar o lo que ya trae el framework.
- No se introducen capas, patrones ni abstracciones para necesidades hipotéticas. La
  abstracción se agrega cuando existe el segundo caso real, no cuando se anticipa.
- La estructura de carpetas MUST ser navegable sin explicación previa: un lector debe
  poder ubicar dónde vive una responsabilidad a partir de los nombres.
- Ninguna funcionalidad fuera del alcance declarado MUST introducirse "porque es barata".

**Razón**: el alcance de esta aplicación es acotado y su persistencia es deliberadamente
simple. La complejidad que excede ese alcance no aporta capacidad y encarece cada cambio
posterior.

## Restricciones Tecnológicas

Estas restricciones son de cumplimiento obligatorio y no se negocian por conveniencia de
implementación. Modificarlas exige una enmienda a esta constitución.

- **TC-01 — Front-end: Angular.** La capa de presentación se implementa en Angular. No se
  admiten frameworks alternativos ni añadidos para porciones de la UI.
- **TC-02 — Back-end: Java.** La lógica de servidor se implementa en Java.
- **TC-03 — Persistencia: archivo JSON.** El estado se almacena en un archivo JSON. MUST
  NOT introducirse motores de base de datos, ORMs ni almacenes embebidos, ni siquiera para
  desarrollo o pruebas.
- **TC-04 — Control de versiones: GitHub.** El repositorio remoto canónico vive en GitHub.
- **TC-05 — Referencia funcional.** `http://leonidasesteban.github.io/react-discussions/`
  sirve como referencia de comportamiento e interacción. Es guía funcional, no
  especificación: las divergencias son legítimas si se justifican.
- **TC-06 — Ejecución local.** La aplicación MUST poder ejecutarse completa en local sin
  depender de servicios de terceros en tiempo de ejecución.

## Flujo de Trabajo y Control de Versiones

### Estrategia de ramas

- **`main`** es la rama de producción. Representa el estado entregable en todo momento.
  MUST NOT recibir commits directos: solo integra cambios provenientes de `dev`.
- **`dev`** es la rama de desarrollo. Todo el trabajo se realiza aquí o en ramas derivadas
  de ella.
- Las ramas de trabajo, cuando se usen, MUST partir de `dev` y volver a `dev`, nombradas
  por intención: `feature/<asunto>`, `fix/<asunto>`, `docs/<asunto>`.
- La promoción de `dev` a `main` ocurre cuando el estado de `dev` es coherente y
  ejecutable: la aplicación levanta y las capacidades comprometidas (AL-01 a AL-04)
  responden.
- `main` MUST NOT quedar nunca en un estado que no se pueda ejecutar siguiendo las
  instrucciones del README.

### Commits

- Cada commit MUST dejar el repositorio en un estado coherente y describir la intención
  del cambio, no el listado de archivos tocados.
- Los mensajes de commit siguen un prefijo de tipo: `feat`, `fix`, `docs`, `refactor`,
  `test`, `chore`.

### Documentación

- El README MUST contener instrucciones de ejecución verificadas: alguien que clone el
  repositorio debe poder levantar front-end y back-end siguiéndolas literalmente.
- La documentación se actualiza en el mismo cambio que la vuelve necesaria. Documentación
  que contradice al código se trata como defecto.

## Governance

Esta constitución prevalece sobre cualquier otra práctica, preferencia o convención del
proyecto. Ante conflicto entre este documento y una decisión de implementación, prevalece
este documento o se enmienda explícitamente.

**Procedimiento de enmienda**

1. La propuesta declara qué principio, restricción o límite de alcance cambia y por qué la
   regla vigente resulta insuficiente.
2. Se evalúa el impacto sobre los artefactos existentes (`spec.md`, `plan.md`, `tasks.md`,
   código ya integrado).
3. La enmienda se aplica sobre este archivo, con incremento de versión y fecha de última
   modificación actualizada, en un commit propio con prefijo `docs:`.

**Política de versionado**

Esta constitución usa versionado semántico, independiente de la versión de la aplicación:

- **MAJOR** — eliminación o redefinición incompatible de un principio o restricción.
- **MINOR** — incorporación de un principio, capacidad de alcance o sección nueva, o
  ampliación material de una guía existente.
- **PATCH** — aclaraciones, correcciones de redacción y precisiones sin cambio semántico.

**Revisión de cumplimiento**

- Toda integración hacia `dev` verifica que el cambio no contradiga estos principios ni
  exceda el alcance declarado.
- Toda promoción hacia `main` verifica además que las restricciones tecnológicas
  (TC-01 a TC-06) sigan intactas.
- La complejidad añadida MUST justificarse contra el Principio IV. Una justificación
  ausente es motivo suficiente para rechazar el cambio.
- El incumplimiento detectado se corrige o se enmienda la constitución. No se acumula como
  excepción tácita.

**Version**: 1.0.0 | **Ratified**: 2026-09-11 | **Last Amended**: 2026-09-11
