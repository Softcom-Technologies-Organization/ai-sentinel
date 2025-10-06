import { Injectable, inject } from '@angular/core';
import { Observable, timer } from 'rxjs';
import { switchMap, map, shareReplay } from 'rxjs/operators';
import { SentinelleApiService } from './sentinelle-api.service';
import { Space } from '../models/space';

export interface SpaceChangeDetection {
  spaces: Space[];
  totalCount: number;
  hasNewSpaces: boolean;
  newSpacesCount: number;
}

/**
 * Service for silent background polling of Confluence spaces.
 * Business purpose: detects new spaces without disrupting user workflow.
 * Aligned with backend refresh interval (5 minutes).
 */
@Injectable({ providedIn: 'root' })
export class SpacesPollingService {
  private readonly POLLING_INTERVAL_MS = 300000; // 5 minutes
  private readonly api = inject(SentinelleApiService);

  /**
   * Starts silent polling every 5 minutes.
   * Emits when space count changes.
   * Business purpose: background monitoring for new spaces.
   */
  startPolling(initialCount: number): Observable<SpaceChangeDetection> {
    let previousCount = initialCount;

    return timer(this.POLLING_INTERVAL_MS, this.POLLING_INTERVAL_MS).pipe(
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
