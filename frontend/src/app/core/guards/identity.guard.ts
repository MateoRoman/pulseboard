import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { IdentityService } from '../services/identity.service';

/**
 * Impide entrar al foro sin identidad declarada (FR-001).
 *
 * Es una puerta de usabilidad, no de seguridad: no hay autenticación y el
 * servidor valida igualmente el nombre y el avatar de cada mensaje.
 */
export const identityGuard: CanActivateFn = () => {
  const identity = inject(IdentityService);
  const router = inject(Router);

  return identity.isEstablished() ? true : router.createUrlTree(['/identidad']);
};
