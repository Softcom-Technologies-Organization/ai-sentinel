
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

export interface RawStreamPayload {
  scanId?: string;
  spaceKey?: string;
  pageId?: string | number;
  pageTitle?: string;
  pageUrl?: string;
  emittedAt?: string;
  isFinal?: boolean;
  pagesTotal?: number;
  pageIndex?: number;
  detectedEntities?: Array<{
    piiTypeLabel?: string;
    piiType?: string;
    sensitiveValue?: string;
    sensitiveContext?: string;
    maskedContext?: string;
    confidence?: number;
  }>;
  summary?: Record<string, number>;
  maskedContent?: string;
  // Attachment context for 'attachment_item' events
  attachmentName?: string;
  attachmentType?: string;
  attachmentUrl?: string;
}
