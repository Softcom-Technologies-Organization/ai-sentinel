
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
  detectedPIIList?: Array<{
    piiTypeLabel?: string;
    piiType?: string;
    sensitiveValue?: string;
    sensitiveContext?: string;
    maskedContext?: string;
    confidence?: number;
    source?: string;
  }>;
  nbOfDetectedPIIBySeverity?: Record<string, number>;  // Severity-based counts (high, medium, low) for badges
  nbOfDetectedPIIByType?: Record<string, number>;  // PII type-based counts (EMAIL, CREDIT_CARD, etc.) for item details
  maskedContent?: string;
  // Attachment context for 'attachment_item' events
  attachmentName?: string;
  attachmentType?: string;
  attachmentUrl?: string;
  // Scan status from backend (e.g., 'RUNNING', 'COMPLETED', 'FAILED')
  status?: string;
  // Pre-calculated severity from backend (HIGH/MEDIUM/LOW)
  severity?: string;
}