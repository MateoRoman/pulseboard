# Phase 1 — Quickstart: Foro con conversaciones jerárquicas

**Feature**: `001-foro-jerarquico` | **Date**: 2026-09-11

Guía para levantar la aplicación en local y validar que la primera versión funcional
cumple lo comprometido. No contiene código de implementación: eso pertenece a `tasks.md` y
a la fase de implementación.

## Prerrequisitos

| Requisito | Versión | Estado en el entorno (2026-09-11) |
|-----------|---------|-----------------------------------|
| JDK | 21 o superior | Presente — 21.0.6 LTS |
| Node.js | ≥ 22.12.0 | Presente — v22.15.1 |
| npm | ≥ 8 | Presente — 11.5.1 |
| Maven | no se requiere instalado | Se usa el wrapper `./mvnw` versionado |
| Angular CLI | no se requiere global | Se usa la dependencia local del proyecto |

**Nota sobre Node**: no actualices a Angular 22 sin subir Node antes. Angular 22 exige
`^22.22.3 || ^24.15.0 || >=26.0.0` y el entorno tiene 22.15.1. El proyecto fija Angular
21.2.x por esa razón — ver [research.md](./research.md) R1.

**Nota sobre `JAVA_HOME`**: ya está configurado a nivel de usuario en
`C:\Program Files\Java\jdk-21` desde el 2026-09-11. Es **obligatorio**: el `mvnw.cmd`
oficial aborta con `Error: JAVA_HOME not found in your environment` si la variable está
vacía, y no recurre al `java` del `PATH` (ver [research.md](./research.md) R3).

Si abriste la terminal antes de ese cambio, cerrala y abrí una nueva. Para comprobarlo:

```powershell
echo $env:JAVA_HOME          # C:\Program Files\Java\jdk-21
Test-Path "$env:JAVA_HOME\bin\javac.exe"   # True
```

En otra máquina, definilo apuntando a la raíz de un JDK 21 (la carpeta que contiene
`bin\javac.exe`, no el `javapath` de Oracle, que es solo un enlace).

---

## Levantar la aplicación

Hacen falta **dos terminales**: el back-end y el front-end corren a la vez.

### Terminal 1 — back-end

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

Queda escuchando en `http://localhost:8080`. La primera ejecución descarga Maven y las
dependencias, así que tarda más.

Comprobación rápida de que responde:

```bash
curl http://localhost:8080/api/config
```

Debe devolver `maxDepth`, los límites de longitud y el conjunto de avatares. El detalle de
la respuesta está en el [contrato REST](./contracts/rest-api.md).

### Terminal 2 — front-end

```bash
cd frontend
npm install
npm start
```

Queda en `http://localhost:4200` y redirige las llamadas a `/api` hacia el back-end
mediante `proxy.conf.json`, de modo que no hace falta configurar CORS para desarrollar
(ver [research.md](./research.md) R7).

Abrí `http://localhost:4200` en el navegador.

---

## Escenarios de validación

Cada uno corresponde a una historia de la [especificación](./spec.md) y se puede ejecutar
de forma independiente.

### V1 — Identidad (US1)

1. Abrí la aplicación sin identidad previa, o borrá `localStorage` para simularlo.
2. Verificá que **pide nombre y avatar antes de permitir publicar**.
3. Intentá continuar con el nombre vacío o solo con espacios → debe rechazarlo y explicar
   por qué.
4. Elegí un avatar → la selección debe verse reflejada antes de confirmar.
5. Recargá la página → nombre y avatar se conservan, sin volver a preguntar.

**Esperado**: FR-001..FR-004 cumplidos.

### V2 — Publicar un mensaje principal (US2)

1. Con identidad establecida y el foro vacío, publicá un mensaje.
2. Debe aparecer en la lista con su contenido, nombre, avatar y fecha, **sin recargar**.
3. Intentá publicar contenido vacío → rechazado, con lo escrito conservado.
4. Publicá un segundo mensaje → aparece como conversación adicional, sin reordenar la
   anterior.

