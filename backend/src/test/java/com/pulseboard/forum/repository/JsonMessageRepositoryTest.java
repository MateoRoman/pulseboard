package com.pulseboard.forum.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pulseboard.forum.config.ForumProperties;
import com.pulseboard.forum.domain.Message;
import com.pulseboard.forum.domain.MessageTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Pruebas de la persistencia: atomicidad, arranque, fallos explícitos y concurrencia. */
class JsonMessageRepositoryTest {

    @TempDir Path tempDir;

    private Path dataFile;
    private JsonMessageRepository repository;

    @BeforeEach
    void setUp() {
        dataFile = tempDir.resolve("data").resolve("messages.json");
        ObjectMapper mapper = JsonMapper.builder().build();
        ForumProperties properties =
                new ForumProperties(5, 2000, 40, dataFile.toString(), List.of("avatar-01"));
        repository = new JsonMessageRepository(properties, mapper);
    }

    private Message message(String content, UUID parentId) {
        return new Message(
                UUID.randomUUID(), content, "Mateo", "avatar-01", Instant.now(), parentId);
    }

    @Test
    @DisplayName("sin archivo, el foro arranca vacío en lugar de fallar")
    void startsEmptyWhenFileIsAbsent() {
        assertThat(Files.exists(dataFile)).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("el archivo se crea al publicar el primer mensaje, no antes")
    void fileIsCreatedLazily() {
        assertThat(Files.exists(dataFile)).isFalse();

        repository.append(message("primero", null));

        assertThat(Files.exists(dataFile)).isTrue();
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("lo escrito se relee igual desde disco")
    void writesAreReadBackIntact() {
        Message root = repository.append(message("raiz", null));
        Message reply = repository.append(message("respuesta", root.id()));

        List<Message> reloaded = repository.findAll();

        assertThat(reloaded).hasSize(2);
        assertThat(repository.findById(reply.id()).orElseThrow().parentId()).isEqualTo(root.id());
        assertThat(repository.findById(root.id()).orElseThrow().content()).isEqualTo("raiz");
    }

    @Test
    @DisplayName("el contenido, la jerarquía y la autoría sobreviven a un reinicio")
    void survivesRestart() {
        Message root = repository.append(message("raiz", null));
        Message child = repository.append(message("hijo", root.id()));
        repository.append(message("nieto", child.id()));

        // Un repositorio nuevo equivale a reiniciar el proceso: no hay estado en memoria.
        ForumProperties properties =
                new ForumProperties(5, 2000, 40, dataFile.toString(), List.of("avatar-01"));
        JsonMessageRepository restarted =
                new JsonMessageRepository(properties, JsonMapper.builder().build());

        MessageTree tree = MessageTree.from(restarted.findAll());

        assertThat(restarted.findAll()).hasSize(3);
        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().get(0).replies().get(0).replies()).hasSize(1);
        assertThat(tree.roots().get(0).message().authorName()).isEqualTo("Mateo");
        assertThat(tree.roots().get(0).replies().get(0).replies().get(0).depth()).isEqualTo(3);
    }

    @Test
    @DisplayName("un JSON malformado falla de forma explícita, no arranca vacío")
    void malformedJsonFailsLoudly() throws IOException {
        Files.createDirectories(dataFile.getParent());
        Files.writeString(dataFile, "{ esto no es json valido");

        assertThatThrownBy(() -> repository.findAll())
                .isInstanceOf(CorruptedStoreException.class)
                .hasMessageContaining("no es un JSON válido");
    }

    @Test
    @DisplayName("una schemaVersion desconocida falla de forma explícita")
    void unknownSchemaVersionFailsLoudly() throws IOException {
        Files.createDirectories(dataFile.getParent());
        Files.writeString(dataFile, "{\"schemaVersion\": 99, \"messages\": []}");

        assertThatThrownBy(() -> repository.findAll())
                .isInstanceOf(CorruptedStoreException.class)
                .hasMessageContaining("schemaVersion 99");
    }

    @Test
    @DisplayName("el archivo persistido guarda una lista plana con parentId")
    void storedFormatIsFlatListWithParentId() throws IOException {
        Message root = repository.append(message("raiz", null));
        repository.append(message("respuesta", root.id()));

        String json = Files.readString(dataFile);

        assertThat(json).contains("\"schemaVersion\":1");
        assertThat(json).contains("\"parentId\":null");
        assertThat(json).contains(root.id().toString());
        // La estructura NO se anida en disco: no hay campo replies ni children.
        assertThat(json).doesNotContain("\"replies\"").doesNotContain("\"children\"");
        // La profundidad no se persiste (Principio I).
        assertThat(json).doesNotContain("\"depth\"");
    }

    @Test
    @DisplayName("publicaciones concurrentes no se pisan entre sí")
    void concurrentAppendsPreserveEveryMessage() throws InterruptedException {
        int threads = 8;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int id = t;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < perThread; i++) {
                                repository.append(message("hilo-" + id + "-msg-" + i, null));
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Ninguna escritura descarta ni sobrescribe otra (FR-030, SC-009).
        assertThat(repository.findAll()).hasSize(threads * perThread);
    }

    @Test
    @DisplayName("no queda archivo temporal tras una escritura correcta")
    void noTemporaryFileRemainsAfterWrite() {
        repository.append(message("uno", null));

        assertThat(Files.exists(dataFile.resolveSibling("messages.json.tmp"))).isFalse();
    }
}
