import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BulkChip, ObfuscationBulkBarComponent } from './obfuscation-bulk-bar.component';
import { ObfuscationPlanDto } from '../../../../core/models/remediation.model';

const FR_TRANSLATIONS = {
  obfuscation: {
    bulk: {
      ariaLabel: 'Actions groupées',
      selectedForObf: 'sélectionnés à supprimer',
      fpSignaled: '{{count}} faux positif(s) signalé(s)',
      attachmentsExcluded:
        '{{count}} pièce(s) jointe(s) sélectionnée(s), exclue(s) de la suppression automatique',
      clear: 'Effacer',
      markTreated: 'Marquer traité',
      markTreatedHint: 'Marquer la sélection comme traitée manuellement',
      markFp: 'Signaler faux positif',
      markFpHint: 'Signaler la sélection comme faux positif',
      obfuscateN: 'Supprimer ({{count}})',
      obfuscateHint:
        'Supprimer les occurrences sélectionnées dans le corps des pages — les IPI des pièces jointes ne peuvent pas être supprimées automatiquement',
    },
  },
};

function plan(overrides: Partial<ObfuscationPlanDto> = {}): ObfuscationPlanDto {
  return {
    totalFindings: 14,
    bySeverity: { high: 4, medium: 10 },
    pagesImpacted: 3,
    falsePositivesReported: 0,
    selectionChecksum: 'sum-1',
    attachmentExclusions: 0,
    ...overrides,
  };
}

function chip(key: string, selectedCount: number, severity = 'high'): BulkChip {
  return { key, label: key, severity, selectedCount };
}

describe('ObfuscationBulkBarComponent', () => {
  let fixture: ComponentFixture<ObfuscationBulkBarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ObfuscationBulkBarComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
    }).compileComponents();
  });

  function createComponent(planValue: ObfuscationPlanDto | null, chips: BulkChip[] = []): void {
    fixture = TestBed.createComponent(ObfuscationBulkBarComponent);
    fixture.componentRef.setInput('plan', planValue);
    fixture.componentRef.setInput('chips', chips);
    fixture.detectChanges();
  }

  function query<T extends HTMLElement>(testId: string): T | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('Should_StayHidden_When_NoPlanAvailable', () => {
    createComponent(null);

    expect(query('obfuscation-bulk-bar')).toBeFalsy();
  });

  it('Should_StayHidden_When_PlanCountsZeroSelection', () => {
    createComponent(plan({ totalFindings: 0 }));

    expect(query('obfuscation-bulk-bar')).toBeFalsy();
  });

  it('Should_StayVisibleWithObfuscationDisabled_When_OnlyAttachmentsSelected', () => {
    createComponent(plan({ totalFindings: 0, attachmentExclusions: 2 }));

    expect(query('obfuscation-bulk-bar')).toBeTruthy();
    expect(query('obfuscation-bulk-report-fp')).toBeTruthy();
    expect(query<HTMLButtonElement>('obfuscation-bulk-obfuscate')?.disabled).toBe(true);
  });

  it('Should_ShowAttachmentNoteFromPlan_When_AttachmentsExcluded', () => {
    createComponent(plan({ attachmentExclusions: 2 }));

    expect(query('obfuscation-bulk-attachment-note')?.textContent).toContain(
      '2 pièce(s) jointe(s) sélectionnée(s)'
    );
  });

  it('Should_HideAttachmentNote_When_NoAttachmentExcluded', () => {
    createComponent(plan({ attachmentExclusions: 0 }));

    expect(query('obfuscation-bulk-attachment-note')).toBeFalsy();
  });

  it('Should_RenderPlanCounterVerbatim_When_ChipsWouldImplyAnotherTotal', () => {
    createComponent(plan({ totalFindings: 14 }), [chip('EMAIL', 2)]);

    expect(query('obfuscation-bulk-counter')?.textContent).toContain('14');
    expect(query('obfuscation-bulk-obfuscate')?.textContent).toContain('Supprimer (14)');
  });

  it('Should_ShowFpNoteFromPlan_When_FalsePositivesReported', () => {
    createComponent(plan({ falsePositivesReported: 3 }));

    expect(query('obfuscation-bulk-fp-note')?.textContent).toContain('3 faux positif(s) signalé(s)');
  });

  it('Should_HideFpNote_When_NoFalsePositiveReported', () => {
    createComponent(plan({ falsePositivesReported: 0 }));

    expect(query('obfuscation-bulk-fp-note')).toBeFalsy();
  });

  it('Should_ShowTopThreeChipsAndOverflow_When_MoreThanThreeChips', () => {
    createComponent(plan(), [chip('A', 5), chip('B', 4), chip('C', 3), chip('D', 2), chip('E', 1)]);

    const chips = fixture.nativeElement.querySelectorAll('[data-testid="obfuscation-bulk-chip"]');
      expect(chips).toHaveLength(3);
    expect(query('obfuscation-bulk-more-chip')?.textContent).toContain('+2');
  });

  it('Should_RenderChipCountsVerbatim_When_ChipsProvided', () => {
    createComponent(plan(), [chip('EMAIL', 7)]);

    expect(query('obfuscation-bulk-chip')?.textContent).toContain('EMAIL · 7');
  });

  it('Should_EmitGestures_When_ActionsClicked', () => {
    createComponent(plan());
    const cleared = vi.fn();
    const markTreated = vi.fn();
    const reportFalsePositive = vi.fn();
    const obfuscate = vi.fn();
    fixture.componentInstance.cleared.subscribe(cleared);
    fixture.componentInstance.markTreated.subscribe(markTreated);
    fixture.componentInstance.reportFalsePositive.subscribe(reportFalsePositive);
    fixture.componentInstance.obfuscate.subscribe(obfuscate);

    query('obfuscation-bulk-clear')?.click();
    query('obfuscation-bulk-mark-treated')?.click();
    query('obfuscation-bulk-report-fp')?.click();
    query('obfuscation-bulk-obfuscate')?.click();

    expect(cleared).toHaveBeenCalledTimes(1);
    expect(markTreated).toHaveBeenCalledTimes(1);
    expect(reportFalsePositive).toHaveBeenCalledTimes(1);
    expect(obfuscate).toHaveBeenCalledTimes(1);
  });
});
