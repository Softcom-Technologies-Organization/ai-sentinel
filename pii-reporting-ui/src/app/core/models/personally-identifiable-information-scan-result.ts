import {DetectedPersonallyIdentifiableInformation} from './detected-personally-identifiable-information';
import {Severity} from './severity';

export interface PersonallyIdentifiableInformationScanResult {
  scanId: string;
  spaceKey: string;
  pageId: string;
  pageTitle?: string;
  pageUrl?: string;
  emittedAt?: string;
  isFinal: boolean;
  severity: Severity;
  summary?: Record<string, number>;
  detectedPersonallyIdentifiableInfo: DetectedPersonallyIdentifiableInformation[];
  maskedHtml?: string;
  // Attachment context when the item comes from an attachment scan
  attachmentName?: string;
  attachmentType?: string;
  attachmentUrl?: string;
}
