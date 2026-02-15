import { Routes } from '@angular/router';
import { AppShellComponent } from './features/app-shell/app-shell.component';
import { PiiSettingsComponent } from './features/pii-settings/pii-settings.component';

export const routes: Routes = [
  { path: '', component: AppShellComponent },
  { path: 'settings', component: PiiSettingsComponent },
  { path: '**', redirectTo: '' }
];
