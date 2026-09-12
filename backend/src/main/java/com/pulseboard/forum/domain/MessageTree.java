package com.pulseboard.forum.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensambla el árbol de conversaciones a partir de la lista plana de mensajes.
 *
 * <p>Esta clase es la expresión del Principio I: la relación padre-hijo almacenada en
 * {@code parentId} es la única fuente de la estructura, y tanto el árbol como la
 * profundidad se derivan de ella en cada lectura.
 *
 * <p>El resultado es <strong>determinista</strong>: no depende del orden en que los
 * mensajes vengan del archivo, porque el ordenamiento se aplica sobre {@code createdAt}
 * con desempate por {@code id} (FR-019).
 */
public final class MessageTree {

    private static final Logger log = LoggerFactory.getLogger(MessageTree.class);

    /**
     * Orden de las respuestas de un mismo padre: de la más antigua a la más reciente
     * (FR-021). El desempate por {@code id} evita que dos mensajes con el mismo instante
     * se muestren en orden distinto entre lecturas.
     */
    static final Comparator<MessageNode> BY_CREATED_AT_THEN_ID =
            Comparator.comparing((MessageNode n) -> n.message().createdAt())
                    .thenComparing(n -> n.message().id());

    /** Las conversaciones se listan con las más recientes primero. */
    private static final Comparator<MessageNode> ROOTS_NEWEST_FIRST =
            BY_CREATED_AT_THEN_ID.reversed();

    private final List<MessageNode> roots;
    private final Map<UUID, MessageNode> byId;

    private MessageTree(List<MessageNode> roots, Map<UUID, MessageNode> byId) {
        this.roots = roots;
        this.byId = byId;
    }

    /**
     * Construye el árbol en dos pasadas sobre la lista: una para indexar por id y otra
     * para enlazar cada mensaje con su padre. Coste lineal en el número de mensajes.
     */
    public static MessageTree from(List<Message> messages) {
        Map<UUID, Message> index = new HashMap<>();
        for (Message m : messages) {
            index.put(m.id(), m);
        }

        Map<UUID, MessageNode> nodes = new HashMap<>();
        List<MessageNode> roots = new ArrayList<>();

        for (Message m : messages) {
            int depth = depthOf(m, index);
            if (depth < 0) {
                // parentId apunta a un mensaje inexistente: datos inconsistentes en el
                // archivo. Se omite del árbol en lugar de colgarlo de una raíz falsa,
                // porque mostrarlo como mensaje principal sería mentir sobre su origen.
                log.warn(
                        "Mensaje {} omitido: su parentId {} no corresponde a ningún mensaje",
                        m.id(),
                        m.parentId());
                continue;
            }
            nodes.put(m.id(), new MessageNode(m, depth));
        }

        for (MessageNode node : nodes.values()) {
            UUID parentId = node.message().parentId();
            if (parentId == null) {
                roots.add(node);
            } else {
                MessageNode parent = nodes.get(parentId);
                if (parent != null) {
                    parent.addReply(node);
                }
            }
        }

        roots.sort(ROOTS_NEWEST_FIRST);
        roots.forEach(MessageNode::sortReplies);

        return new MessageTree(roots, nodes);
    }

    /**
     * Profundidad de un mensaje recorriendo la cadena de ancestros. El mensaje principal
     * es 1. Devuelve -1 si la cadena se rompe porque un ancestro no existe.
     *
     * <p>El recorrido está acotado por el número de mensajes, de modo que un archivo
     * manipulado con referencias circulares no provoca un bucle infinito.
     */
    private static int depthOf(Message message, Map<UUID, Message> index) {
        int depth = 1;
        UUID parentId = message.parentId();
        int guard = index.size() + 1;

        while (parentId != null) {
            if (guard-- <= 0) {
                log.warn("Ciclo de referencias detectado alcanzando el mensaje {}", message.id());
                return -1;
            }
            Message parent = index.get(parentId);
            if (parent == null) {
                return -1;
            }
            depth++;
            parentId = parent.parentId();
        }
        return depth;
    }

    /** Conversaciones completas, de la más reciente a la más antigua. */
    public List<MessageNode> roots() {
        return List.copyOf(roots);
    }

    public Optional<MessageNode> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Conversación concreta; vacío si el id no existe o no corresponde a una raíz. */
    public Optional<MessageNode> findRootById(UUID id) {
        return findById(id).filter(node -> node.message().isRoot());
    }
}
