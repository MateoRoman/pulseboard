package com.pulseboard.forum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pulseboard.forum.config.ForumProperties;
import com.pulseboard.forum.domain.MessageNode;
import com.pulseboard.forum.repository.JsonMessageRepository;
import com.pulseboard.forum.web.dto.CreateMessageRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** Pruebas de las reglas de creación: validaciones, padre existente y límite de anidación. */
class MessageServiceTest {

    private static final int MAX_DEPTH = 5;
    private static final int MAX_CONTENT = 2000;
    private static final int MAX_NAME = 40;

    @TempDir Path tempDir;

    private MessageService service;
    private JsonMessageRepository repository;

    @BeforeEach
    void setUp() {
        ForumProperties properties =
                new ForumProperties(
                        MAX_DEPTH,
                        MAX_CONTENT,
                        MAX_NAME,
                        tempDir.resolve("messages.json").toString(),
                        List.of("avatar-01", "avatar-02"));
        repository = new JsonMessageRepository(properties, JsonMapper.builder().build());
        service = new MessageService(repository, properties);
    }

    private CreateMessageRequest request(String content, UUID parentId) {
        return new CreateMessageRequest(content, "Mateo", "avatar-01", parentId);
    }

    // --- Creación básica ---

    @Test
    @DisplayName("un mensaje sin parentId es principal y queda en profundidad 1")
    void createsRootMessage() {
        MessageNode created = service.create(request("hola", null));

        assertThat(created.depth()).isEqualTo(1);
        assertThat(created.message().parentId()).isNull();
        assertThat(created.message().isRoot()).isTrue();
    }

    @Test
    @DisplayName("el servidor asigna id y createdAt")
    void serverAssignsIdAndTimestamp() {
        MessageNode created = service.create(request("hola", null));

        assertThat(created.message().id()).isNotNull();
        assertThat(created.message().createdAt()).isNotNull();
    }

    @Test
    @DisplayName("el mensaje guarda copiados el nombre y el avatar del autor")
    void authorIsSnapshotted() {
        MessageNode created =
                service.create(new CreateMessageRequest("hola", "Ana", "avatar-02", null));

        assertThat(created.message().authorName()).isEqualTo("Ana");
        assertThat(created.message().authorAvatar()).isEqualTo("avatar-02");
    }

    @Test
    @DisplayName("el contenido se guarda recortado en los extremos")
    void contentIsTrimmed() {
        assertThat(service.create(request("   hola   ", null)).message().content()).isEqualTo("hola");
    }

    // --- Validación de contenido ---

