import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { DetectedPersonallyIdentifiableInformation } from '../../core/models/detected-personally-identifiable-information';

@Injectable({ providedIn: 'root' })
export class PiiTypeTranslationService {
  private readonly translocoService = inject(TranslocoService);

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

  computePiiTypeBadges(items: DetectedPersonallyIdentifiableInformation[]): { label: string; count: number }[] {
    const counts = new Map<string, number>();
    for (const entity of items) {
      const label = entity.piiTypeLabel || entity.piiType || 'UNKNOWN';
      counts.set(label, (counts.get(label) ?? 0) + 1);
    }
    return Array.from(counts.entries()).map(([type, count]) => ({
      label: this.translatePiiType(type),
      count,
    }));
  }

  private formatFallback(key: string): string {
    return key
      .split('_')
      .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join(' ');
  }
}
