import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .catch((err: unknown) => console.error(err)); // NOSONAR - top-level await not supported by Angular CLI browserslist targets
