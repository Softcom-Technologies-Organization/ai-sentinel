import { inject, Injectable, signal } from '@angular/core';
import { JudgeSpaceSummaryDto } from '../../../core/models/judge.model';
import { JudgeApiService } from '../../../core/services/judge-api.service';

/**
 * Per-space judge summary shown on the dashboard (job in flight, proposed false positives).
 * Refreshed on demand: the dashboard asks again on every job progress it observes.
 */
@Injectable({ providedIn: 'root' })
export class JudgeSpaceSummaryService {
  private readonly judgeApi = inject(JudgeApiService);

  readonly summaries = signal<ReadonlyMap<string, JudgeSpaceSummaryDto>>(new Map());

  summaryFor(spaceKey: string): JudgeSpaceSummaryDto | null {
    return this.summaries().get(spaceKey) ?? null;
  }

  refresh(): void {
    this.judgeApi.spaceSummaries().subscribe((list) => {
      this.summaries.set(new Map(list.map((summary) => [summary.spaceKey, summary])));
    });
  }
}
