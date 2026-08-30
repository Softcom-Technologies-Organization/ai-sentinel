import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { PiiCardExpandedComponent } from './pii-card-expanded.component';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PersonallyIdentifiableInformationScanResult } from '../../core/models/personally-identifiable-information-scan-result';
import { SentinelleApiService } from '../../core/services/sentinelle-api.service';
import { RemediationConfigService } from '../../core/services/remediation-config.service';

const FR_TRANSLATIONS = {
  piiItem: {
    severity: {
      label: 'S\u00e9v\u00e9rit\u00e9 : {{value}}',
      high: '\u00c9lev\u00e9e',
      medium: 'Moyenne',
      low: 'Faible',
    },
    fallback: {
      page: 'Page',
    },
    attachment: {
      label: 'Pi\u00e8ce jointe',
    },
    actions: {
      reveal: 'R\u00e9v\u00e9ler',
      mask: 'Masquer',
      revealAriaLabel: 'Afficher les valeurs sensibles (action audit\u00e9e)',
      maskAriaLabel: 'Masquer les valeurs sensibles',
    },
  },
  piiPageCard: {
    detections: 'd\u00e9tections',
    types: 'types',
    openInConfluence: 'Ouvrir dans Confluence',
    filter: {
      all: 'Tous',
    },
    table: {
      type: 'Type',
      value: 'Valeur',
      confidence: 'Confiance',
      detector: 'D\u00e9tecteur',
    },
  },
  dashboard: {
    table: {
      collapse: 'R\u00e9duire',
    },
  },
  piiTypes: {
    EMAIL: 'Email',
    IBAN: 'IBAN',
  },
};

const MOCK_ITEM: PersonallyIdentifiableInformationScanResult = {
  scanId: 'scan-1',
  spaceKey: 'TEST',
  pageId: 'page-1',
  pageTitle: 'Test Page',
  pageUrl: 'https://confluence.example.com/page/1',
  isFinal: true,
  severity: 'high',
  piiTypeSummary: { EMAIL: 3, IBAN: 1 },
  detectedPersonallyIdentifiableInformationList: [
    { startPosition: 0, endPosition: 10, piiTypeLabel: 'EMAIL', confidence: 1, source: 'PRESIDIO', maskedContext: 'user@***' },
    { startPosition: 20, endPosition: 30, piiTypeLabel: 'EMAIL', confidence: 0.95, source: 'PRESIDIO', maskedContext: 'admin@***' },
    { startPosition: 40, endPosition: 50, piiTypeLabel: 'EMAIL', confidence: 0.9, source: 'PRESIDIO', maskedContext: 'test@***' },
    { startPosition: 60, endPosition: 70, piiTypeLabel: 'IBAN', confidence: 0.88, source: 'PRESIDIO', maskedContext: 'CH*** ****' },
  ],
};

