import { DetectorSource } from '../../core/models/detected-personally-identifiable-information';

export interface ValuePart {
  text: string;
  isBadge: boolean;
  isHighlighted?: boolean;
}

export interface PiiEntityRow {
  typeLabel: string;
  value: string;
  valueParts: ValuePart[];
  isRevealed: boolean;
  confidence: number;
  detector: DetectorSource;
}
