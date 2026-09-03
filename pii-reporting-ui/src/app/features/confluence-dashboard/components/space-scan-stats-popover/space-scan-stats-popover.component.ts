import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
  viewChild
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { PopoverModule, Popover } from 'primeng/popover';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { TranslocoModule } from '@jsverse/transloco';
import {
  SentinelleApiService,
  SpaceScanStatsDto
} from '../../../../core/services/sentinelle-api.service';

type LoadState = 'idle' | 'loading' | 'loaded' | 'notFound' | 'error';

/**
 * Action-column trigger that lazy-loads and displays last-scan statistics for a
 * single Confluence space inside a rich popover.
 *
 * The HTTP call is fired on every open (kept intentionally simple: no caching),
 * so the panel always reflects the latest persisted scan figures.
 */
@Component({
  selector: 'app-space-scan-stats-popover',
  standalone: true,
  imports: [ButtonModule, PopoverModule, TableModule, TooltipModule, TranslocoModule],
  templateUrl: './space-scan-stats-popover.component.html',
  styleUrl: './space-scan-stats-popover.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SpaceScanStatsPopoverComponent {
  /** Confluence space key whose scan statistics are displayed. */
  readonly spaceKey = input.required<string>();

  readonly disabled = input(false);

  private readonly api = inject(SentinelleApiService);
  private readonly popover = viewChild.required<Popover>('popover');

  readonly state = signal<LoadState>('idle');
  readonly stats = signal<SpaceScanStatsDto | null>(null);

  /** True while the referenced scan is still running (no finish timestamp). */
  readonly isScanInProgress = computed(() => {
    const current = this.stats();
    return current !== null && current.durationMs === null;
  });

  /** Opens the popover and (re)triggers the statistics fetch. */
  toggle(event: Event): void {
    event.stopPropagation();
    this.popover().toggle(event);
  }

  /** Lazy-load handler wired to the popover's show event. */
  onShow(): void {
    this.fetchStats();
  }

  private fetchStats(): void {
    this.state.set('loading');
    this.stats.set(null);
    this.api.getSpaceScanStats(this.spaceKey()).subscribe({
      next: (result) => {
        this.stats.set(result);
        this.state.set('loaded');
      },
      error: (error: unknown) => this.handleError(error)
    });
  }

  private handleError(error: unknown): void {
    const isNotFound = error instanceof HttpErrorResponse && error.status === 404;
    this.state.set(isNotFound ? 'notFound' : 'error');
  }

  /** Formats a duration as `hh:mm:ss`, or `Xs` when under one minute. */
  formatDuration(durationMs: number): string {
    const totalSeconds = Math.round(durationMs / 1000);
    if (totalSeconds < 60) {
      return `${totalSeconds}s`;
    }
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return [hours, minutes, seconds].map((part) => String(part).padStart(2, '0')).join(':');
  }

  /** Formats a character count compactly using French separators (e.g. `1,2 M`). */
  formatChars(value: number): string {
    if (value >= 1_000_000) {
      return `${this.toFrenchDecimal(value / 1_000_000)} M`;
    }
    if (value >= 1_000) {
      return `${this.toFrenchDecimal(value / 1_000)} k`;
    }
    return String(value);
  }

  /** Formats a throughput value (characters per second), null-safe. The unit is
   * appended in the template so it stays translatable. */
  formatRate(charsPerSecond: number | null): string {
    if (charsPerSecond === null) {
      return '—';
    }
    return Math.round(charsPerSecond).toLocaleString('fr-FR');
  }

  /** True for the deterministic format post-filter pseudo detector, which discards
   * PII and has no characters-per-second throughput. */
  isPostfilter(detector: string): boolean {
    return detector === 'POSTFILTER';
  }

  private toFrenchDecimal(value: number): string {
    return value.toFixed(1).replace('.', ',');
  }
}
