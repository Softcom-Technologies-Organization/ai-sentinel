import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {PiiDetectionConfig, UpdatePiiDetectionConfigRequest} from '../models/pii-detection-config.model';

/**
 * Service for managing PII detection configuration.
 */
@Injectable({providedIn: 'root'})
export class PiiDetectionConfigService {
  private readonly apiUrl = '/api/v1/pii-detection/config';

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
}
