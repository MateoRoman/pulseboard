# Feature Specification: Foro con conversaciones jerárquicas

**Feature Branch**: `dev`

**Feature Directory**: `specs/001-foro-jerarquico`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Foro de discusión con conversaciones jerárquicas. Alcance ratificado en la constitución del proyecto (AL-01..AL-04): (1) crear mensajes principales que inician una conversación nueva; (2) responder a cualquier mensaje existente, sea raíz o respuesta; (3) visualizar la conversación en formato jerárquico con la anidación visible y la relación padre-hijo evidente al leer; (4) soportar respuestas anidadas en múltiples niveles, no un único nivel plano. Fuera de alcance: autenticación y cuentas, edición y borrado de mensajes, votación o reacciones, moderación, notificaciones, búsqueda y paginación."

**Clarificaciones aplicadas** (2026-09-11):

- **Identidad**: sin autenticación. Al entrar, la persona declara un nombre y elige un
  avatar de un conjunto predefinido. Esa identidad se conserva durante la sesión.
- **Profundidad**: límite de **5 niveles** por defecto. Un mensaje principal es el nivel 1,
  de modo que admite hasta 4 respuestas encadenadas por debajo. Alcanzado el límite, la
  acción de responder deja de ofrecerse.

**Cambio funcional posterior** (2026-09-12): el límite pasó a ser **configurable**, con
soporte para topes numéricos (3, 5, …) y para **anidación ilimitada**. Ver FR-016..FR-018.
El valor por defecto sigue siendo 5.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Identificarse para participar (Priority: P1)

Una persona abre el foro por primera vez. Antes de poder publicar, declara con qué nombre
quiere aparecer y elige un avatar de un conjunto de opciones. A partir de ahí participa sin
que se le vuelva a preguntar.

**Why this priority**: sin identidad no hay autoría, y sin autoría una conversación
jerárquica es ilegible: no se distingue quién contesta a quién. Es la puerta de entrada a
todo lo demás.

**Independent Test**: se abre la aplicación sin identidad previa, se completa nombre y
avatar, y se verifica que quedan asociados a la sesión y visibles. No requiere que exista
ningún mensaje.

**Acceptance Scenarios**:

1. **Given** una persona que abre el foro sin identidad establecida, **When** accede,
   **Then** el sistema le pide un nombre y la selección de un avatar antes de permitirle
   publicar.
2. **Given** el formulario de identidad, **When** la persona intenta continuar sin nombre o
   solo con espacios, **Then** el sistema lo rechaza y explica el motivo.
3. **Given** el formulario de identidad, **When** la persona elige un avatar del conjunto
   disponible, **Then** la selección queda reflejada visualmente antes de confirmar.
4. **Given** una identidad ya establecida, **When** la persona vuelve a la aplicación en la
   misma sesión, **Then** conserva su nombre y avatar sin que se le pregunte de nuevo.
5. **Given** una identidad establecida, **When** la persona decide cambiar su nombre o su
   avatar, **Then** puede hacerlo, y el cambio afecta a lo que publique de ahí en adelante
   sin alterar la autoría de lo ya publicado.

---

### User Story 2 - Publicar un mensaje principal (Priority: P1)

Una persona ya identificada quiere plantear un tema. Escribe su mensaje, lo publica y lo ve
aparecer inmediatamente en la lista de conversaciones, firmado con su nombre y su avatar.

**Why this priority**: es la única acción que crea contenido desde cero. Sin ella el foro
está permanentemente vacío y ninguna otra funcionalidad tiene sobre qué operar.

**Independent Test**: con una identidad establecida y el foro vacío, se publica un mensaje
y se verifica que aparece en la lista con su autor. No requiere respuestas ni anidación.

**Acceptance Scenarios**:

1. **Given** el foro sin mensajes y una identidad establecida, **When** la persona escribe
   un contenido no vacío y lo publica, **Then** el mensaje aparece en la lista con su
   contenido, el nombre y el avatar de quien lo publicó, y su fecha de publicación.
