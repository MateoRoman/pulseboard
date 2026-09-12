package com.pulseboard.forum.web.dto;

import java.util.UUID;

/**
 * Petición de creación de un mensaje.
 *
 * <p>{@code parentId} ausente o {@code null} crea un mensaje principal (FR-007); con valor,
 * crea una respuesta (FR-008).
 *
 * <p>No incluye {@code id} ni {@code createdAt}: los asigna el servidor. Si el cliente los
 * enviara, se ignorarían por no existir aquí.
 */
public record CreateMessageRequest(
        String content, String authorName, String authorAvatar, UUID parentId) {}
