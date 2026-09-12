package com.pulseboard.forum.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un mensaje del foro, tal como se persiste.
 *
 * <p>Deliberadamente NO tiene campo {@code depth} ni {@code children}:
 *
 * <ul>
 *   <li>La profundidad se deriva de la cadena de ancestros al ensamblar el árbol
 *       ({@link MessageTree}). Almacenarla crearía un dato capaz de contradecir la
 *       estructura real (Principio I, FR-015).
 *   <li>La relación padre-hijo se guarda una sola vez, en el hijo, vía {@code parentId}.
 *       Duplicarla en el padre abriría la puerta a que ambas copias divergieran.
 * </ul>
 *
 * <p>El autor se guarda copiado ({@code authorName}, {@code authorAvatar}) y no como
 * referencia: la identidad vive en el navegador y no sobrevive necesariamente a la sesión,
 * así que un mensaje debe conservar con qué nombre y avatar se publicó (FR-006).
 *
 * @param id identificador estable y único, asignado por el servidor (FR-012)
 * @param content contenido textual, ya recortado en los extremos
 * @param authorName nombre del autor vigente al publicar
 * @param authorAvatar identificador de avatar vigente al publicar
 * @param createdAt instante de publicación, asignado por el servidor (FR-011)
 * @param parentId mensaje al que responde; {@code null} en los mensajes principales (FR-013)
 */
public record Message(
        UUID id,
        String content,
        String authorName,
        String authorAvatar,
        Instant createdAt,
        UUID parentId) {

    /** Un mensaje principal es exactamente aquel que no referencia a ningún padre. */
    public boolean isRoot() {
        return parentId == null;
    }
}
