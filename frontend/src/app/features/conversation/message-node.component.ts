import { Component, inject, input, output, signal } from '@angular/core';
import { AvatarComponent } from '../../shared/avatar.component';
import { ForumConfigService } from '../../core/services/forum-config.service';
import { Message } from '../../core/models/forum.models';
import { MessageFormComponent } from './message-form.component';

/**
 * Renderiza un mensaje y, recursivamente, sus respuestas.
 *
 * El componente se invoca a sí mismo en su plantilla: es lo que permite
 * representar profundidad arbitraria sin que ninguna parte del código conozca el
 * número de niveles (Principio I). El límite que sí existe se consulta a
 * `ForumConfigService`, nunca se escribe aquí (FR-018).
 */
@Component({
  selector: 'app-message-node',
  imports: [AvatarComponent, MessageFormComponent],
  templateUrl: './message-node.component.html',
  styleUrl: './message-node.component.css',
})
export class MessageNodeComponent {
  private readonly config = inject(ForumConfigService);

  readonly message = input.required<Message>();

  /** Se emite cuando se publica una respuesta, para que la vista se refresque. */
  readonly replied = output<void>();

  protected readonly replying = signal(false);

  /**
   * Si se ofrece responder sobre este mensaje.
   *
   * Ocultar la acción es una mejora de experiencia, no la garantía: el servidor
   * rechaza igualmente cualquier intento de superar el límite (FR-017).
   */
  protected canReply(): boolean {
    return this.config.canReplyTo(this.message().depth);
  }

  /**
   * A partir de cierta profundidad el sangrado deja de crecer, para que la
   * conversación siga siendo legible en pantallas estrechas (FR-022).
   */
  protected indentLevel(): number {
    return Math.min(this.message().depth - 1, MAX_VISUAL_INDENT);
  }

  protected toggleReply(): void {
    this.replying.update((value) => !value);
  }

  protected onPublished(): void {
    this.replying.set(false);
    this.replied.emit();
  }

  protected formatDate(iso: string): string {
    const date = new Date(iso);
    return date.toLocaleString('es', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}

/**
 * Niveles de sangrado visibles antes de dejar de aumentar el sangrado.
 *
 * No es el límite de anidación del producto —ese vive en el servidor y se
 * consulta— sino cuántos escalones caben en pantalla sin comprimir el texto.
 * Son cosas distintas: el modelo admite profundidad arbitraria, la pantalla no.
 */
const MAX_VISUAL_INDENT = 4;
