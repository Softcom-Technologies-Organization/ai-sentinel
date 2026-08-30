import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { ToastService } from '../../../core/services/toast.service';
import { ConfluenceContentPersonallyIdentifiableInformationScanResult } from '../../../core/models/stream-event-type';
import { coerceSpaceKey, formatEventLog, isAttachmentPayload, StreamEventType } from '../spaces-dashboard-stream.utils';
import { PiiItemsStorageService } from './pii-items-storage.service';
import { DashboardUiStateService } from './dashboard-ui-state.service';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';

/**
 * Service responsible for handling and routing Server-Sent Events (SSE) from scan operations.
 *
 * Business Purpose:
 * - Routes incoming SSE events to appropriate handlers based on event type
 * - Handles only PII item events (item, attachmentItem) and errors (scanError)
 * - Status events (start, complete, multiStart, multiComplete, pageStart) are now
 *   handled by ScanStatusPollingService via REST polling
 *
 * Key Business Rules:
 * - Error events are non-fatal - scan continues after errors
 * - Items are deduplicated by pageId + attachmentName
 * - Maximum 400 PII items kept per space
 */
@Injectable({
  providedIn: 'root'
})
export class SseEventHandlerService {
  private readonly translocoService = inject(TranslocoService);
  private readonly toastService = inject(ToastService);
  private readonly piiItemsStorageService = inject(PiiItemsStorageService);
  private readonly uiStateService = inject(DashboardUiStateService);
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);

  /**
   * Routes an SSE event to the appropriate handler based on event type.
   * Only handles item and error events — status events are handled by polling.
   */
  routeStreamEvent(type: StreamEventType, payload?: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    this.uiStateService.append(formatEventLog(type, JSON.stringify(payload ?? {})));

    if (!payload) return;

    switch (type) {
      case 'item':
      case 'attachmentItem':
        this.handleItemEvent(payload);
        break;
      case 'scanError':
        this.handleStreamError(payload);
        break;
      default:
        // Status events (start, complete, multiStart, multiComplete, pageStart)
        // are now handled by ScanStatusPollingService via REST polling.
        break;
    }
  }

  /**
   * Handles PII detection events for both pages and attachments.
   * Adds item to storage for live card display — deduplication handled by storage service.
   * NOTE: Status and counts are now managed by polling.
   */
  private handleItemEvent(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    const incomingKey = coerceSpaceKey(payload);
    const looksLikeAttachment = isAttachmentPayload(payload);
    const spaceKey = incomingKey ?? (looksLikeAttachment ? this.uiStateService.activeSpaceKey() : null);

    if (!spaceKey) return;

    if (!incomingKey && looksLikeAttachment) {
      this.uiStateService.append('[DEBUG_LOG] attachmentItem missing spaceKey; using activeSpaceKey=' + spaceKey);
    }

    // Add item to storage for live card display — deduplication handled by storage service
    this.piiItemsStorageService.addPiiItemToSpace(spaceKey, payload);

    // NOTE: Status and counts are now managed by polling.
    // Items are added to storage for live card display only.
  }

  /**
   * Handles scan error events.
   *
   * CRITICAL Business Rule:
   * - Errors are NON-FATAL - scan continues after errors
   * - Space remains in RUNNING status
   * - Error displayed as sticky toast notification
   *
   * A detector-availability refusal is scan-wide rather than space-bound, so it is
   * notified even without a space key — dropping it would put the operator back in
   * front of a scan that produces nothing with no explanation.
   */
  private handleStreamError(payload: ConfluenceContentPersonallyIdentifiableInformationScanResult): void {
    const errorKey = payload.errorKey;
    const errorParams = payload.errorParams;
    // Diagnostic line for the event log, which is a technical trail: the backend's own
    // wording is the most useful thing there, and the key identifies it when absent.
    const errorMessage = payload.message ?? payload.errorKey
      ?? this.translocoService.translate<string>('errors.unknownError');

    // An outage stopped the whole scan, so it is reported once with its cause instead of
    // being attributed to the page that happened to hit it first. The paused state itself
    // comes from status polling, which turns the Resume button back on.
    if (this.toastService.isScanPaused(errorKey)) {
      this.notifyScanWide(payload, errorMessage);
      return;
    }

    const spaceKey = coerceSpaceKey(payload) ?? this.uiStateService.activeSpaceKey();
    if (!spaceKey) {
      if (this.toastService.isScanRefused(errorKey)) {
        this.notifyScanWide(payload, errorMessage);
      }
      return;
    }

    this.toastService.showScanError({
      scanId: payload.scanId ?? '',
      spaceKey,
      pageId: payload.pageId == null ? undefined : String(payload.pageId),
      pageTitle: payload.pageTitle,
      attachmentName: (payload as Record<string, unknown>)?.['attachmentName'] as string | undefined,
      errorKey,
      errorParams
    });

    // DO NOT mark space as FAILED - errors are non-fatal
    // Only update timestamp to reflect activity
    this.spacesDashboardUtils.updateSpace(spaceKey, {
      lastScanTs: new Date().toISOString()
    });

    this.uiStateService.append(
      this.translocoService.translate('dashboard.logs.errorForSpace', {
        spaceKey,
        error: errorMessage
      })
    );
  }

  /**
   * Notifies a failure that stopped or refused the whole run, once.
   *
   * <p>Reported without a space key on purpose: the cause is the detector or the network,
   * not the space being scanned when it struck, and one notification per remaining item
   * would bury the single thing the operator has to act on.
   */
  private notifyScanWide(
    payload: ConfluenceContentPersonallyIdentifiableInformationScanResult,
    errorMessage: string
  ): void {
    this.toastService.showScanError({
      scanId: payload.scanId ?? '',
      spaceKey: '',
      errorKey: payload.errorKey,
      errorParams: payload.errorParams
    });
    this.uiStateService.append(errorMessage);
  }
}
