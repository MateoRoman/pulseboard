import { Injectable, computed, signal } from '@angular/core';
import { Participant } from '../models/forum.models';

const STORAGE_KEY = 'pulseboard.identity';

/**
 * Identidad local de quien usa la aplicación.
 *
 * No es autenticación: no hay credenciales, no se verifica y no viaja al
 * servidor como entidad propia. Cada mensaje lleva copiados el nombre y el
 * avatar vigentes al publicarlo (FR-006), de modo que cambiar de identidad no
 * altera la autoría de lo ya publicado (FR-005).
 *
 * Se usa localStorage y no sessionStorage para que la identidad sobreviva al
 * cierre de la pestaña: redeclararla en cada visita sería molesto sin aportar
 * nada.
 */
@Injectable({ providedIn: 'root' })
export class IdentityService {
  private readonly current = signal<Participant | null>(read());

  readonly participant = this.current.asReadonly();
  readonly isEstablished = computed(() => this.current() !== null);

  set(participant: Participant): void {
    const trimmed: Participant = {
      name: participant.name.trim(),
      avatar: participant.avatar,
    };
    this.current.set(trimmed);
    write(trimmed);
  }

  clear(): void {
    this.current.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Almacenamiento no disponible: la identidad seguirá viva en memoria
      // durante esta sesión, que es suficiente para poder participar.
    }
  }
}

function read(): Participant | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<Participant>;
    if (typeof parsed.name !== 'string' || typeof parsed.avatar !== 'string') {
      return null;
    }
    return parsed.name.trim() ? { name: parsed.name, avatar: parsed.avatar } : null;
  } catch {
    // Almacenamiento bloqueado o contenido corrupto: se empieza sin identidad
    // en lugar de impedir que la aplicación arranque.
    return null;
  }
}

function write(participant: Participant): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(participant));
  } catch {
    // Ver comentario en clear(): no poder persistir no impide participar.
  }
}
