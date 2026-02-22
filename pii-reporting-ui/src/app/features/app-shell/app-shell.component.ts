import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { NgOptimizedImage } from '@angular/common';
import { TranslocoModule } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { TabsModule } from 'primeng/tabs';
import { LanguageSelectorComponent } from '../../core/components/language-selector/language-selector.component';
import { ConfluenceDashboardComponent } from '../confluence-dashboard/confluence-dashboard.component';
import { PiiSettingsComponent } from '../pii-settings/pii-settings.component';

/**
 * Application shell with top bar and Confluence source tab.
 *
 * Hosts the global toast, confirmation dialog, and settings dialog.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    NgOptimizedImage,
    TranslocoModule,
    ButtonModule,
    TooltipModule,
    ToastModule,
    ConfirmDialogModule,
    DialogModule,
    TabsModule,
    LanguageSelectorComponent,
    ConfluenceDashboardComponent,
    PiiSettingsComponent
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppShellComponent {
  readonly activeSourceId = signal('confluence');

  // Settings dialog state
  readonly showSettingsDialog = signal(false);
  readonly settingsInitialTab = signal(0);

  openSettingsDialog(tab: number = 0): void {
    this.settingsInitialTab.set(tab);
    this.showSettingsDialog.set(true);
  }

  closeSettingsDialog(): void {
    this.showSettingsDialog.set(false);
  }
}
