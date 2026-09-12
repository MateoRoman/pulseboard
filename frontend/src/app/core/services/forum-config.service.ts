import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ForumConfig } from '../models/forum.models';
import { ForumApiService } from './forum-api.service';

/**
 * Guarda los parámetros que el servidor decide.
 *
 * Existe para que el límite de anidación tenga una sola fuente (FR-018): el
 * back-end lo lee de `application.yml` para validar y el front-end lo obtiene de
 * `GET /api/config`. Ningún componente escribe estos números por su cuenta.
 */
@Injectable({ providedIn: 'root' })
export class ForumConfigService {
  private readonly api = inject(ForumApiService);
  private readonly config = signal<ForumConfig | null>(null);

  /** Se invoca una vez al arrancar, antes del primer render. */
  async load(): Promise<void> {
    this.config.set(await firstValueFrom(this.api.config()));
  }

  get maxDepth(): number | null {
    return this.config()?.maxDepth ?? null;
  }

  get maxContentLength(): number | null {
    return this.config()?.maxContentLength ?? null;
  }

  get maxAuthorNameLength(): number | null {
    return this.config()?.maxAuthorNameLength ?? null;
  }

  get avatars(): string[] {
    return this.config()?.avatars ?? [];
  }

  get loaded(): boolean {
    return this.config() !== null;
  }

  /**
   * Si un mensaje admite respuestas.
   *
   * Un mensaje que ya está en el nivel máximo no las admite, porque su hijo
   * sería el nivel maxDepth + 1. Mientras la configuración no haya cargado se
   * responde que no: es preferible ocultar la acción un instante a ofrecerla y
   * que el servidor la rechace (FR-017).
   */
  canReplyTo(depth: number): boolean {
    const max = this.maxDepth;
    return max !== null && depth < max;
  }
}
