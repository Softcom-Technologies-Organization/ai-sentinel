import {Routes} from '@angular/router';
import {SpacesDashboardComponent} from './features/spaces-dashboard/spaces-dashboard.component';

export const routes: Routes = [
  { path: '', component: SpacesDashboardComponent },
  { path: '**', redirectTo: '' }
];
