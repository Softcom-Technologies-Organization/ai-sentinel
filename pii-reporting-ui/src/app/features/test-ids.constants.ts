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
    expandButton: 'expand-button',
    buttons: {
      startScan: 'btn-start-scan',
      pauseScan: 'btn-pause-scan',
      resumeScan: 'btn-resume-scan',
      purgeData: 'btn-purge-data'
    },
    headers: {
      space: 'header-space',
      status: 'header-status',
      progress: 'header-progress',
      lastScan: 'header-last-scan',
      pii: 'header-pii',
      riskScore: 'header-risk-score',
      actions: 'header-actions'
    },
    badges: {
      total: 'pii-badge-total',
      high: 'pii-badge-high',
      medium: 'pii-badge-medium',
      low: 'pii-badge-low'
    }
  },
  piiPageCard: {
    card: 'pii-page-card',
    collapsed: 'pii-card-collapsed',
    expanded: 'pii-card-expanded',
    expandButton: 'pii-card-expand',
    collapseButton: 'pii-card-collapse',
    revealButton: 'pii-card-reveal',
    confluenceLink: 'pii-card-confluence-link',
  },
  obfuscation: {
    page: 'obfuscation-page',
    content: 'obfuscation-content',
    featureDisabled: 'obfuscation-feature-disabled',
    entryButtons: {
      space: 'btn-obfuscate-space',
      page: 'btn-obfuscate-page',
      attachment: 'btn-obfuscate-attachment'
    },
    stats: 'obfuscation-stats',
    entryBanner: 'obfuscation-entry-banner',
    entryBannerDismiss: 'obfuscation-entry-banner-dismiss',
    empty: 'obfuscation-empty',
    toolbar: {
      search: 'obfuscation-search',
      statusFilter: 'obfuscation-status-filter',
      statusOption: 'obfuscation-status-option',
      selectAllPending: 'obfuscation-select-all-pending',
      groupBy: 'obfuscation-group-by',
      groupByOption: 'obfuscation-group-by-option',
      expandAll: 'obfuscation-expand-all',
      collapseAll: 'obfuscation-collapse-all',
      itemChip: 'obfuscation-item-chip',
      itemChipRemove: 'obfuscation-item-chip-remove'
    },
    group: {
      root: 'obfuscation-group',
      master: 'obfuscation-group-master',
      toggle: 'obfuscation-group-toggle',
      count: 'obfuscation-group-count',
      occurrences: 'obfuscation-group-occurrences',
      selectedCount: 'obfuscation-group-selected-count',
      hint: 'obfuscation-group-hint'
    },
    row: {
      root: 'obfuscation-row',
      checkbox: 'obfuscation-row-checkbox',
      value: 'obfuscation-row-value',
      occurrences: 'obfuscation-row-occurrences',
      status: 'obfuscation-row-status',
      ineligible: 'obfuscation-row-ineligible',
      markManual: 'obfuscation-row-mark-manual',
      restore: 'obfuscation-row-restore'
    },
    bulkBar: {
      root: 'obfuscation-bulk-bar',
      counter: 'obfuscation-bulk-counter',
      fpNote: 'obfuscation-bulk-fp-note',
      attachmentNote: 'obfuscation-bulk-attachment-note',
      chip: 'obfuscation-bulk-chip',
      moreChip: 'obfuscation-bulk-more-chip',
      clear: 'obfuscation-bulk-clear',
      markTreated: 'obfuscation-bulk-mark-treated',
      obfuscate: 'obfuscation-bulk-obfuscate',
      reportFp: 'obfuscation-bulk-report-fp'
    },
    confirmDialog: {
      warning: 'obfuscation-confirm-warning',
      lead: 'obfuscation-confirm-lead',
      breakdownRow: 'obfuscation-confirm-breakdown-row',
      total: 'obfuscation-confirm-total',
      fpNote: 'obfuscation-confirm-fp-note',
      cancel: 'obfuscation-confirm-cancel',
      accept: 'obfuscation-confirm-accept'
    },
    jobProgress: {
      root: 'obfuscation-job-progress',
      label: 'obfuscation-job-label',
      outcome: 'obfuscation-job-outcome',
      rescanHint: 'obfuscation-job-rescan-hint'
    },
    pager: {
      root: 'obfuscation-pager',
      label: 'obfuscation-pager-label',
      prev: 'obfuscation-pager-prev',
      next: 'obfuscation-pager-next',
      pageSize: 'obfuscation-pager-page-size'
    }
  }
} as const;

// Type exports for type safety
export type TestIdKey = typeof TestIds;
