import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

/**
 * A renderable fragment of a detected value shown in context: plain text, a translated
 * `[TYPE]` token badge, or the sensitive value highlighted inside its surrounding line.
 */
export interface ValuePart {
  text: string;
  isBadge: boolean;
  isHighlighted?: boolean;
}

export interface PiiValueDisplay {
  /** Flat text of every part joined, for sorting or plain comparison. */
  text: string;
  parts: ValuePart[];
  isRevealed: boolean;
}

const MASK_PLACEHOLDER = '•••••••••';

/**
 * Builds the display of a detected PII value together with its detection context, shared by
 * the dashboard detail table and the obfuscation review rows so both render identically.
 *
 * <p>When the plaintext value is revealed, the surrounding {@code sensitiveContext} is shown
 * with the value highlighted; otherwise the {@code maskedContext} is shown with its `[TYPE]`
 * tokens rendered as translated badges.</p>
 */
@Injectable({ providedIn: 'root' })
export class PiiValueDisplayService {
  private readonly translocoService = inject(TranslocoService);

  build(input: {
    sensitiveValue?: string | null;
    sensitiveContext?: string | null;
    maskedContext?: string | null;
    revealed: boolean;
  }): PiiValueDisplay {
    const hasRevealedValue = input.revealed && !!input.sensitiveValue;
    if (hasRevealedValue) {
      const parts = this.parseRevealedParts(input.sensitiveContext ?? undefined, input.sensitiveValue!);
      return {
        text: parts.map(part => part.text).join(''),
        isRevealed: true,
        parts,
      };
    }
    const text = input.maskedContext || MASK_PLACEHOLDER;
    return { text, isRevealed: false, parts: this.parseValueParts(text) };
  }

  translatePiiType(key: string): string {
    if (!key) return 'Unknown';

    let cleanKey = key;
    if (key.toLowerCase().startsWith('piitype')) {
      const parts = key.split('.');
      cleanKey = parts.length > 1 ? parts.at(-1)! : key;
    }

    const normalizedKey = cleanKey.toUpperCase();
    const translationKey = `piiTypes.${normalizedKey}`;
    const translated = this.translocoService.translate(translationKey);
    const isMissing = translated === translationKey || translated.includes('piiTypes.');
    return isMissing ? this.formatFallback(cleanKey) : translated;
  }

  private parseRevealedParts(sensitiveContext: string | undefined, sensitiveValue: string): ValuePart[] {
    if (!sensitiveContext) {
      return [{ text: sensitiveValue, isBadge: false, isHighlighted: true }];
    }

    const index = sensitiveContext.indexOf(sensitiveValue);
    if (index === -1) {
      return [{ text: sensitiveValue, isBadge: false, isHighlighted: true }];
    }

    const parts: ValuePart[] = [];
    if (index > 0) {
      parts.push({ text: sensitiveContext.slice(0, index), isBadge: false });
    }
    parts.push({ text: sensitiveValue, isBadge: false, isHighlighted: true });
    const after = index + sensitiveValue.length;
    if (after < sensitiveContext.length) {
      parts.push({ text: sensitiveContext.slice(after), isBadge: false });
    }
    return parts;
  }

  private parseValueParts(value: string): ValuePart[] {
    const regex = /\[([A-Z][A-Z0-9_]*)\]/g;
    const parts: ValuePart[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = regex.exec(value)) !== null) {
      if (match.index > lastIndex) {
        parts.push({ text: value.slice(lastIndex, match.index), isBadge: false });
      }
      parts.push({ text: this.translatePiiType(match[1]), isBadge: true });
      lastIndex = regex.lastIndex;
    }

    if (lastIndex < value.length) {
      parts.push({ text: value.slice(lastIndex), isBadge: false });
    }

    return parts.length > 0 ? parts : [{ text: value, isBadge: false }];
  }

  private formatFallback(key: string): string {
    return key
      .split('_')
      .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join(' ');
  }
}
