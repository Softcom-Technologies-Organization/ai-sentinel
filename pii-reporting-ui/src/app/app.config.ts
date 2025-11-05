import {
  APP_INITIALIZER,
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
import {ConfluenceSpacesPollingService} from './core/services/confluence-spaces-polling.service';

/**
 * Initializes polling configuration from backend during app startup.
 * Business purpose: ensures frontend polling interval matches backend configuration.
 */
function initializePollingConfig(pollingService: ConfluenceSpacesPollingService): () => Promise<void> {
  return () => pollingService.loadPollingConfig();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideAnimationsAsync(),
    provideAnimations(),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: false
        }
      }
    }),
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(),
    provideRouter(routes),
    {
      provide: APP_INITIALIZER,
      useFactory: initializePollingConfig,
      deps: [ConfluenceSpacesPollingService],
      multi: true
    }
  ]
};
