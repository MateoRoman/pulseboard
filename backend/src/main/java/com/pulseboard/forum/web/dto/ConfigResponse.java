package com.pulseboard.forum.web.dto;

import com.pulseboard.forum.config.ForumProperties;
import java.util.List;

/**
 * Parámetros que el front-end necesita conocer pero no debe declarar.
 *
 * <p>Existe para que el límite de anidación tenga una sola fuente (FR-018): el servidor lo
 * usa para validar y el cliente lo consulta para decidir si ofrece responder. Duplicar el
 * número en ambos lados dejaría dos verdades capaces de divergir.
 *
 * @param maxDepth profundidad máxima, o {@code null} cuando la anidación es ilimitada. Se
 *     usa {@code null} y no un número enorme para que «sin límite» sea inequívoco en el
 *     JSON: un cliente no puede confundirlo con un tope alto ni hacer aritmética sobre él
 *     por accidente.
 */
public record ConfigResponse(
        Integer maxDepth, int maxContentLength, int maxAuthorNameLength, List<String> avatars) {

    public static ConfigResponse from(ForumProperties properties) {
        return new ConfigResponse(
                properties.hasDepthLimit() ? properties.maxDepth() : null,
                properties.maxContentLength(),
                properties.maxAuthorNameLength(),
                properties.avatars());
    }
}
