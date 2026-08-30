
// --- Stream events (SSE) ---
export type StreamEventType =
  | 'multiStart'
  | 'start'
  | 'pageStart'
  | 'item'
  | 'attachmentItem'
  | 'pageComplete'
  | 'scanError'
  | 'complete'
  | 'multiComplete'
  | 'keepalive';

export interface ConfluenceContentPersonallyIdentifiableInformationScanResult {
  scanId?: string;
  spaceKey?: string;
  pageId?: string | number;
  pageTitle?: string;
  pageUrl?: string;
  emittedAt?: string;
  isFinal?: boolean;
  pagesTotal?: number;
  pageIndex?: number;
  detectedPIIs?: Array<{
    piiTypeLabel?: string;
    piiType?: string;
    sensitiveValue?: string;
    sensitiveContext?: string;
    maskedContext?: string;
    confidence?: number;
    source?: string;
  }>;
  detectedPiiCountBySeverity?: Record<string, number>;  // Severity-based counts (high, medium, low) for badges
  detectedPiiCountByType?: Record<string, number>;  // PII type-based counts (EMAIL, CREDIT_CARD, etc.) for item details
  maskedContent?: string;
  // Attachment context for 'attachment_item' events
  attachmentName?: string;
  attachmentType?: string;
  attachmentUrl?: string;
  // Diagnostic wording from the backend, for the event log. What the operator reads
  // comes from errorKey, translated here.
  message?: string;
  // Translation key naming the error, e.g. 'error.scan.detector_model_not_loaded'
  errorKey?: string;
  // Technical values the translated sentence interpolates (endpoint, model, status...)
  errorParams?: Record<string, string>;
  // Scan status from backend (e.g., 'RUNNING', 'COMPLETED', 'FAILED')
  status?: string;
  // Pre-calculated severity from backend (HIGH/MEDIUM/LOW)
  severity?: string;
  // Event type discriminator used when items are loaded from last scan
  eventType?: StreamEventType;
}