import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { JudgePlanDto } from '../../../../core/models/judge.model';
import { TestIds } from '../../../test-ids.constants';

export interface JudgeSpaceChip {
  key: string;
  name: string;
}

const MAX_VISIBLE_CHIPS = 3;

/**
 * Sticky action bar of the dashboard once spaces are checked: how many pending findings the
 * judge would evaluate across them (backend plan, verbatim) and the launch gesture.
 */
@Component({
  selector: 'app-judge-space-bulk-bar',
  standalone: true,
  imports: [TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './judge-space-bulk-bar.component.html',
  styleUrl: './judge-space-bulk-bar.component.css',
})
export class JudgeSpaceBulkBarComponent {
  readonly spaces = input.required<JudgeSpaceChip[]>();
  readonly plan = input.required<JudgePlanDto | null>();

  readonly disabledReason = input<string | null>(null);

  readonly cleared = output<void>();
  readonly launch = output<void>();

  readonly testIds = TestIds.judge.bulkBar;

  readonly visibleChips = computed(() => this.spaces().slice(0, MAX_VISIBLE_CHIPS));
  readonly overflowCount = computed(() => Math.max(0, this.spaces().length - MAX_VISIBLE_CHIPS));
  readonly totalFindings = computed(() => this.plan()?.totalFindings ?? 0);

  countOf(spaceKey: string): number {
    return this.plan()?.findingsBySpace[spaceKey] ?? 0;
  }
}
