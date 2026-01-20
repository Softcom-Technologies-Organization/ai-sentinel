import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Observable, timer } from 'rxjs';
import { catchError, map, shareReplay, skip, switchMap } from 'rxjs/operators';
import { SentinelleApiService } from './sentinelle-api.service';
import { Space } from '../models/space';
import { SpaceUpdateInfo } from '../models/space-update-info.model';

export interface SpaceChangeDetection {
  spaces: Space[];
  totalCount: number;
  hasNewSpaces: boolean;
  newSpacesCount: number;
}

export interface PollingConfig {
  backendRefreshIntervalMs: number;
  frontendPollingIntervalMs: number;
}

/**
 * Service for silent background polling of Confluence spaces.
 * Business purpose: detects new spaces without disrupting user workflow.
 * Polling interval dynamically retrieved from backend configuration.
 */
@Injectable({ providedIn: 'root' })
export class ConfluenceSpacesPollingService {
  private readonly api = inject(SentinelleApiService);
  private readonly http = inject(HttpClient);
  private pollingIntervalMs: number = 60000; // Default fallback: 1 minute

  /**
   * Initializes polling configuration from backend.
   * Should be called during app initialization.
   */
  async loadPollingConfig(): Promise<void> {
    try {
      const config = await firstValueFrom(
        this.http.get<PollingConfig>('/api/v1/config/polling')
      );
      this.pollingIntervalMs = config.frontendPollingIntervalMs;
      console.log(`Polling interval configured: ${this.pollingIntervalMs}ms`);
    } catch (error) {
      console.warn('Failed to load polling config, using default:', error);
    }
  }

  /**
   * Starts silent polling at interval configured by backend.
   * Emits when space count changes.
   * Business purpose: background monitoring for new spaces.
   */
  startPolling(initialCount: number): Observable<SpaceChangeDetection> {
    let previousCount = initialCount;

    return timer(this.pollingIntervalMs, this.pollingIntervalMs).pipe(
      skip(1),
      switchMap(() => this.api.getSpaces()),
      map(spaces => this.detectChanges(spaces, previousCount)),
      map(detection => {
        if (detection.hasNewSpaces) {
          previousCount = detection.totalCount;
        }
        return detection;
      }),
      shareReplay(1)
    );
  }

  /**
   * Starts polling for Confluence spaces update information.
   * Business purpose: keeps dashboard indicators in sync with backend without manual refresh.
   *
   * Uses the same interval configuration as space discovery polling.
   */
  startUpdateInfoPolling(): Observable<SpaceUpdateInfo[]> {
    return timer(this.pollingIntervalMs, this.pollingIntervalMs).pipe(
      skip(1),
      switchMap(() => this.api.getSpacesUpdateInfo()),
      catchError((err) => {
        console.error('[polling] Error while polling spaces update info:', err);
        return new Observable<SpaceUpdateInfo[]>((observer) => {
          observer.next([]);
          observer.complete();
        });
      }),
      shareReplay(1)
    );
  }

  private detectChanges(spaces: Space[], previousCount: number): SpaceChangeDetection {
    const totalCount = spaces.length;
    const hasNewSpaces = totalCount > previousCount;
    const newSpacesCount = hasNewSpaces ? totalCount - previousCount : 0;

    return {
      spaces,
      totalCount,
      hasNewSpaces,
      newSpacesCount
    };
  }
}
