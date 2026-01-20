/**
 * Represents information about updates to a Confluence space since its last scan.
 *
 * Business purpose: Enables the dashboard to display visual indicators for spaces
 * that may need re-scanning due to recent updates.
 */
export interface SpaceUpdateInfo {
  spaceKey: string;
  spaceName: string;
  hasBeenUpdated: boolean;
  lastModified: string | null;
  lastScanDate: string | null;
  updatedPages: string[] | null;
  updatedAttachments: string[] | null;
}