**Esperado**: FR-007, FR-009, FR-023, FR-025, FR-026 cumplidos. Cronómetro para SC-006:
menos de 2 s hasta verlo.

### V3 — Responder y anidar (US3)

1. Respondé a un mensaje principal → la respuesta aparece asociada visiblemente a él.
2. Respondé a esa respuesta → se vincula a la respuesta, no al mensaje raíz.
3. Encadená respuestas hasta llegar al **nivel 5**.
4. En el mensaje de nivel 5, verificá que **la acción de responder ya no se ofrece**.
5. Forzá el límite por API, saltándote la interfaz:

   ```bash
   curl -i -X POST http://localhost:8080/api/messages \
     -H "Content-Type: application/json" \
     -d '{"content":"nivel 6","authorName":"Test","authorAvatar":"avatar-01","parentId":"<ID-DE-NIVEL-5>"}'
   ```

   Debe responder **`422`** con `"code": "MAX_DEPTH_EXCEEDED"`.
6. Probá un padre inexistente → **`404`** con `"code": "PARENT_NOT_FOUND"`.

**Esperado**: FR-008, FR-013, FR-014, FR-016, FR-017 cumplidos. El paso 5 es el que prueba
que el límite se valida en el servidor y no solo se oculta en la interfaz.

### V4 — Leer la jerarquía (US4)

1. Con una conversación que llegue al nivel 5, verificá que cada mensaje se muestra bajo su
   padre y que la profundidad se distingue visualmente.
2. Creá dos ramas paralelas bajo el mismo padre → debe verse dónde termina una y empieza
   otra.
3. Angostá la ventana a **360 px** → el contenido sigue legible y **no hay desbordamiento
   horizontal**.
4. Verificá que las respuestas de un mismo padre van de la más antigua a la más reciente.

**Esperado**: FR-020, FR-021, FR-022 cumplidos. El paso 3 valida SC-005.

### V5 — Persistencia (US5)

1. Con contenido anidado publicado, detené el back-end (`Ctrl+C`).
2. Inspeccioná `backend/data/messages.json` → lista plana con `parentId`, según
   [data-model.md](./data-model.md).
3. Reiniciá el back-end y recargá el navegador.
4. Todo el contenido, su jerarquía y su autoría siguen presentes.

**Esperado**: FR-027 cumplido, SC-003 al 100%.

### V6 — Casos borde

| Caso | Cómo provocarlo | Esperado |
|------|-----------------|----------|
| Arranque sin datos | Borrá `backend/data/messages.json` y reiniciá | Arranca vacío e invita a publicar, sin error (FR-028) |
| Almacenamiento corrupto | Escribí `{` suelto en el archivo y reiniciá | Falla de forma **explícita**, indicando ruta y problema. No arranca en silencio con datos vacíos (FR-029) |
| Marcado en el contenido | Publicá `<b>hola</b>` y `<script>alert(1)</script>` | Se muestran como **texto literal**. No se interpretan ni alteran la página (FR-024) |
| Marcado en el nombre | Usá `<i>Ana</i>` como nombre | Igual que el contenido: texto literal (FR-024) |
| Contenido excesivo | Pegá más de 2000 caracteres | Se informa **antes** de publicar (FR-010) |

---

## Pruebas automatizadas

### Back-end

```bash
cd backend
./mvnw test
```

Cubre lo que concentra el riesgo real, según [research.md](./research.md) R8: ensamblado
del árbol, derivación de profundidad, orden de hermanos, aplicación del límite y escritura
atómica.

### Front-end

```bash
cd frontend
npm test
```

---

## Comprobación final

La primera versión está completa cuando:

- [ ] V1..V6 pasan enteros.
- [ ] `./mvnw test` y `npm test` pasan en verde.
- [ ] La aplicación arranca desde cero siguiendo este documento al pie de la letra, en una
      máquina sin Maven ni Angular CLI globales.
- [ ] `backend/data/messages.json` no está versionado: es estado de ejecución, no código.
