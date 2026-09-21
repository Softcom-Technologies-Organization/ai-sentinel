import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import { PiiValueDisplayService } from './pii-value-display.service';

const FR_TRANSLATIONS = {
  piiTypes: {
    EMAIL: 'Email',
  },
};

describe('PiiValueDisplayService', () => {
  let service: PiiValueDisplayService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
    });
    service = TestBed.inject(PiiValueDisplayService);
  });

  it('Should_HighlightValueWithinContext_When_RevealedWithContext', () => {
    const display = service.build({
      sensitiveValue: 'user@example.com',
      sensitiveContext: 'Contact: user@example.com for info',
      maskedContext: 'Contact: [EMAIL] for info',
      revealed: true,
    });

    expect(display.isRevealed).toBe(true);
    expect(display.parts).toEqual([
      { text: 'Contact: ', isBadge: false },
      { text: 'user@example.com', isBadge: false, isHighlighted: true },
      { text: ' for info', isBadge: false },
    ]);
  });

  it('Should_HighlightBareValue_When_RevealedWithoutContext', () => {
    const display = service.build({
      sensitiveValue: 'user@example.com',
      maskedContext: 'user@***',
      revealed: true,
    });

    expect(display.isRevealed).toBe(true);
    expect(display.parts).toEqual([
      { text: 'user@example.com', isBadge: false, isHighlighted: true },
    ]);
  });

  it('Should_HighlightBareValue_When_RevealedValueIsMissingFromContext', () => {
    const display = service.build({
      sensitiveValue: 'user@example.com',
      sensitiveContext: 'Contact information unavailable',
      revealed: true,
    });

    expect(display.isRevealed).toBe(true);
    expect(display.text).toBe('user@example.com');
    expect(display.parts).toEqual([
      { text: 'user@example.com', isBadge: false, isHighlighted: true },
    ]);
  });

  it('Should_RenderTokenBadges_When_NotRevealed', () => {
    const display = service.build({
      sensitiveValue: 'user@example.com',
      maskedContext: 'Contact: [EMAIL] now',
      revealed: false,
    });

    expect(display.isRevealed).toBe(false);
    expect(display.parts.some(p => p.isBadge && p.text === 'Email')).toBe(true);
    expect(display.parts.some(p => !p.isBadge && p.text.includes('Contact'))).toBe(true);
    expect(display.text).toBe('Contact: [EMAIL] now');
  });

  it('Should_RenderTokenBadges_When_NoSensitiveValue', () => {
    const display = service.build({
      maskedContext: 'Contact: [EMAIL] now',
      revealed: true,
    });

    expect(display.isRevealed).toBe(false);
    expect(display.parts.some(p => p.isBadge && p.text === 'Email')).toBe(true);
  });

  it('Should_FallBackToPlaceholder_When_NothingToShow', () => {
    const display = service.build({ revealed: true });

    expect(display.isRevealed).toBe(false);
    expect(display.text).toBe('•••••••••');
  });

  it('Should_FormatFallbackLabel_When_TranslationMissing', () => {
    expect(service.translatePiiType('CREDIT_CARD')).toBe('Credit Card');
  });

  it('Should_StripPiiTypePrefix_When_LabelHasDottedKey', () => {
    expect(service.translatePiiType('piiType.EMAIL')).toBe('Email');
  });
});
