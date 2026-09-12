import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AvatarComponent } from './shared/avatar.component';
import { IdentityService } from './core/services/identity.service';

/** Armazón de la aplicación: cabecera con la identidad activa y salida de rutas. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, AvatarComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly identity = inject(IdentityService);
}
