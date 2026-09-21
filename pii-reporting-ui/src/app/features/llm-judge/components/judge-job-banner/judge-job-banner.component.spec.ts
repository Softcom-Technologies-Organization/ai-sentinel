import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { JudgeJobBannerComponent } from './judge-job-banner.component';
import { JudgeJobDto } from '../../../../core/models/judge.model';

const FR_TRANSLATIONS = {
  judge: {
    banner: {
      running: 'Évaluation par juge LLM en cours',
      done: 'Évaluation par juge LLM terminée',
      interrupted: 'Évaluation interrompue',
      progress: '{{done}} / {{total}} détections évaluées · {{fp}} faux positif(s) proposé(s)',
      spaces: '{{done}} / {{total}} espaces',
      errors: '{{count}} sans verdict',
      interrupt: 'Interrompre',
      dismiss: 'Fermer',
    },
  },
};

function job(overrides: Partial<JudgeJobDto> = {}): JudgeJobDto {
  return {
    jobId: 'j1',
    spaceKey: 'AS',
    status: 'RUNNING',
    model: 'judge@q8',
    applyMode: 'PROPOSE',
    confidenceThreshold: 0.8,
    processed: 10,
    total: 20,
    falsePositives: 2,
    errors: 0,
    ...overrides,
  };
}

describe('JudgeJobBannerComponent', () => {
  let fixture: ComponentFixture<JudgeJobBannerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        JudgeJobBannerComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
    }).compileComponents();
  });

  function createComponent(jobs: JudgeJobDto[]): void {
    fixture = TestBed.createComponent(JudgeJobBannerComponent);
    fixture.componentRef.setInput('jobs', jobs);
    fixture.detectChanges();
  }

  function text(testId: string): string {
    return (fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null)?.textContent ?? '';
  }

  it('Should_AggregateJobsAndOfferInterrupt_When_Running', () => {
    createComponent([job(), job({ jobId: 'j2', spaceKey: 'B', status: 'COMPLETED', processed: 5, total: 5, falsePositives: 1 })]);
    const interrupted = vi.fn();
    fixture.componentInstance.interrupt.subscribe(interrupted);

    (fixture.nativeElement.querySelector('[data-testid="judge-banner-interrupt"]') as HTMLButtonElement).click();

    expect(text('judge-banner')).toContain('Évaluation par juge LLM en cours');
    expect(text('judge-banner-progress')).toContain('15 / 25 détections évaluées · 3 faux positif(s) proposé(s)');
    expect(text('judge-banner-progress')).toContain('1 / 2 espaces');
    expect(text('judge-banner-progress')).toContain('judge@q8');
    expect(interrupted).toHaveBeenCalledTimes(1);
  });

  it('Should_ShowInterruptedStateWithDismiss_When_NoJobRunsAnymore', () => {
    createComponent([job({ status: 'INTERRUPTED', errors: 1 })]);

    expect(text('judge-banner')).toContain('Évaluation interrompue');
    expect(text('judge-banner-progress')).toContain('1 sans verdict');
    expect(fixture.nativeElement.querySelector('[data-testid="judge-banner-dismiss"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="judge-banner-interrupt"]')).toBeNull();
  });
});