2. **Given** el foro con mensajes existentes, **When** la persona publica uno nuevo,
   **Then** aparece como una conversación adicional sin alterar ni reordenar las
   existentes.
3. **Given** el formulario de publicación, **When** la persona intenta publicar contenido
   vacío o compuesto solo por espacios, **Then** el sistema rechaza la publicación, explica
   el motivo y conserva lo que la persona había escrito.

---

### User Story 3 - Responder a un mensaje (Priority: P2)

Una persona que lee una conversación quiere contestar a un mensaje concreto. Elige
responder sobre ese mensaje, escribe su respuesta y la ve aparecer asociada visiblemente al
mensaje al que contestó, no al final de una lista plana.

**Why this priority**: convierte el tablón en una conversación. Es el primer punto donde la
relación padre-hijo se vuelve observable para quien lee.

**Independent Test**: con al menos un mensaje publicado, se responde a ese mensaje y se
verifica que la respuesta queda vinculada a él y no a otro. No requiere anidación profunda.

**Acceptance Scenarios**:

1. **Given** una conversación con un mensaje principal, **When** la persona responde a ese
   mensaje, **Then** la respuesta aparece asociada a él y la asociación es visible sin
   necesidad de interpretar el orden de la lista.
2. **Given** un mensaje con varias respuestas, **When** la persona añade otra, **Then** la
   nueva se suma a las hermanas existentes bajo el mismo padre, respetando el orden
   cronológico.
3. **Given** una respuesta ya publicada por debajo del nivel máximo, **When** la persona
   responde a esa respuesta, **Then** el sistema la acepta y la vincula a la respuesta, no
   al mensaje principal.
4. **Given** un mensaje que ya está en el nivel máximo de anidación, **When** la persona lo
   lee, **Then** la acción de responder no se ofrece sobre ese mensaje.
5. **Given** una petición de respuesta que referencia un mensaje inexistente, **When** se
   intenta publicar, **Then** el sistema la rechaza e informa que el mensaje destino no
   está disponible.

---

### User Story 4 - Leer la conversación en su jerarquía (Priority: P3)

Una persona que llega a una conversación ya avanzada quiere entender quién contestó a
quién. Recorre el hilo y, en cada respuesta, identifica sin ambigüedad a qué mensaje
contesta y quién la escribió, aunque la conversación llegue al nivel máximo de profundidad.

**Why this priority**: es el requisito diferenciador del producto. Las historias anteriores
generan la estructura; esta la vuelve legible y justifica que el foro sea jerárquico y no
plano.

**Independent Test**: se carga una conversación preexistente que llegue al nivel 5 y se
verifica que cada mensaje se muestra bajo su padre y que la profundidad es distinguible.

**Acceptance Scenarios**:

1. **Given** una conversación con respuestas anidadas en varios niveles, **When** la
   persona la abre, **Then** cada mensaje se muestra bajo su padre y su nivel de
   profundidad es distinguible visualmente.
2. **Given** una conversación con ramas paralelas, **When** la persona la lee, **Then**
   puede distinguir dónde termina una rama y empieza otra.
3. **Given** una conversación que llega al nivel máximo en una pantalla estrecha, **When**
   la persona la abre, **Then** el contenido sigue siendo legible y no se desborda
   horizontalmente fuera de la vista.
4. **Given** cualquier conversación cargada, **When** se compara el orden mostrado con el
   orden de publicación, **Then** las respuestas de un mismo padre aparecen de la más
   antigua a la más reciente.
5. **Given** cualquier mensaje de la conversación, **When** la persona lo lee, **Then** ve
   el nombre y el avatar de quien lo escribió.

---

### User Story 5 - Recuperar la conversación al volver (Priority: P3)

