import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ConfluenceConnectionConfig,
  UpdateConfluenceConnectionConfigRequest,
  TestConnectionRequest,
  TestConnectionResponse
} from '../models/confluence-connection-config.model';

/**
 * Service for managing Confluence connection configuration.
 */
@Injectable({providedIn: 'root'})
export class ConfluenceConnectionConfigService {
  private readonly apiUrl = '/api/v1/confluence/connection-config';

  constructor(private readonly http: HttpClient) {
  }

  /**
   * Get current Confluence connection configuration.
   */
  getConfig(): Observable<ConfluenceConnectionConfig> {
    return this.http.get<ConfluenceConnectionConfig>(this.apiUrl);
  }

  /**
   * Update Confluence connection configuration.
   */
  updateConfig(request: UpdateConfluenceConnectionConfigRequest): Observable<ConfluenceConnectionConfig> {
    return this.http.put<ConfluenceConnectionConfig>(this.apiUrl, request);
  }

  /**
   * Test the Confluence connection with provided credentials.
   */
  testConnection(request: TestConnectionRequest): Observable<TestConnectionResponse> {
    return this.http.post<TestConnectionResponse>(`${this.apiUrl}/test`, request);
  }
}
