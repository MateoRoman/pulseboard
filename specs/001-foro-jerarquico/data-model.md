# Phase 1 — Data Model: Foro con conversaciones jerárquicas

**Feature**: `001-foro-jerarquico` | **Date**: 2026-09-11

Define las entidades, sus reglas de validación y la forma exacta del almacenamiento.
Complementa el [contrato REST](./contracts/rest-api.md), que describe cómo viajan estos
datos por la API.

## Principio rector

Existe **una sola entidad persistida**: `Message`. El árbol de la conversación no se
almacena; emerge de las referencias `parentId` y se ensambla al leer. La profundidad
tampoco se almacena: se calcula. Esto es el Principio I de la constitución expresado como
modelo de datos, y es lo que hace imposible que la estructura mostrada contradiga a los
datos guardados.

---

## Entidad persistida: `Message`

| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| `id` | UUID v4 (string) | sí | Generado por el servidor. Estable durante toda la vida del mensaje. Nunca se reutiliza ni se reasigna |
| `content` | string | sí | Tras recortar espacios: longitud ≥ 1 y ≤ 2000 caracteres |
| `authorName` | string | sí | Tras recortar espacios: longitud ≥ 1 y ≤ 40 caracteres. Copiado del participante al publicar |
| `authorAvatar` | string | sí | Identificador de un avatar del conjunto predefinido. Copiado al publicar |
| `createdAt` | ISO-8601 UTC con milisegundos | sí | Asignado por el servidor. Nunca lo envía el cliente |
| `parentId` | UUID v4 (string) o `null` | sí (puede ser `null`) | `null` identifica un mensaje raíz. Si no es `null`, MUST referenciar un `id` existente |

**Campos deliberadamente ausentes**

- **`depth`** — prohibido por el Principio I y FR-015. Se deriva de la cadena de ancestros
  al ensamblar. Almacenarlo crearía un dato capaz de contradecir la estructura real.
- **`children`** — la relación se guarda una sola vez, en el hijo. Duplicarla en el padre
  abre la puerta a que ambas copias divergan.
- **`authorId`** — no existe entidad de participante en el servidor. El nombre y el avatar
  se copian al publicar (FR-006), de modo que la autoría no depende de una sesión posterior.

### Reglas de validación

| Regla | Requisito | Respuesta al incumplirse |
|-------|-----------|--------------------------|
| Contenido no vacío tras recortar espacios | FR-009 | `400` con código `CONTENT_EMPTY` |
| Contenido ≤ 2000 caracteres | FR-010 | `400` con código `CONTENT_TOO_LONG` |
| Nombre no vacío tras recortar espacios | FR-002 | `400` con código `AUTHOR_NAME_EMPTY` |
| Nombre ≤ 40 caracteres | FR-002 | `400` con código `AUTHOR_NAME_TOO_LONG` |
| Avatar dentro del conjunto predefinido | FR-003 | `400` con código `AVATAR_INVALID` |
| `parentId` referencia un mensaje existente | FR-014 | `404` con código `PARENT_NOT_FOUND` |
| La profundidad resultante no excede el límite | FR-016, FR-017 | `422` con código `MAX_DEPTH_EXCEEDED` |

El contenido y el nombre se almacenan tal como llegan, recortados en los extremos pero sin
escapar ni transformar. El tratamiento como texto literal (FR-024) es responsabilidad de la
presentación, no del almacenamiento: escapar al guardar corrompería el dato original.

---

## Entidad local (no persistida en el servidor): `Participant`

Vive únicamente en el navegador, en `localStorage`.

| Campo | Tipo | Reglas |
|-------|------|--------|
| `name` | string | 1..40 caracteres tras recortar espacios |
| `avatar` | string | Identificador de un avatar del conjunto predefinido |

No es una cuenta: sin credenciales, sin verificación, sin unicidad. Dos personas pueden
coincidir en nombre y avatar y el sistema no lo impide. Cambiarlos afecta solo a lo que se
publique después (FR-005), porque cada mensaje ya guardó su copia.

### Conjunto de avatares

Identificadores cerrados, resueltos a un icono en el front-end. No hay carga de imágenes
(FR-003):

```text
avatar-01 · avatar-02 · avatar-03 · avatar-04
avatar-05 · avatar-06 · avatar-07 · avatar-08
```

El back-end valida la pertenencia al conjunto; el front-end decide cómo se dibuja cada uno.

---

## Estructura derivada: `MessageTree`

Construida en memoria al leer. Nunca se persiste.

**Algoritmo de ensamblado**

1. Leer la lista plana completa del archivo.
2. Indexar todos los mensajes por `id` — una pasada.
3. Para cada mensaje con `parentId` no nulo, añadirlo a los hijos de su padre; los de
   `parentId` nulo forman el conjunto de raíces — segunda pasada.
