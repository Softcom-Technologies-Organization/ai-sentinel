import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  Input,
  OnChanges,
  OnInit,
  signal,
  SimpleChanges
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {
  PersonallyIdentifiableInformationScanResult
} from '../../core/models/personally-identifiable-information-scan-result';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {TagModule} from 'primeng/tag';
import {ChipModule} from 'primeng/chip';
import {Severity} from '../../core/models/severity';
import {PiiItemCardUtils} from './pii-item-card.utils';
import {TestIds} from '../test-ids.constants';
import {SentinelleApiService} from '../../core/services/sentinelle-api.service';
import {Divider} from 'primeng/divider';
import {TranslocoModule, TranslocoService} from '@jsverse/transloco';

/**
 * Display a single detection item with masked HTML snippet, entities and severity badge.
 * Values are masked by default; user can reveal per-card by calling the reveal endpoint.
 */
@Component({
  selector: 'app-pii-item-card',
  standalone: true,
  imports: [CommonModule, ButtonModule, CardModule, TagModule, ChipModule, Divider, TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pii-item-card.component.html',
  styleUrl: './pii-item-card.component.css',
})
export class PiiItemCardComponent implements OnInit, OnChanges {
  /** Item to render. */
  @Input() item!: PersonallyIdentifiableInformationScanResult;
  /** If true, values are masked until user reveals. */
  @Input() maskByDefault = true;

  revealed = false;
  /** Controls opening of the detail section to match UX spec */
  detailsOpen = false;
  /** Whether revealing secrets is allowed by backend configuration */
  readonly canRevealSecrets = signal<boolean>(true);
  /** Whether a reveal request is in progress */
  readonly isRevealing = signal<boolean>(false);

  // Track previous item identity to avoid resetting user state on same item
  private previousItemId: string | null = null;

  // Utils facade for UI helper methods
  readonly piiItemCardUtils = inject(PiiItemCardUtils);
  private readonly sentinelleApi = inject(SentinelleApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly translocoService = inject(TranslocoService);

  // Test IDs for E2E testing
  readonly testIds = TestIds;

  ngOnInit(): void {
    // Load reveal configuration from backend
    this.sentinelleApi.getRevealConfig().subscribe({
      next: (allowed) => this.canRevealSecrets.set(allowed),
      error: () => this.canRevealSecrets.set(false)
    });
    // Initialize revealed state on first load only
    this.revealed = !this.maskByDefault;
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Only reset user interaction states if this is a genuinely different item
    if (changes['item'] && this.item) {
      const currentItemId = this.getItemIdentity(this.item);
      const isNewItem = this.previousItemId !== currentItemId;

      if (isNewItem) {
        // New item: reset all user interaction states
        this.revealed = !this.maskByDefault;
        this.detailsOpen = false;
        this.previousItemId = currentItemId;
      }
      // Same item: preserve user interaction states (detailsOpen, revealed)
    }

    // Handle maskByDefault changes only if item hasn't changed or if it's a new item
    if (changes['maskByDefault'] && !changes['item']) {
      this.revealed = !this.maskByDefault;
    }
  }

  /**
   * Generate a unique identifier for an item based on its content.
   * Used to detect when we're receiving the same item vs a new one.
   */
  private getItemIdentity(item: PersonallyIdentifiableInformationScanResult): string {
    return `${item.pageId}-${item.attachmentName || 'page'}`;
  }

  get maskedHtmlSafe(): string | null {
    if (!this.item?.maskedHtml) return null;
    return this.item.maskedHtml;
  }

  toggleReveal(): void {
    if (this.revealed) {
      // Already revealed, just toggle back
      this.revealed = false;
      return;
    }

    // Check if secrets are already loaded (detectedValue is present)
    const hasSecrets = this.item?.detectedPersonallyIdentifiableInformationList?.some(e => e.sensitiveValue !== null);
    if (hasSecrets) {
      // Secrets already loaded, just reveal
      this.revealed = true;
      return;
    }

    // Need to fetch secrets from backend
    if (!this.canRevealSecrets() || !this.item?.pageId) {
      // Cannot reveal or missing pageId
      return;
    }

    this.isRevealing.set(true);
    this.sentinelleApi.revealPageSecrets(this.item.scanId, this.item.pageId).subscribe({
      next: (response) => {
        // Map secrets to entities by position
        const enrichedEntities = this.item.detectedPersonallyIdentifiableInformationList.map(entity => {
          const secret = response.secrets.find(
            s => s.startPosition === entity.startPosition &&
              s.endPosition === entity.endPosition &&
              s.maskedContext === entity.maskedContext
          );
          return secret
            ? {...entity, sensitiveValue: secret.sensitiveValue, sensitiveContext: secret.sensitiveContext}
            : entity;
        });
        this.item = { ...this.item, detectedPersonallyIdentifiableInformationList: enrichedEntities };
        this.revealed = true;
        this.isRevealing.set(false);
        // Force change detection since we mutated the entities
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to reveal secrets:', err);
        this.isRevealing.set(false);
      }
    });
  }
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
    const key = this.getSeverityKey(sev);
    return this.translocoService.translate(key);
  }

  private getSeverityKey(sev?: Severity | null): string {
    switch (sev) {
      case 'high':
        return 'piiItem.severity.high';
      case 'medium':
        return 'piiItem.severity.medium';
      case 'low':
        return 'piiItem.severity.low';
      default:
        return 'piiItem.severity.low';
    }
  }

  formatTs(ts?: string): string {
    if (!ts) return '';
    try { return new Date(ts).toLocaleString(); } catch { return ts; }
  }

  objectKeys(obj?: Record<string, number> | null): string[] {
    return Object.keys(obj || {});
  }

  /**
   * Translate PII type key to user-friendly label.
   * Uses i18n translation with fallback to formatted key.
   *
   * Business rule: All PII type keys are normalized to UPPERCASE at the source (Python gRPC service),
   * ensuring consistent translation lookup across all detectors.
   *
   * @param key PII type key (e.g., "EMAIL", "CREDIT_CARD", "Piitype.email"), can be undefined
   * @returns Translated label or formatted fallback
   */
  translatePiiType(key: string | undefined): string {
    // Handle undefined or empty key
    if (!key) {
      return this.formatFallbackLabel('UNKNOWN');
    }

    // Handle edge case where key itself contains "piiTypes." or "Piitype." prefix
    let cleanKey = key;
    if (key.toLowerCase().startsWith('piitype')) {
      // Extract just the actual type after the dot
      const parts = key.split('.');
      cleanKey = parts.length > 1 ? parts[parts.length - 1] : key;
    }

    const normalizedKey = cleanKey.toUpperCase();
    const translationKey = `piiTypes.${normalizedKey}`;
    const translated = this.translocoService.translate(translationKey);

    // If translation returns the key itself (not found), use formatted fallback
    // Check if it contains "piiTypes." which indicates translation was not found
    const isTranslationMissing = translated === translationKey || translated.includes('piiTypes.');
    return isTranslationMissing ? this.formatFallbackLabel(cleanKey) : translated;
  }

  /**
   * Format a key as fallback when translation is missing.
   * Converts "CREDIT_CARD" to "Credit Card", "EMAIL" to "Email", etc.
   *
   * @param key Raw PII type key
   * @returns Formatted human-readable label
   */
  private formatFallbackLabel(key: string): string {
    return key
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  /** Format a numeric score with 2 decimals for chip display */
  formatScore(score: number | undefined | null): string {
    if (score === undefined || score === null || Number.isNaN(Number(score))) return '';
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
