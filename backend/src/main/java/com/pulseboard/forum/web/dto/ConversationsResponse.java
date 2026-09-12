package com.pulseboard.forum.web.dto;

import java.util.List;

/**
 * Lista de conversaciones, cada una como árbol completo.
 *
 * <p>Se devuelve un objeto envolvente en lugar de un array suelto para poder añadir
 * metadatos más adelante sin romper el contrato.
 */
public record ConversationsResponse(List<MessageResponse> conversations) {}
