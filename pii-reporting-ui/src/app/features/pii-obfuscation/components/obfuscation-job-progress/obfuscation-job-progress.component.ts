import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { timer } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';
import { ObfuscationJobDto } from '../../../../core/models/remediation.model';
import { RemediationApiService } from '../../../../core/services/remediation-api.service';
import { toTranslocoKey } from '../../../../core/services/error-notification.service';
import { TestIds } from '../../../test-ids.constants';

export const JOB_POLL_INTERVAL_MS = 1500;

/**
 * Polls the obfuscation job endpoint until a terminal status is reached and
 * renders backend-provided progression and per-finding outcomes verbatim.
 */
@Component({
  selector: 'app-obfuscation-job-progress',
  standalone: true,
  imports: [TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './obfuscation-job-progress.component.html',
  styleUrl: './obfuscation-job-progress.component.css',
})
export class ObfuscationJobProgressComponent implements OnInit {
  readonly jobId = input.required<string>();
  readonly completed = output<ObfuscationJobDto>();

  private readonly remediationApi = inject(RemediationApiService);
  private readonly translocoService = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly testIds = TestIds.obfuscation.jobProgress;
  readonly job = signal<ObfuscationJobDto | null>(null);

  readonly terminal = computed(() => {
    const current = this.job();
    return current !== null && current.status !== 'RUNNING';
  });

  readonly progressPercent = computed(() => {
    const current = this.job();
    if (!current || current.total === 0) {
      return 0;
    }
    return Math.round((current.processed / current.total) * 100);
  });

  ngOnInit(): void {
    timer(0, JOB_POLL_INTERVAL_MS)
      .pipe(
        switchMap(() => this.remediationApi.getJob(this.jobId())),
        takeWhile((job) => job.status === 'RUNNING', true),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((job) => this.applyJobUpdate(job));
  }

  /**
   * The wording explaining why one finding was not anonymised.
   *
   * <p>The backend names the reason with a key rather than a sentence, so the same
   * job reads in French or English depending on who opens it. An unknown key is
   * shown as-is: hiding it would leave an outcome with no explanation at all.
   */
  reasonLabel(reason: string): string {
    const key = toTranslocoKey(reason);
    const translated = this.translocoService.translate(key);
    return translated === key ? reason : translated;
  }

  private applyJobUpdate(job: ObfuscationJobDto): void {
    this.job.set(job);
    if (job.status !== 'RUNNING') {
      this.completed.emit(job);
    }
  }
}
