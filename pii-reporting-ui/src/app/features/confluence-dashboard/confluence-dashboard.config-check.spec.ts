import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfluenceDashboardComponent } from './confluence-dashboard.component';
import { ConfluenceConnectionConfigService } from '../../core/services/confluence-connection-config.service';
import { ConfluenceConnectionConfig } from '../../core/models/confluence-connection-config.model';
import { SpaceDataManagementService } from './services/space-data-management.service';
import { SpaceFilteringService } from './services/space-filtering.service';
import { DashboardUiStateService } from './services/dashboard-ui-state.service';
import { PiiItemsStorageService } from './services/pii-items-storage.service';
import { ScanControlService } from './services/scan-control.service';
import { FilterUrlStateService } from './services/filter-url-state.service';
import { UISpace, SpacesDashboardUtils } from './spaces-dashboard.utils';
import { RemediationConfigService } from '../../core/services/remediation-config.service';
import { JudgeApiService } from '../../core/services/judge-api.service';
import { JudgeJobTrackerService } from '../llm-judge/services/judge-job-tracker.service';

function config(configured: boolean): ConfluenceConnectionConfig {
  return {
    baseUrl: 'https://example.atlassian.net',
    username: 'user@example.com',
    connectTimeout: 5000,
    readTimeout: 30000,
    maxRetries: 3,
    pagesLimit: 25,
    deploymentType: 'CLOUD',
    configured
  };
}

/** The browser reports a refused connection as status 0 with no body. */
const CONNECTION_REFUSED = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });

describe('Confluence dashboard configuration check', () => {
  let getConfig: ReturnType<typeof vi.fn>;
  let dataManagement: {
    isSpacesLoading: ReturnType<typeof signal<boolean>>;
    fetchSpaces: ReturnType<typeof vi.fn>;
    loadLastScan: ReturnType<typeof vi.fn>;
    loadSpacesUpdateInfo: ReturnType<typeof vi.fn>;
    loadLastSpaceStatuses: ReturnType<typeof vi.fn>;
    stopBackgroundPolling: ReturnType<typeof vi.fn>;
  };

  function createComponent(): ConfluenceDashboardComponent {
    return TestBed.runInInjectionContext(() => new ConfluenceDashboardComponent());
  }

  const scanActive = signal(false);
  const actionPending = signal(false);
  const spaces = signal<UISpace[]>([]);
  const judgeReason = signal<string | null>(null);

  let filtering: { reload: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    scanActive.set(false);
    actionPending.set(false);
    spaces.set([]);
    judgeReason.set(null);
    getConfig = vi.fn();
    filtering = { reload: vi.fn() };
    dataManagement = {
      isSpacesLoading: signal(true),
      fetchSpaces: vi.fn().mockReturnValue(of(undefined)),
      loadLastScan: vi.fn().mockReturnValue(of(undefined)),
      loadSpacesUpdateInfo: vi.fn().mockReturnValue(of(undefined)),
      loadLastSpaceStatuses: vi.fn().mockReturnValue(of(undefined)),
      stopBackgroundPolling: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: ConfluenceConnectionConfigService, useValue: { getConfig } },
        { provide: SpaceDataManagementService, useValue: dataManagement },
        { provide: SpaceFilteringService, useValue: filtering },
        { provide: DashboardUiStateService, useValue: {} },
        { provide: PiiItemsStorageService, useValue: {} },
        { provide: ScanControlService, useValue: { reconnectIfScanRunning: vi.fn(), scanActive, actionPending } },
        { provide: FilterUrlStateService, useValue: {} },
        { provide: SpacesDashboardUtils, useValue: { allSpaces: spaces, canViewResult: (space: UISpace) => !!space.lastScanTs } },
        { provide: RemediationConfigService, useValue: { enabled: signal(false) } },
        { provide: JudgeApiService, useValue: {} },
        { provide: JudgeJobTrackerService, useValue: { active: signal(false), launchUnavailableReason: judgeReason } }
      ]
    });
  });

  it('Should_ReportBackendUnreachable_When_ConfigCallFails', () => {
    getConfig.mockReturnValue(throwError(() => CONNECTION_REFUSED));

    const component = createComponent();
    component.ngOnInit();

    expect(component.backendUnreachable()).toBe(true);
    expect(component.confluenceConfigMissing()).toBe(false);
    expect(dataManagement.isSpacesLoading()).toBe(false);
    expect(dataManagement.fetchSpaces).not.toHaveBeenCalled();
  });

  it('Should_ReportMissingCredentials_When_BackendAnswersNotConfigured', () => {
    getConfig.mockReturnValue(of(config(false)));

    const component = createComponent();
    component.ngOnInit();

    expect(component.confluenceConfigMissing()).toBe(true);
    expect(component.backendUnreachable()).toBe(false);
    expect(dataManagement.fetchSpaces).not.toHaveBeenCalled();
  });

  it('Should_LoadData_When_BackendAnswersConfigured', () => {
    getConfig.mockReturnValue(of(config(true)));

    const component = createComponent();
    component.ngOnInit();

    expect(component.confluenceConfigMissing()).toBe(false);
    expect(component.backendUnreachable()).toBe(false);
    expect(dataManagement.fetchSpaces).toHaveBeenCalled();
  });

  it('Should_ClearBackendUnreachable_When_RetrySucceeds', () => {
    getConfig.mockReturnValue(throwError(() => CONNECTION_REFUSED));
    const component = createComponent();
    component.ngOnInit();

    getConfig.mockReturnValue(of(config(true)));
    component.retryBackendConnection();

    expect(component.backendUnreachable()).toBe(false);
    expect(dataManagement.fetchSpaces).toHaveBeenCalled();
  });

  it('Should_RefetchServerOrdering_When_RetrySucceeds', () => {
    getConfig.mockReturnValue(throwError(() => CONNECTION_REFUSED));
    const component = createComponent();
    component.ngOnInit();

    getConfig.mockReturnValue(of(config(true)));
    component.retryBackendConnection();

    // Restocking the space store is not enough: the table renders rows in the server-decided
    // order, so the ordering query has to run again or the table stays empty.
    expect(filtering.reload).toHaveBeenCalledTimes(1);
  });
  it('Should_DisableJudge_When_NoScanDataAndEnableAfterCompletedScan', () => {
    const component = createComponent();
    expect(component.canLaunchJudge()).toBe(false);
    expect(component.judgeUnavailableReason()).toBe('judge.unavailable.noScanData');
    component.openJudgeDialog();
    expect(component.judgeDialogVisible()).toBe(false);

    spaces.set([{ key: 'SPACE', name: 'Space', originalIndex: 0, lastScanTs: '2026-09-13T09:00:00Z' }]);
    expect(component.canLaunchJudge()).toBe(true);

    judgeReason.set('judge.unavailable.noModel');
    expect(component.canLaunchJudge()).toBe(false);
    expect(component.judgeUnavailableReason()).toBe('judge.unavailable.noModel');

    scanActive.set(true);
    expect(component.canLaunchJudge()).toBe(false);
    expect(component.judgeUnavailableReason()).toBe('judge.unavailable.scanInProgress');

    scanActive.set(false);
    judgeReason.set(null);
    expect(component.canLaunchJudge()).toBe(true);
    actionPending.set(true);
    expect(component.canLaunchJudge()).toBe(false);
  });

});
