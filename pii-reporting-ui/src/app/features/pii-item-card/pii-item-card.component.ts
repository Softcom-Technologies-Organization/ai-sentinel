import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnChanges,
  SimpleChanges
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {PiiItem} from '../../core/models/pii-item';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {TagModule} from 'primeng/tag';
import {ChipModule} from 'primeng/chip';
import {Severity} from '../../core/models/severity';
import {PiiItemCardUtils} from './pii-item-card.utils';

/**
 * Display a single detection item with masked HTML snippet, entities and severity badge.
 * Values are masked by default; user can reveal per-card.
 */
@Component({
  selector: 'app-pii-item-card',
  standalone: true,
  imports: [CommonModule, ButtonModule, CardModule, TagModule, ChipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pii-item-card.component.html',
  styleUrl: './pii-item-card.component.css',
})
export class PiiItemCardComponent implements OnChanges {
  /** Item to render. */
  @Input() item!: PiiItem;
  /** If true, values are masked until user reveals. */
  @Input() maskByDefault = true;

  revealed = false;
  /** Controls opening of the detail section to match UX spec */
  detailsOpen = false;

  // Utils facade for UI helper methods
  readonly piiItemCardUtils = inject(PiiItemCardUtils);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['item'] || changes['maskByDefault']) {
      this.revealed = !this.maskByDefault;
      this.detailsOpen = false;
    }
  }

  get maskedHtmlSafe(): string | null {
    if (!this.item?.maskedHtml) return null;
    return this.item.maskedHtml;
  }

  toggleReveal(): void { this.revealed = !this.revealed; }
  toggleDetails(): void { this.detailsOpen = !this.detailsOpen; }

  sevClass(sev?: Severity | null): string {
    if (sev === 'high') return 'sev-high';
    if (sev === 'medium') return 'sev-medium';
    return 'sev-low';
  }

  /** Map domain severity to PrimeNG Tag severity */
  sevPrime(sev?: Severity | null): 'danger' | 'warn' | 'info' {
    if (sev === 'high') return 'danger';
    if (sev === 'medium') return 'warn';
    return 'info';
  }

  sevLabel(sev?: Severity | null): string {
    if (sev === 'high') return 'Élevée';
    if (sev === 'medium') return 'Moyenne';
    return 'Faible';
  }

  formatTs(ts?: string): string {
    if (!ts) return '';
    try { return new Date(ts).toLocaleString(); } catch { return ts; }
  }

  objectKeys(obj: Record<string, number>): string[] { return Object.keys(obj || {}); }

  /** Format a numeric score with 2 decimals for chip display */
  formatScore(score: number | undefined | null): string {
    if (score === undefined || score === null || isNaN(Number(score))) return '';
    return Number(score).toFixed(2);
  }

  /**
   * Normalize attachment type string (MIME or extension) to a known kind for tag rendering.
   * Delegates to PiiItemCardUtils for the actual mapping logic.
   */
  attachmentKind(type?: string | null): 'pdf' | 'excel' | 'word' | 'ppt' | 'txt' | null {
    return this.piiItemCardUtils.attachmentKind(type);
  }
}
