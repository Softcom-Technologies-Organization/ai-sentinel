import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ConcurrencyBenchStatus,
  GroupedPiiTypes,
  PiiDetectionConfig,
  PiiTypeConfig,
  UpdatePiiDetectionConfigRequest,
  UpdatePiiTypeConfigRequest
} from '../models/pii-detection-config.model';

/**
 * Service for managing PII detection configuration.
 */
@Injectable({providedIn: 'root'})
export class PiiDetectionConfigService {
  private readonly apiUrl = '/api/v1/pii-detection/config';
  private readonly typesApiUrl = '/api/v1/pii-detection/pii-types';
  private readonly benchApiUrl = '/api/v1/pii-detection/concurrency-benchmark';

  constructor(private readonly http: HttpClient) {
  }

  /**
   * Get current PII detection configuration.
   */
  getConfig(): Observable<PiiDetectionConfig> {
    return this.http.get<PiiDetectionConfig>(this.apiUrl);
  }

  /**
   * Update PII detection configuration.
   */
  updateConfig(request: UpdatePiiDetectionConfigRequest): Observable<PiiDetectionConfig> {
    return this.http.put<PiiDetectionConfig>(this.apiUrl, request);
  }

  /**
   * Start the Ministral concurrency benchmark job (no restart required).
   */
  runConcurrencyBenchmark(): Observable<void> {
    return this.http.post<void>(`${this.benchApiUrl}/run`, null);
  }

  /**
   * Get the current status of the Ministral concurrency benchmark job.
   */
  getConcurrencyBenchStatus(): Observable<ConcurrencyBenchStatus> {
    return this.http.get<ConcurrencyBenchStatus>(`${this.benchApiUrl}/status`);
  }

  /**
   * Get all PII type configurations.
   */
  getAllPiiTypeConfigs(): Observable<PiiTypeConfig[]> {
    return this.http.get<PiiTypeConfig[]>(this.typesApiUrl);
  }

  /**
   * Get PII type configurations for a specific detector.
   */
  getPiiTypeConfigsByDetector(detector: 'PRESIDIO' | 'REGEX'): Observable<PiiTypeConfig[]> {
    return this.http.get<PiiTypeConfig[]>(`${this.typesApiUrl}/${detector}`);
  }

  /**
   * Get PII type configurations grouped by category.
   */
  getPiiTypeConfigsGrouped(): Observable<Record<string, Record<string, PiiTypeConfig[]>>> {
    return this.http.get<Record<string, Record<string, PiiTypeConfig[]>>>(`${this.typesApiUrl}/grouped/by-category`);
  }

  /**
   * Update a single PII type configuration.
   */
  updatePiiTypeConfig(detector: string, piiType: string, request: UpdatePiiTypeConfigRequest): Observable<PiiTypeConfig> {
    return this.http.put<PiiTypeConfig>(`${this.typesApiUrl}/${detector}/${piiType}`, request);
  }

  /**
   * Bulk update multiple PII type configurations.
   */
  bulkUpdatePiiTypeConfigs(updates: UpdatePiiTypeConfigRequest[]): Observable<PiiTypeConfig[]> {
    return this.http.put<PiiTypeConfig[]>(`${this.typesApiUrl}/bulk`, updates);
  }

  /**
   * Get PII types grouped by detector and category for UI display.
   * Backend already returns the UI-friendly structure.
   */
  getPiiTypesGroupedForUI(): Observable<GroupedPiiTypes[]> {
    return this.http.get<GroupedPiiTypes[]>(`${this.typesApiUrl}/grouped`);
  }

}
