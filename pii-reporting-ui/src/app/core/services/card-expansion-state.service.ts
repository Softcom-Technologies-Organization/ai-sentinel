import { Injectable } from '@angular/core';

/**
 * Tracks which PII cards are expanded across component re-creations.
 *
 * When p-table re-renders expanded rows (e.g. on SSE updates),
 * child components are destroyed and recreated. This service
 * preserves expanded/collapsed state externally so cards can
 * restore their state on init.
 */
@Injectable({ providedIn: 'root' })
export class CardExpansionStateService {
  private readonly expandedKeys = new Set<string>();

  isExpanded(key: string): boolean {
    return this.expandedKeys.has(key);
  }

  expand(key: string): void {
    this.expandedKeys.add(key);
  }

  collapse(key: string): void {
    this.expandedKeys.delete(key);
  }

  clear(): void {
    this.expandedKeys.clear();
  }
}
