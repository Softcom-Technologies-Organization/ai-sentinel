import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig) // NOSONAR - top-level await not supported by Angular CLI esbuild bundler
  .catch((err) => console.error(err));
