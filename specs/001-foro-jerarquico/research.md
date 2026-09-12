# Phase 0 — Research: Foro con conversaciones jerárquicas

**Feature**: `001-foro-jerarquico` | **Date**: 2026-09-11

Resuelve las incógnitas técnicas previas al diseño. El stack está impuesto por la
constitución (TC-01..TC-04): Angular, Java, archivo JSON, GitHub. Lo que se investiga aquí
son las versiones, el andamiaje y los patrones, no la elección de tecnología.

## Entorno detectado

Verificado en la máquina de desarrollo el 2026-09-11:

| Herramienta | Estado | Versión |
|-------------|--------|---------|
| JDK | presente | 21.0.6 LTS (`javac` disponible) |
| Node.js | presente | v22.15.1 |
| npm | presente | 11.5.1 |
| Maven | **ausente** | — |
| Gradle | **ausente** | — |
| Angular CLI | **ausente** | — |
| `JAVA_HOME` | **sin definir** | — |

Este inventario condiciona R1 y R3.

---

## R1 — Versión de Angular

**Decisión**: Angular **21.2.x** (CLI `21.2.24`).

**Rationale**: la última versión publicada es 22.1.8, pero declara
`engines.node: ^22.22.3 || ^24.15.0 || >=26.0.0`. El entorno tiene Node **22.15.1**, que
**no satisface** ese rango. Angular 21.2.24 declara `^20.19.0 || ^22.12.0 || >=24.0.0`, que
sí se cumple. Fijar 21.2.x permite que el proyecto arranque con el entorno tal como está,
sin añadir una actualización de Node como prerrequisito del entregable.

**Alternatives considered**:

- *Angular 22.1.8 actualizando Node a ≥22.22.3 o a 24 LTS*: es la versión más reciente,
  pero convierte una actualización de runtime en requisito previo para ejecutar el
  entregable. Se rechaza para esta iteración; queda como camino de actualización natural y
  sin fricción una vez que el entorno mueva Node.
- *Angular 20.3.x*: cumple igual los requisitos de Node, pero es una línea anterior sin
  ventaja alguna sobre 21.2.x.

**Implicación**: el `package.json` fija la versión de forma explícita y el README declara
Node ≥22.12.0 como prerrequisito.

## R2 — Framework del back-end Java

**Decisión**: **Spring Boot 4.1.1** sobre **Java 21**, con `spring-boot-starter-webmvc`.

**Rationale**: es la versión por defecto que hoy ofrece Spring Initializr, cuyo catálogo ya
no incluye la línea 3.5.x. Java 21 figura entre las versiones soportadas (17, 21, 25, 26) y
coincide con el JDK instalado. El starter aporta enrutado REST, serialización JSON
(Jackson), validación, manejo de errores y servidor embebido: sin él, todo eso habría que
escribirlo a mano, lo que aumenta el volumen de código sin aportar capacidad.

**Alternatives considered**:

- *`com.sun.net.httpserver.HttpServer` del JDK, sin dependencias*: sería lo más afín a la
  preferencia del Principio IV por la biblioteca estándar, pero esa preferencia aplica
  «ante capacidad equivalente», y aquí no lo es: obligaría a implementar a mano el
  enrutado, la negociación de contenido, la serialización, la validación y el manejo de
  errores.
- *Javalin o Spark Java*: más livianos que Spring Boot, pero menos convencionales en el
  ecosistema Java y sin aportar nada que el proyecto necesite.

**Nota sobre el Principio IV**: Spring Boot es una dependencia grande y su incorporación
queda justificada aquí, como exige el principio. Esa justificación no se extiende a sus
módulos adicionales: cada starter que se agregue después requiere su propia razón.

## R3 — Construcción del back-end sin Maven instalado

**Decisión**: **Maven Wrapper** (`mvnw` / `mvnw.cmd`) versionado en el repositorio.

**Rationale**: no hay Maven ni Gradle en el entorno. El wrapper descarga y fija la versión
de Maven en el primer uso, de modo que `./mvnw` funciona sin instalación global y todos
construyen con la misma versión. Spring Initializr lo genera junto al proyecto.

**Alternatives considered**:

- *Pedir una instalación global de Maven*: añade un prerrequisito manual y abre la puerta a
  que cada entorno construya con una versión distinta.
- *Gradle Wrapper*: equivalente en mecánica; Maven se prefiere por alinearse con lo que
  genera Initializr por defecto.

**Riesgo descartado al implementar (2026-09-11)**: se había anticipado que `JAVA_HOME`, sin
definir en el entorno, bloquearía la construcción. La inspección inicial se hizo sobre el
`mvnw.cmd` de la rama `master` de Apache Maven Wrapper, que efectivamente aborta con
`Error: JAVA_HOME not found in your environment` si la variable está vacía.

**Ese no es el wrapper que genera Spring Initializr.** El proyecto trae el wrapper
`3.3.4` con `distributionType=only-script`, que no contiene lógica de `JAVA_HOME` en
absoluto: localiza el JDK por sí mismo. Verificado ejecutando `mvnw.cmd -v` con la variable
vacía, que devolvió Apache Maven 3.9.16 sobre `C:\Program Files\Java\jdk-21`.

`JAVA_HOME` quedó definido igualmente a nivel de usuario apuntando a ese JDK, pero **no es
un prerrequisito** del proyecto y el README no lo exige. Nota para otros entornos: el
`java.exe` del `PATH` en esta máquina es el *shim* `javapath` de Oracle, que no es una raíz
de JDK válida para `JAVA_HOME`; la raíz correcta es la carpeta que contiene `bin\javac.exe`.

