import { computed, DestroyRef, effect, inject, Injectable, NgZone, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Subscription, timer } from 'rxjs';
import { distinctUntilChanged, switchMap, takeWhile } from 'rxjs/operators';
import { JudgeJobDto } from '../../../core/models/judge.model';
import { JudgeApiService } from '../../../core/services/judge-api.service';
import { RemediationConfigService } from '../../../core/services/remediation-config.service';
import { ScanReportingSummaryDto, SentinelleApiService, SpaceSummaryDto } from '../../../core/services/sentinelle-api.service';

export const JUDGE_POLL_INTERVAL_MS = 2000;

/**
 * Follows the judge jobs a screen launched: polls them until every one is terminal and hands
 * each change to the screen so it can refresh what it displays. One instance per screen.
 */
@Injectable()
export class JudgeJobTrackerService {
  private readonly judgeApi = inject(JudgeApiService);
  private readonly zone = inject(NgZone);
  private readonly scanApi = inject(SentinelleApiService);
  private readonly remediationConfig = inject(RemediationConfigService);
  private readonly scanSummary = signal<ScanReportingSummaryDto | null>(null);
  private readonly destroyRef = inject(DestroyRef);
  private polling?: Subscription;

  readonly launchUnavailableReason = computed(() => {
    const summary = this.scanSummary();
    if (!summary) return 'judge.unavailable.scanStatusUnknown';
    const inCurrentScan = (space: SpaceSummaryDto) => !!space.scanId
      && (!summary.scanId || space.scanId === summary.scanId);
    // A paused scan prompts nothing, so the judge is free to use the model endpoint. Its queued
    // spaces still read NOT_STARTED, which would otherwise keep the judge locked out for as long
    // as the pause lasts — and an outage pauses scans on its own.
    const scanPaused = summary.spaces.some((space) => space.status === 'PAUSED' && inCurrentScan(space));
    const scanInProgress = !scanPaused && summary.spaces.some((space) =>
      ['RUNNING', 'NOT_STARTED'].includes(space.status) && inCurrentScan(space)
    );
    if (scanInProgress) return 'judge.unavailable.scanInProgress';
    const hasScanData = summary.spaces.some((space) =>
      space.pagesDone > 0 || space.attachmentsDone > 0 || (space.severityCounts?.total ?? 0) > 0
    );
    return hasScanData ? null : 'judge.unavailable.noScanData';
  });

  readonly jobs = signal<JudgeJobDto[]>([]);
  readonly active = computed(() => this.jobs().some((job) => job.status === 'RUNNING'));
  readonly proposedFalsePositives = computed(() =>
    this.jobs().reduce((sum, job) => sum + job.falsePositives, 0)
  );

  constructor() {
    effect((onCleanup) => {
      if (!this.remediationConfig.enabled()) return;
      const polling = this.zone.runOutsideAngular(() => timer(0, 3000)
        .pipe(switchMap(() => this.scanApi.getDashboardSpacesSummary()))
        .subscribe((summary) => this.scanSummary.set(summary)));
      onCleanup(() => polling.unsubscribe());
    });
  }

  track(jobIds: string[], onChange: (jobs: JudgeJobDto[]) => void): void {
    this.polling?.unsubscribe();
    this.polling = timer(0, JUDGE_POLL_INTERVAL_MS)
      .pipe(
        switchMap(() => forkJoin(jobIds.map((id) => this.judgeApi.getJob(id)))),
        takeWhile((jobs) => jobs.some((job) => job.status === 'RUNNING'), true),
        distinctUntilChanged((previous, next) => signature(previous) === signature(next)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((jobs) => {
        this.jobs.set(jobs);
        onChange(jobs);
      });
  }

  cancelAll(): void {
    const running = this.jobs().filter((job) => job.status === 'RUNNING');
    if (running.length > 0) {
      forkJoin(running.map((job) => this.judgeApi.cancel(job.jobId))).subscribe();
    }
  }

  clear(): void {
    this.polling?.unsubscribe();
    this.jobs.set([]);
  }
}

function signature(jobs: JudgeJobDto[]): string {
  return jobs.map((job) => `${job.jobId}:${job.status}:${job.processed}:${job.falsePositives}`).join('|');
}
