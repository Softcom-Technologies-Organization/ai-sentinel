import { Injectable, signal } from '@angular/core';

/**
 * Service responsible for managing scan progress state across multiple Confluence spaces.
 * Business purpose: Centralized progress tracking with support for both percentage-based
 * and index-based progress calculation.
 */
@Injectable({
  providedIn: 'root'
})
export class ScanProgressService {
  /**
   * Progress data per space key.
   * Each space can have:
   * - total: total number of items to scan
   * - index: current index of item being scanned
   * - percent: explicit percentage (0-100)
   */
  private readonly progress = signal<Record<string, { total?: number; index?: number; percent?: number }>>({});

  /**
   * Gets the complete progress map for all spaces.
   * @returns Immutable progress map
   */
  getProgress(): Record<string, { total?: number; index?: number; percent?: number }> {
    return this.progress();
  }

  /**
   * Gets progress data for a specific space.
   * @param spaceKey The space identifier
   * @returns Progress data or undefined if not found
   */
  getProgressForSpace(spaceKey: string): { total?: number; index?: number; percent?: number } | undefined {
    return this.progress()[spaceKey];
  }

  /**
   * Updates progress for a specific space by merging the patch with existing values.
   * @param spaceKey The space identifier
   * @param patch Partial progress update
   */
  updateProgress(spaceKey: string, patch: Partial<{ total: number; index: number; percent: number }>): void {
    const current = this.progress()[spaceKey] ?? {};
    this.progress.set({ ...this.progress(), [spaceKey]: { ...current, ...patch } });
  }

  /**
   * Calculates the progress percentage for a space.
   * Business rules:
   * - Returns 0 if spaceKey is null/undefined or no progress exists
   * - Prefers explicit percent over calculated value
   * - Calculates from total/index if percent not available
   * - Clamps result to [0, 100] range
   *
   * @param spaceKey The space identifier
   * @returns Progress percentage (0-100)
   */
  getProgressPercent(spaceKey: string | null | undefined): number {
    if (!spaceKey) {
      return 0;
    }

    const progressData = this.progress()[spaceKey];
    if (!progressData) {
      return 0;
    }

    // Prefer explicit percent if available and valid
    if (typeof progressData.percent === 'number' && !Number.isNaN(progressData.percent)) {
      return this.clampPercent(progressData.percent);
    }

    // Calculate from total and index
    const total = progressData.total;
    const index = progressData.index;
    if (typeof total === 'number' && typeof index === 'number' && total > 0) {
      return this.clampPercent(Math.round((index / total) * 100));
    }

    return 0;
  }

  /**
   * Clamps a percentage value to the valid range [0, 100].
   * @param value The percentage value to clamp
   * @returns Clamped and rounded percentage
   */
  private clampPercent(value: number): number {
    if (value < 0) {
      return 0;
    }
    if (value > 100) {
      return 100;
    }
    return Math.round(value);
  }

  /**
   * Extracts the progress percentage from an SSE payload.
   * Business purpose: Parses backend progress updates from stream events.
   *
   * @param payload The raw stream payload
   * @returns Progress percentage or undefined if not available
   */
  extractPercentFromPayload(payload: any): number | undefined {
    if (!payload) {
      return undefined;
    }
    const value = payload.analysisProgressPercentage;
    return typeof value === 'number' ? value : undefined;
  }

  /**
   * Resets progress for a specific space.
   * Business purpose: Clears progress when a space scan is restarted.
   *
   * @param spaceKey The space identifier
   */
  resetProgress(spaceKey: string): void {
    const current = { ...this.progress() };
    delete current[spaceKey];
    this.progress.set(current);
  }

  /**
   * Resets all progress data.
   * Business purpose: Clears all progress when starting a new global scan.
   */
  resetAllProgress(): void {
    this.progress.set({});
  }
}
