import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { JudgeApiService } from '../../../core/services/judge-api.service';
import { RemediationConfigService } from '../../../core/services/remediation-config.service';
import { ScanReportingSummaryDto, SentinelleApiService, SpaceSummaryDto } from '../../../core/services/sentinelle-api.service';
import { JudgeJobTrackerService } from './judge-job-tracker.service';

function space(status: string, scanId = 'scan-current'): SpaceSummaryDto {
  return { spaceKey: 'SPACE', scanId, status, pagesDone: 1, attachmentsDone: 0,
    progressPercentage: null, lastEventAt: '', severityCounts: null };
}

function summary(spaces: SpaceSummaryDto[]): ScanReportingSummaryDto {
  return { scanId: 'scan-current', lastUpdated: '', spacesCount: spaces.length, spaces };
}

describe('JudgeJobTrackerService scan availability', () => {
  const getDashboardSpacesSummary = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    getDashboardSpacesSummary.mockReset();
    TestBed.configureTestingModule({ providers: [
      JudgeJobTrackerService,
      { provide: JudgeApiService, useValue: {} },
      { provide: SentinelleApiService, useValue: { getDashboardSpacesSummary } },
      { provide: RemediationConfigService, useValue: { enabled: signal(true) } }
    ] });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  async function createTracker(spaces: SpaceSummaryDto[]): Promise<JudgeJobTrackerService> {
    getDashboardSpacesSummary.mockReturnValue(of(summary(spaces)));
    const tracker = TestBed.inject(JudgeJobTrackerService);
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(0);
    return tracker;
  }

  it('Should_BlockLaunch_When_InitialScanStatusIsUnknown', () => {
    const tracker = TestBed.inject(JudgeJobTrackerService);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.scanStatusUnknown');
  });

  it('Should_BlockLaunch_When_CompletedScanHasNoPagesAttachmentsOrFindings', async () => {
    const tracker = await createTracker([{ ...space('COMPLETED'), pagesDone: 0 }]);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.noScanData');
  });

  it.each(['RUNNING', 'NOT_STARTED'])('Should_BlockLaunch_When_CurrentScanIs%s', async (status) => {
    const tracker = await createTracker([space(status)]);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.scanInProgress');
  });

  it('Should_AllowLaunch_When_CurrentScanIsPausedAndHasData', async () => {
    const tracker = await createTracker([space('PAUSED')]);
    expect(tracker.launchUnavailableReason()).toBeNull();
  });

  it('Should_AllowLaunch_When_PausedScanStillHasQueuedSpaces', async () => {
    const tracker = await createTracker([space('PAUSED'), space('NOT_STARTED')]);
    expect(tracker.launchUnavailableReason()).toBeNull();
  });

  it('Should_BlockLaunch_When_PausedScanProducedNothingYet', async () => {
    const tracker = await createTracker([{ ...space('PAUSED'), pagesDone: 0 }]);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.noScanData');
  });

  it('Should_AllowLaunch_When_AnOlderScanHasQueuedSpaces', async () => {
    const tracker = await createTracker([space('COMPLETED'), space('NOT_STARTED', 'scan-old')]);
    expect(tracker.launchUnavailableReason()).toBeNull();
  });

  it.each(['PAUSED', 'RUNNING'])('Should_AllowLaunch_When_OnlyAnOlderScanIs%s', async (status) => {
    const tracker = await createTracker([space('COMPLETED'), space(status, 'scan-old')]);
    expect(tracker.launchUnavailableReason()).toBeNull();
  });

  it('Should_BlockLaunch_When_NoScanDataIsAvailable', async () => {
    const tracker = await createTracker([]);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.noScanData');
  });

  it('Should_IgnoreUnscannedSpaces_When_CheckingActiveScans', async () => {
    const tracker = await createTracker([space('COMPLETED'), { ...space('NOT_STARTED', ''), pagesDone: 0 }]);
    expect(tracker.launchUnavailableReason()).toBeNull();
  });

  it('Should_UpdateAvailability_When_ScanStartsFinishesAndDataIsPurged', async () => {
    const tracker = await createTracker([space('COMPLETED')]);
    expect(tracker.launchUnavailableReason()).toBeNull();

    getDashboardSpacesSummary.mockReturnValue(of(summary([space('RUNNING')])));
    await vi.advanceTimersByTimeAsync(3000);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.scanInProgress');

    getDashboardSpacesSummary.mockReturnValue(of(summary([space('COMPLETED')])));
    await vi.advanceTimersByTimeAsync(3000);
    expect(tracker.launchUnavailableReason()).toBeNull();

    getDashboardSpacesSummary.mockReturnValue(of(summary([])));
    await vi.advanceTimersByTimeAsync(3000);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.noScanData');
  });

  it('Should_BlockLaunch_When_ScanStatusCannotBeVerified', async () => {
    const tracker = await createTracker([space('COMPLETED')]);
    getDashboardSpacesSummary.mockReturnValue(of(null));
    await vi.advanceTimersByTimeAsync(3000);
    expect(tracker.launchUnavailableReason()).toBe('judge.unavailable.scanStatusUnknown');
  });
});
