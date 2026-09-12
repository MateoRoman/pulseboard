# Phase 1 — Contrato REST: Foro con conversaciones jerárquicas

**Feature**: `001-foro-jerarquico` | **Date**: 2026-09-11 | **Versión del contrato**: 1.0

Este documento es el acuerdo entre el front-end Angular y el back-end Java. El Principio II
de la constitución exige que exista **antes** de implementar cualquiera de los dos lados, y
que se actualice en el mismo cambio que lo modifique. Un contrato desactualizado es un
defecto, no deuda técnica.

**Base**: `/api` · **Formato**: JSON, UTF-8 · **Autenticación**: ninguna (fuera de alcance)

---

## Convenciones

- El servidor asigna `id` y `createdAt`. El cliente que los envíe los verá ignorados.
- Las marcas de tiempo son ISO-8601 en UTC con milisegundos: `2026-09-11T14:22:08.412Z`.
- El front-end **nunca** recibe rutas de archivo, offsets ni detalles del almacenamiento
  (Principio II). Que detrás haya un archivo JSON es invisible en este contrato.
- Todo error se devuelve con la forma estable descrita en *Errores* y con un código HTTP
  significativo. Ninguna respuesta `200` transporta un fallo (Principio II).

---

## `GET /api/config`

Devuelve los parámetros que el front-end necesita conocer pero no debe declarar.

**Por qué existe**: FR-018 exige que el límite de anidación viva en un único lugar. El
back-end lo necesita para validar; el front-end, para decidir si ofrece la acción de
responder. Este endpoint elimina la duplicación en vez de sincronizar dos constantes.

**Respuesta `200`**

```json
{
  "maxDepth": 5,
  "maxContentLength": 2000,
  "maxAuthorNameLength": 40,
  "avatars": ["avatar-01", "avatar-02", "avatar-03", "avatar-04",
              "avatar-05", "avatar-06", "avatar-07", "avatar-08"]
}
```

El front-end MUST usar estos valores para validar en el formulario y para decidir la
visibilidad de la acción de responder. MUST NOT incorporar ninguno de estos números por su
cuenta.

---

## `GET /api/conversations`

Lista las conversaciones, cada una como árbol completo.

No admite paginación: la especificación la declara fuera de alcance y asume un volumen de
cientos de mensajes.

**Respuesta `200`**

```json
{
  "conversations": [
    {
      "id": "3f2a1b7c-8d4e-4f10-9a23-5c6b7d8e9f01",
      "content": "¿Alguien probó la nueva versión?",
      "authorName": "Mateo",
      "authorAvatar": "avatar-03",
      "createdAt": "2026-09-11T14:22:08.412Z",
      "parentId": null,
      "depth": 1,
      "replies": [
        {
          "id": "7b1c2d3e-4f50-4a61-8b72-9c0d1e2f3a4b",
          "content": "Sí, funciona bien.",
          "authorName": "Ana",
          "authorAvatar": "avatar-07",
          "createdAt": "2026-09-11T14:25:31.008Z",
          "parentId": "3f2a1b7c-8d4e-4f10-9a23-5c6b7d8e9f01",
          "depth": 2,
          "replies": []
        }
      ]
    }
  ]
}
```

**Ordenamiento** — parte del contrato, no detalle de implementación:

- Conversaciones raíz: `createdAt` **descendente**, las más recientes primero.
- Respuestas de un mismo padre: `createdAt` **ascendente** (FR-021).
- Empate exacto en `createdAt`: desempate por `id` ascendente, para que el orden sea
  estable entre lecturas.

**Sobre `depth`**: es un campo **calculado** que el servidor expone por conveniencia del
cliente. No está almacenado (Principio I). El front-end puede usarlo para decidir el
tratamiento visual, pero MUST NOT compararlo contra un número propio: para saber si aún se
puede responder usa `maxDepth` de `/api/config`.

**Foro vacío**: `200` con `"conversations": []`. No es un error (FR-025).

---

## `GET /api/conversations/{id}`

Devuelve una conversación concreta, con la misma forma que un elemento de la lista
anterior. `{id}` MUST ser el `id` de un mensaje raíz.

- `200` — el árbol de esa conversación.
- `404` `CONVERSATION_NOT_FOUND` — no existe, o el `id` corresponde a una respuesta y no a
  una raíz.

---

## `POST /api/messages`

Crea un mensaje principal o una respuesta. La diferencia la marca `parentId`.

**Petición**

