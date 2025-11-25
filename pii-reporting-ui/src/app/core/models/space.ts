import { SeverityCounts } from './severity-counts';

export interface Space {
  status?: 'FAILED' | 'RUNNING' | 'OK' | 'PENDING' | 'INTERRUPTED' | 'PAUSED';
  key: string;
  name?: string;
  url?: string;
  counts?: SeverityCounts;
}
