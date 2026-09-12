package com.pulseboard.forum.repository;

import com.pulseboard.forum.config.ForumProperties;
import com.pulseboard.forum.domain.Message;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Persistencia sobre un único archivo JSON.
 *
 * <p>Es la única clase del sistema que conoce la ruta y el formato del archivo
 * (Principio III). Dos garantías la sostienen:
 *
 * <ul>
 *   <li><strong>Atomicidad</strong>: se escribe a un temporal y se reemplaza con
 *       {@code ATOMIC_MOVE}. Una interrupción deja intacto el archivo anterior, nunca uno
 *       truncado a medias.
 *   <li><strong>Exclusión</strong>: todo el acceso pasa por un {@link
 *       ReentrantReadWriteLock}, de modo que varias lecturas pueden solaparse entre sí
 *       pero ninguna se solapa con una escritura (FR-030).
 * </ul>
 */
@Repository
public class JsonMessageRepository implements MessageRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonMessageRepository.class);

    private final Path dataFile;
    private final Path tempFile;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonMessageRepository(ForumProperties properties, ObjectMapper objectMapper) {
        this.dataFile = Path.of(properties.dataFile()).toAbsolutePath();
        this.tempFile = this.dataFile.resolveSibling(this.dataFile.getFileName() + ".tmp");
        this.objectMapper = objectMapper;
        log.info("Almacén del foro: {}", this.dataFile);
    }

    @Override
    public List<Message> findAll() {
        lock.readLock().lock();
        try {
            return read().messages();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Message> findById(UUID id) {
        lock.readLock().lock();
        try {
            return read().messages().stream().filter(m -> m.id().equals(id)).findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsById(UUID id) {
        return findById(id).isPresent();
    }

    @Override
    public Message append(Message message) {
        lock.writeLock().lock();
        try {
            List<Message> messages = new ArrayList<>(read().messages());
            messages.add(message);
            write(new MessageStore(MessageStore.CURRENT_SCHEMA_VERSION, messages));
            return message;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Lee el archivo completo.
     *
     * <p>Que no exista es el estado normal del primer arranque: se devuelve un almacén
     * vacío y el archivo se creará al publicar el primer mensaje (FR-028). Que exista pero
     * no se pueda interpretar es un fallo y se propaga (FR-029).
     */
    private MessageStore read() {
        if (!Files.exists(dataFile)) {
            return MessageStore.empty();
        }
        try {
            MessageStore store = objectMapper.readValue(Files.readAllBytes(dataFile), MessageStore.class);
            if (store.schemaVersion() != MessageStore.CURRENT_SCHEMA_VERSION) {
                throw new CorruptedStoreException(
                        "El almacén %s declara schemaVersion %d y se esperaba %d. No se arranca con datos que no se pueden interpretar."
                                .formatted(
                                        dataFile,
                                        store.schemaVersion(),
                                        MessageStore.CURRENT_SCHEMA_VERSION));
            }
            return store.messages() == null ? MessageStore.empty() : store;
        } catch (JacksonException e) {
            // En Jackson 3 JacksonException es unchecked, pero se captura explícitamente
            // para distinguir "el archivo no se puede interpretar" de "el archivo no se
            // puede leer del disco": la primera es un fallo de datos, la segunda de E/S.
            throw new CorruptedStoreException(
                    "El almacén %s no es un JSON válido para el formato esperado: %s"
                            .formatted(dataFile, e.getMessage()),
                    e);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el almacén " + dataFile, e);
        }
    }

    /** Escritura atómica: temporal primero, reemplazo después. Nunca in situ. */
    private void write(MessageStore store) {
        try {
            Path parent = dataFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(tempFile, objectMapper.writeValueAsBytes(store));
            moveIntoPlace();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el almacén " + dataFile, e);
        }
    }

    private void moveIntoPlace() throws IOException {
        try {
            Files.move(
                    tempFile,
                    dataFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Algunos sistemas de archivos no ofrecen movimiento atómico. Se degrada al
            // reemplazo simple, que sigue siendo mejor que truncar y reescribir en sitio,
            // y se deja constancia de que la garantía se perdió en este entorno.
            log.warn("ATOMIC_MOVE no soportado en {}; se usa reemplazo simple", dataFile);
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
