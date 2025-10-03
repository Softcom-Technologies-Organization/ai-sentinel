
// --- Stream events (SSE) ---
export type StreamEventType =
  | 'multiStart'
  | 'start'
  | 'pageStart'
  | 'item'
  | 'attachmentItem'
  | 'pageComplete'
  | 'error'
  | 'complete'
  | 'multiComplete'
  | 'keepalive';

export interface RawStreamPayload {
  spaceKey?: string;
  pageId?: string | number;
  pageTitle?: string;
  pageUrl?: string;
  emittedAt?: string;
  isFinal?: boolean;
  pagesTotal?: number;
  pageIndex?: number;
  entities?: Array<{ typeLabel?: string; type?: string; text?: string; score?: number }>;
  summary?: Record<string, number>;
  maskedContent?: string;
  // Attachment context for 'attachment_item' events
  attachmentName?: string;
  attachmentType?: string;
  attachmentUrl?: string;
}
