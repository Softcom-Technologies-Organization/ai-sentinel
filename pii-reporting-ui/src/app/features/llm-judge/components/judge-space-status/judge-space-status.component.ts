import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { JudgeJobDto, JudgeSpaceSummaryDto } from '../../../../core/models/judge.model';
import { TestIds } from '../../../test-ids.constants';

/**
 * Judge sub-status of one dashboard row: the running job with its own progress, or the
 * outcome of the last run once judged.
 */
@Component({
  selector: 'app-judge-space-status',
  standalone: true,
  imports: [TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './judge-space-status.component.html',
  styleUrl: './judge-space-status.component.css',
})
export class JudgeSpaceStatusComponent {
  readonly summary = input<JudgeSpaceSummaryDto | null>(null);

  readonly testIds = TestIds.judge;

  readonly activeJob = computed<JudgeJobDto | null>(() => this.summary()?.activeJob ?? null);
  readonly judged = computed(() => (this.summary()?.judgedCount ?? 0) > 0);
  readonly percent = computed(() => {
    const job = this.activeJob();
    return !job || job.total === 0 ? 0 : Math.round((job.processed / job.total) * 100);
  });
}