## R4 — Estructura del archivo JSON y estrategia de escritura

**Decisión**: **lista plana** de mensajes, cada uno con su `parentId`, en un único archivo.
Escritura **atómica** mediante archivo temporal y `Files.move(..., ATOMIC_MOVE)`, con acceso
serializado por un `ReentrantReadWriteLock` en memoria.

**Rationale**: una lista plana permite reconstruir el árbol de forma determinista sin
depender del orden de lectura (FR-019) y convierte la inserción en un `append`, sin mutar
estructuras anidadas. La escritura atómica satisface el Principio III: una interrupción
deja el archivo previo intacto en lugar de truncado. El bloqueo de lectura/escritura
serializa el acceso concurrente (FR-030) con el modelo más simple que cubre el caso: un
único proceso escribiendo.

**Alternatives considered**:

- *JSON anidado que refleje el árbol*: legible a simple vista, pero insertar en profundidad
  exige recorrer y reescribir la rama, y la forma del archivo pasa a depender del orden de
  inserción.
- *Escritura directa sobre el archivo*: descartada de plano por el Principio III; una
  interrupción corrompe los datos.
- *Bloqueo de archivo del sistema operativo (`FileLock`)*: necesario solo si varios procesos
  compartieran el archivo, lo que no ocurre. Añade complejidad sin cubrir un caso real.

## R5 — Construcción del árbol y profundidad

**Decisión**: el árbol se ensambla **en memoria al leer**, indexando los mensajes por `id` y
enlazando cada uno a su padre en una sola pasada. La profundidad se **calcula** al ensamblar;
no se persiste.

**Rationale**: implementa directamente el Principio I y FR-015. Una sola pasada sobre la
lista basta para construir el índice y una segunda para enlazar, de modo que el coste es
lineal en el número de mensajes, holgado para el volumen asumido (cientos por conversación).
Al no almacenarse, la profundidad no puede desincronizarse de la estructura real.

**Alternatives considered**:

- *Persistir la profundidad en cada mensaje*: prohibido por el Principio I. Introduce un
  dato que puede contradecir la cadena de ancestros.
- *Persistir un `path` materializado*: acelera consultas por subárbol, que este producto no
  tiene. Complejidad sin caso de uso.

## R6 — Fuente única del límite de anidación

**Decisión**: el límite vive como propiedad de configuración del back-end
(`forum.max-depth`, valor 5) y el front-end lo **consulta**, no lo declara.

**Rationale**: FR-018 exige que el número esté en un único lugar. El back-end lo necesita
para validar (FR-017) y el front-end para decidir si ofrece la acción de responder. Si cada
lado lo escribiera por su cuenta habría dos fuentes que pueden divergir. Exponerlo en la
respuesta de la API elimina la duplicación y cumple además el Principio I: ningún
componente asume un número fijo.

**Alternatives considered**:

- *Constante compartida replicada en ambos lados*: viola FR-018 por construcción.
- *Solo validación en el back-end, sin que el front lo conozca*: el usuario descubriría el
  límite recibiendo un error tras escribir su respuesta, en vez de no ver la acción.

## R7 — Integración en desarrollo

**Decisión**: **proxy del servidor de desarrollo de Angular** (`proxy.conf.json`) hacia el
back-end.

**Rationale**: front-end y back-end corren en puertos distintos durante el desarrollo. El
proxy hace que el navegador vea un mismo origen, con lo que no hace falta configurar CORS
para trabajar en local. Es configuración del entorno de desarrollo, no código de la
aplicación.

**Alternatives considered**:

- *Habilitar CORS en el back-end*: introduce configuración de producción para resolver un
  problema de desarrollo.
- *Servir el build de Angular desde el back-end*: elimina el problema, pero rompe la
  recarga en caliente y vuelve lento el ciclo de trabajo.

## R8 — Pruebas

**Decisión**: **JUnit 5** (vía `spring-boot-starter-test`) en el back-end, concentrado en el
ensamblado del árbol, el cálculo de profundidad y la validación del límite. En el front-end,
el arnés que genera `ng new` por defecto.

**Rationale**: la lógica con riesgo real de error está en el back-end: construcción del
árbol, derivación de profundidad, aplicación del límite y escritura atómica. Ahí es donde
las pruebas pagan. No se busca cobertura amplia en esta iteración.

## R9 — Persistencia de la identidad local

**Decisión**: `localStorage` del navegador.

**Rationale**: la identidad es local, no verificada y no viaja al servidor como entidad
propia: cada mensaje lleva copiados el nombre y el avatar vigentes al publicarlo (FR-006).
`localStorage` sobrevive al cierre de la pestaña, que es el comportamiento que la
especificación asume al declarar fuera de alcance la recuperación tras limpiar el
almacenamiento local.

**Alternatives considered**:

- *`sessionStorage`*: se pierde al cerrar la pestaña y obligaría a redeclarar la identidad
  con demasiada frecuencia.
- *Persistir participantes en el back-end*: los convertiría en entidad del dominio con ciclo
  de vida propio, que es justo lo que la especificación evita al excluir las cuentas.

---

## Incógnitas resueltas

Ninguna marca `NEEDS CLARIFICATION` permanece abierta. Las decisiones R1 y R3 nacen de
limitaciones reales del entorno y quedan documentadas como tales, no como preferencias.
