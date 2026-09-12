import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import {
  ApiError,
  ConversationsResponse,
  CreateMessageRequest,
  ForumConfig,
  Message,
} from '../models/forum.models';

/** Error de la API ya normalizado, con el código estable del contrato. */
export class ForumApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly field?: string,
  ) {
    super(message);
    this.name = 'ForumApiError';
  }
}

/**
 * Único punto de acceso a la API (Principio II).
 *
 * Ningún componente llama a HttpClient por su cuenta, y las decisiones se toman
 * sobre `code`, nunca sobre el texto de `message`: el texto puede cambiar sin
 * romper el contrato, el código no.
 */
@Injectable({ providedIn: 'root' })
export class ForumApiService {
  private readonly http = inject(HttpClient);

  config(): Observable<ForumConfig> {
    return this.http.get<ForumConfig>('/api/config').pipe(catchError(normalizeError));
  }

  conversations(): Observable<Message[]> {
    return this.http.get<ConversationsResponse>('/api/conversations').pipe(
      map((response) => response.conversations),
      catchError(normalizeError),
    );
  }

  createMessage(request: CreateMessageRequest): Observable<Message> {
    return this.http.post<Message>('/api/messages', request).pipe(catchError(normalizeError));
  }
}

/**
 * Traduce cualquier fallo a un ForumApiError con código.
 *
 * Un fallo de red no trae cuerpo del servidor, así que se le asigna un código
 * propio en lugar de dejar que llegue como error genérico sin identificar.
 */
function normalizeError(response: HttpErrorResponse): Observable<never> {
  const body = response.error as ApiError | null;

  if (body?.code) {
    return throwError(() => new ForumApiError(body.code, body.message, body.field));
  }

  if (response.status === 0) {
    return throwError(
      () =>
        new ForumApiError(
          'NETWORK_ERROR',
          'No se pudo contactar con el servidor. ¿Está levantado el back-end?',
        ),
    );
  }

  return throwError(() => new ForumApiError('UNKNOWN_ERROR', 'Ocurrió un error inesperado.'));
}
