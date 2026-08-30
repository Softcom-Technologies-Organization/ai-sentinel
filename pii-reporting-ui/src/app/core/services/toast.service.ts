import { inject, Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';
import { TranslocoService } from '@jsverse/transloco';
import { toTranslocoKey } from './error-notification.service';

export interface ErrorToastData {
  scanId: string;
  spaceKey: string;
  pageId?: string;
  pageTitle?: string;
  attachmentName?: string;
  /** Translation key the backend sent, e.g. 'error.scan.detector_model_not_loaded'. */
  errorKey?: string;
  /** Technical values the translated sentence interpolates. */
  errorParams?: Record<string, string>;
}

/** Key used when the backend sent an error event without naming which error it was. */
const UNKNOWN_ERROR_KEY = 'error.scan.unexpected';

/**
 * Keys the backend uses to refuse a scan before it starts, because a detector the
 * operator enabled cannot run. They all share one consequence for the dashboard:
 * nothing was scanned, so the optimistic "scan starting" state must be released.
 */
const DETECTOR_REFUSAL_PREFIX = 'error.scan.detector_';

/** Keys meaning the scan stopped on an outage and waits on the Resume button. */
const SCAN_PAUSED_KEYS: readonly string[] = ['error.scan.paused_detector', 'error.scan.paused_network'];

@Injectable()
export class ToastService {
  private readonly translocoService = inject(TranslocoService);

  constructor(readonly messageService: MessageService) {}

  showScanError(data: ErrorToastData): void {
    this.messageService.add({
      severity: 'error',
      summary: this.translate(data, 'title'),
      detail: this.formatDetail(data),
      sticky: true,
      life: undefined,
      key: 'scan-errors',
      contentStyleClass: 'scan-error-toast'
    });
  }

  clearScanErrors(): void {
    this.messageService.clear('scan-errors');
  }

  /**
   * Whether this error stopped the scan, as opposed to failing one item.
   *
   * <p>Drives a distinct notification: the operator has something to do (restore the
   * detector or the network, then press Resume) rather than an item to note.
   */
  isScanPaused(errorKey: string | undefined): boolean {
    return errorKey !== undefined && SCAN_PAUSED_KEYS.includes(errorKey);
  }

  /**
   * Whether this error refused the scan before a single page was opened.
   *
   * <p>Told apart from a paused scan because there is nothing to resume: the
   * dashboard drops its optimistic "starting" state instead of offering Resume.
   */
  isScanRefused(errorKey: string | undefined): boolean {
    return errorKey !== undefined && errorKey.startsWith(DETECTOR_REFUSAL_PREFIX);
  }

  /**
   * Renders one side of an error message in the operator's language.
   *
   * <p>Falls back to a generic wording rather than showing a raw key: a backend
   * newer than this dashboard must still produce a readable notification.
   */
  private translate(data: ErrorToastData, part: 'title' | 'detail'): string {
    const key = `${toTranslocoKey(data.errorKey ?? UNKNOWN_ERROR_KEY)}.${part}`;
    const translated = this.translocoService.translate(key, data.errorParams ?? {});
    return translated === key
      ? this.translocoService.translate(`${toTranslocoKey(UNKNOWN_ERROR_KEY)}.${part}`, data.errorParams ?? {})
      : translated;
  }

  /**
   * The detail line, plus what to do about it when the scan can be resumed.
   *
   * <p>A scan-wide failure names no page: it is neither caused by nor limited to the
   * one being analysed when it struck. An item failure adds where it happened.
   */
  private formatDetail(data: ErrorToastData): string {
    const detail = this.translate(data, 'detail');

    if (this.isScanPaused(data.errorKey)) {
      return `${detail}\n${this.translocoService.translate('errors.scan.resumeHint')}`;
    }
    if (this.isScanRefused(data.errorKey)) {
      return detail;
    }

    const location = [
      data.spaceKey ? this.translocoService.translate('errors.scan.location.space', { space: data.spaceKey }) : null,
      this.formatItemLocation(data)
    ].filter(Boolean);

    return location.length === 0 ? detail : `${detail}\n${location.join('\n')}`;
  }

  private formatItemLocation(data: ErrorToastData): string | null {
    if (data.attachmentName) {
      return this.translocoService.translate('errors.scan.location.attachment', { attachment: data.attachmentName });
    }
    if (data.pageTitle) {
      return this.translocoService.translate('errors.scan.location.page', { page: data.pageTitle });
    }
    if (data.pageId) {
      return this.translocoService.translate('errors.scan.location.pageId', { pageId: data.pageId });
    }
    return null;
  }
}
