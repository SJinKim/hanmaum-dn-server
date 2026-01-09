import {
  APP_INITIALIZER,
  ApplicationConfig, inject, provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config'
import Aura from '@primeng/themes/aura';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import {AuthService} from './core/auth/auth.service';
import {authInterceptor} from './core/auth/auth.interceptor';

// Factory: wartet auf das Promise von init()
function initializeApp(authService: AuthService) {
  return () => authService.init()
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),

    // HTTP + Interceptor
    provideHttpClient(withInterceptors([authInterceptor])),

    // OAuth Lib Provider
    provideOAuthClient(),

    // init startet kc und blockiert den app-start bis login approved
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      return authService.init()
    }),

    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.dark-mode',
          cssLayer: {
            name: 'primeng',
            order: 'tailwind-base, primeng, tailwind-utilities',
          }
        }
      }
    })

  ]
};
