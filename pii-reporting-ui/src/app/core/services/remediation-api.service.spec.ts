import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RemediationApiService } from './remediation-api.service';
import {
  ChangeFindingsStatusResponse,
  CreateObfuscationJobResponse,
  ObfuscationJobDto,
  ObfuscationPlanDto,
  RemediationFindingsSearchRequest,
  RemediationFindingsSearchResponse,
  RemediationSelectionDto
} from '../models/remediation.model';

const EMPTY_SELECTION: RemediationSelectionDto = {
  scope: { spaceKey: 'SPACE' },
  piiTypes: [],
  severities: [],
  excludedFindingIds: [],
  includedFindingIds: []
};

describe('RemediationApiService', () => {
  let service: RemediationApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RemediationApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('Should_GetConfig_When_ConfigRequested', () => {
    let enabled: boolean | undefined;
    service.getConfig().subscribe((config) => (enabled = config.enabled));

    const req = httpTesting.expectOne('/api/v1/pii/remediation/config');
    expect(req.request.method).toBe('GET');
    req.flush({ enabled: true });

    expect(enabled).toBe(true);
  });

  it('Should_PostSearchRequest_When_FindingsSearched', () => {
    const request: RemediationFindingsSearchRequest = {
      scope: { spaceKey: 'SPACE' },
      groupBy: 'type',
      statusFilter: 'PENDING',
      searchText: 'foo',
      page: 2,
      pageSize: 50,
      selection: EMPTY_SELECTION
    };
    const response: RemediationFindingsSearchResponse = {
      groups: [],
      totals: { pending: 0, handled: 0, falsePositive: 0, proposedFalsePositive: 0, total: 0 },
      page: 2,
      pageSize: 50,
      totalElements: 0,
      totalGroups: 0,
      nonEligibleLegacyCount: 0
    };

    let received: RemediationFindingsSearchResponse | undefined;
    service.searchFindings(request).subscribe((value) => (received = value));

    const req = httpTesting.expectOne('/api/v1/pii/remediation/findings/search');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(response);

    expect(received).toEqual(response);
  });

  it('Should_PostSelection_When_PlanRequested', () => {
    const plan: ObfuscationPlanDto = {
      totalFindings: 4,
      bySeverity: { high: 3, low: 1 },
      pagesImpacted: 2,
      falsePositivesReported: 1,
      selectionChecksum: 'abc123',
      attachmentExclusions: 0
    };

    let received: ObfuscationPlanDto | undefined;
    service.planObfuscation(EMPTY_SELECTION).subscribe((value) => (received = value));

    const req = httpTesting.expectOne('/api/v1/pii/remediation/plan');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(EMPTY_SELECTION);
    req.flush(plan);

    expect(received).toEqual(plan);
  });

  it('Should_PostSelectionWithChecksum_When_JobCreated', () => {
    let received: CreateObfuscationJobResponse | undefined;
    service
      .createJob({ selection: EMPTY_SELECTION, selectionChecksum: 'abc123' })
      .subscribe((value) => (received = value));

    const req = httpTesting.expectOne('/api/v1/pii/remediation/jobs');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ selection: EMPTY_SELECTION, selectionChecksum: 'abc123' });
    req.flush({ jobId: 'job-1' });

    expect(received).toEqual({ jobId: 'job-1' });
  });

  it('Should_GetJobById_When_JobPolled', () => {
    const job: ObfuscationJobDto = {
      jobId: 'job 1',
      status: 'RUNNING',
      processed: 1,
      total: 4,
      outcomes: [{ findingId: 'f1', piiType: 'EMAIL', outcome: 'REDACTED' }]
    };

    let received: ObfuscationJobDto | undefined;
    service.getJob('job 1').subscribe((value) => (received = value));

    const req = httpTesting.expectOne('/api/v1/pii/remediation/jobs/job%201');
    expect(req.request.method).toBe('GET');
    req.flush(job);

    expect(received).toEqual(job);
  });

  it('Should_PostStatusChanges_When_FindingStatusChanged', () => {
    const changes = [{ findingId: 'f1', targetStatus: 'FALSE_POSITIVE' as const }];
    const response: ChangeFindingsStatusResponse = { applied: ['f1'], rejected: [] };

    let received: ChangeFindingsStatusResponse | undefined;
    service.changeFindingsStatus({ changes }).subscribe((value) => (received = value));

    const req = httpTesting.expectOne('/api/v1/pii/remediation/findings/status');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ changes });
    req.flush(response);

    expect(received).toEqual(response);
  });

  it('Should_PostSelection_When_StatusChangedBySelection', () => {
    const request = { selection: EMPTY_SELECTION, targetStatus: 'MANUALLY_HANDLED' as const };
    const response: ChangeFindingsStatusResponse = { applied: ['f1', 'f2'], rejected: [] };

    let received: ChangeFindingsStatusResponse | undefined;
    service.changeFindingsStatusBySelection(request).subscribe((value) => (received = value));

    const req = httpTesting.expectOne('/api/v1/pii/remediation/findings/status/by-selection');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(response);

    expect(received).toEqual(response);
  });
});
