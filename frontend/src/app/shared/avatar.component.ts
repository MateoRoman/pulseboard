import { Component, computed, input } from '@angular/core';

/**
 * Dibuja uno de los avatares del conjunto predefinido.
 *
 * Los identificadores (`avatar-01`..`avatar-08`) los define el servidor; el
 * front-end decide únicamente cómo se ven. No hay carga de imágenes: el conjunto
 * es cerrado (FR-003), así que se resuelven a una figura generada, sin pedir
 * archivos externos ni depender de servicios de terceros (TC-06).
 */
@Component({
  selector: 'app-avatar',
  template: `
    <span
      class="avatar"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [style.background]="background()"
      [style.font-size.px]="size() * 0.42"
      [attr.title]="name()"
      [attr.aria-label]="'Avatar de ' + name()"
      role="img"
    >
      {{ glyph() }}
    </span>
  `,
  styles: `
    .avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      color: #fff;
      font-weight: 600;
      flex: 0 0 auto;
      user-select: none;
      line-height: 1;
    }
  `,
})
export class AvatarComponent {
  readonly avatar = input.required<string>();
  readonly name = input<string>('');
  readonly size = input<number>(36);

  /** Cada avatar del conjunto tiene su propio color y símbolo. */
  private readonly index = computed(() => {
    const match = /avatar-(\d+)/.exec(this.avatar());
    return match ? (Number(match[1]) - 1 + PALETTE.length) % PALETTE.length : 0;
  });

  readonly background = computed(() => PALETTE[this.index()]);
  readonly glyph = computed(() => GLYPHS[this.index()]);
}

const PALETTE = [
  'linear-gradient(135deg, #6366f1, #4338ca)',
  'linear-gradient(135deg, #ec4899, #be185d)',
  'linear-gradient(135deg, #14b8a6, #0f766e)',
  'linear-gradient(135deg, #f59e0b, #b45309)',
  'linear-gradient(135deg, #3b82f6, #1d4ed8)',
  'linear-gradient(135deg, #8b5cf6, #6d28d9)',
  'linear-gradient(135deg, #10b981, #047857)',
  'linear-gradient(135deg, #ef4444, #b91c1c)',
];

const GLYPHS = ['◆', '●', '▲', '★', '■', '⬢', '♦', '✦'];
