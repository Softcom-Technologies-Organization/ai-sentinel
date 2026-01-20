import { Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';

export interface ErrorToastData {
  scanId: string;
  spaceKey: string;
  pageId?: string;
  pageTitle?: string;
  attachmentName?: string;
  errorMessage: string;
  errorType: 'TIMEOUT_REACTOR' | 'TIMEOUT_GRPC' | 'ERROR_GRPC' | 'ERROR_GENERAL';
}

@Injectable()
export class ToastService {
  constructor(readonly messageService: MessageService) {}

  showScanError(data: ErrorToastData): void {
    const summary = this.formatSummary(data);
    const detail = this.formatDetail(data);

    this.messageService.add({
      severity: 'error',
      summary,
      detail,
      sticky: true,
      life: undefined,
      key: 'scan-errors',
      contentStyleClass: 'scan-error-toast'
    });
  }

  clearScanErrors(): void {
    this.messageService.clear('scan-errors');
  }

  private formatSummary(data: ErrorToastData): string {
    const typeLabels: Record<ErrorToastData['errorType'], string> = {
      'TIMEOUT_REACTOR': 'Reactor Timeout',
      'TIMEOUT_GRPC': 'gRPC Timeout',
      'ERROR_GRPC': 'Analyse du contenu impossible',
      'ERROR_GENERAL': 'Scan error'
    };
    return typeLabels[data.errorType];
  }

  private formatDetail(data: ErrorToastData): string {
    const parts: string[] = [];

    // Message user-friendly pour erreurs gRPC
    if (data.errorType === 'ERROR_GRPC') {
      parts.push('Service d\'analyse indisponible');
    }

    // Espace Confluence (libellé amélioré pour gRPC)
    if (data.errorType === 'ERROR_GRPC') {
      parts.push(`Espace confluence: ${data.spaceKey}`);
    } else {
      parts.push(`Space: ${data.spaceKey}`);
    }

    // Page info (identique pour tous)
    if (data.pageTitle) {
      parts.push(`Page: "${data.pageTitle}"`);
    } else if (data.pageId) {
      parts.push(`Page ID: ${data.pageId}`);
    }

    // Attachement (si applicable)
    if (data.attachmentName) {
      parts.push(`Pièce jointe: "${data.attachmentName}"`);
    }

    // Message technique uniquement pour non-gRPC errors
    if (data.errorType !== 'ERROR_GRPC') {
      parts.push(`Message: ${data.errorMessage}`);
    }

    return parts.join('\n');
  }

  detectErrorType(errorMessage: string): ErrorToastData['errorType'] {
    const lowerMsg = errorMessage.toLowerCase();

    if (lowerMsg.includes('timeout') && lowerMsg.includes('reactor')) {
      return 'TIMEOUT_REACTOR';
    }
    if (lowerMsg.includes('timeout') && lowerMsg.includes('grpc')) {
      return 'TIMEOUT_GRPC';
    }
    if (lowerMsg.includes('deadline_exceeded')) {
      return 'TIMEOUT_GRPC';
    }
    if (lowerMsg.includes('grpc')) {
      return 'ERROR_GRPC';
    }

    return 'ERROR_GENERAL';
  }
}
