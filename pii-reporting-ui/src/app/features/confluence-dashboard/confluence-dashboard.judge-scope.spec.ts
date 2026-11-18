import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { ConfluenceDashboardComponent } from './confluence-dashboard.component';
import { ConfluenceConnectionConfigService } from '../../core/services/confluence-connection-config.service';
import { JudgeApiService } from '../../core/services/judge-api.service';
import { RemediationConfigService } from '../../core/services/remediation-config.service';
import { JudgeLaunchRequest, JudgePlanDto, JudgeScopeDto } from '../../core/models/judge.model';
import { JudgeJobTrackerService } from '../llm-judge/services/judge-job-tracker.service';
import { JudgeSpaceSummaryService } from '../llm-judge/services/judge-space-summary.service';
import { DashboardUiStateService } from './services/dashboard-ui-state.service';
import { FilterUrlStateService } from './services/filter-url-state.service';
import { PiiItemsStorageService } from './services/pii-items-storage.service';
import { ScanControlService } from './services/scan-control.service';
import { SpaceDataManagementService } from './services/space-data-management.service';
import { SpaceFilteringService } from './services/space-filtering.service';
import { SpacesDashboardUtils, UISpace } from './spaces-dashboard.utils';

const ALPHA: UISpace = {key: 'A', name: 'Alpha', originalIndex: 0, lastScanTs: '2026-11-11T12:00:00Z'};
const BETA: UISpace = {key: 'B', originalIndex: 1, lastScanTs: '2026-11-11T12:00:00Z'};
const UNSCANNED: UISpace = {key: 'C', name: 'Unscanned', originalIndex: 2};
const PLAN: JudgePlanDto = {totalFindings: 7, findingsBySpace: {A: 5, B: 2}};
const REQUEST: JudgeLaunchRequest = {
  scope: {spaceKeys: ['A']}, model: null, applyMode: 'PROPOSE', confidenceThreshold: 0.95
};

describe('Confluence dashboard judge scope', () => {
  let component: ConfluenceDashboardComponent;
  const scanActive = signal(false);
  const enabled = signal(false);
  const selectedSpaces = signal<UISpace[]>([]);
  const plan = vi.fn<(scope: JudgeScopeDto) => Observable<JudgePlanDto>>();
  const launch = vi.fn();
  const refresh = vi.fn();
  const track = vi.fn<(ids: string[], onChange: () => void) => void>();

  beforeEach(() => {
    vi.useFakeTimers();
    scanActive.set(false);
    enabled.set(false);
    selectedSpaces.set([]);
    plan.mockReset().mockReturnValue(of(PLAN));
    launch.mockReset().mockReturnValue(of({jobIds: ['job-a']}));
    refresh.mockReset();
    track.mockReset();
    TestBed.configureTestingModule({providers: [
      {provide: ConfluenceConnectionConfigService, useValue: {}},
      {provide: SpaceFilteringService, useValue: {}},
      {provide: SpaceDataManagementService, useValue: {}},
      {provide: DashboardUiStateService, useValue: {selectedSpaces}},
      {provide: FilterUrlStateService, useValue: {}},
      {provide: PiiItemsStorageService, useValue: {}},
      {provide: ScanControlService, useValue: {scanActive, actionPending: signal(false)}},
      {provide: SpacesDashboardUtils, useValue: {
        allSpaces: signal([ALPHA, BETA, UNSCANNED]), canViewResult: (space: UISpace) => !!space.lastScanTs
      }},
      {provide: RemediationConfigService, useValue: {enabled}},
      {provide: JudgeApiService, useValue: {plan, launch}},
      {provide: JudgeJobTrackerService, useValue: {track, active: signal(false), launchUnavailableReason: signal(null)}},
      {provide: JudgeSpaceSummaryService, useValue: {
        refresh, summaryFor: (key: string) => key === 'A' ? {judgedCount: 5} : null
      }}
    ]});
    component = TestBed.runInInjectionContext(() => new ConfluenceDashboardComponent());
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('Should_OfferOnlyScannedSpaces_When_OpeningJudgeWithoutSelection', () => {
    component.openJudgeDialog();

    expect(plan).toHaveBeenCalledWith({spaceKeys: ['A', 'B']});
    expect(component.judgeScopeOptions()).toMatchObject([
      {key: 'all', count: 7, scope: {spaceKeys: ['A', 'B']}},
      {key: 'never', count: 2, scope: {spaceKeys: ['B']}}
    ]);
    expect(component.judgeDialogVisible()).toBe(true);
  });

  it('Should_PreserveSelectedScopeAndCounts_When_OpeningJudgeFromSelection', () => {
    component.selectedSpaces = [BETA, UNSCANNED];
    component.openJudgeDialog('selection');
    expect(component.judgeScopeOptions()).toMatchObject([
      {key: 'selection', count: 2, hintText: 'B', scope: {spaceKeys: ['B']}},
      {key: 'all', count: 7},
      {key: 'never', count: 2}
    ]);

    component.openJudgeDialog();
    expect(component.judgeScopeOptions().map(option => option.key)).toEqual(['all', 'selection', 'never']);
  });

  it('Should_DebouncePlanningAndClearCounts_When_SelectionChangesThenClears', () => {
    enabled.set(true);
    component.selectedSpaces = [ALPHA];
    component.selectedSpaces = [BETA, UNSCANNED];
    expect(plan).not.toHaveBeenCalled();
    vi.advanceTimersByTime(300);

    expect(plan).toHaveBeenCalledOnce();
    expect(plan).toHaveBeenCalledWith({spaceKeys: ['B']});
    expect(component.selectedSpaceChips()).toEqual([{key: 'B', name: 'B'}]);
    expect(component.judgeSelectionPlan()).toEqual(PLAN);

    component.clearSpaceSelection();
    vi.advanceTimersByTime(300);
    expect(component.judgeSelectionPlan()).toBeNull();
    expect(component.selectedSpaceChips()).toEqual([]);
    expect(plan).toHaveBeenCalledOnce();
  });

  it('Should_KeepJudgeClosed_When_ScanStartsBeforePlanArrives', () => {
    const pending = new Subject<JudgePlanDto>();
    plan.mockReturnValue(pending);
    component.openJudgeDialog();
    scanActive.set(true);
    pending.next(PLAN);
    pending.complete();

    expect(component.judgeDialogVisible()).toBe(false);
    expect(component.judgeScopeOptions()).toEqual([]);
    component.launchJudge(REQUEST);
    expect(launch).not.toHaveBeenCalled();
  });

  it('Should_ClearSelectionAndRefreshSummaries_When_JudgeLaunchSucceeds', () => {
    component.selectedSpaces = [ALPHA];
    component.judgeDialogVisible.set(true);
    component.launchJudge(REQUEST);

    expect(launch).toHaveBeenCalledWith(REQUEST);
    expect(component.judgeDialogVisible()).toBe(false);
    expect(component.selectedSpaces).toEqual([]);
    expect(track).toHaveBeenCalledWith(['job-a'], expect.any(Function));
    track.mock.calls[0][1]();
    expect(refresh).toHaveBeenCalled();
  });
});
