import {inject, Injectable} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {ScanProgressService} from '../../../core/services/scan-progress.service';
import {ToastService} from '../../../core/services/toast.service';
import {
  ConfluenceContentPersonallyIdentifiableInformationScanResult
} from '../../../core/models/stream-event-type';
import {SpacesDashboardUtils} from '../spaces-dashboard.utils';
import {
  coerceSpaceKey,
  formatEventLog,
  isAttachmentPayload,
  StreamEventType
} from '../spaces-dashboard-stream.utils';
import {SpaceDataManagementService} from './space-data-management.service';
import {PiiItemsStorageService} from './pii-items-storage.service';
import {DashboardUiStateService} from './dashboard-ui-state.service';

/**
 * Service responsible for handling and routing Server-Sent Events (SSE) from scan operations.
 *
 * Business Purpose:
 * - Routes incoming SSE events to appropriate handlers based on event type
 * - Updates dashboard UI state in real-time as scan progresses
 * - Manages scan metadata capture from event payloads
 * - Coordinates state updates across multiple services
 *
 * Supported Event Types:
 * - multiStart: Global multi-space scan initiation
 * - start: Individual space scan start
 * - pageStart: Page processing start with progress
 * - item: Page PII detection result
 * - attachmentItem: Attachment PII detection result
 * - scanError: Non-fatal scan error (scan continues)
 * - complete: Space scan completion
 * - multiComplete: Global scan completion
 *
 * Key Business Rules:
 * - Error events are non-fatal - scan continues after errors
 * - Progress updates are authoritative when provided in payload
 * - Completed spaces always show 100% progress
 * - Items are deduplicated by pageId + attachmentName
 * - Maximum 400 PII items kept per space
 * - Space status reflects real-time scan state
 *
 * Integration Points:
 * - SpaceDataManagementService: Scan metadata management
 * - PiiItemsStorageService: PII items storage and aggregation
 * - DashboardUiStateService: UI state and scan history
 * - ScanProgressService: Progress tracking
 * - SpacesDashboardUtils: UI decoration and updates
 * - ToastService: Error notifications
 *
 * @since Phase 6 - SSE Event Handler Extraction
 */
