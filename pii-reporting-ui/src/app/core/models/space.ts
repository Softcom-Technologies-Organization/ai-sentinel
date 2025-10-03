
export interface Space {
  status?: 'FAILED' | 'RUNNING' | 'OK' | 'PENDING' | 'INTERRUPTED';
  key: string;
  name?: string;
  url?: string;
}
