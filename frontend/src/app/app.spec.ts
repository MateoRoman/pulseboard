import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { IdentityService } from './core/services/identity.service';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('se crea correctamente', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('muestra la marca y la salida de rutas', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.brand-name')?.textContent).toContain('Pulseboard');
    expect(host.querySelector('router-outlet')).not.toBeNull();
  });

  it('no muestra identidad en la cabecera mientras no haya una declarada', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.identity')).toBeNull();
  });

  it('muestra el nombre en la cabecera cuando hay identidad', async () => {
    TestBed.inject(IdentityService).set({ name: 'Mateo', avatar: 'avatar-02' });

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.identity-name')?.textContent).toContain(
      'Mateo',
    );
  });
});
