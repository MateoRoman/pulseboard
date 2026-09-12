import { Component, input, output } from '@angular/core';
import { Message } from '../../core/models/forum.models';
import { MessageNodeComponent } from './message-node.component';

/**
 * Una conversación completa, desde su mensaje raíz.
 *
 * Delega todo el dibujo en el nodo recursivo: aquí solo se aporta el contenedor
 * que separa una conversación de la siguiente.
 */
@Component({
  selector: 'app-conversation',
  imports: [MessageNodeComponent],
  template: `
    <section class="conversation">
      <app-message-node [message]="root()" (replied)="changed.emit()" />
    </section>
  `,
  styles: `
    .conversation {
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 10px;
      min-width: 0;
      overflow-x: hidden;
    }
  `,
})
export class ConversationComponent {
  readonly root = input.required<Message>();
  readonly changed = output<void>();
}
