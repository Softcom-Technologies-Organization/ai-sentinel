import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  JudgeJobDto,
  JudgeLaunchRequest,
  JudgeLaunchResponse,
  JudgePlanDto,
  JudgeScopeDto,
  JudgeSpaceSummaryDto
} from '../models/judge.model';

/**
 * Pure HTTP client for the LLM judge API. One method per endpoint, no business logic.
 */
@Injectable({ providedIn: 'root' })
export class JudgeApiService {
  private readonly apiUrl = '/api/v1/pii/judge';
  private readonly http = inject(HttpClient);

  plan(scope: JudgeScopeDto): Observable<JudgePlanDto> {
    return this.http.post<JudgePlanDto>(`${this.apiUrl}/plan`, scope);
  }

  launch(request: JudgeLaunchRequest): Observable<JudgeLaunchResponse> {
    return this.http.post<JudgeLaunchResponse>(`${this.apiUrl}/jobs`, request);
  }

  getJob(jobId: string): Observable<JudgeJobDto> {
    return this.http.get<JudgeJobDto>(`${this.apiUrl}/jobs/${encodeURIComponent(jobId)}`);
  }

  cancel(jobId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/jobs/${encodeURIComponent(jobId)}/cancel`, null);
  }

  spaceSummaries(): Observable<JudgeSpaceSummaryDto[]> {
    return this.http.get<JudgeSpaceSummaryDto[]>(`${this.apiUrl}/spaces`);
  }
}
