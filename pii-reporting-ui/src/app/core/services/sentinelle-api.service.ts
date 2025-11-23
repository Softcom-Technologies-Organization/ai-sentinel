import {Injectable, NgZone} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {Space} from '../models/space';
import {StreamEvent} from '../models/stream-event';
import {RawStreamPayload, StreamEventType} from '../models/stream-event-type';
import {Severity} from '../models/severity';
import {SpaceUpdateInfo} from '../models/space-update-info.model';

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
  lastEventTs: string;
  progressPercentage?: number;
}

export interface SpaceSummaryDto {
  spaceKey: string;
  status: string;
  progressPercentage: number | null;
  pagesDone: number;
  attachmentsDone: number;
  lastEventTs: string;
}

export interface ScanReportingSummaryDto {
  scanId: string;
  lastUpdated: string;
  spacesCount: number;
  spaces: SpaceSummaryDto[];
}

@Injectable({ providedIn: 'root' })
export class SentinelleApiService {
  constructor(private readonly http: HttpClient, private readonly zone: NgZone) {
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
  getLastScanItems(): Observable<RawStreamPayload[]> {
    return new Observable<RawStreamPayload[]>((observer) => {
      const sub = this.http.get<RawStreamPayload[]>('/api/v1/scans/last/items').subscribe({
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
  getDashboardSpacesSummary(): Observable<ScanReportingSummaryDto | null> {
    return new Observable<ScanReportingSummaryDto | null>((observer) => {
      const sub = this.http.get<ScanReportingSummaryDto>('/api/v1/scans/dashboard/spaces-summary').subscribe({
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
      const sub = this.http.post<void>(`/api/v1/scans/${id}/pause`, {}).subscribe({
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

  private onSseEvent(observer: { next: (ev: StreamEvent) => void }, type: StreamEventType, e: MessageEvent): void {
    const raw = String((e as any)?.data ?? '');
    this.zone.run(() => this.emitStreamEvent(observer, type, raw));
  }

  private emitStreamEvent(observer: { next: (ev: StreamEvent) => void }, type: StreamEventType, raw: string): void {
    const parsed = this.parseRawPayload(raw);
    observer.next({ type, dataRaw: raw, data: parsed });
  }

  private parseRawPayload(raw: string): RawStreamPayload | undefined {
    try {
      return JSON.parse(raw);
    } catch {
      return undefined;
    }
  }

  /** Compute severity level based on max entity confidence. */
  severityForEntities(entities: Array<{ confidence?: number }> | undefined): Severity {
    if (!Array.isArray(entities) || entities.length === 0) return 'low';
    let max = 0;
    for (const e of entities) {
      const s = typeof e?.confidence === 'number' ? e.confidence : 0;
      if (s > max) max = s;
    }
    if (max >= 0.95) return 'high';
    if (max >= 0.85) return 'medium';
    return 'low';
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
