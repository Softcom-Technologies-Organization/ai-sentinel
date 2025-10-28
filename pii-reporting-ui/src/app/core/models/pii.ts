
export interface PiiEntity {
  startPosition: number;
  endPosition: number;
  piiTypeLabel: string; // Business label to display (e.g., "Email")
  piiType?: string; // Technical type, if any
  confidence?: number; // Confidence score 0..1
  sensitiveValue?: string; // sensitive value (revealed on demand)
  sensitiveContext?: string; // Real context with actual PII values (encrypted, for reveal)
  maskedContext?: string; // Masked context with tokens (clear text, for immediate display)
}
