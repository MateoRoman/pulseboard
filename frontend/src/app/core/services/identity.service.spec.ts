import { TestBed } from '@angular/core/testing';
import { IdentityService } from './identity.service';

describe('IdentityService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  function service(): IdentityService {
    return TestBed.inject(IdentityService);
  }

  it('arranca sin identidad cuando no hay nada guardado', () => {
    expect(service().isEstablished()).toBe(false);
    expect(service().participant()).toBeNull();
  });

  it('guarda la identidad y la marca como establecida', () => {
    const identity = service();

    identity.set({ name: 'Mateo', avatar: 'avatar-03' });

    expect(identity.isEstablished()).toBe(true);
    expect(identity.participant()).toEqual({ name: 'Mateo', avatar: 'avatar-03' });
  });

  it('recorta los espacios del nombre al guardarlo', () => {
    const identity = service();

    identity.set({ name: '   Ana   ', avatar: 'avatar-01' });

    expect(identity.participant()?.name).toBe('Ana');
  });

  it('la identidad sobrevive a recrear el servicio, que equivale a recargar', () => {
    service().set({ name: 'Mateo', avatar: 'avatar-05' });

    // Un servicio nuevo lee de localStorage, como haría un arranque en frío.
    TestBed.resetTestingModule();

    expect(service().participant()).toEqual({ name: 'Mateo', avatar: 'avatar-05' });
  });

  it('clear borra la identidad de memoria y de localStorage', () => {
    const identity = service();
    identity.set({ name: 'Mateo', avatar: 'avatar-01' });

    identity.clear();

    expect(identity.isEstablished()).toBe(false);
    TestBed.resetTestingModule();
    expect(service().participant()).toBeNull();
  });

  it('ignora contenido corrupto en localStorage en lugar de romper el arranque', () => {
    localStorage.setItem('pulseboard.identity', 'no es json');

    expect(service().participant()).toBeNull();
  });

  it('ignora una identidad guardada sin nombre utilizable', () => {
    localStorage.setItem('pulseboard.identity', JSON.stringify({ name: '   ', avatar: 'avatar-01' }));

    expect(service().participant()).toBeNull();
  });
});
