package com.pulseboard.forum.web;

import com.pulseboard.forum.config.ForumProperties;
import com.pulseboard.forum.web.dto.ConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone los parámetros que el front-end necesita pero no debe declarar.
 *
 * <p>Es la pieza que hace cumplible FR-018: el límite de anidación vive en
 * {@code application.yml} y ambos lados lo leen de ahí, en lugar de mantener dos constantes
 * sincronizadas a mano.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ForumProperties properties;

    public ConfigController(ForumProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public ConfigResponse config() {
        return ConfigResponse.from(properties);
    }
}
