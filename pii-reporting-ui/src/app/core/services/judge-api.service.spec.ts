import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { JudgeApiService } from './judge-api.service';
import { JudgeJobDto, JudgeLaunchRequest } from '../models/judge.model';

describe('JudgeApiService', () => {
  let service: JudgeApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(JudgeApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('Should_PostScope_When_Planning', () => {
    let total: number | undefined;
    service.plan({ spaceKeys: ['AS'] }).subscribe((plan) => (total = plan.totalFindings));

    const req = httpTesting.expectOne('/api/v1/pii/judge/plan');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ spaceKeys: ['AS'] });
    req.flush({ totalFindings: 108, findingsBySpace: { AS: 108 } });

    expect(total).toBe(108);
  });

  it('Should_PostLaunchRequestAndReturnJobIds_When_Launching', () => {
    const request: JudgeLaunchRequest = {
      scope: { spaceKeys: ['AS'] },
      model: 'judge@q8',
      applyMode: 'PROPOSE',
      confidenceThreshold: 0.8
    };
    let jobIds: string[] = [];
    service.launch(request).subscribe((response) => (jobIds = response.jobIds));

    const req = httpTesting.expectOne('/api/v1/pii/judge/jobs');
    expect(req.request.body).toEqual(request);
    req.flush({ jobIds: ['j1'] });

    expect(jobIds).toEqual(['j1']);
  });

  it('Should_EncodeJobId_When_PollingAndCancelling', () => {
    let job: JudgeJobDto | undefined;
    service.getJob('a/b').subscribe((dto) => (job = dto));
    httpTesting.expectOne('/api/v1/pii/judge/jobs/a%2Fb').flush({ jobId: 'a/b', status: 'RUNNING' });
    expect(job?.status).toBe('RUNNING');

    service.cancel('a/b').subscribe();
    const cancel = httpTesting.expectOne('/api/v1/pii/judge/jobs/a%2Fb/cancel');
    expect(cancel.request.method).toBe('POST');
    cancel.flush(null);
  });

  it('Should_GetSpaceSummaries_When_Requested', () => {
    service.spaceSummaries().subscribe();

    expect(httpTesting.expectOne('/api/v1/pii/judge/spaces').request.method).toBe('GET');
  });
});
