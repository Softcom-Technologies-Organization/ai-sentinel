import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { NgClass, NgOptimizedImage } from '@angular/common';
import { TranslocoModule } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { TabsModule } from 'primeng/tabs';
import { LanguageSelectorComponent } from '../../core/components/language-selector/language-selector.component';
import { ConfluenceDashboardComponent } from '../confluence-dashboard/confluence-dashboard.component';
import { SourceEmptyStateComponent } from '../source-empty-state/source-empty-state.component';
import { PiiSettingsComponent } from '../pii-settings/pii-settings.component';
import { DATA_SOURCES, DataSource } from '../../core/models/data-source.model';

/**
 * Application shell with top bar and horizontal source tabs.
 *
 * Hosts the global toast, confirmation dialog, and settings dialog.
 * Each data source gets its own tab panel.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    NgClass,
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
    SourceEmptyStateComponent,
    PiiSettingsComponent
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppShellComponent {
  readonly sources: DataSource[] = DATA_SOURCES;
  readonly activeSourceId = signal('confluence');

  // Settings dialog state
  readonly showSettingsDialog = signal(false);
  readonly settingsInitialTab = signal(0);

  onSourceChange(id: string): void {
    this.activeSourceId.set(id);
  }

  getSource(id: string): DataSource {
    return this.sources.find(s => s.id === id)!;
  }

  openSettingsDialog(tab: number = 0): void {
    this.settingsInitialTab.set(tab);
    this.showSettingsDialog.set(true);
  }

  closeSettingsDialog(): void {
    this.showSettingsDialog.set(false);
  }
}
