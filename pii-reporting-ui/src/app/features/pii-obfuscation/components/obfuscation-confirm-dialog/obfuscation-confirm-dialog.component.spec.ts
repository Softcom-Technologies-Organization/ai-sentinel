import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ObfuscationConfirmDialogComponent } from './obfuscation-confirm-dialog.component';
import { ObfuscationPlanDto } from '../../../../core/models/remediation.model';

const FR_TRANSLATIONS = {
  obfuscation: {
    severity: { high: 'Critique', medium: 'Modéré', low: 'Faible' },
    bulk: { obfuscateN: 'Supprimer ({{count}})' },
    confirm: {
      title: 'Supprimer {{count}} finding(s) de la source ?',
      irreversibleTitle: 'Action irréversible.',
      irreversibleBody:
        'Les valeurs seront définitivement supprimées du document source (Confluence) et remplacées par un marqueur de type (ex. [EMAIL]). Cette opération ne peut pas être annulée.',
      lead: 'Vous êtes sur le point de supprimer <b>{{count}}</b> occurrence(s) dans <b>{{space}}</b>.',
      total: 'Total',
      cancel: 'Annuler',
      fpFeedbackNote:
        '{{count}} occurrence(s) exclue(s) et signalée(s) comme faux positif (feedback détection).',
    },
  },
};

function plan(overrides: Partial<ObfuscationPlanDto> = {}): ObfuscationPlanDto {
  return {
    totalFindings: 9,
    bySeverity: { medium: 2, high: 4, low: 3 },
    pagesImpacted: 2,
    falsePositivesReported: 0,
    selectionChecksum: 'sum-1',
    attachmentExclusions: 0,
    ...overrides,
  };
}

describe('ObfuscationConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ObfuscationConfirmDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ObfuscationConfirmDialogComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
    }).compileComponents();
  });

  function createComponent(planValue: ObfuscationPlanDto | null, visible = true): void {
    fixture = TestBed.createComponent(ObfuscationConfirmDialogComponent);
    fixture.componentRef.setInput('visible', visible);
    fixture.componentRef.setInput('plan', planValue);
    fixture.componentRef.setInput('spaceKey', 'SPACE');
    fixture.detectChanges();
  }

  function query<T extends HTMLElement>(testId: string): T | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('Should_RenderNothing_When_NotVisible', () => {
    createComponent(plan(), false);

    expect(query('obfuscation-confirm-warning')).toBeFalsy();
  });

  it('Should_ShowIrreversibleWarning_When_Visible', () => {
    createComponent(plan());

    const warning = query('obfuscation-confirm-warning');
    expect(warning?.textContent).toContain('Action irréversible.');
    expect(warning?.textContent).toContain('ne peut pas être annulée');
  });

  it('Should_RenderBackendBreakdownVerbatimInSeverityOrder_When_PlanProvided', () => {
    createComponent(plan({ bySeverity: { low: 3, high: 4 } }));

    const rows = fixture.nativeElement.querySelectorAll(
      '[data-testid="obfuscation-confirm-breakdown-row"]'
    );
      expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Critique');
    expect(rows[0].textContent).toContain('4');
    expect(rows[1].textContent).toContain('Faible');
    expect(rows[1].textContent).toContain('3');
  });

  it('Should_RenderBackendTotalVerbatim_When_BreakdownWouldSumDifferently', () => {
    createComponent(plan({ totalFindings: 999, bySeverity: { high: 1 } }));

    expect(query('obfuscation-confirm-total')?.textContent).toContain('999');
    expect(query('obfuscation-confirm-accept')?.textContent).toContain('Supprimer (999)');
  });

  it('Should_ShowFpFeedbackNote_When_PlanReportsFalsePositives', () => {
    createComponent(plan({ falsePositivesReported: 5 }));

    expect(query('obfuscation-confirm-fp-note')?.textContent).toContain('5 occurrence(s)');
  });

  it('Should_HideFpFeedbackNote_When_PlanReportsNone', () => {
    createComponent(plan({ falsePositivesReported: 0 }));

    expect(query('obfuscation-confirm-fp-note')).toBeFalsy();
  });

  it('Should_ShowLeadWithSpace_When_Visible', () => {
    createComponent(plan());

    expect(query('obfuscation-confirm-lead')?.textContent).toContain('SPACE');
  });

  it('Should_EmitConfirmed_When_DangerButtonClicked', () => {
    createComponent(plan());
    const confirmed = vi.fn();
    fixture.componentInstance.confirmed.subscribe(confirmed);

    query('obfuscation-confirm-accept')?.click();

    expect(confirmed).toHaveBeenCalledTimes(1);
  });

  it('Should_EmitCancelled_When_CancelButtonClicked', () => {
    createComponent(plan());
    const cancelled = vi.fn();
    fixture.componentInstance.cancelled.subscribe(cancelled);

    query('obfuscation-confirm-cancel')?.click();

    expect(cancelled).toHaveBeenCalledTimes(1);
  });
});
