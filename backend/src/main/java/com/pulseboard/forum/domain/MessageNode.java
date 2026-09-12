package com.pulseboard.forum.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un mensaje ya situado dentro del árbol, con su profundidad calculada y sus respuestas
 * enlazadas.
 *
 * <p>Es una estructura derivada: se construye en memoria al leer y nunca se persiste. La
 * profundidad que expone es el resultado de recorrer la cadena de ancestros, no un dato
 * almacenado (Principio I, FR-015).
 */
public final class MessageNode {

    private final Message message;
    private final int depth;
    private final List<MessageNode> replies = new ArrayList<>();

    MessageNode(Message message, int depth) {
        this.message = message;
        this.depth = depth;
    }

    void addReply(MessageNode reply) {
        replies.add(reply);
    }

    void sortReplies() {
        replies.sort(MessageTree.BY_CREATED_AT_THEN_ID);
        replies.forEach(MessageNode::sortReplies);
    }

    public Message message() {
        return message;
    }

    /** Profundidad en el árbol: el mensaje principal es 1, cada respuesta suma uno. */
    public int depth() {
        return depth;
    }

    public List<MessageNode> replies() {
        return Collections.unmodifiableList(replies);
    }
}
