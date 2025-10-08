import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection
} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideAnimations} from '@angular/platform-browser/animations';

import {routes} from './app.routes';
import {providePrimeNG} from 'primeng/config';
import {provideHttpClient} from '@angular/common/http';
import Aura from '@primeuix/themes/aura';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';

export const appConfig: ApplicationConfig = {
  providers: [
    provideAnimationsAsync(),
    provideAnimations(),
    providePrimeNG({
      theme: {
        preset: Aura
      }
    }),
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(),
    provideRouter(routes)
  ]
};
