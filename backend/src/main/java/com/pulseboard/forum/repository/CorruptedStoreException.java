package com.pulseboard.forum.repository;

/**
 * El almacenamiento existe pero no se puede interpretar.
 *
 * <p>Se lanza en lugar de arrancar con el foro vacío o con datos parciales (FR-029):
 * mostrar una conversación incompleta sin avisar es peor que no arrancar, porque quien
 * la lee no tiene forma de notar que falta contenido.
 */
public class CorruptedStoreException extends RuntimeException {

    public CorruptedStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public CorruptedStoreException(String message) {
        super(message);
    }
}
