/**
 * PII Detection Configuration model matching backend DTO.
 */
export interface PiiDetectionConfig {
  glinerEnabled: boolean;
  presidioEnabled: boolean;
  regexEnabled: boolean;
  defaultThreshold: number;
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
}
