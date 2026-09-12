import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ForumApiError, ForumApiService } from '../../core/services/forum-api.service';
import { ForumConfigService } from '../../core/services/forum-config.service';
import { IdentityService } from '../../core/services/identity.service';

/**
 * Formulario para publicar un mensaje principal o una respuesta.
 *
 * La diferencia la marca `parentId`: sin él se crea un mensaje principal
 * (FR-007), con él una respuesta (FR-008).
 */
@Component({
  selector: 'app-message-form',
  imports: [FormsModule],
  templateUrl: './message-form.component.html',
  styleUrl: './message-form.component.css',
})
export class MessageFormComponent {
  private readonly api = inject(ForumApiService);
  private readonly identity = inject(IdentityService);
  protected readonly config = inject(ForumConfigService);

  readonly parentId = input<string | null>(null);
  readonly placeholder = input('¿Qué querés compartir?');
  readonly submitLabel = input('Publicar');

  /** Se emite tras publicar con éxito; también cuando hay que refrescar. */
  readonly published = output<void>();

  protected readonly content = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly trimmed = computed(() => this.content().trim());

  protected readonly tooLong = computed(() => {
    const max = this.config.maxContentLength;
    return max !== null && this.trimmed().length > max;
  });

  protected readonly canSubmit = computed(
    () => this.trimmed().length > 0 && !this.tooLong() && !this.sending(),
  );

  protected submit(): void {
    if (!this.canSubmit()) {
      // Contenido vacío: se informa y NO se borra lo escrito (FR-009).
      if (!this.trimmed()) {
        this.error.set('El mensaje no puede estar vacío.');
      }
      return;
    }

    const participant = this.identity.participant();
    if (!participant) {
      this.error.set('Necesitás declarar tu identidad antes de publicar.');
      return;
    }

    this.sending.set(true);
    this.error.set(null);

    this.api
      .createMessage({
        content: this.trimmed(),
        authorName: participant.name,
        authorAvatar: participant.avatar,
        parentId: this.parentId(),
      })
      .subscribe({
        next: () => {
          this.content.set('');
          this.sending.set(false);
          this.published.emit();
        },
        error: (err: ForumApiError) => {
          this.sending.set(false);
          // Lo escrito se conserva: el formulario no se vacía ante un rechazo.
          this.error.set(this.describe(err));

          // 422 significa que la vista tenía una idea desactualizada del árbol.
          // Refrescar es la reacción correcta, no mostrarlo como error de campo.
          if (err.code === 'MAX_DEPTH_EXCEEDED' || err.code === 'PARENT_NOT_FOUND') {
            this.published.emit();
          }
        },
      });
  }

  private describe(err: ForumApiError): string {
    switch (err.code) {
      case 'MAX_DEPTH_EXCEEDED':
        return 'Ese mensaje ya llegó al nivel máximo de anidación. Actualizamos la conversación.';
      case 'PARENT_NOT_FOUND':
        return 'El mensaje al que respondías ya no está disponible.';
      case 'NETWORK_ERROR':
        return 'No se pudo contactar con el servidor. Revisá que el back-end esté levantado.';
      default:
        return err.message;
    }
  }
}
