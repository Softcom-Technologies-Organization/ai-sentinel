export type SourceStatus = 'active' | 'configured' | 'not_configured';

export interface DataSource {
  id: string;
  labelKey: string;
  icon: string;
  status: SourceStatus;
}

export const DATA_SOURCES: DataSource[] = [
  { id: 'confluence',  labelKey: 'sources.confluence',  icon: 'pi pi-globe',    status: 'active' },
  { id: 'sharepoint',  labelKey: 'sources.sharepoint',  icon: 'pi pi-microsoft', status: 'not_configured' },
  { id: 'database',    labelKey: 'sources.database',    icon: 'pi pi-database', status: 'not_configured' },
];
