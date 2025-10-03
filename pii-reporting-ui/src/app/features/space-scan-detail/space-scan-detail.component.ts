import {ChangeDetectionStrategy, Component, input} from '@angular/core';

import {TagModule} from 'primeng/tag';
import {DataViewModule} from 'primeng/dataview';
import {PiiItem} from '../../core/models/pii-item';
import {ProgressMap} from '../../core/models/progress-map';
import {PiiItemCardComponent} from '../pii-item-card/pii-item-card.component';

// Keep a local minimal model for UI space to avoid importing private types
export type UISpaceLike = { key: string; name?: string; status: 'FAILED'|'RUNNING'|'OK' } | null;

/**
 * Bottom panel showing the current scan state and the PII list for the selected space.
 * Business-oriented: provides an at-a-glance progress and details for the selected space.
 */
@Component({
  selector: 'app-space-scan-detail',
  standalone: true,
  imports: [TagModule, DataViewModule, PiiItemCardComponent],
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

  /** User-facing label for a technical status. */
  statusLabel(status: string): string {
    if (status === 'FAILED') return 'échec';
    if (status === 'RUNNING') return 'en cours';
    return 'ok';
  }
  /** Severity mapping for PrimeNG tags. */
  statusSeverity(status: string): 'danger'|'warning'|'success' {
    if (status === 'FAILED') return 'danger';
    if (status === 'RUNNING') return 'warning';
    return 'success';
  }
}
