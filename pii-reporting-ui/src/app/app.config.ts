import {
  APP_INITIALIZER,
  ApplicationConfig,
  isDevMode,
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
import {provideTransloco} from '@jsverse/transloco';
import {TranslocoHttpLoader} from './core/services/transloco-http-loader';
import {ConfirmationService, MessageService} from 'primeng/api';
import {ToastService} from './core/services/toast.service';

/**
 * Initializes polling configuration from backend during app startup.
 * Business purpose: ensures frontend polling interval matches backend configuration.
 */
function initializePollingConfig(pollingService: ConfluenceSpacesPollingService): () => Promise<void> {
  return () => pollingService.loadPollingConfig();
}

export const appConfig: ApplicationConfig = {
  providers: [
    ConfirmationService,
    MessageService,
    ToastService,
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
    provideTransloco({
      config: {
        availableLangs: ['fr', 'en'],
        defaultLang: 'fr',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
        fallbackLang: 'fr',
        missingHandler: {
          useFallbackTranslation: true,
          logMissingKey: !isDevMode()
        }
      },
      loader: TranslocoHttpLoader
    }),
    {
      provide: APP_INITIALIZER,
      useFactory: initializePollingConfig,
      deps: [ConfluenceSpacesPollingService],
      multi: true
    }
  ]
};
