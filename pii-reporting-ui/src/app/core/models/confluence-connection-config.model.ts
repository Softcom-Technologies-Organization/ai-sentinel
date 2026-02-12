export interface ConfluenceConnectionConfig {
  baseUrl: string;
  username: string;
  apiTokenMasked: string;
  connectTimeout: number;
  readTimeout: number;
  maxRetries: number;
  pagesLimit: number;
  maxPages: number;
  updatedAt?: string;
  updatedBy?: string;
  configured: boolean;
}

export interface UpdateConfluenceConnectionConfigRequest {
  baseUrl: string;
  username: string;
  apiToken: string;
  connectTimeout: number;
  readTimeout: number;
  maxRetries: number;
  pagesLimit: number;
  maxPages: number;
}

export interface TestConnectionRequest {
  baseUrl: string;
  username: string;
  apiToken: string;
}

export interface TestConnectionResponse {
  success: boolean;
  message?: string;
}
