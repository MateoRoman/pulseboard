package com.pulseboard.forum.service;

import org.springframework.http.HttpStatus;

/**
 * Fallo de negocio con código estable y estado HTTP asociado.
 *
 * <p>El código es lo que el front-end interpreta; el mensaje es lo que se muestra a la
 * persona. Separarlos permite cambiar el texto sin romper al cliente (Principio II).
 */
public class ForumException extends RuntimeException {

    private final String code;
    private final String field;
    private final HttpStatus status;

    private ForumException(HttpStatus status, String code, String field, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public HttpStatus status() {
        return status;
    }

    private static ForumException badRequest(String code, String field, String message) {
        return new ForumException(HttpStatus.BAD_REQUEST, code, field, message);
    }

    public static ForumException contentEmpty() {
        return badRequest("CONTENT_EMPTY", "content", "El mensaje no puede estar vacío.");
    }

    public static ForumException contentTooLong(int max) {
        return badRequest(
                "CONTENT_TOO_LONG",
                "content",
                "El mensaje no puede superar los %d caracteres.".formatted(max));
    }

    public static ForumException authorNameEmpty() {
        return badRequest("AUTHOR_NAME_EMPTY", "authorName", "El nombre no puede estar vacío.");
    }

    public static ForumException authorNameTooLong(int max) {
        return badRequest(
                "AUTHOR_NAME_TOO_LONG",
                "authorName",
                "El nombre no puede superar los %d caracteres.".formatted(max));
    }

    public static ForumException avatarInvalid() {
        return badRequest(
                "AVATAR_INVALID", "authorAvatar", "El avatar seleccionado no está disponible.");
    }

    public static ForumException parentNotFound() {
        return new ForumException(
                HttpStatus.NOT_FOUND,
                "PARENT_NOT_FOUND",
                "parentId",
                "El mensaje al que intentás responder no está disponible.");
    }

    public static ForumException conversationNotFound() {
        return new ForumException(
                HttpStatus.NOT_FOUND,
                "CONVERSATION_NOT_FOUND",
                "id",
                "La conversación solicitada no existe.");
    }

    /**
     * La petición es válida; lo que falla es una regla sobre el estado actual del árbol.
     * Por eso 422 y no 400: permite al cliente refrescar la vista en lugar de mostrar un
     * error de validación de formulario.
     */
    public static ForumException maxDepthExceeded(int maxDepth) {
        return new ForumException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "MAX_DEPTH_EXCEEDED",
                "parentId",
                "No se puede responder: se alcanzó el nivel máximo de anidación (%d)."
                        .formatted(maxDepth));
    }
}
