package com.pulseboard.forum.web.dto;

import com.pulseboard.forum.domain.MessageNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Forma en que un mensaje viaja por la API.
 *
 * <p>Es distinta del esquema persistido a propósito (Principio III): aquí las respuestas
 * van anidadas y la profundidad viaja calculada, mientras que en disco se guarda una lista
 * plana sin profundidad. La traducción ocurre en {@link #from(MessageNode)}.
 *
 * <p>El campo {@code depth} se expone por conveniencia del cliente, que lo usa para el
 * tratamiento visual. El cliente NO debe compararlo contra un número propio: para saber si
 * aún se puede responder consulta {@code maxDepth} de {@code GET /api/config}.
 */
public record MessageResponse(
        UUID id,
        String content,
        String authorName,
        String authorAvatar,
        Instant createdAt,
        UUID parentId,
        int depth,
        List<MessageResponse> replies) {

    public static MessageResponse from(MessageNode node) {
        return new MessageResponse(
                node.message().id(),
                node.message().content(),
                node.message().authorName(),
                node.message().authorAvatar(),
                node.message().createdAt(),
                node.message().parentId(),
                node.depth(),
                node.replies().stream().map(MessageResponse::from).toList());
    }
}
