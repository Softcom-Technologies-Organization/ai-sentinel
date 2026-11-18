import { ChangeDetectionStrategy, Component, signal, viewChild } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { AppHeaderComponent } from '../app-header/app-header.component';
import { ConfluenceDashboardComponent } from '../confluence-dashboard/confluence-dashboard.component';
import { PiiSettingsComponent } from '../pii-settings/pii-settings.component';

/**
 * Application shell hosting the shared header (with the data source selector) and the
 * Confluence dashboard, plus the global toast, confirmation dialog, and settings dialog.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    TranslocoModule,
    TooltipModule,
    ToastModule,
    ConfirmDialogModule,
    DialogModule,
    AppHeaderComponent,
    ConfluenceDashboardComponent,
    PiiSettingsComponent
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppShellComponent {
  // Settings dialog state
  readonly showSettingsDialog = signal(false);
  readonly settingsInitialTab = signal(0);

  // Child component references
  private readonly confluenceDashboard = viewChild(ConfluenceDashboardComponent);

  openSettingsDialog(tab: number = 0): void {
    this.settingsInitialTab.set(tab);
    this.showSettingsDialog.set(true);
  }

  closeSettingsDialog(): void {
    this.showSettingsDialog.set(false);
  }

  /**
   * Handle settings saved event from PiiSettingsComponent.
   * Triggers dashboard refresh to reflect configuration changes.
   */
  onSettingsSaved(): void {
    this.confluenceDashboard()?.refreshAfterSettingsSave();
  }
}
