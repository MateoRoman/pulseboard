package com.pulseboard.forum.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del foro, leídos de {@code application.yml}.
 *
 * <p>Es la única fuente del límite de anidación (FR-018). Ningún otro punto del sistema
 * declara ese número: el servicio lo consulta para validar y el front-end lo obtiene por
 * {@code GET /api/config}.
 */
@ConfigurationProperties(prefix = "forum")
public record ForumProperties(
        int maxDepth,
        int maxContentLength,
        int maxAuthorNameLength,
        String dataFile,
        List<String> avatars) {

    public ForumProperties {
        avatars = avatars == null ? List.of() : List.copyOf(avatars);
    }

    public boolean isKnownAvatar(String avatar) {
        return avatars.contains(avatar);
    }
}
