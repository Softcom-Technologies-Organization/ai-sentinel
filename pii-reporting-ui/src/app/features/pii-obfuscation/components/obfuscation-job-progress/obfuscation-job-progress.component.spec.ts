import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  JOB_POLL_INTERVAL_MS,
  ObfuscationJobProgressComponent,
} from './obfuscation-job-progress.component';
import { RemediationApiService } from '../../../../core/services/remediation-api.service';
import { ObfuscationJobDto } from '../../../../core/models/remediation.model';

const FR_TRANSLATIONS = {
  obfuscation: {
    job: {
      running: 'Suppression en cours… {{done}}/{{total}}',
      rescanRecommended: 'Suppression terminée — relancez un scan pour mettre à jour les findings.',
    },
  },
};

function job(overrides: Partial<ObfuscationJobDto> = {}): ObfuscationJobDto {
  return {
    jobId: 'job-1',
    status: 'RUNNING',
    processed: 2,
    total: 5,
    outcomes: [],
    ...overrides,
  };
}

describe('ObfuscationJobProgressComponent', () => {
  let fixture: ComponentFixture<ObfuscationJobProgressComponent>;
  let getJob: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    getJob = vi.fn();

    await TestBed.configureTestingModule({
      imports: [
        ObfuscationJobProgressComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
      providers: [{ provide: RemediationApiService, useValue: { getJob } }],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(ObfuscationJobProgressComponent);
    fixture.componentRef.setInput('jobId', 'job-1');
    fixture.detectChanges();
  }

  function tick(ms: number): void {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
  }

  function query<T extends HTMLElement>(testId: string): T | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('Should_RenderBackendProgressVerbatim_When_JobRunning', () => {
    getJob.mockReturnValue(of(job({ processed: 2, total: 5 })));
    createComponent();

    tick(0);

    const root = query('obfuscation-job-progress');
    expect(root?.getAttribute('role')).toBe('status');
    expect(query('obfuscation-job-label')?.textContent).toContain('2/5');
    expect(query('obfuscation-job-rescan-hint')).toBeFalsy();
  });

  it('Should_PollUntilTerminalStatusAndEmitCompleted_When_JobFinishes', () => {
    const finalJob = job({ status: 'COMPLETED', processed: 5, total: 5 });
    getJob
      .mockReturnValueOnce(of(job()))
      .mockReturnValueOnce(of(job({ processed: 4 })))
      .mockReturnValue(of(finalJob));
    createComponent();
    const completed = vi.fn();
    fixture.componentInstance.completed.subscribe(completed);

    tick(0);
    expect(getJob).toHaveBeenCalledTimes(1);

    tick(JOB_POLL_INTERVAL_MS);
    expect(getJob).toHaveBeenCalledTimes(2);
    expect(completed).not.toHaveBeenCalled();

    tick(JOB_POLL_INTERVAL_MS);
    expect(completed).toHaveBeenCalledTimes(1);
    expect(completed).toHaveBeenCalledWith(finalJob);

    tick(JOB_POLL_INTERVAL_MS * 3);
    expect(getJob).toHaveBeenCalledTimes(3);
  });

  it('Should_ListOutcomesVerbatim_When_BackendReportsThem', () => {
    getJob.mockReturnValue(
      of(
        job({
          status: 'COMPLETED_WITH_ERRORS',
          outcomes: [
            { findingId: 'f1', piiType: 'EMAIL', outcome: 'REDACTED' },
            { findingId: 'f2', piiType: 'IBAN', outcome: 'SKIPPED_VALUE_NOT_FOUND', reason: 'stale' },
          ],
        })
      )
    );
    createComponent();

    tick(0);

    const outcomes = fixture.nativeElement.querySelectorAll(
      '[data-testid="obfuscation-job-outcome"]'
    );
      expect(outcomes).toHaveLength(2);
    expect(outcomes[0].textContent).toContain('EMAIL');
    expect(outcomes[0].textContent).toContain('REDACTED');
    expect(outcomes[1].textContent).toContain('SKIPPED_VALUE_NOT_FOUND');
    expect(outcomes[1].textContent).toContain('stale');
  });

  it('Should_RecommendRescan_When_JobTerminal', () => {
    getJob.mockReturnValue(of(job({ status: 'COMPLETED', processed: 5, total: 5 })));
    createComponent();

    tick(0);

    expect(query('obfuscation-job-rescan-hint')?.textContent).toContain('relancez un scan');
  });
});
