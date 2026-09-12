import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Message } from '../../core/models/forum.models';
import { ForumConfigService } from '../../core/services/forum-config.service';
import { MessageNodeComponent } from './message-node.component';

/** ForumConfigService con el límite fijado, sin pegarle a la API en las pruebas. */
class StubForumConfigService {
  constructor(private readonly max: number | null) {}
  get maxDepth() {
    return this.max;
  }
  get unlimitedDepth() {
    return this.max === null;
  }
  get maxContentLength() {
    return 2000;
  }
  get maxAuthorNameLength() {
    return 40;
  }
  get avatars() {
    return ['avatar-01'];
  }
  get loaded() {
    return true;
  }
  canReplyTo(depth: number) {
    return this.max === null ? true : depth < this.max;
  }
}

/** Construye una cadena anidada de la profundidad pedida. */
function chain(depth: number, current = 1): Message {
  return {
    id: `msg-${current}`,
    content: `contenido nivel ${current}`,
    authorName: `Autor ${current}`,
    authorAvatar: 'avatar-01',
    createdAt: '2026-09-11T10:00:00.000Z',
    parentId: current === 1 ? null : `msg-${current - 1}`,
    depth: current,
    replies: current < depth ? [chain(depth, current + 1)] : [],
  };
}

describe('MessageNodeComponent', () => {
  async function render(
    message: Message,
    maxDepth: number | null = 5,
  ): Promise<ComponentFixture<MessageNodeComponent>> {
    await TestBed.configureTestingModule({
      imports: [MessageNodeComponent],
      providers: [
        provideHttpClient(),
        { provide: ForumConfigService, useValue: new StubForumConfigService(maxDepth) },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(MessageNodeComponent);
    fixture.componentRef.setInput('message', message);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('muestra el contenido, el autor y el nivel del mensaje', async () => {
    const fixture = await render(chain(1));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('contenido nivel 1');
    expect(text).toContain('Autor 1');
    expect(text).toContain('N1');
  });

  it('renderiza recursivamente los 5 niveles de una conversación', async () => {
    const fixture = await render(chain(5));
    const host = fixture.nativeElement as HTMLElement;

    // El componente se invoca a sí mismo: un nodo por nivel.
    expect(host.querySelectorAll('app-message-node').length).toBe(4);

    const text = host.textContent ?? '';
    for (let level = 1; level <= 5; level++) {
      expect(text).toContain(`contenido nivel ${level}`);
    }
  });

  it('cada nivel queda anidado dentro del anterior, no en paralelo', async () => {
    const fixture = await render(chain(3));
    const host = fixture.nativeElement as HTMLElement;

    const firstReplies = host.querySelector('.replies');
    expect(firstReplies).not.toBeNull();
    // La respuesta del nivel 2 contiene a su vez la del nivel 3.
    expect(firstReplies?.querySelector('.replies')).not.toBeNull();
  });

  it('ofrece responder mientras no se alcanzó el nivel máximo', async () => {
    const fixture = await render(chain(1), 5);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.link')?.textContent).toContain('Responder');
    expect(host.querySelector('.max-depth')).toBeNull();
  });

  it('no ofrece responder sobre un mensaje que ya está en el nivel máximo', async () => {
    const deepest: Message = { ...chain(1), depth: 5 };

    const fixture = await render(deepest, 5);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.link')).toBeNull();
    expect(host.querySelector('.max-depth')?.textContent).toContain('Nivel máximo');
  });

  it('el límite sale de la configuración, no de un número propio del componente', async () => {
    // Con maxDepth 3, un mensaje en nivel 3 ya no admite respuestas, aunque el
    // valor de producción sea 5. Si el componente tuviera el número escrito,
    // esta prueba fallaría.
    const fixture = await render({ ...chain(1), depth: 3 }, 3);

    expect((fixture.nativeElement as HTMLElement).querySelector('.link')).toBeNull();
  });

  it('muestra el contenido con marcado como texto literal', async () => {
    const withMarkup: Message = { ...chain(1), content: '<script>alert(1)</script>' };

    const fixture = await render(withMarkup);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('script')).toBeNull();
    expect(host.querySelector('.content')?.textContent).toBe('<script>alert(1)</script>');
  });

  it('muestra el nombre del autor como texto literal', async () => {
    const fixture = await render({ ...chain(1), authorName: '<b>Ana</b>' });
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.author b')).toBeNull();
    expect(host.querySelector('.author')?.textContent).toBe('<b>Ana</b>');
  });

  it('muestra todas las ramas hermanas de un mismo padre', async () => {
    const root: Message = {
      ...chain(1),
      replies: [
        { ...chain(1), id: 'r1', content: 'rama uno', depth: 2, parentId: 'msg-1', replies: [] },
        { ...chain(1), id: 'r2', content: 'rama dos', depth: 2, parentId: 'msg-1', replies: [] },
      ],
    };

    const fixture = await render(root);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('rama uno');
    expect(text).toContain('rama dos');
  });

  it('con anidación ilimitada siempre ofrece responder, sin importar la profundidad', async () => {
    // Profundidad 42, muy por encima del tope que rige por defecto.
    const fixture = await render({ ...chain(1), depth: 42 }, null);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.link')?.textContent).toContain('Responder');
    expect(host.querySelector('.max-depth')).toBeNull();
  });

  it('con anidación ilimitada renderiza cadenas más profundas que el tope por defecto', async () => {
    const fixture = await render(chain(12), null);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelectorAll('app-message-node').length).toBe(11);
    expect(host.textContent).toContain('contenido nivel 12');
  });
});
