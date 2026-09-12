import { Routes } from '@angular/router';
import { identityGuard } from './core/guards/identity.guard';

export const routes: Routes = [
  {
    path: 'identidad',
    loadComponent: () =>
      import('./features/identity/identity.component').then((m) => m.IdentityComponent),
  },
  {
    path: '',
    // Sin identidad no se puede participar (FR-001): el guard redirige a
    // declararla antes de mostrar el foro.
    canActivate: [identityGuard],
    loadComponent: () =>
      import('./features/conversation-list/conversation-list.component').then(
        (m) => m.ConversationListComponent,
      ),
  },
  { path: '**', redirectTo: '' },
];
