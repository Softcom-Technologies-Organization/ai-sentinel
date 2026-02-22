import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PiiCardCollapsedComponent } from './pii-card-collapsed.component';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PersonallyIdentifiableInformationScanResult } from '../../core/models/personally-identifiable-information-scan-result';

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
  },
  piiPageCard: {
    detections: 'd\u00e9tections',
  },
  dashboard: {
    table: {
      expand: 'D\u00e9velopper',
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
  pageTitle: 'Test Page Title',
  pageUrl: 'https://confluence.example.com/page/1',
  isFinal: true,
  severity: 'high',
  piiTypeSummary: { EMAIL: 4, IBAN: 1 },
  detectedPersonallyIdentifiableInformationList: [
    { startPosition: 0, endPosition: 10, piiTypeLabel: 'Email', confidence: 1.0, source: 'GLINER' },
    { startPosition: 20, endPosition: 30, piiTypeLabel: 'Email', confidence: 0.95, source: 'GLINER' },
    { startPosition: 40, endPosition: 50, piiTypeLabel: 'Email', confidence: 0.98, source: 'GLINER' },
    { startPosition: 60, endPosition: 70, piiTypeLabel: 'Email', confidence: 0.92, source: 'PRESIDIO' },
    { startPosition: 80, endPosition: 90, piiTypeLabel: 'IBAN', confidence: 0.88, source: 'GLINER' },
  ],
};

const MOCK_ITEM_NO_TITLE: PersonallyIdentifiableInformationScanResult = {
  ...MOCK_ITEM,
  pageTitle: undefined,
};

const MOCK_ITEM_WITH_ATTACHMENT: PersonallyIdentifiableInformationScanResult = {
  ...MOCK_ITEM,
  attachmentName: 'report.pdf',
  attachmentType: 'application/pdf',
};

describe('PiiCardCollapsedComponent', () => {
  let fixture: ComponentFixture<PiiCardCollapsedComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PiiCardCollapsedComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
    }).compileComponents();
  });

  it('Should_DisplayPageTitle_When_ItemProvided', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const title = fixture.nativeElement.querySelector('.collapsed-title');
    expect(title.textContent.trim()).toBe('Test Page Title');
  });

  it('Should_DisplayFallbackTitle_When_PageTitleMissing', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM_NO_TITLE);
    fixture.detectChanges();
    const title = fixture.nativeElement.querySelector('.collapsed-title');
    expect(title.textContent.trim()).toBe('Page');
  });

  it('Should_DisplayTotalDetections_When_ItemHasEntities', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const total = fixture.nativeElement.querySelector('.collapsed-total-count');
    expect(total.textContent.trim()).toBe('5');
  });

  it('Should_EmitExpand_When_CardClicked', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const spy = jest.spyOn(fixture.componentInstance.expand, 'emit');
    fixture.nativeElement.querySelector('.collapsed-card').click();
    expect(spy).toHaveBeenCalled();
  });

  it('Should_EmitExpand_When_EnterKeyPressed', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const spy = jest.spyOn(fixture.componentInstance.expand, 'emit');
    const card = fixture.nativeElement.querySelector('.collapsed-card');
    card.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(spy).toHaveBeenCalled();
  });

  it('Should_EmitExpand_When_SpaceKeyPressed', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const spy = jest.spyOn(fixture.componentInstance.expand, 'emit');
    const card = fixture.nativeElement.querySelector('.collapsed-card');
    card.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));
    expect(spy).toHaveBeenCalled();
  });

  it('Should_ShowSeverityStrip_When_SeverityIsHigh', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const strip = fixture.nativeElement.querySelector('.severity-strip') as HTMLElement;
    expect(strip.style.backgroundColor).toBe('rgb(220, 38, 38)');
  });

  it('Should_RenderPiiTypeBadges_When_DetectionsExist', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const badges = fixture.nativeElement.querySelectorAll('.pii-type-badge');
    expect(badges.length).toBe(2); // Email + IBAN
    const labels = Array.from(badges).map((b: any) => b.querySelector('.pii-type-label')?.textContent?.trim());
    expect(labels).toContain('Email');
    expect(labels).toContain('IBAN');
  });

  it('Should_ShowAttachmentBadge_When_AttachmentPresent', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM_WITH_ATTACHMENT);
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.attachment-badge');
    expect(badge).toBeTruthy();
    expect(badge.textContent).toContain('report.pdf');
  });

  it('Should_NotShowAttachmentBadge_When_NoAttachment', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.attachment-badge');
    expect(badge).toBeNull();
  });

  it('Should_HaveButtonRole_When_Rendered', () => {
    fixture = TestBed.createComponent(PiiCardCollapsedComponent);
    fixture.componentRef.setInput('item', MOCK_ITEM);
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('.collapsed-card');
    expect(card.getAttribute('role')).toBe('button');
    expect(card.getAttribute('tabindex')).toBe('0');
  });
});
