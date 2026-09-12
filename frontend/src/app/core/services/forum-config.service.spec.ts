import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ForumApiService } from './forum-api.service';
import { ForumConfigService } from './forum-config.service';
import { ForumConfig } from '../models/forum.models';

/**
 * Pruebas de la interpretación del límite de anidación.
 *
 * Lo delicado aquí es que `maxDepth === null` significa dos cosas opuestas según el
 * estado: «sin cargar» antes de arrancar, «sin límite» después. Confundirlas ofrecería
 * responder cuando no corresponde, o al revés.
 */
describe('ForumConfigService', () => {
  function serviceWith(config: ForumConfig | null): ForumConfigService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: ForumApiService, useValue: { config: () => of(config) } }],
    });
    return TestBed.inject(ForumConfigService);
  }

  const limited: ForumConfig = {
    maxDepth: 5,
    maxContentLength: 2000,
    maxAuthorNameLength: 40,
    avatars: ['avatar-01'],
  };

  const unlimited: ForumConfig = { ...limited, maxDepth: null };

  it('antes de cargar no ofrece responder, aunque maxDepth sea null', async () => {
    const service = serviceWith(limited);

    // Sin llamar a load(): el estado inicial no debe confundirse con «sin límite».
    expect(service.loaded).toBe(false);
    expect(service.unlimitedDepth).toBe(false);
    expect(service.canReplyTo(1)).toBe(false);
  });

  it('con un tope, permite responder por debajo y lo impide al alcanzarlo', async () => {
    const service = serviceWith(limited);
    await service.load();

    expect(service.maxDepth).toBe(5);
    expect(service.unlimitedDepth).toBe(false);
    expect(service.canReplyTo(1)).toBe(true);
    expect(service.canReplyTo(4)).toBe(true);
    expect(service.canReplyTo(5)).toBe(false);
    expect(service.canReplyTo(6)).toBe(false);
  });

  it('con maxDepth null y configuración cargada, la anidación es ilimitada', async () => {
    const service = serviceWith(unlimited);
    await service.load();

    expect(service.loaded).toBe(true);
    expect(service.unlimitedDepth).toBe(true);
    expect(service.canReplyTo(1)).toBe(true);
    expect(service.canReplyTo(5)).toBe(true);
    expect(service.canReplyTo(500)).toBe(true);
  });

  it('expone el resto de límites que el front no debe declarar', async () => {
    const service = serviceWith(limited);
    await service.load();

    expect(service.maxContentLength).toBe(2000);
    expect(service.maxAuthorNameLength).toBe(40);
    expect(service.avatars).toEqual(['avatar-01']);
  });
});
