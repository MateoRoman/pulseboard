/**
 * Modelos que espejan el contrato REST documentado en
 * `specs/001-foro-jerarquico/contracts/rest-api.md`.
 *
 * Si el contrato cambia, estos tipos cambian en el mismo commit: un contrato
 * desactualizado se trata como defecto, no como deuda (Principio II).
 */

/** Un mensaje con sus respuestas ya anidadas, tal como llega de la API. */
export interface Message {
  id: string;
  content: string;
  authorName: string;
  authorAvatar: string;
  createdAt: string;
  parentId: string | null;
  /**
   * Profundidad calculada por el servidor: el mensaje principal es 1.
   *
   * Se usa para el tratamiento visual. NUNCA se compara contra un número escrito
   * aquí: para saber si aún se puede responder se usa `maxDepth` de ForumConfig.
   */
  depth: number;
  replies: Message[];
}

/**
 * Parámetros que el servidor decide y el front-end consulta.
 *
 * Ningún componente declara estos valores por su cuenta (FR-018).
 */
export interface ForumConfig {
  maxDepth: number;
  maxContentLength: number;
  maxAuthorNameLength: number;
  avatars: string[];
}

export interface ConversationsResponse {
  conversations: Message[];
}

/** Identidad local de quien usa la aplicación. No es una cuenta. */
export interface Participant {
  name: string;
  avatar: string;
}

export interface CreateMessageRequest {
  content: string;
  authorName: string;
  authorAvatar: string;
  parentId?: string | null;
}

/** Forma estable de error del contrato. El código es lo que se interpreta. */
export interface ApiError {
  code: string;
  message: string;
  field?: string;
  timestamp: string;
}
