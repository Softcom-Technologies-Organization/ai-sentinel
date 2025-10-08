/**
 * Centralized test ID constants for E2E testing.
 * These constants are shared between Angular components and Playwright tests
 * to prevent typos and make refactoring easier.
 */
export const TestIds = {
  dashboard: {
    table: 'spaces-table',
    spaceRow: 'space-row',
    spaceName: 'space-name',
    spaceStatus: 'space-status',
    buttons: {
      startScan: 'btn-start-scan',
      pauseScan: 'btn-pause-scan',
      resumeScan: 'btn-resume-scan'
    },
    headers: {
      space: 'header-space',
      status: 'header-status',
      progress: 'header-progress',
      lastScan: 'header-last-scan',
      pii: 'header-pii',
      actions: 'header-actions'
    },
    badges: {
      total: 'pii-badge-total',
      high: 'pii-badge-high',
      medium: 'pii-badge-medium',
      low: 'pii-badge-low'
    }
  }
} as const;

// Type exports for type safety
export type TestIdKey = typeof TestIds;
