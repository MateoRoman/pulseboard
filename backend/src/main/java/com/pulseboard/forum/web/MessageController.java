package com.pulseboard.forum.web;

import com.pulseboard.forum.domain.MessageNode;
import com.pulseboard.forum.service.MessageService;
import com.pulseboard.forum.web.dto.ConversationsResponse;
import com.pulseboard.forum.web.dto.CreateMessageRequest;
import com.pulseboard.forum.web.dto.MessageResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de conversaciones y mensajes, según {@code contracts/rest-api.md}. */
@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    /** Foro vacío devuelve 200 con la lista vacía, nunca un error (FR-025). */
    @GetMapping("/conversations")
    public ConversationsResponse conversations() {
        return new ConversationsResponse(
                service.conversations().stream().map(MessageResponse::from).toList());
    }

    @GetMapping("/conversations/{id}")
    public MessageResponse conversation(@PathVariable UUID id) {
        return MessageResponse.from(service.conversation(id));
    }

    /** Crea un mensaje principal ({@code parentId} nulo) o una respuesta. */
    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> create(@RequestBody CreateMessageRequest request) {
        MessageNode created = service.create(request);
        MessageResponse body = MessageResponse.from(created);
        return ResponseEntity.created(URI.create("/api/messages/" + body.id())).body(body);
    }
}