Una persona que publicó mensajes ayer vuelve al foro hoy, después de que la aplicación
fuera reiniciada, y encuentra todo el contenido tal como lo dejó, con su estructura de
anidación y su autoría intactas.

**Why this priority**: sin persistencia el foro pierde su propósito, pero la funcionalidad
es demostrable solo una vez que existe contenido que preservar.

**Independent Test**: se publica contenido con varios niveles, se reinicia la aplicación y
se verifica que todo el contenido, su jerarquía y sus autores siguen presentes.

**Acceptance Scenarios**:

1. **Given** una conversación con mensajes anidados, **When** la aplicación se detiene y se
   vuelve a iniciar, **Then** todos los mensajes siguen disponibles con la misma estructura
   de anidación, los mismos datos y la misma autoría.
2. **Given** un arranque en el que no existe contenido previo, **When** la persona abre el
   foro, **Then** ve un estado vacío que le invita a publicar, no un error.

---

### Edge Cases

- **Nombre vacío o solo espacios**: no se permite continuar; se explica el motivo.
- **Nombre excesivamente largo**: existe un límite declarado y se informa al superarlo.
- **Identidad perdida entre sesiones**: la persona vuelve a declarar nombre y avatar; los
  mensajes que ya publicó conservan la autoría con la que se publicaron.
- **Contenido vacío o solo espacios**: la publicación se rechaza con un mensaje claro y sin
  perder lo escrito.
- **Contenido excesivamente largo**: existe un límite declarado; al superarlo el sistema lo
  informa antes de publicar, no después.
- **Respuesta a un mensaje inexistente**: se rechaza de forma explícita en lugar de crear un
  mensaje huérfano.
- **Respuesta sobre un mensaje en el nivel máximo**: la acción no se ofrece; si aun así se
  intenta, se rechaza con una explicación en lugar de crear un nivel 6 silencioso.
- **Anidación en el nivel máximo sobre pantalla estrecha**: la presentación deja de
  aumentar el sangrado pero conserva la relación padre-hijo de forma inequívoca.
- **Publicaciones casi simultáneas**: dos mensajes enviados con milisegundos de diferencia
  se conservan ambos; ninguno pisa al otro ni se pierde.
- **Almacenamiento ausente al primer arranque**: la aplicación inicia con el foro vacío y lo
  crea al primer mensaje, sin fallar.
- **Almacenamiento ilegible o corrupto**: la aplicación informa el problema de forma
  explícita en lugar de arrancar en silencio con datos incompletos o vacíos.
- **Contenido con marcado o caracteres especiales**: se muestra como texto literal; nunca se
  interpreta como formato ni altera la estructura de la página.
- **Nombre con marcado o caracteres especiales**: se muestra como texto literal, igual que
  el contenido de los mensajes.
- **Conversación con muchas ramas hermanas**: se muestran todas sin degradar la legibilidad
  de la jerarquía.

## Requirements *(mandatory)*

### Functional Requirements

**Identidad de participante**

- **FR-001**: El sistema MUST requerir que la persona declare un nombre y seleccione un
  avatar antes de permitirle publicar o responder.
- **FR-002**: El sistema MUST rechazar nombres vacíos o compuestos solo por espacios, y
  MUST aplicar un límite máximo de longitud al nombre, informándolo.
- **FR-003**: El sistema MUST ofrecer un conjunto predefinido de avatares seleccionables.
  La carga de imágenes propias queda fuera de alcance.
- **FR-004**: El sistema MUST conservar la identidad declarada durante la sesión, sin
  volver a solicitarla en cada publicación.
- **FR-005**: El sistema MUST permitir cambiar el nombre y el avatar; el cambio MUST NOT
  alterar la autoría de los mensajes ya publicados.
- **FR-006**: El sistema MUST registrar en cada mensaje el nombre y el avatar vigentes en
  el momento de publicarlo, de modo que su autoría no dependa de una sesión posterior.

**Creación de contenido**

