export type ConfluenceDeploymentType = 'CLOUD' | 'DATA_CENTER';

export interface ConfluenceConnectionConfig {
  baseUrl: string;
  username: string;
  connectTimeout: number;
  readTimeout: number;
  maxRetries: number;
  pagesLimit: number;
  deploymentType: ConfluenceDeploymentType;
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
  deploymentType: ConfluenceDeploymentType;
}

export interface TestConnectionRequest {
  baseUrl: string;
  username: string;
  apiToken: string;
  deploymentType: ConfluenceDeploymentType;
}

export interface TestConnectionResponse {
  success: boolean;
  message?: string;
}
