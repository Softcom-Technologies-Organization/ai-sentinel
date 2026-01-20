import { Injectable } from '@angular/core';

/**
 * Utilities for PII Item Card UI.
 *
 * Business purpose: normalize attachment MIME types or extensions to a small set of
 * display kinds so the component can render consistent tags (PDF, Excel, Word, PPT, Texte).
 */
@Injectable({ providedIn: 'root' })
export class PiiItemCardUtils {
  /**
   * Normalize attachment type string (MIME or extension) to a known kind for tag rendering.
   * Accepts common MIME types (application/pdf, vnd.ms-excel, openxmlformats, text/plain, etc.) and extensions.
   * Returns 'pdf'|'excel'|'word'|'ppt'|'txt' or null if unknown.
   */
  attachmentKind(type?: string | null): 'pdf' | 'excel' | 'word' | 'ppt' | 'txt' | null {
    const t = (type ?? '').toLowerCase();
    if (!t) return null;

    const match = (tokens: string[]) => tokens.some((x) => t.includes(x));

    // PDF
    if (match(['pdf'])) return 'pdf';

    // Excel and spreadsheets (CSV treated as spreadsheet for business UX)
    if (match(['vnd.ms-excel', 'spreadsheet', 'excel', 'xls', 'xlsx', 'xlsm', 'xltx', 'csv', 'ods'])) {
      return 'excel';
    }

    // Word processors (including RTF and OpenDocument Text)
    if (match(['msword', 'wordprocessingml', 'application/rtf', 'rtf', 'doc', 'docx', 'odt'])) {
      return 'word';
    }

    // PowerPoint / Presentations (including OpenDocument Presentation)
    if (match(['powerpoint', 'presentationml', 'presentation', 'ppt', 'pptx', 'odp'])) {
      return 'ppt';
    }

    // Plain/text-like content (HTML shown as text badge for now)
    if (match(['text/plain', 'plain', 'txt', 'text/', 'html', 'htm', 'md'])) {
      return 'txt';
    }

    return null;
  }
}
