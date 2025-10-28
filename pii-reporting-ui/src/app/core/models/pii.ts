
export interface PiiEntity {
  piiTypeLabel: string; // Business label to display (e.g., "Email")
  piiType?: string; // Technical type, if any
  detectedValue?: string; // Raw detected value (revealed on demand)
  context?: string; // Context of the detected value
  confidence?: number; // Confidence score 0..1
}
