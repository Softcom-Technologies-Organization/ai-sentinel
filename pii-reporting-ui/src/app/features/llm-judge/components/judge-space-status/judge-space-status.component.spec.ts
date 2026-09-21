import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import { JudgeSpaceStatusComponent } from './judge-space-status.component';
import { JudgeSpaceSummaryDto } from '../../../../core/models/judge.model';

const FR_TRANSLATIONS = {
  judge: {
    space: {
      running: 'Juge LLM · {{done}} / {{total}}',
      judged: 'Jugé · {{count}} faux positif(s) proposé(s)',
      judgedNone: 'Jugé · aucun faux positif proposé',
    },
  },
};

describe('JudgeSpaceStatusComponent', () => {
  let fixture: ComponentFixture<JudgeSpaceStatusComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        JudgeSpaceStatusComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
    }).compileComponents();
  });

  function createComponent(summary: JudgeSpaceSummaryDto | null): string {
    fixture = TestBed.createComponent(JudgeSpaceStatusComponent);
    fixture.componentRef.setInput('summary', summary);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('Should_ShowRunningProgress_When_JobActive', () => {
    const text = createComponent({
      spaceKey: 'AS', judgedCount: 0, proposedFalsePositives: 0,
      activeJob: { jobId: 'j', spaceKey: 'AS', status: 'RUNNING', model: null, applyMode: 'PROPOSE',
        confidenceThreshold: 0.8, processed: 64, total: 108, falsePositives: 3, errors: 0 },
    });

    expect(text).toContain('Juge LLM · 64 / 108');
  });

  it('Should_ShowProposedCount_When_Judged', () => {
    expect(createComponent({ spaceKey: 'AS', judgedCount: 95, proposedFalsePositives: 4 }))
      .toContain('Jugé · 4 faux positif(s) proposé(s)');
    expect(createComponent({ spaceKey: 'AS', judgedCount: 95, proposedFalsePositives: 0 }))
      .toContain('Jugé · aucun faux positif proposé');
  });

  it('Should_RenderNothing_When_NeverJudged', () => {
    expect(createComponent(null).trim()).toBe('');
  });
});
