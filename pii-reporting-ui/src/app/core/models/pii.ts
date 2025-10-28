
export interface PiiEntity {
  startPosition: number;
  endPosition: number;
  piiTypeLabel: string; // Business label to display (e.g., "Email")
  piiType?: string; // Technical type, if any
  detectedValue?: string; // Raw detected value (revealed on demand)
  context?: string; // Real context with actual PII values (encrypted, for reveal)
  maskedContext?: string; // Masked context with tokens (clear text, for immediate display)
  confidence?: number; // Confidence score 0..1
}
