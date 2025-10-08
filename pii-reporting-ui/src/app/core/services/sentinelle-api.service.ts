import {Injectable, NgZone} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {Space} from '../models/space';
import {StreamEvent} from '../models/stream-event';
import {RawStreamPayload, StreamEventType} from '../models/stream-event-type';
import {Severity} from '../models/severity';

export interface LastScanMeta {
  scanId: string;
  lastUpdated: string;
  spacesCount: number;
}

export interface SpaceStatusDto {
  spaceKey: string;
  status: string;
  pagesDone: number;
  attachmentsDone: number;
  lastEventTs: string;
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

  /** Fetch metadata for the last scan (may be null if none). */
  getLastScanMeta(): Observable<LastScanMeta | null> {
    return new Observable<LastScanMeta | null>((observer) => {
      const sub = this.http.get<LastScanMeta>('/api/v1/scans/last').subscribe({
        next: (meta) => {
          observer.next(meta ?? null);
          observer.complete();
        },
        error: (err) => {
          // No content or backend error → expose null to simplify UI
          observer.next(null);
          observer.complete();
        }
      });
      return () => sub.unsubscribe();
    });
  }

  /** Fetch per-space statuses for the last scan. */
  getLastScanSpaceStatuses(): Observable<SpaceStatusDto[]> {
    return new Observable<SpaceStatusDto[]>((observer) => {
      const sub = this.http.get<SpaceStatusDto[]>('/api/v1/scans/last/spaces').subscribe({
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

  /** Command the backend to resume the last scan with the same scanId (best-effort). */
  resumeScan(scanId: string): Observable<void> {
    return new Observable<void>((observer) => {
      const id = encodeURIComponent(String(scanId ?? ''));
      const sub = this.http.post<void>(`/api/v1/scans/${id}/resume`, {}).subscribe({
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
        'multiStart', 'start', 'pageStart', 'item', 'attachmentItem', 'pageComplete', 'error', 'complete', 'multiComplete', 'keepalive'
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

  /** Compute severity level based on max entity score. */
  severityForEntities(entities: Array<{ score?: number }> | undefined): Severity {
    if (!Array.isArray(entities) || entities.length === 0) return 'low';
    let max = 0;
    for (const e of entities) {
      const s = typeof e?.score === 'number' ? e.score : 0;
      if (s > max) max = s;
    }
    if (max >= 0.95) return 'high';
    if (max >= 0.85) return 'medium';
    return 'low';
  }

  /**
   * Replace [TOKEN] style markup with chip spans; used only for visual amenity.
   * Returned string should be considered unsafe; bind via DomSanitizer in components.
   */
  sanitizeMaskedHtml(raw?: string): string | undefined {
    if (!raw) return undefined;
    try {
      return raw.replaceAll(/\[([A-Z_]+)]/g, (_m: string, g1: string) => `<span class="chip">[${g1}]</span>`);
    } catch {
      return raw;
    }
  }
}
