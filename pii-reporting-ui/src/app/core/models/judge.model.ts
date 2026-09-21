import { RemediationSelectionDto } from './remediation.model';

/**
 * Types mirroring the LLM judge REST contract (/api/v1/pii/judge).
 * The backend resolves scopes into findings and owns every counter; these types carry data only.
 */

export type JudgeApplyMode = 'PROPOSE' | 'AUTO_APPLY';

export type JudgeJobStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED';

/** Whole spaces (dashboard) or one selection inside a space (remediation view). */
export interface JudgeScopeDto {
  spaceKeys?: string[];
  selection?: RemediationSelectionDto;
}

export interface JudgeLaunchRequest {
  scope: JudgeScopeDto;
  /** LM Studio model identifier; null = the model configured for detection. */
  model: string | null;
  applyMode: JudgeApplyMode;
  confidenceThreshold: number;
}

export interface JudgeLaunchResponse {
  jobIds: string[];
}

export interface JudgePlanDto {
  totalFindings: number;
  findingsBySpace: Record<string, number>;
}

export interface JudgeJobDto {
  jobId: string;
  spaceKey: string;
  status: JudgeJobStatus;
  model: string | null;
  applyMode: JudgeApplyMode;
  confidenceThreshold: number;
  processed: number;
  total: number;
  falsePositives: number;
  errors: number;
}

export interface JudgeSpaceSummaryDto {
  spaceKey: string;
  judgedCount: number;
  proposedFalsePositives: number;
  activeJob?: JudgeJobDto | null;
}
