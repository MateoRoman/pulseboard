package com.pulseboard.forum.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Comportamiento de la API con la anidación configurada como ilimitada.
 *
 * <p>Levanta la misma aplicación con {@code forum.max-depth: 0}, que es el convenio para
 * «sin tope». Verifica que cambiar esa única propiedad basta para cambiar el
 * comportamiento del sistema completo, sin tocar código.
 */
@SpringBootTest(properties = "forum.max-depth=0")
@AutoConfigureMockMvc
class UnlimitedDepthApiTest {

    @TempDir static Path tempDir;

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("forum.data-file", () -> tempDir.resolve("unlimited.json").toString());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private JsonNode create(String content, String parentId) throws Exception {
        String body =
                parentId == null
                        ? """
                        {"content":"%s","authorName":"Mateo","authorAvatar":"avatar-01"}
                        """
                                .formatted(content)
                        : """
                        {"content":"%s","authorName":"Mateo","authorAvatar":"avatar-01","parentId":"%s"}
                        """
                                .formatted(content, parentId);

        String response =
                mockMvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(response);
    }

    @Test
    @DisplayName("GET /api/config informa maxDepth null cuando la anidación es ilimitada")
    void configReportsNullMaxDepth() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                // null significa «sin límite», no «desconocido»: el cliente lo distingue
                // de la ausencia de configuración por otros medios.
                .andExpect(jsonPath("$.maxDepth").doesNotExist())
                .andExpect(jsonPath("$.maxContentLength").value(2000));
    }

    @Test
    @DisplayName("se puede anidar muy por encima del tope que rige por defecto")
    void nestingGoesWellBeyondTheDefaultLimit() throws Exception {
        JsonNode current = create("nivel 1", null);

        for (int depth = 2; depth <= 20; depth++) {
            current = create("nivel " + depth, current.get("id").asString());
            assertThat(current.get("depth").asInt()).isEqualTo(depth);
        }

        assertThat(current.get("depth").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("sin tope, nunca se devuelve MAX_DEPTH_EXCEEDED")
    void maxDepthExceededNeverHappens() throws Exception {
        JsonNode current = create("raiz", null);
        for (int depth = 2; depth <= 10; depth++) {
            current = create("nivel " + depth, current.get("id").asString());
        }

        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"content":"uno mas","authorName":"Mateo","authorAvatar":"avatar-01","parentId":"%s"}
                                        """
                                                .formatted(current.get("id").asString())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("el árbol profundo se devuelve completo y correctamente anidado")
    void deepTreeIsReturnedFullyNested() throws Exception {
        JsonNode root = create("raiz profunda", null);
        JsonNode current = root;
        for (int depth = 2; depth <= 12; depth++) {
            current = create("nivel " + depth, current.get("id").asString());
        }

        String response =
                mockMvc.perform(get("/api/conversations"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode found = null;
        for (JsonNode c : objectMapper.readTree(response).get("conversations")) {
            if (root.get("id").asString().equals(c.get("id").asString())) {
                found = c;
            }
        }
        assertThat(found).isNotNull();

        // Descender la cadena entera confirma que la anidación llegó completa.
        JsonNode node = found;
        int levels = 1;
        while (node.get("replies").size() > 0) {
            node = node.get("replies").get(0);
            levels++;
        }
        assertThat(levels).isEqualTo(12);
        assertThat(node.get("depth").asInt()).isEqualTo(12);
    }
}
