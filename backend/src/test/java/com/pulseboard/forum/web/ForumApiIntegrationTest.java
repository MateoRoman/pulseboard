package com.pulseboard.forum.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Prueba del contrato REST sobre la aplicación completa.
 *
 * <p>Ejercita controladores, serialización JSON y manejo de errores tal como los verá el
 * front-end, incluidos los códigos de estado y la forma estable de {@link ApiError}.
 *
 * <p>Se usa MockMvc en lugar de un cliente HTTP real para no añadir una dependencia extra
 * solo para las pruebas: el Principio IV exige justificar cada dependencia, y aquí la
 * capacidad es equivalente para lo que se quiere verificar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ForumApiIntegrationTest {

    @TempDir static Path tempDir;

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("forum.data-file", () -> tempDir.resolve("messages.json").toString());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    /** Publica un mensaje y devuelve el cuerpo de la respuesta ya parseado. */
    private JsonNode create(String content, String author, String avatar, String parentId)
            throws Exception {
        String body =
                parentId == null
                        ? """
                        {"content":"%s","authorName":"%s","authorAvatar":"%s"}
                        """
                                .formatted(content, author, avatar)
                        : """
                        {"content":"%s","authorName":"%s","authorAvatar":"%s","parentId":"%s"}
                        """
                                .formatted(content, author, avatar, parentId);

        String response =
                mockMvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(response);
    }

    @Test
    @DisplayName("GET /api/config expone el límite y el conjunto de avatares")
    void configExposesLimits() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDepth").value(5))
                .andExpect(jsonPath("$.maxContentLength").value(2000))
                .andExpect(jsonPath("$.maxAuthorNameLength").value(40))
                .andExpect(jsonPath("$.avatars.length()").value(8))
                .andExpect(jsonPath("$.avatars[0]").value("avatar-01"));
    }

    @Test
    @DisplayName("POST /api/messages crea un mensaje principal, devuelve 201 y cabecera Location")
    void createsRootMessage() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"content":"primer mensaje","authorName":"Mateo","authorAvatar":"avatar-01"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.depth").value(1))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.replies").isArray());
    }

    @Test
    @DisplayName("contenido vacío devuelve 400 CONTENT_EMPTY con la forma estable de error")
    void emptyContentReturnsStableError() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"content":"   ","authorName":"Mateo","authorAvatar":"avatar-01"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTENT_EMPTY"))
                .andExpect(jsonPath("$.field").value("content"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("un avatar fuera del conjunto devuelve 400 AVATAR_INVALID")
    void unknownAvatarIsRejected() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"content":"hola","authorName":"Mateo","authorAvatar":"avatar-99"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_INVALID"));
    }

    @Test
    @DisplayName("un parentId inexistente devuelve 404 PARENT_NOT_FOUND")
    void unknownParentReturns404() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"content":"respuesta","authorName":"Mateo","authorAvatar":"avatar-01","parentId":"00000000-0000-4000-8000-000000000000"}
                                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("responder por encima del nivel 5 devuelve 422 MAX_DEPTH_EXCEEDED")
    void beyondMaxDepthReturns422() throws Exception {
        JsonNode current = create("nivel 1", "Mateo", "avatar-01", null);
        for (int depth = 2; depth <= 5; depth++) {
            current = create("nivel " + depth, "Mateo", "avatar-01", current.get("id").asString());
            assertThat(current.get("depth").asInt()).isEqualTo(depth);
        }

        // El servidor rechaza el nivel 6 aunque la petición esquive la interfaz.
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"content":"nivel 6","authorName":"Mateo","authorAvatar":"avatar-01","parentId":"%s"}
                                        """
                                                .formatted(current.get("id").asString())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAX_DEPTH_EXCEEDED"));
    }

    @Test
    @DisplayName("GET /api/conversations devuelve los árboles con las respuestas anidadas")
    void conversationsAreReturnedAsTrees() throws Exception {
        JsonNode root = create("raiz del arbol", "Mateo", "avatar-01", null);
        create("respuesta anidada", "Ana", "avatar-02", root.get("id").asString());

        String response =
                mockMvc.perform(get("/api/conversations"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode conversations = objectMapper.readTree(response).get("conversations");
        JsonNode found = null;
        for (JsonNode c : conversations) {
            if (root.get("id").asString().equals(c.get("id").asString())) {
                found = c;
            }
        }

        assertThat(found).isNotNull();
        assertThat(found.get("replies")).hasSize(1);
        assertThat(found.get("replies").get(0).get("content").asString())
                .isEqualTo("respuesta anidada");
        assertThat(found.get("replies").get(0).get("depth").asInt()).isEqualTo(2);
        assertThat(found.get("replies").get(0).get("authorName").asString()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("GET /api/conversations/{id} con el id de una respuesta devuelve 404")
    void conversationByReplyIdReturns404() throws Exception {
        JsonNode root = create("otra raiz", "Mateo", "avatar-01", null);
        JsonNode reply = create("una respuesta", "Mateo", "avatar-01", root.get("id").asString());

        mockMvc.perform(get("/api/conversations/" + reply.get("id").asString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("el contenido con marcado se devuelve literal, sin interpretar ni escapar")
    void markupIsStoredLiterally() throws Exception {
        JsonNode created = create("<b>hola</b>", "Mateo", "avatar-01", null);

        assertThat(created.get("content").asString()).isEqualTo("<b>hola</b>");
    }

    @Test
    @DisplayName("un cuerpo ilegible devuelve 400 MALFORMED_REQUEST, no 500")
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
