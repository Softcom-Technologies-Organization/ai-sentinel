import { Injectable, NgZone, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { Space } from '../models/space';
import { StreamEvent } from '../models/stream-event';
import {
  ConfluenceContentPersonallyIdentifiableInformationScanResult,
  StreamEventType
} from '../models/stream-event-type';
import { SpaceUpdateInfo } from '../models/space-update-info.model';

export interface LastScanMeta {
  scanId: string;
  lastUpdated: string;
  spacesCount: number;
}

export interface SpaceScanStateDto {
  spaceKey: string;
  status: string;
  pagesDone: number;
  attachmentsDone: number;
  lastEventAt: string;
  progressPercentage?: number;
  /** Scan id of the space's latest checkpoint (absent/null if never scanned). */
  scanId?: string;
}

export interface SpaceSummaryDto {
  spaceKey: string;
  status: string;
  progressPercentage: number | null;
  pagesDone: number;
  attachmentsDone: number;
  lastEventAt: string;
  severityCounts: { high: number; medium: number; low: number; total: number; } | null;
  /** Scan id of the space's latest checkpoint (absent/null if never scanned). */
  scanId?: string;
  /** Human-readable space name as known by the backend (optional, additive). */
  spaceName?: string;
  /**
   * PII type code -> occurrence count for this space in the latest scan.
   * Always an empty object (never null) when there are no detections.
   */
  piiTypeCounts?: Record<string, number>;
}

/** Bi-level facet counter for one filter option (server-computed, contextual). */
export interface FacetCount {
  spaceCount: number;
  totalOccurrences: number;
}

/** Server-computed facet counts for each filter axis. */
export interface DashboardFacets {
  piiTypes: Record<string, FacetCount>;
  severities: Record<string, FacetCount>;
  statuses: Record<string, FacetCount>;
}

export interface ScanReportingSummaryDto {
  scanId: string;
  lastUpdated: string;
  /** Total number of spaces BEFORE filtering (for the "X / Y" counter). */
  spacesCount: number;
  /** Spaces after server-side filter + search + sort. */
  spaces: SpaceSummaryDto[];
  /** Contextual facet counts; absent on legacy/unfiltered responses. */
  facets?: DashboardFacets;
}

/** Query parameters for server-side dashboard filtering / sorting / search. */
export interface DashboardFilterParams {
  piiTypes?: string[];
  severities?: string[];
  statuses?: string[];
  q?: string;
  /** name | totalDetections | severityScore | lastScan | piiType:<CODE> */
  sort?: string;
  order?: 'asc' | 'desc';
}

export interface ScanFailedItemDto {
  itemType: 'PAGE' | 'ATTACHMENT';
  title: string;
}

export interface ScanDetectorStatDto {
  detector: string;
  detections: number;
  charsProcessed: number;
  busyMs: number;
  charsPerSecond: number | null;
  /** PII discarded by this stage (0 for real detectors; >0 for the PREFILTER post-filter). */
  discarded: number;
  /**
   * Analysis requests this detector could not serve (e.g. its endpoint was
   * unreachable). Non-zero means the scan is incomplete for this detector, which
   * its detection count alone cannot reveal.
   */
  failedRequests: number;
  /** Latest failure reason, null when the detector never failed. */
  lastError: string | null;
}

export interface SpaceScanStatsDto {
  scanId: string;
  spaceKey: string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  pagesScanned: number;
  pagesFailed: number;
  pageChars: number;
  attachmentsScanned: number;
  attachmentsFailed: number;
  attachmentChars: number;
  failedItems: ScanFailedItemDto[];
  detectorStats: ScanDetectorStatDto[];
}

@Injectable({ providedIn: 'root' })
export class SentinelleApiService {
  /** Signal holding the reveal allowed configuration state */
  readonly revealAllowed = signal<boolean>(false);

  constructor(private readonly http: HttpClient, private readonly zone: NgZone) {
  }

  /**
   * Loads the reveal configuration from backend and updates the signal.
   * Intended to be called during app initialization.
   */
  loadRevealConfig(): Observable<boolean> {
    return this.http.get<boolean>('/api/v1/pii/config/reveal-allowed').pipe(
      tap(allowed => this.revealAllowed.set(allowed)),
      catchError((err) => {
        console.error('Failed to load reveal config', err);
        this.revealAllowed.set(false);
        return of(false);
      })
    );
  }

  getSpaces(): Observable<Space[]> {
    return new Observable<Space[]>((observer) => {
      const sub = this.http.get<Space[]>('/api/v1/confluence/spaces').subscribe({
        next: (data) => {
          const spaces: Space[] = data.map((space) => ({
              key: space.key,
              name: space.name ?? '',
              url: space.url ?? undefined
            }))
            .filter((space) => !!space.key);
          observer.next(spaces);
          observer.complete();
        },
        error: (err) => observer.error(err)
      });
      return () => sub.unsubscribe();
    });
  }

  /** Fetch update information for all Confluence spaces. */
  getSpacesUpdateInfo(): Observable<SpaceUpdateInfo[]> {
    return new Observable<SpaceUpdateInfo[]>((observer) => {
      const sub = this.http.get<SpaceUpdateInfo[]>('/api/v1/confluence/spaces/update-info').subscribe({
        next: (data) => {
          observer.next(Array.isArray(data) ? data : []);
          observer.complete();
        },
        error: (err) => {
          observer.error(err);
        }
      });
      return () => sub.unsubscribe();
    });
  }

  /** Fetch metadata for the last scan (may be null if none). */
  getLastScanMeta(): Observable<LastScanMeta | null> {
    return new Observable<LastScanMeta | null>((observer) => {
      const sub = this.http.get<LastScanMeta>('/api/v1/scans/last').subscribe({
        next: (meta) => {
          observer.next(meta ?? null);
          observer.complete();
        },
        error: () => {
          // No content or backend error → expose null to simplify UI
          observer.next(null);
          observer.complete();
        }
      });
      return () => sub.unsubscribe();
    });
  }

  /** Fetch per-space statuses for the last scan. */
  getLastScanSpaceStatuses(): Observable<SpaceScanStateDto[]> {
    return new Observable<SpaceScanStateDto[]>((observer) => {
      const sub = this.http.get<SpaceScanStateDto[]>('/api/v1/scans/last/spaces').subscribe({
        next: (list) => {
          observer.next(Array.isArray(list) ? list : []);
          observer.complete();
        },
        error: () => {
          observer.next([]);
          observer.complete();
        }
      });
      return () => sub.unsubscribe();
    });
  }

  /** Fetch persisted item events for the last scan (page and attachment items). */
  getLastScanItems(): Observable<ConfluenceContentPersonallyIdentifiableInformationScanResult[]> {
    return new Observable<ConfluenceContentPersonallyIdentifiableInformationScanResult[]>((observer) => {
      const sub = this.http.get<ConfluenceContentPersonallyIdentifiableInformationScanResult[]>('/api/v1/scans/last/items').subscribe({
        next: (list) => {
          observer.next(Array.isArray(list) ? list : []);
          observer.complete();
        },
        error: () => {
          observer.next([]);
          observer.complete();
        }
      });
      return () => sub.unsubscribe();
    });
  }

  /**
   * Fetch unified dashboard summary combining authoritative progress from checkpoints
   * with aggregated counters from events. Replaces separate getLastScanSpaceStatuses()
   * and getLastScanItems() calls to avoid race conditions.
   */
  getDashboardSpacesSummary(filter?: DashboardFilterParams): Observable<ScanReportingSummaryDto | null> {
    const params = this.buildDashboardFilterParams(filter);
    return new Observable<ScanReportingSummaryDto | null>((observer) => {
      const sub = this.http
        .get<ScanReportingSummaryDto>('/api/v1/scans/dashboard/spaces-summary', { params })
        .subscribe({
          next: (summary) => {
            observer.next(summary ?? null);
            observer.complete();
          },
          error: () => {
            observer.next(null);
            observer.complete();
          }
        });
      return () => sub.unsubscribe();
    });
  }

  /** Builds the query params for the dashboard endpoint; empty axes are omitted. */
  private buildDashboardFilterParams(filter?: DashboardFilterParams): HttpParams {
    let params = new HttpParams();
    if (!filter) {
      return params;
    }
    const csv = (values?: string[]): string | null =>
      values && values.length > 0 ? values.join(',') : null;
    const piiTypes = csv(filter.piiTypes);
    const severities = csv(filter.severities);
    const statuses = csv(filter.statuses);
    if (piiTypes) params = params.set('piiTypes', piiTypes);
    if (severities) params = params.set('severities', severities);
    if (statuses) params = params.set('statuses', statuses);
    if (filter.q) params = params.set('q', filter.q);
    if (filter.sort) {
      params = params.set('sort', filter.sort);
      params = params.set('order', filter.order ?? 'desc');
    }
    return params;
  }

  /**
   * Fetch last-scan statistics for a single space (lazy-loaded on demand).
   * Errors are propagated so the caller can distinguish 404 (no stats) from
   * network/server failures and render the appropriate message.
   */
  getSpaceScanStats(spaceKey: string): Observable<SpaceScanStatsDto> {
    const key = encodeURIComponent(String(spaceKey ?? ''));
    return this.http.get<SpaceScanStatsDto>(`/api/v1/scans/dashboard/spaces/${key}/stats`);
  }

  /** Command the backend to resume the last scan with the same scanId (best-effort). */
  resumeScan(scanId: string): Observable<void> {
    return new Observable<void>((observer) => {
      const id = encodeURIComponent(String(scanId ?? ''));
      const sub = this.http.post<void>(`/api/v1/stream/${id}/resume`, {}).subscribe({
        next: () => { observer.next(); observer.complete(); },
        error: (err) => { observer.error(err); }
      });
      return () => sub.unsubscribe();
    });
  }

  /** Command the backend to pause a running scan by updating checkpoints to PAUSED status. */
  pauseScan(scanId: string): Observable<void> {
    return new Observable<void>((observer) => {
      const id = encodeURIComponent(String(scanId ?? ''));
      const sub = this.http.post<void>(`/api/v1/stream/${id}/pause`, {}).subscribe({
        next: () => { observer.next(); observer.complete(); },
        error: (err) => { observer.error(err); }
      });
      return () => sub.unsubscribe();
    });
  }

  /** Purge all previous scan data on the server. */
  purgeAllScans(): Observable<void> {
    return new Observable<void>((observer) => {
      const sub = this.http.post<void>('/api/v1/scans/purge', {}).subscribe({
        next: () => { observer.next(); observer.complete(); },
        error: (err) => observer.error(err)
      });
      return () => sub.unsubscribe();
    });
  }

  /** Start SSE stream for multi-space scanning and expose as Observable of events. */
  startAllSpacesStream(scanId?: string): Observable<StreamEvent> {
    return new Observable<StreamEvent>((observer) => {
      const url = scanId && String(scanId).trim().length > 0
        ? `/api/v1/stream/confluence/spaces/events?scanId=${encodeURIComponent(scanId)}`
        : '/api/v1/stream/confluence/spaces/events';
      const es = new EventSource(url);

      const types: StreamEventType[] = [
        'multiStart', 'start', 'pageStart', 'item', 'attachmentItem', 'pageComplete', 'scanError', 'complete', 'multiComplete', 'keepalive'
      ];

      // Register event listeners with lightweight, named handlers to avoid deep nesting
      for (const t of types) {
        const handler = (e: Event) => this.onSseEvent(observer, t, e as MessageEvent);
        es.addEventListener(t, handler as EventListener);
      }

      const onError = () => this.zone.run(() => observer.error(new Error('SSE connection error')));
      es.onerror = onError as any;

      // Teardown: close EventSource when unsubscribed.
      return () => {
        try {
          es.close();
        } catch {
          // ignore
        }
      };
    });
  }

  /** Start SSE stream for selected spaces scan. */
  startSelectedSpacesStream(spaceKeys: string[]): Observable<StreamEvent> {
    return new Observable<StreamEvent>((observer) => {
      // Build query params manually to ensure correct format for List<String>
      const params = spaceKeys.map(k => `spaceKeys=${encodeURIComponent(k)}`).join('&');
      const url = `/api/v1/stream/confluence/spaces/events/selected?${params}`;
      const es = new EventSource(url);

      const types: StreamEventType[] = [
        'multiStart', 'start', 'pageStart', 'item', 'attachmentItem', 'pageComplete', 'scanError', 'complete', 'multiComplete', 'keepalive'
      ];

      for (const t of types) {
        const handler = (e: Event) => this.onSseEvent(observer, t, e as MessageEvent);
        es.addEventListener(t, handler as EventListener);
      }

      const onError = () => this.zone.run(() => observer.error(new Error('SSE connection error')));
      es.onerror = onError as any;

      return () => {
        try {
          es.close();
        } catch {
          // ignore
        }
      };
    });
  }

  private onSseEvent(observer: { next: (ev: StreamEvent) => void }, type: StreamEventType, e: MessageEvent): void {
    const raw = String((e as any)?.data ?? '');
    this.zone.run(() => this.emitStreamEvent(observer, type, raw));
  }

  private emitStreamEvent(observer: { next: (ev: StreamEvent) => void }, type: StreamEventType, raw: string): void {
    const parsed = this.parseRawPayload(raw);
    observer.next({ type, data: parsed });
  }

  private parseRawPayload(raw: string): ConfluenceContentPersonallyIdentifiableInformationScanResult | undefined {
    try {
      return JSON.parse(raw);
    } catch {
      return undefined;
    }
  }


  /**
   * Check if revealing PII secrets is allowed by backend configuration.
   */
  getRevealConfig(): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      const sub = this.http.get<boolean>('/api/v1/pii/config/reveal-allowed').subscribe({
        next: (allowed) => {
          observer.next(allowed);
          observer.complete();
        },
        error: (err) => {
          observer.error(err);
        }
      });
      return () => sub.unsubscribe();
    });
  }

  /**
   * Reveal decrypted PII secrets for a specific Confluence page.
   * Triggers audit log on backend.
   */
  revealPageSecrets(scanId: string, pageId: string): Observable<PageSecretsResponse> {
    return new Observable<PageSecretsResponse>((observer) => {
      const sub = this.http.post<PageSecretsResponse>(
        '/api/v1/pii/reveal-page',
        { scanId, pageId }
      ).subscribe({
        next: (response) => {
          observer.next(response);
          observer.complete();
        },
        error: (err) => {
          observer.error(err);
        }
      });
      return () => sub.unsubscribe();
    });
  }
}

export interface PageSecretsResponse {
  scanId: string;
  pageId: string;
  pageTitle: string;
  secrets: RevealedSecret[];
}

export interface RevealedSecret {
  startPosition: number;
  endPosition: number;
  sensitiveValue: string;
  sensitiveContext: string;
  maskedContext: string;
}
