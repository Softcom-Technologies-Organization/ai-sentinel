import {
  DetectedPersonallyIdentifiableInformation
} from './detected-personally-identifiable-information';
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
  summary?: Record<string, number>;  // Severity-based counts (high, medium, low) for badges
  piiTypeSummary?: Record<string, number>;  // PII type-based counts (EMAIL, CREDIT_CARD, etc.) for item details
  detectedPersonallyIdentifiableInformationList: DetectedPersonallyIdentifiableInformation[];
  maskedHtml?: string;
  // Attachment context when the item comes from an attachment scan
  attachmentName?: string;
  attachmentType?: string;
  attachmentUrl?: string;
}
