import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { PiiCardExpandedComponent } from './pii-card-expanded.component';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PersonallyIdentifiableInformationScanResult } from '../../core/models/personally-identifiable-information-scan-result';
import { SentinelleApiService } from '../../core/services/sentinelle-api.service';

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
    { startPosition: 0, endPosition: 10, piiTypeLabel: 'EMAIL', confidence: 1.0, source: 'GLINER', maskedContext: 'user@***' },
    { startPosition: 20, endPosition: 30, piiTypeLabel: 'EMAIL', confidence: 0.95, source: 'GLINER', maskedContext: 'admin@***' },
    { startPosition: 40, endPosition: 50, piiTypeLabel: 'EMAIL', confidence: 0.90, source: 'PRESIDIO', maskedContext: 'test@***' },
    { startPosition: 60, endPosition: 70, piiTypeLabel: 'IBAN', confidence: 0.88, source: 'GLINER', maskedContext: 'CH*** ****' },
  ],
};

describe('PiiCardExpandedComponent', () => {
  let fixture: ComponentFixture<PiiCardExpandedComponent>;

  beforeEach(async () => {
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
        { provide: SentinelleApiService, useValue: { revealAllowed: signal(true) } },
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
    expect(rows.length).toBe(4); // One row per entity
  });

  it('Should_EmitCollapse_When_CloseButtonClicked', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const spy = jest.spyOn(fixture.componentInstance.collapse, 'emit');
    fixture.nativeElement.querySelector('.expanded-header').click();
    expect(spy).toHaveBeenCalled();
  });

  it('Should_EmitRevealRequested_When_RevealClicked', () => {
    fixture = TestBed.createComponent(PiiCardExpandedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.componentRef.setInput('revealed', false);
    fixture.componentRef.setInput('isRevealing', false);
    fixture.detectChanges();
    const spy = jest.spyOn(fixture.componentInstance.revealRequested, 'emit');
    fixture.nativeElement.querySelector('.btn-reveal').click();
    expect(spy).toHaveBeenCalled();
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
          startPosition: 0, endPosition: 10, piiTypeLabel: 'EMAIL', confidence: 1.0, source: 'GLINER',
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
    expect(valueCell.textContent!.trim()).toContain('Contact:');
    expect(valueCell.textContent!.trim()).toContain('user@example.com');
    expect(valueCell.textContent!.trim()).toContain('for info');
    expect(valueCell.classList).toContain('td-value--revealed');

    const highlight = valueCell.querySelector('.revealed-highlight') as HTMLElement;
    expect(highlight).toBeTruthy();
    expect(highlight.textContent!.trim()).toBe('user@example.com');
  });

  it('Should_DisplaySensitiveValueOnly_When_RevealedWithoutContext', () => {
    const revealedItem: PersonallyIdentifiableInformationScanResult = {
      ...MOCK_ITEM,
      detectedPersonallyIdentifiableInformationList: [
        {
          startPosition: 0, endPosition: 10, piiTypeLabel: 'EMAIL', confidence: 1.0, source: 'GLINER',
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
    expect(highlight.textContent!.trim()).toBe('user@example.com');
    expect(valueCell.classList).toContain('td-value--revealed');
  });
});
