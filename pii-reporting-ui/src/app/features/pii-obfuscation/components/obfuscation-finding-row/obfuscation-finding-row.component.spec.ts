import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ObfuscationFindingRowComponent } from './obfuscation-finding-row.component';
import { RemediationFindingDto } from '../../../../core/models/remediation.model';

const FR_TRANSLATIONS = {
  piiTypes: {
    EMAIL: 'Email',
  },
  obfuscation: {
    select: 'Sélectionner',
    nOccurrences: '{{count}} occurrences',
    redactedValue: 'Valeur supprimée',
    status: {
      pending: 'À traiter',
      redacted: 'Supprimé de la source',
      manual: 'Traité (manuel)',
      fp: 'Faux positif',
    },
    action: {
      markManual: 'Marquer comme traité manuellement',
      undoManual: 'Rétablir « à traiter »',
      markFp: 'Signaler faux positif',
      restore: 'Rétablir',
    },
    ineligible: {
      attachment: 'Pièce jointe : suppression impossible ({{kind}})',
      selectHint: 'Pièce jointe : suppression automatique impossible.',
    },
  },
};

function finding(overrides: Partial<RemediationFindingDto> = {}): RemediationFindingDto {
  return {
    findingId: 'f1',
    piiType: 'EMAIL',
    severity: 'high',
    detector: 'PRESIDIO',
    confidenceScore: 0.87,
    maskedContext: 'contact: [EMAIL]',
    occurrenceCount: 1,
    pageId: 'p1',
    pageTitle: 'Team page',
    status: 'PENDING',
    selected: false,
    eligibleForRedaction: true,
    ...overrides,
  };
}

