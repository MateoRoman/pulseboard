package com.pulseboard.forum.repository;

import com.pulseboard.forum.domain.Message;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Única puerta de acceso al almacenamiento (Principio III).
 *
 * <p>Ninguna capa de dominio, servicio o web toca el archivo directamente. Que detrás haya
 * un archivo JSON es un detalle que no cruza esta frontera: reemplazarlo por otro mecanismo
 * no debe requerir cambios fuera de este paquete.
 */
public interface MessageRepository {

    /** Todos los mensajes, sin orden garantizado. El árbol se ensambla aparte. */
    List<Message> findAll();

    Optional<Message> findById(UUID id);

    boolean existsById(UUID id);

    /** Añade un mensaje y lo devuelve. La escritura es atómica. */
    Message append(Message message);
}