- **FR-007**: El sistema MUST permitir publicar un mensaje principal que inicie una
  conversación nueva.
- **FR-008**: El sistema MUST permitir responder a cualquier mensaje existente que no haya
  alcanzado el nivel máximo de anidación.
- **FR-009**: El sistema MUST rechazar mensajes cuyo contenido esté vacío o se componga
  únicamente de espacios en blanco, informando el motivo.
- **FR-010**: El sistema MUST aplicar un límite máximo de longitud al contenido de un
  mensaje e informarlo antes de aceptar la publicación.
- **FR-011**: El sistema MUST registrar el instante de publicación de cada mensaje.

**Estructura jerárquica**

- **FR-012**: El sistema MUST asignar a cada mensaje un identificador estable y único que
  no cambie durante la vida del mensaje.
- **FR-013**: El sistema MUST registrar, para cada respuesta, una referencia explícita al
  mensaje al que responde; un mensaje principal MUST distinguirse por no tener tal
  referencia.
- **FR-014**: El sistema MUST rechazar una respuesta que referencie un mensaje inexistente.
- **FR-015**: El sistema MUST derivar la profundidad de cada mensaje de su cadena de
  ancestros, sin depender de un valor almacenado por separado.
- **FR-016**: El sistema MUST permitir configurar la profundidad máxima de anidación,
  contando el mensaje principal como nivel 1. La configuración MUST admitir tanto un tope
  numérico (por ejemplo 3 o 5) como **anidación ilimitada**. El valor por defecto es 5, de
  modo que un mensaje principal admite hasta 4 respuestas encadenadas por debajo.
- **FR-017**: Cuando hay un tope configurado, el sistema MUST NO ofrecer la acción de
  responder sobre un mensaje que ya se encuentra en el nivel máximo, y MUST rechazar
  cualquier intento de crear un mensaje que excedería ese límite. Con anidación ilimitada
  la acción MUST ofrecerse siempre y ningún mensaje MUST rechazarse por profundidad.
- **FR-018**: El límite de anidación MUST estar declarado en un único lugar del sistema.
  Ningún componente de presentación o de validación MUST incorporar el número por su
  cuenta.
- **FR-019**: El sistema MUST garantizar que la estructura de la conversación sea
  reconstruible de forma determinista, independientemente del orden en que se lean los
  datos almacenados.

**Presentación**

- **FR-020**: El sistema MUST mostrar cada respuesta subordinada visualmente al mensaje al
  que responde, de modo que la relación sea evidente sin inferirla del orden de lectura.
- **FR-021**: El sistema MUST ordenar las respuestas de un mismo padre cronológicamente, de
  la más antigua a la más reciente.
- **FR-022**: El sistema MUST mantener legible la conversación cuando la profundidad supera
  lo que el ancho disponible permite representar con sangrado creciente.
- **FR-023**: El sistema MUST mostrar el nombre y el avatar del autor junto a cada mensaje.
- **FR-024**: El sistema MUST mostrar el contenido de los mensajes y los nombres de los
  autores como texto literal, sin interpretar marcado incrustado.
- **FR-025**: El sistema MUST mostrar un estado vacío comprensible cuando no existen
  mensajes.
- **FR-026**: El sistema MUST reflejar un mensaje recién publicado en la vista sin requerir
  que la persona recargue manualmente.

**Persistencia**

- **FR-027**: El sistema MUST conservar todos los mensajes, su estructura de anidación y su
  autoría entre reinicios de la aplicación.
- **FR-028**: El sistema MUST iniciar correctamente con el foro vacío cuando no existe
  contenido previo.
- **FR-029**: El sistema MUST informar de forma explícita cuando el contenido almacenado no
  se pueda leer, en lugar de arrancar en silencio con datos vacíos o parciales.
- **FR-030**: El sistema MUST preservar todos los mensajes publicados de forma concurrente,
  sin que una publicación descarte o sobrescriba otra.

### Key Entities

