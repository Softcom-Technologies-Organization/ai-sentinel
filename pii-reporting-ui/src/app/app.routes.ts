import {Routes} from '@angular/router';
import {SpacesDashboardComponent} from './features/spaces-dashboard/spaces-dashboard.component';
import {PiiSettingsComponent} from './features/pii-settings/pii-settings.component';

export const routes: Routes = [
  { path: '', component: SpacesDashboardComponent },
  { path: 'settings', component: PiiSettingsComponent },
  { path: '**', redirectTo: '' }
];