```json
{
  "content": "Sí, funciona bien.",
  "authorName": "Ana",
  "authorAvatar": "avatar-07",
  "parentId": "3f2a1b7c-8d4e-4f10-9a23-5c6b7d8e9f01"
}
```

| Campo | Obligatorio | Reglas |
|-------|-------------|--------|
| `content` | sí | 1..2000 caracteres tras recortar espacios |
| `authorName` | sí | 1..40 caracteres tras recortar espacios |
| `authorAvatar` | sí | Debe pertenecer al conjunto de `/api/config` |
| `parentId` | no | Ausente o `null` crea un mensaje principal (FR-007). Con valor, crea una respuesta (FR-008) |

**Respuesta `201`** con cabecera `Location: /api/messages/{id}`:

```json
{
  "id": "7b1c2d3e-4f50-4a61-8b72-9c0d1e2f3a4b",
  "content": "Sí, funciona bien.",
  "authorName": "Ana",
  "authorAvatar": "avatar-07",
  "createdAt": "2026-09-11T14:25:31.008Z",
  "parentId": "3f2a1b7c-8d4e-4f10-9a23-5c6b7d8e9f01",
  "depth": 2,
  "replies": []
}
```

**Errores posibles**

| HTTP | `code` | Cuándo | Requisito |
|------|--------|--------|-----------|
| `400` | `CONTENT_EMPTY` | `content` vacío o solo espacios | FR-009 |
| `400` | `CONTENT_TOO_LONG` | `content` supera 2000 caracteres | FR-010 |
| `400` | `AUTHOR_NAME_EMPTY` | `authorName` vacío o solo espacios | FR-002 |
| `400` | `AUTHOR_NAME_TOO_LONG` | `authorName` supera 40 caracteres | FR-002 |
| `400` | `AVATAR_INVALID` | `authorAvatar` fuera del conjunto | FR-003 |
| `404` | `PARENT_NOT_FOUND` | `parentId` no corresponde a ningún mensaje | FR-014 |
| `422` | `MAX_DEPTH_EXCEEDED` | El padre ya está en `maxDepth` | FR-016, FR-017 |

**Por qué `422` y no `400` para la profundidad**: la petición está bien formada y todos sus
campos son válidos; lo que falla es una regla de negocio sobre el estado actual del árbol.
Distinguirlo permite al front-end reaccionar distinto — refrescar la vista, porque su idea
de la profundidad quedó desactualizada — en lugar de mostrar un error de validación de
formulario.

---

## Errores

Forma **única y estable** para toda respuesta de error (Principio II):

```json
{
  "code": "MAX_DEPTH_EXCEEDED",
  "message": "No se puede responder: el mensaje ya está en el nivel máximo de anidación.",
  "field": "parentId",
  "timestamp": "2026-09-11T14:26:02.115Z"
}
```

| Campo | Presencia | Descripción |
|-------|-----------|-------------|
| `code` | siempre | Identificador estable y legible por máquina. El front-end decide sobre este campo, nunca sobre `message` |
| `message` | siempre | Texto explicativo para mostrar a la persona |
| `field` | cuando aplica | Campo de la petición que originó el fallo |
| `timestamp` | siempre | Instante del error |

**Códigos HTTP usados**

| Código | Significado en este contrato |
|--------|------------------------------|
| `200` | Lectura correcta |
| `201` | Mensaje creado |
| `400` | Petición mal formada o campo inválido |
| `404` | El recurso referenciado no existe |
| `422` | Petición válida que infringe una regla de negocio |
| `500` | Fallo del servidor, incluido el almacenamiento ilegible (FR-029) |

El front-end MUST NOT inferir fallos a partir de cuerpos vacíos ni de respuestas `200`
ambiguas (Principio II).

---

## Fuera de este contrato

No existen endpoints para editar, borrar, votar, moderar, buscar, paginar, ni para
autenticar o gestionar participantes. Está fuera del alcance declarado en la constitución,
y añadirlos exige enmendarla primero.

La identidad del participante no tiene endpoints: vive en el navegador y viaja copiada en
cada `POST /api/messages` (FR-006).

## Trazabilidad

| Endpoint | Historias | Requisitos |
|----------|-----------|------------|
| `GET /api/config` | US1, US3 | FR-002, FR-003, FR-010, FR-018 |
| `GET /api/conversations` | US4, US5 | FR-019, FR-020, FR-021, FR-025, FR-027 |
| `GET /api/conversations/{id}` | US4 | FR-019, FR-021 |
| `POST /api/messages` | US2, US3 | FR-007..FR-014, FR-016, FR-017 |
