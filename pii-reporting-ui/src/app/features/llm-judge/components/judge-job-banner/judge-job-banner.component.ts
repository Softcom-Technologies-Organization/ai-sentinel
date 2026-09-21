import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { JudgeJobDto } from '../../../../core/models/judge.model';
import { TestIds } from '../../../test-ids.constants';

/**
 * Progress banner of one judge run, aggregated over the jobs it created (one per space).
 * Renders the backend counters verbatim and emits the interrupt / dismiss gestures only.
 */
@Component({
  selector: 'app-judge-job-banner',
  standalone: true,
  imports: [TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './judge-job-banner.component.html',
  styleUrl: './judge-job-banner.component.css',
})
export class JudgeJobBannerComponent {
  readonly jobs = input.required<JudgeJobDto[]>();

  readonly interrupt = output<void>();
  readonly dismissed = output<void>();

  readonly testIds = TestIds.judge.banner;

  readonly active = computed(() => this.jobs().some((job) => job.status === 'RUNNING'));
  readonly interrupted = computed(
    () => !this.active() && this.jobs().some((job) => job.status === 'INTERRUPTED')
  );
  readonly processed = computed(() => sum(this.jobs(), (job) => job.processed));
  readonly total = computed(() => sum(this.jobs(), (job) => job.total));
  readonly falsePositives = computed(() => sum(this.jobs(), (job) => job.falsePositives));
  readonly errors = computed(() => sum(this.jobs(), (job) => job.errors));
  readonly spacesDone = computed(() => this.jobs().filter((job) => job.status !== 'RUNNING').length);
  readonly spacesTotal = computed(() => this.jobs().length);
  readonly model = computed(() => this.jobs()[0]?.model ?? null);
  readonly percent = computed(() => (this.total() === 0 ? 0 : Math.round((this.processed() / this.total()) * 100)));

  readonly titleKey = computed(() => {
    if (this.active()) {
      return 'judge.banner.running';
    }
    return this.interrupted() ? 'judge.banner.interrupted' : 'judge.banner.done';
  });
}

function sum(jobs: JudgeJobDto[], pick: (job: JudgeJobDto) => number): number {
  return jobs.reduce((total, job) => total + pick(job), 0);
}
