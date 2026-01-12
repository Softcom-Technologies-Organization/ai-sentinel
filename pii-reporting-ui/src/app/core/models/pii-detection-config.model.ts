/**
 * PII Detection Configuration model matching backend DTO.
 */
export interface PiiDetectionConfig {
  glinerEnabled: boolean;
  presidioEnabled: boolean;
  regexEnabled: boolean;
  defaultThreshold: number;
  nbOfLabelByPass: number;
  updatedAt?: string;
  updatedBy?: string;
}

/**
 * Request DTO for updating PII detection configuration.
 */
export interface UpdatePiiDetectionConfigRequest {
  glinerEnabled: boolean;
  presidioEnabled: boolean;
  regexEnabled: boolean;
  defaultThreshold: number;
  nbOfLabelByPass: number;
}

/**
 * PII Type Configuration model matching backend PiiTypeConfigResponseDto.
 */
export interface PiiTypeConfig {
  id: number;
  piiType: string;
  detector: 'GLINER' | 'PRESIDIO' | 'REGEX';
  enabled: boolean;
  threshold: number;
  displayName: string;
  description: string;
  category: string;
  countryCode?: string;
  updatedAt?: string;
  updatedBy?: string;
}

/**
 * Request DTO for updating a single PII type configuration.
 */
export interface UpdatePiiTypeConfigRequest {
  piiType: string;
  detector: 'GLINER' | 'PRESIDIO' | 'REGEX';
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
  detector: 'GLINER' | 'PRESIDIO';
  categories: CategoryGroup[];
}

/**
 * Category group containing PII types.
 */
export interface CategoryGroup {
  category: string;
  types: PiiTypeConfig[];
}