@Injectable({
  providedIn: 'root'
})
export class SseEventHandlerService {
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);
  private readonly translocoService = inject(TranslocoService);
  private readonly scanProgressService = inject(ScanProgressService);
  private readonly toastService = inject(ToastService);
  private readonly spaceDataManagementService = inject(SpaceDataManagementService);
  private readonly piiItemsStorageService = inject(PiiItemsStorageService);
  private readonly uiStateService = inject(DashboardUiStateService);

  /**
   * Routes an SSE event to the appropriate handler based on event type.
   *
   * Business Logic:
   * - Logs event for debugging and audit trail
   * - Handles multiStart immediately (no payload validation needed)
   * - Routes other events to specialized handlers
   * - Ignores events with missing payload (except multiStart)
   *
   * @param type Event type from SSE stream
   * @param payload Event payload data
   */
  routeStreamEvent(type: StreamEventType, payload?: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    this.uiStateService.append(formatEventLog(type, JSON.stringify(payload ?? {})));

    // Handle multiStart early to refresh dashboard even if payload is missing
    if (type === 'multiStart') {
      this.handleAllSpaceScanStart(payload);
      return;
    }

    if (!payload) {
      return;
    }

    switch (type) {
      case 'start': {
        this.handleStreamScanStart(payload);
        break;
      }
      case 'pageStart': {
        this.handlePageStart(payload);
        break;
      }
      case 'item':
      case 'attachmentItem': {
        this.handleItemEvent(payload);
        break;
      }
      case 'scanError': {
        this.handleStreamError(payload);
        break;
      }
      case 'complete': {
        this.handleStreamComplete(payload);
        break;
      }
      case 'multiComplete': {
        // Handled externally by scan control
        break;
      }
      default: {
        // No-op for other types (keepalive, pageComplete, etc.)
        break;
      }
    }
  }

  /**
   * Handles global multi-space scan start event.
   *
   * Business Rules:
   * - Rebuilds queue with all known space keys
   * - Resets all spaces to PENDING status
   * - Captures scanId from payload when available
   * - Selects first space if none selected
   *
   * UI Impact:
   * - All spaces show PENDING status
   * - Queue reflects full space list
   * - First space auto-selected for user convenience
   *
   * @param payload Optional scan initiation payload
   */
  private handleAllSpaceScanStart(payload?: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    if (payload) {
      this.ensureLastScanMetaFromPayload(payload);
    }

    const spaces = this.spaceDataManagementService.spaces();
    if (!Array.isArray(spaces) || spaces.length === 0) {
      return;
    }

    // Rebuild queue with all known space keys in their current display order
    this.spaceDataManagementService.queue.set(spaces.map((s) => s.key));

    // Reset status to PENDING to reflect a fresh multi-space scan
    for (const space of spaces) {
      this.spacesDashboardUtils.updateSpace(space.key, { status: 'PENDING' });
    }

    // Auto-select first space if none selected
    if (!this.uiStateService.selectedSpaceKey()) {
      this.uiStateService.selectedSpaceKey.set(spaces[0].key);
    }
  }

  /**
   * Handles individual space scan start event.
   *
   * Business Rules:
   * - Marks space as RUNNING
   * - Removes space from pending queue
   * - Initializes progress tracking
   * - Captures scanId for resume capability
   *
   * Progress Handling:
   * - Uses payload progress if provided
   * - Falls back to existing progress
   * - Defaults to 0% if no prior data
   *
   * UI Impact:
   * - Space shows RUNNING status
   * - Progress bar initialized
   * - Space removed from queue
   * - Active space marker set
   *
   * @param payload Space scan start payload with metadata
   */
  private handleStreamScanStart(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }

    // Ensure UI knows the current scanId early for resume capability
    this.ensureLastScanMetaFromPayload(payload);

    // Update active space markers
    this.uiStateService.activeSpaceKey.set(spaceKey);
    if (!this.uiStateService.selectedSpaceKey()) {
      this.uiStateService.selectedSpaceKey.set(spaceKey);
    }

    // Remove from queue
    const currentQueue = this.spaceDataManagementService.queue();
    this.spaceDataManagementService.queue.set(
      currentQueue.filter((queuedKey) => queuedKey !== spaceKey)
    );

    // Initialize progress
    const current = this.scanProgressService.getProgress()[spaceKey]?.percent;
    const percent = this.extractPercent(payload) ?? current ?? 0;
    const total = (payload as any).pagesTotal as number | undefined;
    const prevTotal = this.scanProgressService.getProgress()[spaceKey]?.total;
    this.updateProgress(spaceKey, { total: total ?? prevTotal, index: 0, percent });

    // Update scan history and UI status
    this.uiStateService.upsertScanHistory(spaceKey, 'running');
    this.spacesDashboardUtils.updateSpace(spaceKey, { status: 'RUNNING' });
  }

  /**
   * Handles page processing start event with progress update.
   *
   * Business Rules:
   * - Updates progress based on page index and total
   * - Calculates percentage if not provided in payload
   * - Preserves existing total if not in payload
   *
   * Progress Calculation:
   * - Uses payload percentage if provided
   * - Otherwise: (pageIndex / pagesTotal) * 100
   * - Rounds to nearest integer
   *
   * @param payload Page start payload with progress data
   */
  private handlePageStart(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }

    const currentProgress = this.scanProgressService.getProgress()[spaceKey] ?? {};
    const total = (payload as any).pagesTotal ?? currentProgress.total;
    const index = (payload as any).pageIndex ?? currentProgress.index;

    let percent = this.extractPercent(payload);
    if (percent == null && typeof total === 'number' && typeof index === 'number' && total > 0) {
      percent = Math.round((index / total) * 100);
    }

    this.updateProgress(spaceKey, { total, index, percent });
  }

  /**
   * Handles PII detection events for both pages and attachments.
   *
   * Business Rules:
   * - Adds PII item to storage with deduplication
   * - Updates space counts in real-time
   * - Updates progress if provided (except for completed scans)
   * - Handles missing spaceKey for attachments using activeSpaceKey
   *
   * Deduplication:
   * - By pageId + attachmentName combination
   * - Maintains max 400 items per space
   *
   * UI Impact:
   * - PII badges update immediately
   * - Last scan timestamp refreshed
   * - Progress bar advances
   *
   * @param payload Item event payload with PII data
   */
  private handleItemEvent(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    const incomingKey = coerceSpaceKey(payload);
    const looksLikeAttachment = isAttachmentPayload(payload);
    const spaceKey = incomingKey ?? (looksLikeAttachment ? this.uiStateService.activeSpaceKey() : null);

    if (!spaceKey) {
      return;
    }

    if (!incomingKey && looksLikeAttachment) {
      this.uiStateService.append('[DEBUG_LOG] attachmentItem missing spaceKey; using activeSpaceKey=' + spaceKey);
    }

    // Add item to storage - returns true if item was added (not a duplicate)
    const wasAdded = this.addPiiItemToSpace(spaceKey, payload);

    // Update progress if provided (skip for completed scans)
    const progressPercent = this.extractPercent(payload);
    if (progressPercent != null && payload.status !== 'COMPLETED') {
      this.updateProgress(spaceKey, { percent: progressPercent });
    }

    const timestamp = (payload as any).ts ?? new Date().toISOString();

    // Update severity counts in real-time only if item was actually added (not duplicate)
    if (wasAdded) {
      const currentCounts = this.spacesDashboardUtils.getSpaceCounts(spaceKey);

      // Use backend-provided severity counts from summary (already calculated per severity)
      // Backend provides Map<String, Integer> with keys: "high", "medium", "low"
      const summary = payload.nbOfDetectedPIIBySeverity as Record<string, number> | undefined;
      const deltaHigh = summary?.high ?? summary?.HIGH ?? 0;
      const deltaMedium = summary?.medium ?? summary?.MEDIUM ?? 0;
      const deltaLow = summary?.low ?? summary?.LOW ?? 0;
      const deltaTotal = deltaHigh + deltaMedium + deltaLow;

      const newCounts = {
        total: currentCounts.total + deltaTotal,
        high: currentCounts.high + deltaHigh,
        medium: currentCounts.medium + deltaMedium,
        low: currentCounts.low + deltaLow
      };

      this.spacesDashboardUtils.updateSpace(spaceKey, {
        lastScanTs: timestamp,
        status: 'RUNNING',
        counts: newCounts
      });
    } else {
      // Item was duplicate or had no entities - just update timestamp and status
      this.spacesDashboardUtils.updateSpace(spaceKey, {
        lastScanTs: timestamp,
        status: 'RUNNING'
      });
    }
  }

  /**
   * Handles scan error events.
   *
   * CRITICAL Business Rule:
   * - Errors are NON-FATAL - scan continues after errors
   * - Space remains in RUNNING status
   * - Error displayed as sticky toast notification
   * - Error logged but does not update scan history to 'failed'
   *
   * Error Handling:
   * - Extracts error message from 'message' field
   * - Detects error type for appropriate icon/severity
   * - Shows context: space, page, attachment if available
   * - Preserves scan progress and status
   *
   * UI Impact:
   * - Sticky error toast with full context
   * - Last scan timestamp updated
   * - Space status unchanged (remains RUNNING)
   * - Scan continues normally
   *
   * @param payload Error event payload with error details
   */
  private handleStreamError(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }

    // Extract error message from 'message' field (not 'errorMessage')
    const errorMessage = (payload as any)?.message ?? (payload as any)?.errorMessage ?? 'Erreur inconnue';
    const errorType = this.toastService.detectErrorType(errorMessage);

    // Display sticky error toast with full context
    this.toastService.showScanError({
      scanId: payload.scanId ?? '',
      spaceKey,
      pageId: payload.pageId == null ? undefined : String(payload.pageId),
      pageTitle: payload.pageTitle,
      attachmentName: (payload as any)?.attachmentName,
      errorMessage,
      errorType
    });

    // DO NOT mark space as FAILED - errors are non-fatal
    // Only update timestamp to reflect activity
    this.spacesDashboardUtils.updateSpace(spaceKey, {
      lastScanTs: new Date().toISOString()
    });

    // Log error without changing scan history status
    this.uiStateService.append(
      this.translocoService.translate('dashboard.logs.errorForSpace', {
        spaceKey,
        error: errorMessage
      })
    );
  }

  /**
   * Handles space scan completion event.
   *
   * Business Rules:
   * - Marks space as OK (successful completion)
   * - Sets progress to 100%
   * - Clears active space marker if this space was active
   *
   * UI Impact:
   * - Space shows OK status (green)
   * - Progress bar at 100%
   * - Last scan timestamp updated
   *
   * NOTE: Counts are NOT recalculated from items - backend counts are authoritative.
   * The dashboard will fetch final counts from backend after scan completion.
   *
   * @param payload Completion event payload
   */
  private handleStreamComplete(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }

    // Update scan history and progress
    this.uiStateService.upsertScanHistory(spaceKey, 'completed');
    this.updateProgress(spaceKey, { percent: 100 });

    // Mark as OK without recalculating counts (backend counts are authoritative)
    this.spacesDashboardUtils.updateSpace(spaceKey, {
      status: 'OK',
      lastScanTs: new Date().toISOString()
    });

    // Clear active marker if this was the active space
    if (this.uiStateService.activeSpaceKey() === spaceKey) {
      this.uiStateService.activeSpaceKey.set(null);
    }
  }

  /**
   * Adds a PII item to storage for a specific space.
   *
   * Business Purpose:
   * - Delegates to PiiItemsStorageService for storage and deduplication
   *
   * @param spaceKey Space identifier
   * @param payload Event payload with PII data (includes backend-calculated severity)
   * @returns true if item was added (not a duplicate), false otherwise
   */
  private addPiiItemToSpace(spaceKey: string, payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): boolean {
    // Delegate to storage service - it handles conversion, deduplication, and severity normalization
    return this.piiItemsStorageService.addPiiItemToSpace(spaceKey, payload);
  }

  /**
   * Captures scan metadata from SSE payload for resume capability.
   *
   * Business Purpose:
   * - Enables resume button after scan interruption
   * - Ensures scanId available before backend fetch completes
   * - Prevents resume UI delay on disconnection
   *
   * Logic:
   * - Extracts scanId from payload
   * - Only updates if different from current meta
   * - Creates minimal metadata object
   * - Delegates to space data management service
   *
   * @param payload Event payload potentially containing scanId
   */
  private ensureLastScanMetaFromPayload(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    try {
      const scanId = (payload as any)?.scanId as string | undefined;
      if (!scanId) {
        return;
      }

      const current = this.spaceDataManagementService.lastScanMeta();
      if (current?.scanId !== scanId) {
        const timestamp = (payload as any)?.ts ?? new Date().toISOString();
        const spacesCount = this.spaceDataManagementService.spaces().length;

        this.spaceDataManagementService.lastScanMeta.set({
          scanId,
          lastUpdated: timestamp,
          spacesCount
        });
      }
    } catch {
      // Ignore parsing errors
    }
  }

  /**
   * Extracts progress percentage from payload.
   *
   * @param payload Event payload
   * @returns Progress percentage (0-100) or undefined
   */
  private extractPercent(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): number | undefined {
    return this.scanProgressService.extractPercentFromPayload(payload);
  }

  /**
   * Updates scan progress for a space.
   *
   * @param spaceKey Space identifier
   * @param patch Progress updates (total, index, percent)
   */
  private updateProgress(
    spaceKey: string,
    patch: Partial<{ total: number; index: number; percent: number }>
  ): void {
    this.scanProgressService.updateProgress(spaceKey, patch);
  }
}
