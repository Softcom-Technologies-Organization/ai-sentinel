import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { MultiSelectModule } from 'primeng/multiselect';
import { MultiSelectChangeEvent } from 'primeng/types/multiselect';
import { PersonallyIdentifiableInformationScanResult } from '../../core/models/personally-identifiable-information-scan-result';
import { DetectorTagComponent } from '../../shared/detector-tag/detector-tag.component';
import { ConfidenceIndicatorComponent } from '../../shared/confidence-indicator/confidence-indicator.component';
import { SEVERITY_STYLES } from './severity.config';
import { PiiEntityRow, ValuePart } from './pii-type-row.model';
import { PiiItemCardUtils } from '../pii-item-card/pii-item-card.utils';
import { SentinelleApiService } from '../../core/services/sentinelle-api.service';

export type SortColumn = 'typeLabel' | 'value' | 'confidence' | 'detector';
export type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-pii-card-expanded',
  standalone: true,
  imports: [TranslocoModule, FormsModule, MultiSelectModule, DetectorTagComponent, ConfidenceIndicatorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pii-card-expanded.component.html',
  styleUrls: ['./pii-card-expanded.component.scss'],
})
export class PiiCardExpandedComponent {
  readonly item = input.required<PersonallyIdentifiableInformationScanResult>();
  readonly revealed = input.required<boolean>();
  readonly isRevealing = input.required<boolean>();

  readonly collapse = output<void>();
  readonly revealRequested = output<void>();
  readonly openInConfluence = output<void>();

  readonly sentinelleApi = inject(SentinelleApiService);
  private readonly translocoService = inject(TranslocoService);
  private readonly piiItemCardUtils = inject(PiiItemCardUtils);

  readonly severityStyle = computed(() => SEVERITY_STYLES[this.item().severity] ?? SEVERITY_STYLES.low);

  readonly totalDetections = computed(() =>
    this.item().detectedPersonallyIdentifiableInformationList?.length ?? 0
  );

  readonly entityRows = computed<PiiEntityRow[]>(() => {
    const entities = this.item().detectedPersonallyIdentifiableInformationList ?? [];
    const isRevealed = this.revealed();

    return entities.map(entity => {
      const label = entity.piiTypeLabel || entity.piiType || 'UNKNOWN';
      const hasRevealedValue = isRevealed && !!entity.sensitiveValue;
      const displayValue = hasRevealedValue
        ? (entity.sensitiveContext || entity.sensitiveValue!)
        : (entity.maskedContext || '\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022');

      return {
        typeLabel: this.translatePiiType(label),
        value: displayValue,
        valueParts: hasRevealedValue
          ? this.parseRevealedParts(entity.sensitiveContext, entity.sensitiveValue!)
          : this.parseValueParts(displayValue),
        isRevealed: hasRevealedValue,
        confidence: entity.confidence ?? 0,
        detector: entity.source ?? 'UNKNOWN_SOURCE',
      };
    });
  });

  readonly piiTypeBadges = computed(() => {
    const counts = new Map<string, number>();
    for (const entity of this.item().detectedPersonallyIdentifiableInformationList ?? []) {
      const label = entity.piiTypeLabel || entity.piiType || 'UNKNOWN';
      counts.set(label, (counts.get(label) ?? 0) + 1);
    }
    return Array.from(counts.entries()).map(([type, count]) => ({
      label: this.translatePiiType(type),
      count,
    }));
  });

  readonly attachmentKind = computed(() =>
    this.piiItemCardUtils.attachmentKind(this.item().attachmentType)
  );

  private static readonly ALL_VALUE = '__ALL__';

  // Filter & sort state
  readonly sortColumn = signal<SortColumn | null>(null);
  readonly sortDirection = signal<SortDirection>('asc');

  /** Unique PII type labels from entity rows. */
  private readonly uniqueTypes = computed(() =>
    [...new Set(this.entityRows().map(r => r.typeLabel))].sort((a, b) => a.localeCompare(b))
  );

  /** MultiSelect options: "Tous" first, then each PII type. */
  readonly filterOptions = computed<{ label: string; value: string }[]>(() => {
    const allLabel = this.translocoService.translate('piiPageCard.filter.all');
    return [
      { label: allLabel, value: PiiCardExpandedComponent.ALL_VALUE },
      ...this.uniqueTypes().map(t => ({ label: t, value: t }))
    ];
  });

  /** Currently selected values in the MultiSelect. Starts with ["__ALL__"]. */
  readonly selectedFilterValues = signal<string[]>([PiiCardExpandedComponent.ALL_VALUE]);

  /** Whether "Tous" is the active filter (= no filtering). */
  readonly isAllSelected = computed(() =>
    this.selectedFilterValues().includes(PiiCardExpandedComponent.ALL_VALUE)
  );

  /** Handle MultiSelect change with "Tous" exclusivity logic. */
  onFilterChanged(event: MultiSelectChangeEvent): void {
    const newValues: string[] = event.value;
    const oldValues = this.selectedFilterValues();
    const wasAllSelected = oldValues.includes(PiiCardExpandedComponent.ALL_VALUE);
    const isAllInNew = newValues.includes(PiiCardExpandedComponent.ALL_VALUE);

    if (!wasAllSelected && isAllInNew) {
      // User checked "Tous" → select only "Tous", deselect all types
      this.selectedFilterValues.set([PiiCardExpandedComponent.ALL_VALUE]);
    } else if (wasAllSelected && newValues.length > 1) {
      // User checked a specific type while "Tous" was selected → deselect "Tous"
      this.selectedFilterValues.set(newValues.filter(v => v !== PiiCardExpandedComponent.ALL_VALUE));
    } else if (newValues.length === 0) {
      // All deselected → fall back to "Tous"
      this.selectedFilterValues.set([PiiCardExpandedComponent.ALL_VALUE]);
    } else {
      this.selectedFilterValues.set(newValues);
    }
  }

  readonly filteredAndSortedRows = computed(() => {
    let rows = this.entityRows();

    // Filter: if "Tous" is selected, show all; otherwise filter by selected types
    if (!this.isAllSelected()) {
      const selectedSet = new Set(this.selectedFilterValues());
      rows = rows.filter(r => selectedSet.has(r.typeLabel));
    }

    // Sort
    const col = this.sortColumn();
    const dir = this.sortDirection();
    if (col) {
      rows = [...rows].sort((a, b) => {
        const valA = a[col];
        const valB = b[col];
        const cmp = typeof valA === 'string'
          ? valA.localeCompare(valB as string)
          : valA - (valB as number);
        return dir === 'asc' ? cmp : -cmp;
      });
    }

    return rows;
  });

  onSort(column: SortColumn): void {
    if (this.sortColumn() === column) {
      this.sortDirection.update(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set('asc');
    }
  }

  private translatePiiType(key: string): string {
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
      return [{ text: sensitiveContext, isBadge: false, isHighlighted: true }];
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
