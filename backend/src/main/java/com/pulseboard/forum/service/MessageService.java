package com.pulseboard.forum.service;

import com.pulseboard.forum.config.ForumProperties;
import com.pulseboard.forum.domain.Message;
import com.pulseboard.forum.domain.MessageNode;
import com.pulseboard.forum.domain.MessageTree;
import com.pulseboard.forum.repository.MessageRepository;
import com.pulseboard.forum.web.dto.CreateMessageRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reglas de creación y lectura de mensajes.
 *
 * <p>Valida contenido, autoría y profundidad antes de persistir. Ninguna validación
 * incorpora números por su cuenta: todos vienen de {@link ForumProperties}, que los lee de
 * {@code application.yml} (FR-018).
 */
@Service
public class MessageService {

    private final MessageRepository repository;
    private final ForumProperties properties;

    public MessageService(MessageRepository repository, ForumProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Árbol completo de conversaciones, ensamblado en cada lectura. */
    public MessageTree tree() {
        return MessageTree.from(repository.findAll());
    }

    public List<MessageNode> conversations() {
        return tree().roots();
    }

    public MessageNode conversation(UUID id) {
        return tree().findRootById(id).orElseThrow(ForumException::conversationNotFound);
    }

    /**
     * Crea un mensaje principal o una respuesta, según venga {@code parentId}.
     *
     * <p>El {@code id} y el {@code createdAt} los asigna el servidor: el cliente no tiene
     * forma de influir en ellos.
     */
    public MessageNode create(CreateMessageRequest request) {
        String content = validatedContent(request.content());
        String authorName = validatedAuthorName(request.authorName());
        String authorAvatar = validatedAvatar(request.authorAvatar());
        UUID parentId = validatedParent(request.parentId());

        Message message =
                new Message(
                        UUID.randomUUID(),
                        content,
                        authorName,
                        authorAvatar,
                        Instant.now(),
                        parentId);

        repository.append(message);

        // Se relee el árbol para devolver el mensaje con su profundidad ya derivada,
        // en lugar de calcularla aquí por separado y arriesgar que difiera.
        return tree().findById(message.id()).orElseThrow();
    }

    private String validatedContent(String raw) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            throw ForumException.contentEmpty();
        }
        if (content.length() > properties.maxContentLength()) {
            throw ForumException.contentTooLong(properties.maxContentLength());
        }
        return content;
    }

    private String validatedAuthorName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw ForumException.authorNameEmpty();
        }
        if (name.length() > properties.maxAuthorNameLength()) {
            throw ForumException.authorNameTooLong(properties.maxAuthorNameLength());
        }
        return name;
    }

    private String validatedAvatar(String avatar) {
        if (avatar == null || !properties.isKnownAvatar(avatar)) {
            throw ForumException.avatarInvalid();
        }
        return avatar;
    }

    /**
     * Comprueba que el padre existe y que la respuesta cabe dentro del límite.
     *
     * <p>Un padre en el nivel máximo no admite respuestas: su hijo sería el nivel
     * {@code maxDepth + 1}. La validación vive aquí, en el servidor, y no solo en la
     * interfaz: ocultar el botón mejora la experiencia, pero no es una garantía.
     */
    private UUID validatedParent(UUID parentId) {
        if (parentId == null) {
            return null;
        }
        MessageNode parent = tree().findById(parentId).orElseThrow(ForumException::parentNotFound);
        if (parent.depth() >= properties.maxDepth()) {
            throw ForumException.maxDepthExceeded(properties.maxDepth());
        }
        return parentId;
    }
}
