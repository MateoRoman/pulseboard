package com.pulseboard.forum.web.dto;

import com.pulseboard.forum.config.ForumProperties;
import java.util.List;

/**
 * Parámetros que el front-end necesita conocer pero no debe declarar.
 *
 * <p>Existe para que el límite de anidación tenga una sola fuente (FR-018): el servidor lo
 * usa para validar y el cliente lo consulta para decidir si ofrece responder. Duplicar el
 * número en ambos lados dejaría dos verdades capaces de divergir.
 */
public record ConfigResponse(
        int maxDepth, int maxContentLength, int maxAuthorNameLength, List<String> avatars) {

    public static ConfigResponse from(ForumProperties properties) {
        return new ConfigResponse(
                properties.maxDepth(),
                properties.maxContentLength(),
                properties.maxAuthorNameLength(),
                properties.avatars());
    }
}
