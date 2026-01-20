/**
 * Centralized dialog selectors for E2E tests.
 * Makes tests more maintainable and resilient to UI changes.
 */
export const DIALOG_SELECTORS = {
  confirmDialog: {
    container: '.p-confirmdialog',
    header: '.p-dialog-title',
    message: '.p-confirmdialog-message',
    acceptButton: '.p-confirmdialog-accept-button',
    rejectButton: '.p-confirmdialog-reject-button'
  }
} as const;
