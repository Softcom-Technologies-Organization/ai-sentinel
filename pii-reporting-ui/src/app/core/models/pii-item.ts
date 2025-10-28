import {PiiEntity} from './pii';
import {Severity} from './severity';

export interface PiiItem {
  pageId: string;
  pageTitle?: string;
  pageUrl?: string;
  emittedAt?: string;
  isFinal: boolean;
  severity: Severity;
  summary?: Record<string, number>;
  detectedEntities: PiiEntity[];
  maskedHtml?: string;
  // Attachment context when the item comes from an attachment scan
  attachmentName?: string;
  attachmentType?: string;
  attachmentUrl?: string;
}
