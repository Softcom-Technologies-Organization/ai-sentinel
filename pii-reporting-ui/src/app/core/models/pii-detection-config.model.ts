export type DetectorType = 'PRESIDIO' | 'REGEX' | 'MINISTRAL';

/**
 * PII Detection Configuration model matching backend DTO.
 */
export interface PiiDetectionConfig {
  presidioEnabled: boolean;
  regexEnabled: boolean;
  postfilterEnabled: boolean;
  ministralEnabled: boolean;
  ministralChunkSize: number;
  ministralOverlap: number;
  ministralConcurrency: number;
  ministralConcurrencyAuto: boolean;
  ministralConcurrencyTunedSignature?: string | null;
  defaultThreshold: number;
  lmStudioHost: string;
  lmStudioPort: number;
  updatedAt?: string;
  updatedBy?: string;
}

/**
 * Request DTO for updating PII detection configuration.
 */
export interface UpdatePiiDetectionConfigRequest {
  presidioEnabled: boolean;
  regexEnabled: boolean;
  postfilterEnabled: boolean;
  ministralEnabled: boolean;
  ministralChunkSize: number;
  ministralOverlap: number;
  ministralConcurrency: number;
  ministralConcurrencyAuto: boolean;
  ministralConcurrencyTunedSignature?: string | null;
  defaultThreshold: number;
  lmStudioHost: string;
  lmStudioPort: number;
}

/**
 * Status of the Ministral concurrency benchmark job.
 */
export interface ConcurrencyBenchStatus {
  status: string;
  progress: number;
  message: string | null;
  concurrency: number;
  tunedSignature: string | null;
  /** Highest concurrency level the benchmark measures (2..20). */
  maxConcurrency: number;
}

/**
 * PII Type Configuration model matching backend PiiTypeConfigResponseDto.
 */
export interface PiiTypeConfig {
  id: number;
  piiType: string;
  detector: DetectorType;
  enabled: boolean;
  threshold: number;
  category: string;
  countryCode?: string;
  detectorLabel?: string;
  severity?: string;
  updatedAt?: string;
  updatedBy?: string;
}

/**
 * Request DTO for updating a single PII type configuration.
 */
export interface UpdatePiiTypeConfigRequest {
  piiType: string;
  detector: DetectorType;
  enabled: boolean;
  threshold: number;
}

/**
 * Request DTO for bulk updating PII type configurations.
 */
export interface BulkUpdatePiiTypeConfigRequest {
  updates: UpdatePiiTypeConfigRequest[];
}

/**
 * Grouped PII types by detector and category for UI display.
 */
export interface GroupedPiiTypes {
  detector: 'PRESIDIO' | 'MINISTRAL';
  categories: CategoryGroup[];
}

/**
 * Category group containing PII types.
 */
export interface CategoryGroup {
  category: string;
  types: PiiTypeConfig[];
}
