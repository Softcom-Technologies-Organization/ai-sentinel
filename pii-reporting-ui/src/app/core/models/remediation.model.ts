/**
 * Types mirroring the PII remediation REST contract (/api/v1/pii/remediation).
 * The backend owns all aggregation logic; these types are pure data carriers.
 * The plaintext value (sensitiveValue) and its surrounding line (sensitiveContext) only cross
 * this API when the feature is enabled together with pii.reporting.allow-secret-reveal;
 * otherwise only maskedContext is sent.
 */

export interface RemediationScope {
  spaceKey: string;
  pageId?: string;
  attachmentName?: string;
}

export interface RemediationSelectionDto {
  scope: RemediationScope;
  piiTypes: string[];
  severities: string[];
  excludedFindingIds: string[];
  includedFindingIds: string[];
}

export type RemediationGroupBy = 'type' | 'severity';

export type RemediationStatusFilter = 'ALL' | 'PENDING' | 'HANDLED' | 'FALSE_POSITIVE';

export type FindingStatus =
  | 'PENDING'
  | 'REDACTED'
  | 'MANUALLY_HANDLED'
  | 'FALSE_POSITIVE'
  | 'PROPOSED_FALSE_POSITIVE';

/** LLM judge verdict once the confidence threshold has been honoured by the backend. */
export type JudgeVerdict = 'TRUE_POSITIVE' | 'FALSE_POSITIVE' | 'UNSURE';

export type FindingTargetStatus = 'FALSE_POSITIVE' | 'MANUALLY_HANDLED' | 'PENDING';

export type GroupMasterState = 'none' | 'partial' | 'all';

export type ObfuscationJobStatus =
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'INTERRUPTED'
  | 'FAILED';

export type RedactionOutcome =
  | 'REDACTED'
  | 'SKIPPED_STALE'
  | 'SKIPPED_VALUE_NOT_FOUND'
  | 'SKIPPED_ATTACHMENT'
  | 'FAILED';

export interface RemediationConfigDto {
  enabled: boolean;
}

export interface RemediationFindingsSearchRequest {
  scope: RemediationScope;
  groupBy: RemediationGroupBy;
  statusFilter: RemediationStatusFilter;
  searchText?: string;
  itemFilter?: string;
  page: number;
  pageSize: number;
  selection: RemediationSelectionDto;
}

export interface RemediationFindingDto {
  findingId: string;
  piiType: string;
  severity: string;
  detector: string;
  confidenceScore: number;
  maskedContext: string;
  sensitiveValue?: string;
  sensitiveContext?: string;
  occurrenceCount: number;
  pageId: string;
  pageTitle: string;
  attachmentName?: string;
  status: FindingStatus;
  selected: boolean;
  eligibleForRedaction: boolean;
  ineligibilityReason?: string;
  judgeVerdict?: JudgeVerdict;
  judgeConfidence?: number;
}

export interface RemediationGroupDto {
  key: string;
  label: string;
  severity?: string;
  total: number;
  occurrenceCount: number;
  selectedCount: number;
  masterState: GroupMasterState;
  findings: RemediationFindingDto[];
}

export interface RemediationTotalsDto {
  pending: number;
  handled: number;
  falsePositive: number;
  /** Judge rejections still awaiting a reviewer, apart from confirmed false positives. */
  proposedFalsePositive: number;
  total: number;
}

export interface RemediationFindingsSearchResponse {
  groups: RemediationGroupDto[];
  totals: RemediationTotalsDto;
  page: number;
  pageSize: number;
  totalElements: number;
  totalGroups: number;
  nonEligibleLegacyCount: number;
}

export interface ObfuscationPlanDto {
  totalFindings: number;
  bySeverity: Record<string, number>;
  pagesImpacted: number;
  falsePositivesReported: number;
  selectionChecksum: string;
  attachmentExclusions: number;
}

export interface CreateObfuscationJobRequest {
  selection: RemediationSelectionDto;
  selectionChecksum: string;
}

export interface CreateObfuscationJobResponse {
  jobId: string;
}

export interface ObfuscationJobOutcomeDto {
  findingId: string;
  piiType: string;
  outcome: RedactionOutcome;
  reason?: string;
}

export interface ObfuscationJobDto {
  jobId: string;
  status: ObfuscationJobStatus;
  processed: number;
  total: number;
  outcomes: ObfuscationJobOutcomeDto[];
}

export interface FindingStatusChange {
  findingId: string;
  targetStatus: FindingTargetStatus;
}

export interface ChangeFindingsStatusRequest {
  changes: FindingStatusChange[];
}

export interface SelectionStatusChangeRequest {
  selection: RemediationSelectionDto;
  targetStatus: FindingTargetStatus;
}

export interface RejectedStatusChange {
  findingId: string;
  reason: string;
}

export interface ChangeFindingsStatusResponse {
  applied: string[];
  rejected: RejectedStatusChange[];
}