describe('ObfuscationFindingRowComponent', () => {
  let fixture: ComponentFixture<ObfuscationFindingRowComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ObfuscationFindingRowComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
    }).compileComponents();
  });

  function createComponent(value: RemediationFindingDto, groupBy: 'type' | 'severity' = 'type'): void {
    fixture = TestBed.createComponent(ObfuscationFindingRowComponent);
    fixture.componentRef.setInput('finding', value);
    fixture.componentRef.setInput('groupBy', groupBy);
    fixture.detectChanges();
  }

  function query<T extends HTMLElement>(testId: string): T | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('Should_RenderMaskedContextWithTokenBadge_When_NoSensitiveValue', () => {
    createComponent(finding());

    const value = query('obfuscation-row-value');
    expect(value?.textContent).toContain('contact:');
    expect(value?.querySelector('.ob-token-badge')?.textContent).toContain('Email');
    expect(value?.textContent).not.toContain('[EMAIL]');
    expect(fixture.nativeElement.textContent).not.toContain('john.doe@example.com');
  });

  it('Should_RenderClearValue_When_SensitiveValuePresentWithoutContext', () => {
    createComponent(finding({ sensitiveValue: 'john.doe@example.com' }));

    const value = query('obfuscation-row-value');
    expect(value?.querySelector('.ob-highlight')?.textContent).toContain('john.doe@example.com');
    expect(value?.textContent).not.toContain('[EMAIL]');
  });

  it('Should_HighlightValueWithinContext_When_SensitiveContextPresent', () => {
    createComponent(
      finding({
        sensitiveValue: 'john.doe@example.com',
        sensitiveContext: 'Contact: john.doe@example.com for info',
      })
    );

    const value = query('obfuscation-row-value');
    expect(value?.textContent).toContain('Contact:');
    expect(value?.textContent).toContain('for info');
    expect(value?.querySelector('.ob-highlight')?.textContent?.trim()).toBe('john.doe@example.com');
  });

  it('Should_ShowOccurrenceCount_When_ValueAppearsMoreThanOnce', () => {
    createComponent(finding({ occurrenceCount: 4 }));

    expect(query('obfuscation-row-occurrences')?.textContent).toContain('4 occurrences');
  });

  it('Should_HideOccurrenceCount_When_SingleOccurrence', () => {
    createComponent(finding({ occurrenceCount: 1 }));

    expect(query('obfuscation-row-occurrences')).toBeFalsy();
  });

  it('Should_CheckCheckbox_When_ApiSaysSelected', () => {
    createComponent(finding({ selected: true }));

    const checkbox = query<HTMLInputElement>('obfuscation-row-checkbox');
    expect(checkbox?.checked).toBe(true);
    expect(checkbox?.disabled).toBe(false);
  });

  it('Should_DisableCheckbox_When_FindingRedacted', () => {
    createComponent(finding({ status: 'REDACTED' }));

    expect(query<HTMLInputElement>('obfuscation-row-checkbox')?.disabled).toBe(true);
  });

  it('Should_DisableCheckbox_When_FindingManuallyHandled', () => {
    createComponent(finding({ status: 'MANUALLY_HANDLED' }));

    expect(query<HTMLInputElement>('obfuscation-row-checkbox')?.disabled).toBe(true);
  });

  it('Should_KeepCheckboxEnabled_When_FindingFalsePositive', () => {
    createComponent(finding({ status: 'FALSE_POSITIVE' }));

    expect(query<HTMLInputElement>('obfuscation-row-checkbox')?.disabled).toBe(false);
  });

  it('Should_RenderRedactedBlockInsteadOfValue_When_FindingRedacted', () => {
    createComponent(finding({ status: 'REDACTED' }));

    const value = query('obfuscation-row-value');
    expect(value?.textContent).toContain('Valeur supprimée');
    expect(value?.textContent).not.toContain('[EMAIL]');
  });

  it('Should_KeepCheckboxEnabledAndExplain_When_ApiSaysIneligible', () => {
    createComponent(
      finding({
        eligibleForRedaction: false,
        ineligibilityReason: 'ATTACHMENT',
        attachmentName: 'report.pdf',
      })
    );

    const checkbox = query<HTMLInputElement>('obfuscation-row-checkbox');
    expect(checkbox?.disabled).toBe(false);
    expect(checkbox?.title).toContain('suppression automatique impossible');
    expect(query('obfuscation-row-ineligible')?.textContent).toContain(
      'Pièce jointe : suppression impossible (pdf)'
    );
  });

  it('Should_NotShowSelectHint_When_FindingIsRedactable', () => {
    createComponent(finding());

    expect(query<HTMLInputElement>('obfuscation-row-checkbox')?.title).toBe('');
  });

  it('Should_ShowRawReason_When_IneligibleWithoutAttachment', () => {
    createComponent(finding({ eligibleForRedaction: false, ineligibilityReason: 'SOME_REASON' }));

    expect(query('obfuscation-row-ineligible')?.textContent).toContain('SOME_REASON');
  });

  it('Should_ShowStatusBadgeFromApi_When_Rendered', () => {
    createComponent(finding({ status: 'FALSE_POSITIVE' }));

    expect(query('obfuscation-row-status')?.textContent).toContain('Faux positif');
  });

  it('Should_ShowPendingActions_When_FindingPending', () => {
    createComponent(finding());

    expect(query('obfuscation-row-mark-manual')).toBeTruthy();
    expect(query('obfuscation-row-restore')).toBeFalsy();
  });

  it('Should_ShowOnlyRestore_When_FindingManuallyHandled', () => {
    createComponent(finding({ status: 'MANUALLY_HANDLED' }));

    expect(query('obfuscation-row-restore')).toBeTruthy();
    expect(query('obfuscation-row-mark-manual')).toBeFalsy();
  });

  it('Should_ShowOnlyRestore_When_FindingFalsePositive', () => {
    createComponent(finding({ status: 'FALSE_POSITIVE' }));

    expect(query('obfuscation-row-restore')).toBeTruthy();
    expect(query('obfuscation-row-mark-manual')).toBeFalsy();
  });

  it('Should_ShowNoAction_When_FindingRedacted', () => {
    createComponent(finding({ status: 'REDACTED' }));

    expect(query('obfuscation-row-mark-manual')).toBeFalsy();
    expect(query('obfuscation-row-restore')).toBeFalsy();
  });

  it('Should_EmitToggled_When_CheckboxChanged', () => {
    createComponent(finding());
    const toggled = vi.fn();
    fixture.componentInstance.toggled.subscribe(toggled);

    query<HTMLInputElement>('obfuscation-row-checkbox')?.dispatchEvent(new Event('change'));

    expect(toggled).toHaveBeenCalledTimes(1);
  });

  it('Should_EmitMarkManual_When_ActionButtonClicked', () => {
    createComponent(finding());
    const markManual = vi.fn();
    fixture.componentInstance.markManual.subscribe(markManual);

    query('obfuscation-row-mark-manual')?.click();

    expect(markManual).toHaveBeenCalledTimes(1);
  });

  it('Should_EmitRestore_When_RestoreClicked', () => {
    createComponent(finding({ status: 'FALSE_POSITIVE' }));
    const restore = vi.fn();
    fixture.componentInstance.restore.subscribe(restore);

    query('obfuscation-row-restore')?.click();

    expect(restore).toHaveBeenCalledTimes(1);
  });

  it('Should_ShowPageMetaAndReusedIndicators_When_Rendered', () => {
    createComponent(finding({ attachmentName: 'report.pdf' }));

    expect(fixture.nativeElement.textContent).toContain('Team page');
    expect(fixture.nativeElement.textContent).toContain('report.pdf');
    expect(fixture.nativeElement.querySelector('app-confidence-indicator')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-detector-tag')).toBeTruthy();
  });

  it('Should_ShowPiiTypeTag_When_GroupedBySeverity', () => {
    createComponent(finding(), 'severity');

    expect(fixture.nativeElement.textContent).toContain('EMAIL');
  });
});
