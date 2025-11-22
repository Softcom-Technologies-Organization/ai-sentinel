import {ChangeDetectionStrategy, Component, computed, inject, Input} from '@angular/core';
import {ProgressBarModule} from 'primeng/progressbar';
import {ScanProgressService} from '../../../core/services/scan-progress.service';

/**
 * Component responsible for displaying scan progress for a Confluence space.
 * Business purpose: Provides visual feedback on scan advancement using a progress bar.
 *
 * This component delegates all progress calculation logic to ScanProgressService
 * and focuses solely on presentation concerns.
 */
@Component({
  selector: 'app-scan-progress-bar',
  standalone: true,
  imports: [ProgressBarModule],
  templateUrl: './scan-progress-bar.component.html',
  styleUrl: './scan-progress-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScanProgressBarComponent {
  private readonly scanProgressService = inject(ScanProgressService);

  /**
   * Space key for which to display progress.
   * Required input that identifies the Confluence space being scanned.
   */
  @Input({ required: true }) spaceKey!: string;

  /**
   * Computed signal that retrieves the current progress percentage from the service.
   * Business rule: Progress is calculated by the service with appropriate fallbacks.
   *
   * @returns Progress percentage (0-100)
   */
  readonly progressPercent = computed(() => {
    return this.scanProgressService.getProgressPercent(this.spaceKey);
  });
}