4. Ordenar cada conjunto de hermanos por `createdAt` ascendente (FR-021); las raíces, por
   `createdAt` descendente (las conversaciones más recientes primero).
5. Asignar profundidad recorriendo desde cada raíz: raíz = 1, cada hijo = profundidad del
   padre + 1.

Coste lineal en el número de mensajes. Determinista: el resultado no depende del orden en
que estuvieran en el archivo (FR-019), porque el ordenamiento se aplica sobre `createdAt`.

**Casos límite del ensamblado**

| Situación | Comportamiento |
|-----------|----------------|
| Archivo vacío o inexistente | Cero raíces. El front-end muestra el estado vacío (FR-025, FR-028) |
| `parentId` apunta a un `id` inexistente | Datos inconsistentes en el archivo. Se registra y el mensaje se omite del árbol en lugar de quedar huérfano en una raíz falsa |
| Empate exacto en `createdAt` entre hermanos | Desempate por `id` ascendente, para que el orden mostrado sea estable entre lecturas |
| Ciclo de referencias | Imposible por construcción: `parentId` solo puede apuntar a un mensaje que ya existía al crear el hijo. El ensamblado acota igualmente el descenso al límite de profundidad |

---

## Esquema del archivo

Ruta: `backend/data/messages.json`. Se crea al publicar el primer mensaje (FR-028).

```json
{
  "schemaVersion": 1,
  "messages": [
    {
      "id": "3f2a1b7c-8d4e-4f10-9a23-5c6b7d8e9f01",
      "content": "¿Alguien probó la nueva versión?",
      "authorName": "Mateo",
      "authorAvatar": "avatar-03",
      "createdAt": "2026-09-11T14:22:08.412Z",
      "parentId": null
    },
    {
      "id": "7b1c2d3e-4f50-4a61-8b72-9c0d1e2f3a4b",
      "content": "Sí, funciona bien.",
      "authorName": "Ana",
      "authorAvatar": "avatar-07",
      "createdAt": "2026-09-11T14:25:31.008Z",
      "parentId": "3f2a1b7c-8d4e-4f10-9a23-5c6b7d8e9f01"
    }
  ]
}
```

**Por qué un objeto envolvente y no una lista suelta**: `schemaVersion` permite detectar un
archivo de formato desconocido y fallar de forma explícita (FR-029) en lugar de leerlo mal
en silencio. Una lista en la raíz no deja dónde ponerlo.

**Por qué lista plana y no anidada**: insertar es añadir al final, sin recorrer ni
reescribir ramas. Y la forma del archivo no depende del orden de inserción, lo que sostiene
la reconstrucción determinista de FR-019.

### Garantías de escritura

Exigidas por el Principio III y por FR-030:

1. Serializar el estado completo a `data/messages.json.tmp`.
2. Volcar a disco.
3. `Files.move(tmp, destino, ATOMIC_MOVE, REPLACE_EXISTING)`.

Una interrupción en cualquier punto deja el archivo anterior intacto: nunca truncado ni a
medio escribir. Todo el acceso pasa por un `ReentrantReadWriteLock` en memoria, de modo que
las lecturas concurrentes no se bloquean entre sí pero ninguna se solapa con una escritura.

### Lectura al arrancar

| Situación | Comportamiento | Requisito |
|-----------|----------------|-----------|
| El archivo no existe | Se arranca con el foro vacío; el archivo se crea al primer mensaje | FR-028 |
| JSON malformado | Fallo explícito al iniciar, indicando la ruta y el problema | FR-029 |
| `schemaVersion` desconocida | Fallo explícito indicando la versión encontrada y la esperada | FR-029 |
| Un mensaje individual no válido | Fallo explícito. No se descartan mensajes en silencio | FR-029 |

Arrancar con datos parciales sería peor que no arrancar: el usuario vería un foro
incompleto sin saberlo.

---

## Trazabilidad

| Elemento del modelo | Requisitos que satisface |
|---------------------|--------------------------|
| `id` estable y único | FR-012 |
| `parentId` explícito, `null` en raíces | FR-013 |
| Ausencia de `depth` persistido | FR-015, Principio I |
| `authorName` y `authorAvatar` copiados al publicar | FR-005, FR-006 |
| Validaciones de contenido y nombre | FR-002, FR-009, FR-010 |
| Validación de avatar contra el conjunto | FR-003 |
| Validación de `parentId` existente | FR-014 |
| Validación del límite de profundidad | FR-016, FR-017 |
| Ordenamiento de hermanos por `createdAt` | FR-021 |
| Ensamblado determinista | FR-019 |
| Escritura atómica con bloqueo | FR-030, Principio III |
| `schemaVersion` y fallo explícito al leer | FR-029 |
| Creación perezosa del archivo | FR-027, FR-028 |
