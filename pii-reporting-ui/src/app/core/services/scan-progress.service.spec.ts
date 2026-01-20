import { TestBed } from '@angular/core/testing';
import { ScanProgressService } from './scan-progress.service';

describe('ScanProgressService', () => {
  let service: ScanProgressService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ScanProgressService]
    });
    service = TestBed.inject(ScanProgressService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getProgress', () => {
    it('should return empty object initially', () => {
      expect(service.getProgress()).toEqual({});
    });

    it('should return progress for specific space', () => {
      service.updateProgress('SPACE1', { percent: 50 });

      const progress = service.getProgressForSpace('SPACE1');

      expect(progress).toEqual({ percent: 50 });
    });

    it('should return undefined for unknown space', () => {
      expect(service.getProgressForSpace('UNKNOWN')).toBeUndefined();
    });
  });

  describe('updateProgress', () => {
    it('should set initial progress for space', () => {
      service.updateProgress('SPACE1', { total: 100, index: 0 });

      expect(service.getProgressForSpace('SPACE1')).toEqual({ total: 100, index: 0 });
    });

    it('should merge partial updates with existing progress', () => {
      service.updateProgress('SPACE1', { total: 100, index: 0 });
      service.updateProgress('SPACE1', { index: 50 });

      expect(service.getProgressForSpace('SPACE1')).toEqual({ total: 100, index: 50 });
    });

    it('should update percent directly when provided', () => {
      service.updateProgress('SPACE1', { percent: 75 });

      expect(service.getProgressForSpace('SPACE1')?.percent).toBe(75);
    });

    it('should handle multiple spaces independently', () => {
      service.updateProgress('SPACE1', { percent: 30 });
      service.updateProgress('SPACE2', { percent: 60 });

      expect(service.getProgressForSpace('SPACE1')?.percent).toBe(30);
      expect(service.getProgressForSpace('SPACE2')?.percent).toBe(60);
    });
  });

  describe('getProgressPercent', () => {
    it('should return 0 when spaceKey is null', () => {
      expect(service.getProgressPercent(null)).toBe(0);
    });

    it('should return 0 when spaceKey is undefined', () => {
      expect(service.getProgressPercent(undefined)).toBe(0);
    });

    it('should return 0 when no progress exists for space', () => {
      expect(service.getProgressPercent('UNKNOWN')).toBe(0);
    });

    it('should return percent when directly set', () => {
      service.updateProgress('SPACE1', { percent: 42 });

      expect(service.getProgressPercent('SPACE1')).toBe(42);
    });

    it('should calculate percent from total and index', () => {
      service.updateProgress('SPACE1', { total: 100, index: 25 });

      expect(service.getProgressPercent('SPACE1')).toBe(25);
    });

    it('should round calculated percent', () => {
      service.updateProgress('SPACE1', { total: 3, index: 1 });

      expect(service.getProgressPercent('SPACE1')).toBe(33);
    });

    it('should return 0 when total is 0', () => {
      service.updateProgress('SPACE1', { total: 0, index: 5 });

      expect(service.getProgressPercent('SPACE1')).toBe(0);
    });

    it('should prefer explicit percent over calculated value', () => {
      service.updateProgress('SPACE1', { total: 100, index: 50, percent: 75 });

      expect(service.getProgressPercent('SPACE1')).toBe(75);
    });

    it('should clamp negative percent to 0', () => {
      service.updateProgress('SPACE1', { percent: -10 });

      expect(service.getProgressPercent('SPACE1')).toBe(0);
    });

    it('should clamp percent above 100 to 100', () => {
      service.updateProgress('SPACE1', { percent: 150 });

      expect(service.getProgressPercent('SPACE1')).toBe(100);
    });

    it('should handle NaN percent gracefully', () => {
      service.updateProgress('SPACE1', { percent: NaN });

      expect(service.getProgressPercent('SPACE1')).toBe(0);
    });
  });

  describe('resetProgress', () => {
    it('should clear progress for specific space', () => {
      service.updateProgress('SPACE1', { percent: 50 });
      service.updateProgress('SPACE2', { percent: 75 });

      service.resetProgress('SPACE1');

      expect(service.getProgressForSpace('SPACE1')).toBeUndefined();
      expect(service.getProgressForSpace('SPACE2')).toEqual({ percent: 75 });
    });

    it('should handle resetting non-existent space gracefully', () => {
      expect(() => service.resetProgress('UNKNOWN')).not.toThrow();
    });
  });

  describe('resetAllProgress', () => {
    it('should clear all progress', () => {
      service.updateProgress('SPACE1', { percent: 50 });
      service.updateProgress('SPACE2', { percent: 75 });

      service.resetAllProgress();

      expect(service.getProgress()).toEqual({});
    });
  });

  describe('extractPercentFromPayload', () => {
    it('should extract analysisProgressPercentage from payload', () => {
      const payload = { analysisProgressPercentage: 67 };

      expect(service.extractPercentFromPayload(payload)).toBe(67);
    });

    it('should return undefined when analysisProgressPercentage is missing', () => {
      const payload = { someOtherField: 'value' };

      expect(service.extractPercentFromPayload(payload)).toBeUndefined();
    });

    it('should return undefined when analysisProgressPercentage is not a number', () => {
      const payload = { analysisProgressPercentage: 'not-a-number' };

      expect(service.extractPercentFromPayload(payload)).toBeUndefined();
    });

    it('should return undefined for null payload', () => {
      expect(service.extractPercentFromPayload(null)).toBeUndefined();
    });

    it('should return undefined for undefined payload', () => {
      expect(service.extractPercentFromPayload(undefined)).toBeUndefined();
    });
  });

  describe('signal reactivity', () => {
    it('should emit new value when progress is updated', () => {
      const initialProgress = service.getProgress();

      service.updateProgress('SPACE1', { percent: 50 });

      const updatedProgress = service.getProgress();
      expect(updatedProgress).not.toBe(initialProgress);
      expect(updatedProgress['SPACE1']).toEqual({ percent: 50 });
    });
  });
});
