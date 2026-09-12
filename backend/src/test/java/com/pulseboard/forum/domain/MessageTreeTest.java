package com.pulseboard.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del ensamblado del árbol.
 *
 * <p>Es la lógica con más riesgo real del sistema: si el árbol se arma mal, la aplicación
 * muestra una conversación que no corresponde con lo guardado.
 */
class MessageTreeTest {

    private static final Instant T0 = Instant.parse("2026-09-11T10:00:00.000Z");

    private static Message msg(String id, String parentId, long secondsAfterT0) {
        return new Message(
                uuid(id),
                "contenido " + id,
                "Autor " + id,
                "avatar-01",
                T0.plusSeconds(secondsAfterT0),
                parentId == null ? null : uuid(parentId));
    }

    /** Genera un UUID estable a partir de una etiqueta corta, para que las pruebas se lean. */
    private static UUID uuid(String label) {
        return UUID.nameUUIDFromBytes(label.getBytes());
    }

    @Test
    @DisplayName("un mensaje sin padre es raíz y tiene profundidad 1")
    void rootHasDepthOne() {
        MessageTree tree = MessageTree.from(List.of(msg("a", null, 0)));

        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().get(0).depth()).isEqualTo(1);
        assertThat(tree.roots().get(0).message().isRoot()).isTrue();
    }

    @Test
    @DisplayName("la profundidad se deriva de la cadena de ancestros")
    void depthIsDerivedFromAncestorChain() {
        List<Message> messages =
                List.of(
                        msg("a", null, 0),
                        msg("b", "a", 1),
                        msg("c", "b", 2),
                        msg("d", "c", 3),
                        msg("e", "d", 4));

        MessageTree tree = MessageTree.from(messages);

        assertThat(tree.findById(uuid("a")).orElseThrow().depth()).isEqualTo(1);
        assertThat(tree.findById(uuid("b")).orElseThrow().depth()).isEqualTo(2);
        assertThat(tree.findById(uuid("c")).orElseThrow().depth()).isEqualTo(3);
        assertThat(tree.findById(uuid("d")).orElseThrow().depth()).isEqualTo(4);
        assertThat(tree.findById(uuid("e")).orElseThrow().depth()).isEqualTo(5);
    }

    @Test
    @DisplayName("el árbol es el mismo sin importar el orden de lectura del archivo")
    void assemblyIsDeterministicRegardlessOfInputOrder() {
        List<Message> messages =
                new ArrayList<>(
                        List.of(msg("a", null, 0), msg("b", "a", 1), msg("c", "a", 2), msg("d", "b", 3)));

        String expected = describe(MessageTree.from(messages));

        // Varias permutaciones deben producir exactamente la misma estructura.
        for (int i = 0; i < 10; i++) {
            Collections.shuffle(messages);
            assertThat(describe(MessageTree.from(messages)))
                    .as("permutación %d", i)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("las respuestas de un mismo padre van de la más antigua a la más reciente")
    void siblingsAreOrderedOldestFirst() {
        MessageTree tree =
                MessageTree.from(
                        List.of(
                                msg("a", null, 0),
                                msg("tarde", "a", 30),
                                msg("temprano", "a", 10),
                                msg("medio", "a", 20)));

        List<String> contents =
                tree.roots().get(0).replies().stream().map(n -> n.message().content()).toList();

        assertThat(contents)
                .containsExactly("contenido temprano", "contenido medio", "contenido tarde");
    }

    @Test
    @DisplayName("las conversaciones raíz se listan de la más reciente a la más antigua")
    void rootsAreOrderedNewestFirst() {
        MessageTree tree =
                MessageTree.from(
                        List.of(msg("vieja", null, 0), msg("nueva", null, 100), msg("media", null, 50)));

        List<String> contents = tree.roots().stream().map(n -> n.message().content()).toList();

        assertThat(contents).containsExactly("contenido nueva", "contenido media", "contenido vieja");
    }

    @Test
    @DisplayName("dos hermanos con el mismo instante se ordenan de forma estable por id")
    void tiesAreBrokenByIdSoOrderIsStable() {
        Message a = msg("a", null, 0);
        Message x = new Message(uuid("x"), "x", "A", "avatar-01", T0.plusSeconds(5), a.id());
        Message y = new Message(uuid("y"), "y", "A", "avatar-01", T0.plusSeconds(5), a.id());

        List<UUID> first =
                MessageTree.from(List.of(a, x, y)).roots().get(0).replies().stream()
                        .map(n -> n.message().id())
                        .toList();
        List<UUID> second =
                MessageTree.from(List.of(a, y, x)).roots().get(0).replies().stream()
                        .map(n -> n.message().id())
                        .toList();

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("un mensaje cuyo padre no existe se omite en lugar de colgarse como raíz")
    void orphanIsOmittedNotPromotedToRoot() {
        MessageTree tree = MessageTree.from(List.of(msg("a", null, 0), msg("huerfano", "inexistente", 1)));

        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().get(0).message().id()).isEqualTo(uuid("a"));
        assertThat(tree.findById(uuid("huerfano"))).isEmpty();
    }

    @Test
    @DisplayName("ramas paralelas se mantienen separadas")
    void parallelBranchesStaySeparate() {
        MessageTree tree =
                MessageTree.from(
                        List.of(
                                msg("a", null, 0),
                                msg("rama1", "a", 1),
                                msg("rama2", "a", 2),
                                msg("hijo1", "rama1", 3),
                                msg("hijo2", "rama2", 4)));

        MessageNode root = tree.roots().get(0);
        assertThat(root.replies()).hasSize(2);
        assertThat(root.replies().get(0).replies()).hasSize(1);
        assertThat(root.replies().get(0).replies().get(0).message().id()).isEqualTo(uuid("hijo1"));
        assertThat(root.replies().get(1).replies().get(0).message().id()).isEqualTo(uuid("hijo2"));
    }

    @Test
    @DisplayName("una lista vacía produce un árbol sin raíces, no un fallo")
    void emptyListProducesEmptyTree() {
        assertThat(MessageTree.from(List.of()).roots()).isEmpty();
    }

    @Test
    @DisplayName("findRootById no devuelve respuestas, solo mensajes principales")
    void findRootByIdRejectsReplies() {
        MessageTree tree = MessageTree.from(List.of(msg("a", null, 0), msg("b", "a", 1)));

        assertThat(tree.findRootById(uuid("a"))).isPresent();
        assertThat(tree.findRootById(uuid("b"))).isEmpty();
    }

    /** Serializa la estructura para poder compararla entre permutaciones. */
    private static String describe(MessageTree tree) {
        StringBuilder sb = new StringBuilder();
        tree.roots().forEach(node -> describe(node, sb));
        return sb.toString();
    }

    private static void describe(MessageNode node, StringBuilder sb) {
        sb.append(node.depth()).append(':').append(node.message().content()).append('|');
        node.replies().forEach(child -> describe(child, sb));
    }
}