    @Test
    @DisplayName("contenido vacío se rechaza con CONTENT_EMPTY")
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> service.create(request("", null)))
                .isInstanceOf(ForumException.class)
                .extracting(e -> ((ForumException) e).code())
                .isEqualTo("CONTENT_EMPTY");
    }

    @Test
    @DisplayName("contenido de solo espacios se rechaza")
    void rejectsBlankContent() {
        assertThatThrownBy(() -> service.create(request("      ", null)))
                .isInstanceOf(ForumException.class)
                .extracting(e -> ((ForumException) e).code())
                .isEqualTo("CONTENT_EMPTY");
    }

    @Test
    @DisplayName("contenido de más de 2000 caracteres se rechaza")
    void rejectsTooLongContent() {
        assertThatThrownBy(() -> service.create(request("x".repeat(MAX_CONTENT + 1), null)))
                .isInstanceOf(ForumException.class)
                .extracting(e -> ((ForumException) e).code())
                .isEqualTo("CONTENT_TOO_LONG");
    }

    @Test
    @DisplayName("contenido de exactamente 2000 caracteres se acepta")
    void acceptsContentAtExactLimit() {
        assertThat(service.create(request("x".repeat(MAX_CONTENT), null)).message().content())
                .hasSize(MAX_CONTENT);
    }

    // --- Validación de autoría ---

    @Test
    @DisplayName("nombre vacío se rechaza con AUTHOR_NAME_EMPTY")
    void rejectsEmptyAuthorName() {
        assertThatThrownBy(
                        () -> service.create(new CreateMessageRequest("hola", "  ", "avatar-01", null)))
                .isInstanceOf(ForumException.class)
                .extracting(e -> ((ForumException) e).code())
                .isEqualTo("AUTHOR_NAME_EMPTY");
    }

    @Test
    @DisplayName("nombre de más de 40 caracteres se rechaza")
    void rejectsTooLongAuthorName() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateMessageRequest(
                                                "hola", "x".repeat(MAX_NAME + 1), "avatar-01", null)))
                .isInstanceOf(ForumException.class)
                .extracting(e -> ((ForumException) e).code())
                .isEqualTo("AUTHOR_NAME_TOO_LONG");
    }

    @Test
    @DisplayName("un avatar fuera del conjunto se rechaza")
    void rejectsUnknownAvatar() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateMessageRequest("hola", "Mateo", "avatar-99", null)))
                .isInstanceOf(ForumException.class)
                .extracting(e -> ((ForumException) e).code())
                .isEqualTo("AVATAR_INVALID");
    }

    // --- Jerarquía y límite ---

    @Test
    @DisplayName("responder a un mensaje existente lo vincula a su padre")
    void replyIsLinkedToItsParent() {
        MessageNode root = service.create(request("raiz", null));

        MessageNode reply = service.create(request("respuesta", root.message().id()));

        assertThat(reply.message().parentId()).isEqualTo(root.message().id());
        assertThat(reply.depth()).isEqualTo(2);
    }

    @Test
    @DisplayName("responder a una respuesta la vincula a ella, no al mensaje raíz")
    void replyToReplyLinksToTheReply() {
        MessageNode root = service.create(request("raiz", null));
        MessageNode level2 = service.create(request("nivel 2", root.message().id()));

        MessageNode level3 = service.create(request("nivel 3", level2.message().id()));

        assertThat(level3.message().parentId()).isEqualTo(level2.message().id());
        assertThat(level3.depth()).isEqualTo(3);
    }

    @Test
    @DisplayName("un parentId inexistente se rechaza con PARENT_NOT_FOUND")
    void rejectsUnknownParent() {
        assertThatThrownBy(() -> service.create(request("huerfano", UUID.randomUUID())))
                .isInstanceOf(ForumException.class)
                .satisfies(
                        e -> {
                            ForumException fe = (ForumException) e;
                            assertThat(fe.code()).isEqualTo("PARENT_NOT_FOUND");
                            assertThat(fe.status()).isEqualTo(HttpStatus.NOT_FOUND);
                        });
    }

    @Test
    @DisplayName("se puede responder hasta alcanzar el nivel máximo")
    void allowsRepliesUpToMaxDepth() {
        MessageNode current = service.create(request("nivel 1", null));

        for (int depth = 2; depth <= MAX_DEPTH; depth++) {
            current = service.create(request("nivel " + depth, current.message().id()));
            assertThat(current.depth()).isEqualTo(depth);
        }

        assertThat(current.depth()).isEqualTo(MAX_DEPTH);
    }

    @Test
    @DisplayName("responder a un mensaje en el nivel máximo se rechaza con 422")
    void rejectsReplyBeyondMaxDepth() {
        MessageNode current = service.create(request("nivel 1", null));
        for (int depth = 2; depth <= MAX_DEPTH; depth++) {
            current = service.create(request("nivel " + depth, current.message().id()));
        }
        UUID deepestId = current.message().id();

        assertThatThrownBy(() -> service.create(request("nivel 6", deepestId)))
                .isInstanceOf(ForumException.class)
                .satisfies(
                        e -> {
                            ForumException fe = (ForumException) e;
                            assertThat(fe.code()).isEqualTo("MAX_DEPTH_EXCEEDED");
                            assertThat(fe.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                        });
    }

    @Test
    @DisplayName("un rechazo no persiste el mensaje")
    void rejectedMessagesAreNotPersisted() {
        service.create(request("valido", null));
        int before = repository.findAll().size();

        assertThatThrownBy(() -> service.create(request("", null))).isInstanceOf(ForumException.class);
        assertThatThrownBy(() -> service.create(request("x", UUID.randomUUID())))
                .isInstanceOf(ForumException.class);

        assertThat(repository.findAll()).hasSize(before);
    }

    @Test
    @DisplayName("ninguna secuencia de rechazos deja un mensaje por encima del nivel máximo")
    void noMessageEverExceedsMaxDepth() {
        MessageNode current = service.create(request("nivel 1", null));
        for (int depth = 2; depth <= MAX_DEPTH; depth++) {
            current = service.create(request("nivel " + depth, current.message().id()));
        }
        UUID deepestId = current.message().id();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.create(request("exceso", deepestId)))
                    .isInstanceOf(ForumException.class);
        }

        assertThat(service.tree().roots()).isNotEmpty();
        assertThat(maxDepthOf(service.tree().roots())).isEqualTo(MAX_DEPTH);
    }

    private int maxDepthOf(List<MessageNode> nodes) {
        int max = 0;
        for (MessageNode node : nodes) {
            max = Math.max(max, node.depth());
            max = Math.max(max, maxDepthOf(node.replies()));
        }
        return max;
    }

    // --- Lectura ---

    @Test
    @DisplayName("las conversaciones se listan con las más recientes primero")
    void conversationsAreNewestFirst() {
        service.create(request("primera", null));
        service.create(request("segunda", null));

        assertThat(service.conversations().get(0).message().content()).isEqualTo("segunda");
    }

    @Test
    @DisplayName("pedir una conversación por el id de una respuesta da CONVERSATION_NOT_FOUND")
    void conversationByReplyIdIsNotFound() {
        MessageNode root = service.create(request("raiz", null));
        MessageNode reply = service.create(request("respuesta", root.message().id()));

        assertThatThrownBy(() -> service.conversation(reply.message().id()))
                .isInstanceOf(ForumException.class)
                .extracting(e -> ((ForumException) e).code())
                .isEqualTo("CONVERSATION_NOT_FOUND");
    }
}