describe('PiiCardExpandedComponent', () => {
  let fixture: ComponentFixture<PiiCardExpandedComponent>;
  let remediationConfigMock: { enabled: ReturnType<typeof signal<boolean>> };

  beforeEach(async () => {
    remediationConfigMock = { enabled: signal(false) };

    await TestBed.configureTestingModule({
      imports: [
        PiiCardExpandedComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: SentinelleApiService, useValue: { revealAllowed: signal(true) } },
        { provide: RemediationConfigService, useValue: remediationConfigMock },
      ],
    }).compileComponents();
  });

  it('Should_DisplayPageTitle_When_ItemProvided', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const title = fixture.nativeElement.querySelector('.expanded-title');
    expect(title.textContent.trim()).toBe('Test Page');
  });

  it('Should_DisplayTotalDetections_When_ItemHasEntities', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const total = fixture.nativeElement.querySelector('.badges-total-count');
    expect(total.textContent.trim()).toBe('4');
  });

  it('Should_DisplayOneRowPerEntity_When_MultipleEntitiesExist', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('.pii-row');
    expect(rows).toHaveLength(4); // One row per entity
  });

  it('Should_EmitCollapse_When_CloseButtonClicked', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const spy = vi.spyOn(fixture.componentInstance.collapse, 'emit');
    fixture.nativeElement.querySelector('.expanded-header').click();
    expect(spy).toHaveBeenCalled();
  });

  it('Should_EmitRevealRequested_When_RevealClicked', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const spy = vi.spyOn(fixture.componentInstance.revealRequested, 'emit');
    fixture.nativeElement.querySelector('.btn-reveal').click();
    expect(spy).toHaveBeenCalled();
  });

  it('Should_DisplayTopRevealButton_When_RevealAllowed', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const topReveal = fixture.nativeElement.querySelector('.card-header-actions .btn-reveal');
    expect(topReveal).toBeTruthy();
    // Both the top duplicate and the footer button are present
    const allReveal = fixture.nativeElement.querySelectorAll('.btn-reveal');
    expect(allReveal).toHaveLength(2);
  });

  it('Should_EmitRevealRequested_When_TopRevealClicked', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const spy = vi.spyOn(fixture.componentInstance.revealRequested, 'emit');
    fixture.nativeElement.querySelector('.card-header-actions .btn-reveal').click();
    expect(spy).toHaveBeenCalled();
  });

  it('Should_ShowPageObfuscationEntry_When_RemediationEnabled', () => {
    remediationConfigMock.enabled.set(true);
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const entry = fixture.nativeElement.querySelector('[data-testid="btn-obfuscate-page"]');
    expect(entry).toBeTruthy();
  });

  it('Should_HideObfuscationEntry_When_RemediationDisabled', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const entry = fixture.nativeElement.querySelector('app-obfuscation-entry-button a');
    expect(entry).toBeFalsy();
  });

  it('Should_ShowAttachmentObfuscationEntry_When_ItemIsAttachment', () => {
    remediationConfigMock.enabled.set(true);
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', { ...MOCK_ITEM, attachmentName: 'doc.pdf' });
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const entry = fixture.nativeElement.querySelector('[data-testid="btn-obfuscate-attachment"]');
    expect(entry).toBeTruthy();
  });

  it('Should_DisplayConfluenceLink_When_PageUrlExists', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('.btn-confluence') as HTMLAnchorElement;
    expect(link).toBeTruthy();
    expect(link.href).toBe('https://confluence.example.com/page/1');
    expect(link.target).toBe('_blank');
  });

  it('Should_ShowMaskLabel_When_Revealed', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', true);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.btn-reveal');
    expect(btn.textContent).toContain('Masquer');
  });

  it('Should_DisplayMaskedContext_When_NotRevealed', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const valueCells = fixture.nativeElement.querySelectorAll('.td-value');
    expect(valueCells[0].textContent.trim()).toBe('user@***');
    expect(valueCells[0].classList).not.toContain('td-value--revealed');
  });

  it('Should_DisplaySensitiveValueWithContext_When_RevealedWithContext', () => {
    const revealedItem: PersonallyIdentifiableInformationScanResult = {
      ...MOCK_ITEM,
      detectedPersonallyIdentifiableInformationList: [
        {
          startPosition: 0, endPosition: 10, piiTypeLabel: 'EMAIL', confidence: 1, source: 'PRESIDIO',
          maskedContext: 'Contact: [EMAIL] for info',
          sensitiveValue: 'user@example.com',
          sensitiveContext: 'Contact: user@example.com for info',
        },
      ],
    };
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', revealedItem);
    fixture.componentRef.setInput('revealed', true);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const valueCell = fixture.nativeElement.querySelector('.td-value') as HTMLElement;
    expect(valueCell.textContent?.trim()).toContain('Contact:');
    expect(valueCell.textContent?.trim()).toContain('user@example.com');
    expect(valueCell.textContent?.trim()).toContain('for info');
    expect(valueCell.classList).toContain('td-value--revealed');

    const highlight = valueCell.querySelector('.revealed-highlight') as HTMLElement;
    expect(highlight).toBeTruthy();
    expect(highlight.textContent?.trim()).toBe('user@example.com');
  });

  it('Should_DisplaySensitiveValueOnly_When_RevealedWithoutContext', () => {
    const revealedItem: PersonallyIdentifiableInformationScanResult = {
      ...MOCK_ITEM,
      detectedPersonallyIdentifiableInformationList: [
        {
          startPosition: 0, endPosition: 10, piiTypeLabel: 'EMAIL', confidence: 1, source: 'PRESIDIO',
          maskedContext: 'user@***',
          sensitiveValue: 'user@example.com',
        },
      ],
    };
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', revealedItem);
    fixture.componentRef.setInput('revealed', true);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const valueCell = fixture.nativeElement.querySelector('.td-value') as HTMLElement;
    const highlight = valueCell.querySelector('.revealed-highlight') as HTMLElement;
    expect(highlight).toBeTruthy();
    expect(highlight.textContent?.trim()).toBe('user@example.com');
    expect(valueCell.classList).toContain('td-value--revealed');
  });

  // ========== Filter & sort behaviour ==========

  function createComponent(item: PersonallyIdentifiableInformationScanResult = MOCK_ITEM): PiiCardExpandedComponent {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', item);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('Should_ExposeAllOptionFirst_When_FilterOptionsComputed', () => {
    const component = createComponent();

    const options = component.filterOptions();

    expect(options[0]).toEqual({ label: 'Tous', value: '__ALL__' });
    expect(options.map((o) => o.value)).toContain('Email');
    expect(options.map((o) => o.value)).toContain('IBAN');
  });

  it('Should_SelectOnlyAll_When_AllCheckedFromSpecificSelection', () => {
    const component = createComponent();
    component.selectedFilterValues.set(['Email']);

    component.onFilterChanged({ value: ['Email', '__ALL__'] } as never);

    expect(component.selectedFilterValues()).toEqual(['__ALL__']);
    expect(component.isAllSelected()).toBe(true);
  });

  it('Should_DropAll_When_SpecificTypeCheckedWhileAllSelected', () => {
    const component = createComponent();
    component.selectedFilterValues.set(['__ALL__']);

    component.onFilterChanged({ value: ['__ALL__', 'Email'] } as never);

    expect(component.selectedFilterValues()).toEqual(['Email']);
    expect(component.isAllSelected()).toBe(false);
  });

  it('Should_FallbackToAll_When_EverythingDeselected', () => {
    const component = createComponent();
    component.selectedFilterValues.set(['Email']);

    component.onFilterChanged({ value: [] } as never);

    expect(component.selectedFilterValues()).toEqual(['__ALL__']);
  });

  it('Should_KeepSelection_When_MultipleSpecificTypesSelected', () => {
    const component = createComponent();
    component.selectedFilterValues.set(['Email']);

    component.onFilterChanged({ value: ['Email', 'IBAN'] } as never);

    expect(component.selectedFilterValues()).toEqual(['Email', 'IBAN']);
  });

  it('Should_FilterRowsByType_When_SpecificFilterApplied', () => {
    const component = createComponent();

    component.selectedFilterValues.set(['IBAN']);

    const rows = component.filteredAndSortedRows();
    expect(rows).toHaveLength(1);
    expect(rows[0].typeLabel).toBe('IBAN');
  });

  it('Should_ToggleSortDirection_When_SameColumnSortedTwice', () => {
    const component = createComponent();

    component.onSort('confidence');
    expect(component.sortColumn()).toBe('confidence');
    expect(component.sortDirection()).toBe('asc');

    component.onSort('confidence');
    expect(component.sortDirection()).toBe('desc');
  });

  it('Should_ResetToAscending_When_DifferentColumnSorted', () => {
    const component = createComponent();
    component.onSort('confidence');
    component.onSort('confidence');

    component.onSort('typeLabel');

    expect(component.sortColumn()).toBe('typeLabel');
    expect(component.sortDirection()).toBe('asc');
  });

  it('Should_SortRowsAscending_When_NumericColumnSorted', () => {
    const component = createComponent();

    component.onSort('confidence');

    const confidences = component.filteredAndSortedRows().map((r) => r.confidence);
    expect(confidences).toEqual([...confidences].sort((a, b) => a - b));
  });

  it('Should_SortRowsDescending_When_StringColumnSortedTwice', () => {
    const component = createComponent();

    component.onSort('typeLabel');
    component.onSort('typeLabel');

    const labels = component.filteredAndSortedRows().map((r) => r.typeLabel);
    expect(labels).toEqual([...labels].sort((a, b) => b.localeCompare(a)));
  });

  it('Should_CountEntitiesPerType_When_PiiTypeBadgesComputed', () => {
    const component = createComponent();

    const badges = component.piiTypeBadges();
    const emailBadge = badges.find((b) => b.label === 'Email');
    const ibanBadge = badges.find((b) => b.label === 'IBAN');

    expect(emailBadge?.count).toBe(3);
    expect(ibanBadge?.count).toBe(1);
  });

  it('Should_FormatFallbackLabel_When_TranslationMissing', () => {
    const item: PersonallyIdentifiableInformationScanResult = {
      ...MOCK_ITEM,
      detectedPersonallyIdentifiableInformationList: [
        { startPosition: 0, endPosition: 5, piiTypeLabel: 'CREDIT_CARD', confidence: 0.5, source: 'REGEX', maskedContext: '***' },
      ],
    };
    const component = createComponent(item);

    expect(component.entityRows()[0].typeLabel).toBe('Credit Card');
  });

  it('Should_StripPiiTypePrefix_When_LabelHasDottedKey', () => {
    const item: PersonallyIdentifiableInformationScanResult = {
      ...MOCK_ITEM,
      detectedPersonallyIdentifiableInformationList: [
        { startPosition: 0, endPosition: 5, piiTypeLabel: 'piiType.EMAIL', confidence: 0.5, source: 'PRESIDIO', maskedContext: '***' },
      ],
    };
    const component = createComponent(item);

    expect(component.entityRows()[0].typeLabel).toBe('Email');
  });

  it('Should_ParseBadgeParts_When_MaskedValueContainsTypePlaceholder', () => {
    const item: PersonallyIdentifiableInformationScanResult = {
      ...MOCK_ITEM,
      detectedPersonallyIdentifiableInformationList: [
        { startPosition: 0, endPosition: 5, piiTypeLabel: 'EMAIL', confidence: 0.5, source: 'PRESIDIO', maskedContext: 'Contact: [EMAIL] now' },
      ],
    };
    const component = createComponent(item);

    const parts = component.entityRows()[0].valueParts;
    expect(parts.some((p) => p.isBadge && p.text === 'Email')).toBe(true);
    expect(parts.some((p) => !p.isBadge && p.text.includes('Contact'))).toBe(true);
  });
});
