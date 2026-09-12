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

  /** Profundidad máxima, o `null` si es ilimitada o si aún no cargó la configuración. */
  get maxDepth(): number | null {
    return this.config()?.maxDepth ?? null;
  }

  /** Si la anidación no tiene tope. Falso mientras la configuración no haya cargado. */
  get unlimitedDepth(): boolean {
    return this.loaded && this.config()!.maxDepth === null;
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
   * Tres casos, en este orden:
   *
   * 1. Configuración sin cargar → no. Es preferible ocultar la acción un instante
   *    a ofrecerla y que el servidor la rechace (FR-017).
   * 2. Anidación ilimitada → sí, siempre.
   * 3. Con tope → solo si el mensaje está por debajo de él: un mensaje en el nivel
   *    máximo no admite respuestas porque su hijo sería `maxDepth + 1`.
   *
   * El orden importa: `maxDepth === null` significa cosas opuestas en los casos 1 y 2,
   * así que hay que descartar el 1 antes de interpretarlo.
   */
  canReplyTo(depth: number): boolean {
    if (!this.loaded) {
      return false;
    }
    if (this.unlimitedDepth) {
      return true;
    }
    return depth < this.maxDepth!;
  }
}
