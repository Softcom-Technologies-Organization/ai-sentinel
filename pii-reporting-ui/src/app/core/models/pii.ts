
export interface PiiEntity {
  label: string; // Business label to display (e.g., "Email")
  type?: string; // Technical type, if any
  text?: string; // Raw detected value (revealed on demand)
  score?: number; // Confidence score 0..1
}
