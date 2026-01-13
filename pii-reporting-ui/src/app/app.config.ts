import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection
} from '@angular/core';
import {provideRouter} from '@angular/router';

import {routes} from './app.routes';
import {providePrimeNG} from 'primeng/config';
import {provideHttpClient} from '@angular/common/http';
import Aura from '@primeuix/themes/aura';
import {ConfluenceSpacesPollingService} from './core/services/confluence-spaces-polling.service';
import {provideTransloco} from '@jsverse/transloco';
import {TranslocoHttpLoader} from './core/services/transloco-http-loader';
import {ConfirmationService, MessageService} from 'primeng/api';
import {ToastService} from './core/services/toast.service';
import {SentinelleApiService} from './core/services/sentinelle-api.service';

export const appConfig: ApplicationConfig = {
  providers: [
    ConfirmationService,
    MessageService,
    ToastService,
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
    provideAppInitializer(() => inject(ConfluenceSpacesPollingService).loadPollingConfig()),
    provideAppInitializer(() => inject(SentinelleApiService).loadRevealConfig())
  ]
};
