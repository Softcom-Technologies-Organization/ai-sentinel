import {RawStreamPayload} from '../../core/models/stream-event-type';

/**
 * Utilities for stream-event handling in Spaces Dashboard.
 * Pure functions only; no side effects. Business logic unchanged.
 */
export type StreamEventType =
  | 'start'
  | 'pageStart'
  | 'item'
  | 'attachmentItem'
  | 'scanError'
  | 'complete'
  | 'multiStart'
  | 'multiComplete'
  | 'pageComplete'
  | 'keepalive';

/**
 * Returns a human-readable log line for the given event type and raw payload json.
 * Focus on business semantics: space, pagination, and attachment information.
 */
export function formatEventLog(type: StreamEventType, jsonData: string): string {
  try {
    const o = JSON.parse(jsonData ?? '{}');
    const spacePart = o.spaceKey ? ` space=${o.spaceKey}` : '';

    switch (type) {
      case 'start': {
        const pagesTotalPart = o.pagesTotal != null ? ` pagesTotal=${o.pagesTotal}` : '';
        return `[start]${spacePart}${pagesTotalPart}`;
      }
      case 'pageStart': {
        const pageIndex = o.pageIndex ?? '?';
        const totalPart = o.pagesTotal ? `/${o.pagesTotal}` : '';
        const title = o.pageTitle ?? '';
        return `[page_start]${spacePart} ${pageIndex}${totalPart} ${title}`.trim();
      }
      case 'item': {
        const count = Array.isArray(o.entities) ? o.entities.length : 0;
        return `[item]${spacePart} ${count} entities`;
      }
      case 'attachmentItem': {
        const name = o.attachmentName ?? '';
        const typePart = o.attachmentType ? `(${o.attachmentType})` : '';
        return `[attachmentItem]${spacePart} ${name} ${typePart}`.trim();
      }
      case 'complete':
        return `[complete]${spacePart}`;
      case 'multiStart':
      case 'multiComplete':
        return `[${type}]`;
      default:
        return `[${type}] ${jsonData}`;
    }
  } catch {
    return `[${type}] ${jsonData}`;
  }
}

/**
 * Determines if the payload looks like an attachment line item.
 */
export function isAttachmentPayload(payload: RawStreamPayload | undefined | null): boolean {
  const anyPayload = payload as any;
  return Boolean(anyPayload?.attachmentName ?? anyPayload?.attachmentUrl);
}

/**
 * Tries to extract a trimmed space key from payload; returns null if empty or whitespace.
 */
export function coerceSpaceKey(payload: RawStreamPayload | undefined | null): string | null {
  const key = payload?.spaceKey;
  if (key == null) return null;
  const trimmed = String(key).trim();
  return trimmed.length ? trimmed : null;
}
