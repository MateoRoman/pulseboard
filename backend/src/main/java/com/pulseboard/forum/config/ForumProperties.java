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

    /**
     * Si la anidación tiene tope.
     *
     * <p>Un {@code max-depth} de cero o negativo expresa «sin límite». Se usa ese convenio
     * en lugar de un valor centinela grande (como {@code Integer.MAX_VALUE}) porque este
     * es un valor que una persona escribe a mano en {@code application.yml}: pedirle que
     * ponga {@code 2147483647} para decir «ilimitado» sería un enigma, y cualquier número
     * grande arbitrario seguiría siendo un tope disfrazado.
     *
     * <p>Todo el sistema consulta este método en lugar de comparar contra {@code maxDepth}
     * directamente, de modo que el convenio está definido en un solo sitio.
     */
    public boolean hasDepthLimit() {
        return maxDepth > 0;
    }
}
