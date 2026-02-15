/**
 * Confirmation dialog messages for dashboard operations.
 * Centralizes all user-facing messages for maintainability and future i18n support.
 */
export const CONFIRMATION_MESSAGES = {
  GLOBAL_SCAN: {
    header: 'Confirmation de scan global',
    message:
      '<p>Êtes-vous sûr de vouloir démarrer le scan de tous les espaces Confluence ?</p>\n' +
      '<p><b>Les données existantes seront écrasées.</b></p>\n' +
      '<p>Cette opération peut prendre du temps.</p>',
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: 'Oui, démarrer',
    rejectLabel: 'Annuler',
    acceptButtonStyleClass: 'p-button-primary',
    rejectButtonStyleClass: 'p-button-secondary'
  }
} as const;
