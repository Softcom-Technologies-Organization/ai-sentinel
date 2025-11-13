import {ChangeDetectionStrategy, Component, inject, input} from '@angular/core';

import {TagModule} from 'primeng/tag';
import {DataViewModule} from 'primeng/dataview';
import {PiiItem} from '../../core/models/pii-item';
import {ProgressMap} from '../../core/models/progress-map';
import {PiiItemCardComponent} from '../pii-item-card/pii-item-card.component';
import {TranslocoModule, TranslocoService} from '@jsverse/transloco';

// Keep a local minimal model for UI space to avoid importing private types
export type UISpaceLike = { key: string; name?: string; status: 'FAILED'|'RUNNING'|'OK' } | null;

/**
 * Bottom panel showing the current scan state and the PII list for the selected space.
 * Business-oriented: provides an at-a-glance progress and details for the selected space.
 */
@Component({
  selector: 'app-space-scan-detail',
  standalone: true,
  imports: [TagModule, DataViewModule, PiiItemCardComponent, TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './space-scan-detail.component.html',
  styleUrl: './space-scan-detail.component.css',
})
export class SpaceScanDetailComponent {
  readonly scanning = input<boolean>(false);
  readonly currentSpaceKey = input<string | null>(null);
  readonly progress = input<ProgressMap>({});
  readonly space = input<UISpaceLike>(null);
  readonly items = input<PiiItem[]>([]);
  readonly translocoService = inject(TranslocoService);

  /** User-facing label for a technical status. */
  statusLabel(status: string): string {
    const key = this.getStatusKey(status);
    return this.translocoService.translate(key);
  }

  private getStatusKey(status: string): string {
    switch (status) {
      case 'FAILED':
        return 'dashboard.status.failed';
      case 'RUNNING':
        return 'dashboard.status.running';
      case 'OK':
        return 'dashboard.status.ok';
      default:
        return 'dashboard.status.ok';
    }
  }
  /** Severity mapping for PrimeNG tags. */
  statusSeverity(status: string): 'danger'|'warning'|'success' {
    if (status === 'FAILED') return 'danger';
    if (status === 'RUNNING') return 'warning';
    return 'success';
  }

  /**
   * TrackBy function to preserve component state when list updates.
   * Uses pageId + attachmentName as unique identifier to prevent unnecessary re-renders.
   * This ensures user interactions (expanded details, revealed state) are preserved when new items arrive.
   */
  trackByItem(index: number, item: PiiItem): string {
    return `${item.pageId}-${item.attachmentName || 'page'}`;
  }
}
