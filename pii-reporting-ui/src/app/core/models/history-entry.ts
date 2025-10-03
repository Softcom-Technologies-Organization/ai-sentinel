export interface HistoryEntry {
  spaceKey: string;
  status: 'running' | 'completed' | 'failed';
}
