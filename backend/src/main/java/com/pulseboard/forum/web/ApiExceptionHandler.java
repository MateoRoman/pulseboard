package com.pulseboard.forum.web;

import com.pulseboard.forum.repository.CorruptedStoreException;
import com.pulseboard.forum.service.ForumException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los fallos a la forma estable de {@link ApiError}.
 *
 * <p>Ninguna respuesta 200 transporta un error, y ningún error llega con cuerpo vacío
 * (Principio II).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ForumException.class)
    public ResponseEntity<ApiError> handleForum(ForumException e) {
        return ResponseEntity.status(e.status())
                .body(ApiError.of(e.code(), e.getMessage(), e.field()));
    }

    /** Cuerpo ilegible o tipos que no encajan: la petición está mal formada. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("MALFORMED_REQUEST", "La petición no se pudo interpretar.", null));
    }

    /**
     * El almacén existe pero no se puede leer. Es un fallo del servidor, no de quien pide:
     * se informa explícitamente en lugar de devolver una lista vacía que parecería un foro
     * sin mensajes (FR-029).
     */
    @ExceptionHandler(CorruptedStoreException.class)
    public ResponseEntity<ApiError> handleCorruptedStore(CorruptedStoreException e) {
        log.error("Almacén ilegible", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("STORE_UNREADABLE", e.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Fallo no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "Ocurrió un error inesperado.", null));
    }
}
