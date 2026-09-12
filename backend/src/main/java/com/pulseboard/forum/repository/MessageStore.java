package com.pulseboard.forum.repository;

import com.pulseboard.forum.domain.Message;
import java.util.List;

/**
 * Envoltorio del archivo JSON.
 *
 * <p>El {@code schemaVersion} permite detectar un formato desconocido y fallar de forma
 * explícita en lugar de leerlo mal en silencio. Una lista suelta en la raíz del archivo no
 * dejaría dónde ponerlo.
 */
record MessageStore(int schemaVersion, List<Message> messages) {

    static final int CURRENT_SCHEMA_VERSION = 1;

    static MessageStore empty() {
        return new MessageStore(CURRENT_SCHEMA_VERSION, List.of());
    }
}
