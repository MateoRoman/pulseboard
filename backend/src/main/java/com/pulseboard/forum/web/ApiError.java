package com.pulseboard.forum.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Forma única y estable de toda respuesta de error (Principio II).
 *
 * <p>El front-end decide sobre {@code code}, nunca sobre {@code message}. {@code field} se
 * omite del JSON cuando no aplica.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String field, Instant timestamp) {

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field, Instant.now());
    }
}
