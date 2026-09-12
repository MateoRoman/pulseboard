import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AvatarComponent } from '../../shared/avatar.component';
import { ForumConfigService } from '../../core/services/forum-config.service';
import { IdentityService } from '../../core/services/identity.service';

/**
 * Alta y cambio de la identidad local: nombre y avatar (US1).
 *
 * El límite de longitud del nombre y el conjunto de avatares vienen de
 * `GET /api/config`; este componente no declara ninguno de los dos.
 */
@Component({
  selector: 'app-identity',
  imports: [FormsModule, AvatarComponent],
  templateUrl: './identity.component.html',
  styleUrl: './identity.component.css',
})
export class IdentityComponent {
  private readonly identity = inject(IdentityService);
  private readonly router = inject(Router);
  protected readonly config = inject(ForumConfigService);

  protected readonly name = signal(this.identity.participant()?.name ?? '');
  protected readonly avatar = signal(
    this.identity.participant()?.avatar ?? this.config.avatars[0] ?? 'avatar-01',
  );
  protected readonly submitted = signal(false);

  protected readonly isEditing = this.identity.isEstablished;

  protected readonly trimmedName = computed(() => this.name().trim());

  /** Mensaje de validación, o null si el nombre es aceptable (FR-002). */
  protected readonly nameError = computed(() => {
    const value = this.trimmedName();
    const max = this.config.maxAuthorNameLength;

    if (!value) {
      return 'Escribí un nombre para poder participar.';
    }
    if (max !== null && value.length > max) {
      return `El nombre no puede superar los ${max} caracteres.`;
    }
    return null;
  });

  protected readonly canSubmit = computed(() => this.nameError() === null);

  protected selectAvatar(avatar: string): void {
    this.avatar.set(avatar);
  }

  protected submit(): void {
    this.submitted.set(true);
    if (!this.canSubmit()) {
      return;
    }
    this.identity.set({ name: this.trimmedName(), avatar: this.avatar() });
    this.router.navigate(['/']);
  }
}
