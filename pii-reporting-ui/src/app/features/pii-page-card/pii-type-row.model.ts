import { DetectorSource } from '../../core/models/detected-personally-identifiable-information';
import { ValuePart } from '../../shared/pii-value-display/pii-value-display.service';

export interface PiiEntityRow {
  typeLabel: string;
  value: string;
  valueParts: ValuePart[];
  isRevealed: boolean;
  confidence: number;
  confidenceAvailable: boolean;
  detector: DetectorSource;
}
