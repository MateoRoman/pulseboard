import { Component, inject, signal } from '@angular/core';
import { ConversationComponent } from '../conversation/conversation.component';
import { ForumApiError, ForumApiService } from '../../core/services/forum-api.service';
import { Message } from '../../core/models/forum.models';
import { MessageFormComponent } from '../conversation/message-form.component';

/**
 * Vista principal: publicar un mensaje nuevo y leer todas las conversaciones.
 *
 * No hay paginación: la especificación la deja fuera de alcance y asume un
 * volumen de cientos de mensajes, así que se carga el foro completo.
 */
@Component({
  selector: 'app-conversation-list',
  imports: [ConversationComponent, MessageFormComponent],
  templateUrl: './conversation-list.component.html',
  styleUrl: './conversation-list.component.css',
})
export class ConversationListComponent {
  private readonly api = inject(ForumApiService);

  protected readonly conversations = signal<Message[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  /**
   * Recarga las conversaciones.
   *
   * Se invoca al arrancar y tras cada publicación, de modo que un mensaje nuevo
   * aparece sin que nadie recargue la página a mano (FR-026).
   */
  protected reload(): void {
    this.api.conversations().subscribe({
      next: (conversations) => {
        this.conversations.set(conversations);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (err: ForumApiError) => {
        this.loading.set(false);
        this.error.set(
          err.code === 'STORE_UNREADABLE'
            ? 'El almacén del foro no se pudo leer. Revisá backend/data/messages.json.'
            : err.message,
        );
      },
    });
  }
}
