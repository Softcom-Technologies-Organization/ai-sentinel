import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { PiiPageCardComponent } from './pii-page-card.component';
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
    fallback: { page: 'Page' },
    attachment: { label: 'Pi\u00e8ce jointe' },
    actions: {
      reveal: 'R\u00e9v\u00e9ler',
      mask: 'Masquer',
      revealAriaLabel: 'Afficher les valeurs sensibles',
      maskAriaLabel: 'Masquer les valeurs sensibles',
    },
  },
  piiPageCard: {
    detections: 'd\u00e9tections',
    types: 'types',
    openInConfluence: 'Ouvrir dans Confluence',
    table: { type: 'Type', occurrences: 'Occurrences', confidence: 'Confiance', detector: 'D\u00e9tecteur' },
  },
  dashboard: { table: { expand: 'D\u00e9velopper', collapse: 'R\u00e9duire' } },
  piiTypes: {},
};

const MOCK_ITEM: PersonallyIdentifiableInformationScanResult = {
  scanId: 'scan-1',
  spaceKey: 'TEST',
  pageId: 'page-1',
  pageTitle: 'Test',
  isFinal: true,
  severity: 'high',
  detectedPersonallyIdentifiableInformationList: [],
};

describe('PiiPageCardComponent', () => {
  let fixture: ComponentFixture<PiiPageCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PiiPageCardComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
      providers: [
        { provide: SentinelleApiService, useValue: { revealAllowed: signal(false), revealPageSecrets: jest.fn() } },
      ],
    }).compileComponents();
  });

  it('Should_ShowCollapsedView_When_NotExpanded', () => {
    fixture = TestBed.createComponent(PiiPageCardComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-pii-card-collapsed')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-pii-card-expanded')).toBeFalsy();
  });

  it('Should_ShowExpandedView_When_Expanded', () => {
    fixture = TestBed.createComponent(PiiPageCardComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    fixture.componentInstance.expanded.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-pii-card-expanded')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-pii-card-collapsed')).toBeFalsy();
  });

  it('Should_CollapseBack_When_CollapseEmitted', () => {
    fixture = TestBed.createComponent(PiiPageCardComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    fixture.componentInstance.expanded.set(true);
    fixture.detectChanges();
    fixture.componentInstance.onCollapse();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-pii-card-collapsed')).toBeTruthy();
  });

  it('Should_HaveTestId_When_Rendered', () => {
    fixture = TestBed.createComponent(PiiPageCardComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="pii-page-card"]');
    expect(el).toBeTruthy();
  });

  it('Should_ToggleRevealedOff_When_AlreadyRevealed', () => {
    fixture = TestBed.createComponent(PiiPageCardComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    fixture.componentInstance.revealed.set(true);
    fixture.componentInstance.onRevealRequested();
    expect(fixture.componentInstance.revealed()).toBe(false);
  });
});
