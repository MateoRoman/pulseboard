import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  inject,
} from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { ForumConfigService } from './core/services/forum-config.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(),
    provideRouter(routes),
    /**
     * La configuración del foro se carga antes del primer render.
     *
     * Así ningún componente tiene que lidiar con un estado intermedio en el que
     * `maxDepth` todavía no se conoce, que es justo cuando resultaría tentador
     * escribir el número a mano y romper FR-018.
     */
    provideAppInitializer(() => inject(ForumConfigService).load()),
  ],
};