- **Participante**: identidad local de quien usa la aplicación. Atributos: nombre elegido y
  avatar seleccionado de un conjunto predefinido. No es una cuenta: no tiene credenciales,
  no se verifica y no sobrevive necesariamente a la sesión.
- **Mensaje**: unidad de contenido publicada por un participante. Atributos: identificador
  estable y único, contenido textual, nombre y avatar del autor en el momento de publicar,
  instante de publicación y referencia al mensaje padre (ausente en los mensajes
  principales). Su profundidad no es un atributo propio: se deriva de la cadena de
  referencias hasta la raíz.
- **Conversación**: árbol de mensajes cuya raíz es un mensaje principal y cuya profundidad
  no excede el límite declarado. No es contenido creado por separado, sino la estructura
  que emerge de las referencias padre-hijo entre mensajes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona completa su identidad (nombre y avatar) y publica su primer
  mensaje principal en menos de 60 segundos desde que abre el foro, sin instrucciones
  previas.
- **SC-002**: Una persona responde a un mensaje concreto en como máximo dos interacciones
  desde que decide hacerlo.
- **SC-003**: El 100% de los mensajes publicados, su relación padre-hijo y su autoría
  sobreviven a un reinicio de la aplicación.
- **SC-004**: Dada una conversación que llega al nivel 5, una persona que la lee por primera
  vez identifica correctamente el mensaje padre y el autor de cualquier respuesta señalada,
  sin ayuda externa.
- **SC-005**: Una conversación de 200 mensajes con profundidad máxima se muestra completa y
  legible en pantallas de 360 px de ancho o más, sin desbordamiento horizontal de la vista.
- **SC-006**: Un mensaje recién publicado es visible en la conversación en menos de 2
  segundos desde que se confirma la publicación.
- **SC-007**: El 100% de los intentos de publicar contenido vacío son rechazados con una
  explicación comprensible, conservando lo que la persona había escrito.
- **SC-008**: El 100% de los intentos de responder por encima del nivel 5 son impedidos, y
  ninguno produce un mensaje almacenado fuera del límite.
- **SC-009**: Ninguna secuencia de publicaciones concurrentes produce pérdida de mensajes ni
  contenido ilegible al recargar.

## Assumptions

- **Identidad local y no verificada**: la identidad declarada no es una cuenta. No hay
  credenciales, verificación ni unicidad de nombres: dos participantes pueden coincidir en
  nombre y avatar, y el sistema no lo impide.
- **Alcance de la sesión**: la identidad se conserva mientras dura la sesión en el
  dispositivo. Recuperarla en otro dispositivo o tras limpiar el almacenamiento local queda
  fuera de alcance.
- **Uso local y sin control de acceso**: la aplicación se ejecuta en el entorno local. Toda
  persona con acceso puede publicar y responder.
- **Volumen modesto**: del orden de cientos de mensajes por conversación, no decenas de
  miles. Los criterios de éxito se fijan sobre ese orden de magnitud.
- **Sin paginación**: dado que la búsqueda y la paginación están fuera de alcance, una
  conversación se carga y se muestra completa.
- **Orden de conversaciones**: las más recientes primero; las respuestas dentro de una
  conversación, de la más antigua a la más reciente. Es el patrón habitual en foros y
  coincide con la referencia funcional.
- **Contenido solo textual**: los mensajes son texto plano. Adjuntos, imágenes y formato
  enriquecido quedan fuera de esta iteración; los avatares son un conjunto cerrado, no
  archivos que la persona aporte.
- **Sin concurrencia real de usuarios**: se asume un único usuario interactuando a la vez.
  El requisito FR-030 protege ante escrituras solapadas, no ante colaboración simultánea.
- **Un solo idioma**: la interfaz no requiere internacionalización.
- **La referencia funcional es guía, no contrato**: `react-discussions` orienta el
  comportamiento y la interacción esperados; las divergencias justificadas son legítimas.
