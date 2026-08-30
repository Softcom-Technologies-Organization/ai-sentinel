import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MessageService } from 'primeng/api';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { PiiObfuscationComponent } from './pii-obfuscation.component';
import { ObfuscationSelectionService } from './services/obfuscation-selection.service';
import { ObfuscationViewStateService } from './services/obfuscation-view-state.service';
import { RemediationConfigService } from '../../core/services/remediation-config.service';
import { RemediationApiService } from '../../core/services/remediation-api.service';
import {
  ObfuscationJobDto,
  ObfuscationPlanDto,
  RemediationFindingDto,
  RemediationFindingsSearchRequest,
  RemediationFindingsSearchResponse,
  RemediationGroupDto,
} from '../../core/models/remediation.model';

const FR_TRANSLATIONS = {
  common: { success: 'Succès', warning: 'Attention' },
  dashboard: {
    table: { paginatorReport: 'Affichage de {first} à {last} sur {totalRecords} entrées' },
  },
  obfuscation: {
    featureDisabled: 'La suppression automatique est désactivée pour cette instance.',
    noFindings: 'Aucun finding ne correspond.',
    selectAllPending: 'Tout sélectionner (à traiter)',
    searchPlaceholder: 'Rechercher…',
    dismiss: 'Fermer',
    pageFilterChip: 'Item : {{name}}',
    expandAll: 'Tout déplier',
    collapseAll: 'Tout replier',
    selectAllGroup: 'Sélectionner tout le groupe (toutes pages)',
    nSelectedShort: '{{count}} sélectionnés',
    nOccurrences: '{{count}} occurrences',
    table: { selected: 'Sélectionnés', occurrences: 'Occurrences', total: 'Valeurs', expand: 'Développer', collapse: 'Réduire' },
    groupPageHint: '{{visible}} sur {{total}}',
    select: 'Sélectionner',
    redactedValue: 'Valeur supprimée',
    entryBanner: {
      space: 'Ouvert depuis le tableau de bord — <b>{{count}}</b> findings de « {{space}} » pré-sélectionnés pour revue.',
      page: 'Ouvert depuis l’item <b>{{name}}</b> — <b>{{count}}</b> findings pré-sélectionnés pour revue.',
    },
    stats: { toTreat: 'À traiter', treated: 'Traités', falsePositives: 'Faux positifs' },
    filter: { all: 'Tous', pending: 'À traiter', treated: 'Traités', fp: 'Faux positifs' },
    groupBy: { label: 'Grouper par', type: 'Type', severity: 'Sévérité' },
    status: { pending: 'À traiter', redacted: 'Supprimé de la source', manual: 'Traité (manuel)', fp: 'Faux positif' },
    action: { markManual: 'Manuel', undoManual: 'Rétablir', restore: 'Rétablir' },
    ineligible: { attachment: 'Pièce jointe : suppression impossible ({{kind}})' },
    bulk: {
      ariaLabel: 'Actions groupées',
      selectedForObf: 'sélectionnés à supprimer',
      fpSignaled: '{{count}} faux positif(s) signalé(s)',
      clear: 'Effacer',
      markTreated: 'Marquer traité',
      markTreatedHint: 'Marquer la sélection comme traitée manuellement',
      markFp: 'Signaler faux positif',
      markFpHint: 'Signaler la sélection comme faux positif',
      obfuscateN: 'Supprimer ({{count}})',
    },
    confirm: {
      title: 'Supprimer {{count}} finding(s) de la source ?',
      irreversibleTitle: 'Action irréversible.',
      irreversibleBody: 'Irréversible.',
      lead: 'Vous allez supprimer <b>{{count}}</b> occurrence(s) dans <b>{{space}}</b>.',
      total: 'Total',
      cancel: 'Annuler',
      fpFeedbackNote: '{{count}} FP.',
    },
    severity: { high: 'Critique', medium: 'Modéré', low: 'Faible' },
    toast: {
      obfuscated: '<b>{{count}}</b> finding(s) supprimé(s) dans la source.',
      treated: '<b>{{count}}</b> finding(s) marqué(s) traité(s).',
      fpReported: '<b>{{count}}</b> finding(s) signalé(s) comme faux positif(s).',
      completedWithIssues: 'Suppression terminée avec des erreurs — consultez le détail des résultats.',
    },
    pager: {
      label: '{{first}}–{{last}} sur {{total}} groupes',
      loading: 'Chargement…',
      prev: 'Précédent',
      next: 'Suivant',
      refresh: 'Revenir à la première page',
      rowsPerPage: 'Par page :',
    },
    job: {
      running: 'Suppression en cours… {{done}}/{{total}}',
      rescanRecommended: 'Relancez un scan.',
    },
  },
};

function finding(overrides: Partial<RemediationFindingDto> = {}): RemediationFindingDto {
  return {
    findingId: 'f1',
    piiType: 'EMAIL',
    severity: 'high',
    detector: 'PRESIDIO',
    confidenceScore: 0.87,
    maskedContext: 'contact: [EMAIL]',
    occurrenceCount: 1,
    pageId: 'p1',
    pageTitle: 'Team page',
    status: 'PENDING',
    selected: false,
    eligibleForRedaction: true,
    ...overrides,
  };
}

function group(overrides: Partial<RemediationGroupDto> = {}): RemediationGroupDto {
  return {
    key: 'EMAIL',
    label: 'Email',
    severity: 'high',
    total: 12,
    occurrenceCount: 12,
    selectedCount: 0,
    masterState: 'none',
    findings: [finding()],
    ...overrides,
  };
}

function searchResponse(
  overrides: Partial<RemediationFindingsSearchResponse> = {}
): RemediationFindingsSearchResponse {
  return {
    groups: [group()],
    totals: { pending: 8, handled: 3, falsePositive: 1, total: 12 },
    page: 0,
    pageSize: 20,
    totalElements: 12,
    totalGroups: 1,
    nonEligibleLegacyCount: 0,
    ...overrides,
  };
}

function plan(overrides: Partial<ObfuscationPlanDto> = {}): ObfuscationPlanDto {
  return {
    totalFindings: 5,
    bySeverity: { high: 5 },
    pagesImpacted: 2,
    falsePositivesReported: 0,
    selectionChecksum: 'sum-1',
    attachmentExclusions: 0,
    ...overrides,
  };
}

function runningJob(overrides: Partial<ObfuscationJobDto> = {}): ObfuscationJobDto {
  return { jobId: 'job-1', status: 'RUNNING', processed: 0, total: 5, outcomes: [], ...overrides };
}

interface ApiMock {
  searchFindings: Mock;
  planObfuscation: Mock;
  createJob: Mock;
  getJob: Mock;
  changeFindingsStatus: Mock;
  changeFindingsStatusBySelection: Mock;
}

describe('PiiObfuscationComponent', () => {
  let fixture: ComponentFixture<PiiObfuscationComponent>;
  let remediationConfigMock: { enabled: ReturnType<typeof signal<boolean>> };
  let queryParams$: BehaviorSubject<ParamMap>;
  let api: ApiMock;
  let messageService: { add: Mock };

  beforeEach(async () => {
    // The shared app header pulls in ThemeService, which probes matchMedia at
    // construction; jsdom does not implement it.
    globalThis.matchMedia ??= vi.fn().mockReturnValue({ matches: false }) as unknown as typeof globalThis.matchMedia;
    // ThemeService also reads localStorage: Node 22+ shadows the jsdom one with an
    // undefined accessor unless --localstorage-file is passed.
    globalThis.localStorage ??= { getItem: () => null, setItem: () => {} } as unknown as Storage;
    // The header's data source tabs (PrimeNG TabList) observe resize; jsdom lacks it.
    globalThis.ResizeObserver ??= class {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    } as unknown as typeof globalThis.ResizeObserver;

    remediationConfigMock = { enabled: signal(false) };
    queryParams$ = new BehaviorSubject(convertToParamMap({}));
    api = {
      searchFindings: vi.fn().mockReturnValue(of(searchResponse())),
      planObfuscation: vi.fn().mockReturnValue(of(plan())),
      createJob: vi.fn().mockReturnValue(of({ jobId: 'job-1' })),
      getJob: vi.fn().mockReturnValue(of(runningJob())),
      changeFindingsStatus: vi.fn().mockReturnValue(of({ applied: ['f1'], rejected: [] })),
      changeFindingsStatusBySelection: vi.fn().mockReturnValue(of({ applied: ['f1'], rejected: [] })),
    };
    messageService = { add: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [
        PiiObfuscationComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: RemediationConfigService, useValue: remediationConfigMock },
        { provide: RemediationApiService, useValue: api },
        { provide: MessageService, useValue: messageService },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams$ } },
      ],
    }).compileComponents();
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(PiiObfuscationComponent);
    fixture.detectChanges();
  }

  function createEnabledComponent(params: Record<string, string> = { spaceKey: 'SPACE' }): void {
    remediationConfigMock.enabled.set(true);
    queryParams$.next(convertToParamMap(params));
    createComponent();
  }

  function selectionService(): ObfuscationSelectionService {
    return fixture.debugElement.injector.get(ObfuscationSelectionService);
  }

  function viewStateService(): ObfuscationViewStateService {
    return fixture.debugElement.injector.get(ObfuscationViewStateService);
  }

  function query<T extends HTMLElement>(testId: string): T | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function lastSearchRequest(): RemediationFindingsSearchRequest {
    return api.searchFindings.mock.calls[api.searchFindings.mock.calls.length - 1][0];
  }

  it('Should_ShowFeatureDisabledState_When_FlagOff', () => {
    createComponent();

    const disabled = query('obfuscation-feature-disabled');
    expect(disabled).toBeTruthy();
    expect(disabled?.textContent).toContain('désactivé');
    expect(query('obfuscation-content')).toBeFalsy();
  });

  it('Should_NotCallBackend_When_FlagOff', () => {
    createComponent();

    expect(api.searchFindings).not.toHaveBeenCalled();
    expect(api.planObfuscation).not.toHaveBeenCalled();
  });

  it('Should_ShowPageContent_When_FlagOn', () => {
    createEnabledComponent();

    expect(query('obfuscation-content')).toBeTruthy();
    expect(query('obfuscation-feature-disabled')).toBeFalsy();
  });

  it('Should_SetScopeFromQueryParams_When_Loaded', () => {
    queryParams$.next(
      convertToParamMap({ spaceKey: 'SPACE', pageId: 'p1', attachmentName: 'doc.pdf' })
    );

    createComponent();

    expect(selectionService().scope()).toEqual({
      spaceKey: 'SPACE',
      pageId: 'p1',
      attachmentName: 'doc.pdf',
    });
  });

  it('Should_OmitOptionalScopeParts_When_ParamsAbsent', () => {
    queryParams$.next(convertToParamMap({ spaceKey: 'SPACE' }));

    createComponent();

    expect(selectionService().scope()).toEqual({ spaceKey: 'SPACE' });
  });

  it('Should_SearchWithScopeAndDefaults_When_FlagOn', () => {
    createEnabledComponent();

    expect(api.searchFindings).toHaveBeenCalledTimes(1);
    expect(lastSearchRequest()).toMatchObject({
      scope: { spaceKey: 'SPACE' },
      groupBy: 'type',
      statusFilter: 'ALL',
      page: 0,
      pageSize: 20,
    });
  });

  it('Should_DisplaySpaceKeyHeading_When_FlagOn', () => {
    createEnabledComponent();

    const heading = fixture.nativeElement.querySelector('h2');
    expect(heading.textContent).toContain('SPACE');
  });

  it('Should_RenderTotalsVerbatim_When_RowsWouldImplyOtherCounts', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ totals: { pending: 999, handled: 31, falsePositive: 7, total: 12 } }))
    );

    createEnabledComponent();

    const stats = query('obfuscation-stats');
    expect(stats?.textContent).toContain('999');
    expect(stats?.textContent).toContain('31');
    expect(stats?.textContent).toContain('7');
  });

  it('Should_OpenOnlyFirstGroup_When_FirstResponseArrives', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [group({ key: 'A' }), group({ key: 'B' })] }))
    );

    createEnabledComponent();

    expect([...viewStateService().openAccordions()]).toEqual(['A']);
  });

  it('Should_PreselectAllSeveritiesAndShowBanner_When_PreselectParamTrue', () => {
    createEnabledComponent({ spaceKey: 'SPACE', preselect: 'true' });

    const dto = selectionService().buildSelectionDto();
    expect([...dto.severities].sort((a, b) => a.localeCompare(b))).toEqual([
      'HIGH',
      'LOW',
      'MEDIUM',
    ]);
      expect(lastSearchRequest().selection.severities).toHaveLength(3);
    const banner = query('obfuscation-entry-banner');
    expect(banner).toBeTruthy();
    expect(banner?.getAttribute('role')).toBe('status');
  });

  it('Should_HideBanner_When_Dismissed', () => {
    createEnabledComponent({ spaceKey: 'SPACE', preselect: 'true' });

    query('obfuscation-entry-banner-dismiss')?.click();
    fixture.detectChanges();

    expect(query('obfuscation-entry-banner')).toBeFalsy();
  });

  it('Should_SelectAllSeverities_When_SelectAllPendingClicked', () => {
    createEnabledComponent();

    // The select-all is a p-checkbox: the testid is on the host, the toggle on its input.
    query('obfuscation-select-all-pending')?.querySelector('input')?.click();

    expect(selectionService().checkedSeverities().size).toBe(3);
    expect(api.planObfuscation).toHaveBeenCalled();
  });

  it('Should_ClearSelection_When_ToggledWhileAllSelected', () => {
    createEnabledComponent();

    query('obfuscation-select-all-pending')?.querySelector('input')?.click();
    expect(fixture.componentInstance.allPendingSelected()).toBe(true);

    fixture.detectChanges();
    query('obfuscation-select-all-pending')?.querySelector('input')?.click();

    expect(selectionService().checkedSeverities().size).toBe(0);
    expect(fixture.componentInstance.allPendingSelected()).toBe(false);
  });

  it('Should_ResetPageAndSearch_When_StatusFilterChanged', () => {
    createEnabledComponent();
    viewStateService().page.set(3);

    fixture.componentInstance.setStatusFilter('PENDING');

    expect(viewStateService().page()).toBe(0);
    expect(lastSearchRequest().statusFilter).toBe('PENDING');
  });

  it('Should_ReopenFirstGroupOnly_When_GroupByChanges', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [group({ key: 'high' }), group({ key: 'low' })] }))
    );
    createEnabledComponent();
    viewStateService().openAll(['high', 'low']);

    fixture.componentInstance.setGroupBy('severity');

    expect(lastSearchRequest().groupBy).toBe('severity');
    expect([...viewStateService().openAccordions()]).toEqual(['high']);
  });

  it('Should_CheckTypeCriterion_When_TypeMasterToggledFromNone', () => {
    createEnabledComponent();

    fixture.componentInstance.onMasterToggled(group({ masterState: 'partial' }));

    expect([...selectionService().checkedTypes()]).toEqual(['EMAIL']);
    expect(api.planObfuscation).toHaveBeenCalled();
  });

  it('Should_UncheckCriterion_When_MasterToggledFromAll', () => {
    createEnabledComponent();
    selectionService().checkType('EMAIL');

    fixture.componentInstance.onMasterToggled(group({ masterState: 'all' }));

    expect(selectionService().checkedTypes().size).toBe(0);
  });

  it('Should_UncheckSeverity_When_SeverityMasterToggledWithUppercaseBackendKey', () => {
    createEnabledComponent();
    fixture.componentInstance.setGroupBy('severity');
    selectionService().checkSeverity('HIGH');

    fixture.componentInstance.onMasterToggled(
      group({ key: 'HIGH', masterState: 'all' })
    );

    expect(selectionService().checkedSeverities().size).toBe(0);
  });

  it('Should_DeselectTypeGroupViaCrossAxis_When_SelectionDrivenBySeverities', () => {
    api.searchFindings.mockReturnValue(
      of(
        searchResponse({
          groups: [
            group({ key: 'EMAIL', masterState: 'all' }),
            group({ key: 'PHONE', masterState: 'all' }),
            group({ key: 'IBAN', masterState: 'all' }),
          ],
        })
      )
    );
    createEnabledComponent({ spaceKey: 'SPACE', preselect: 'true' });

    fixture.componentInstance.onMasterToggled(group({ key: 'EMAIL', masterState: 'all' }));

    const dto = selectionService().buildSelectionDto();
    expect(dto.severities).toEqual([]);
    expect([...dto.piiTypes].sort()).toEqual(['IBAN', 'PHONE']);
  });

  it('Should_IncludeFinding_When_UnselectedPendingRowChecked', () => {
    createEnabledComponent();

    fixture.componentInstance.onRowToggled(finding({ selected: false }));

    expect([...selectionService().includedFindingIds()]).toEqual(['f1']);
    expect(api.changeFindingsStatus).not.toHaveBeenCalled();
  });

  it('Should_FlagFalsePositiveImmediately_When_SelectedRowUnchecked', () => {
    createEnabledComponent();
    api.searchFindings.mockClear();

    fixture.componentInstance.onRowToggled(finding({ selected: true }));

    expect([...selectionService().excludedFindingIds()]).toEqual(['f1']);
    expect(api.changeFindingsStatus).toHaveBeenCalledWith({
      changes: [{ findingId: 'f1', targetStatus: 'FALSE_POSITIVE' }],
    });
    expect(api.searchFindings).toHaveBeenCalledTimes(1);
  });

  it('Should_RestoreAndReselect_When_FalsePositiveRowRechecked', () => {
    createEnabledComponent();
    selectionService().excludeFinding('f1');

    fixture.componentInstance.onRowToggled(finding({ status: 'FALSE_POSITIVE' }));

    expect([...selectionService().includedFindingIds()]).toEqual(['f1']);
    expect(api.changeFindingsStatus).toHaveBeenCalledWith({
      changes: [{ findingId: 'f1', targetStatus: 'PENDING' }],
    });
  });

  it('Should_RestoreWithoutReselecting_When_RestoreActionUsed', () => {
    createEnabledComponent();
    selectionService().excludeFinding('f1');

    fixture.componentInstance.onRowRestore(finding({ status: 'FALSE_POSITIVE' }));

    expect(selectionService().includedFindingIds().size).toBe(0);
    expect(selectionService().excludedFindingIds().size).toBe(0);
    expect(api.changeFindingsStatus).toHaveBeenCalledWith({
      changes: [{ findingId: 'f1', targetStatus: 'PENDING' }],
    });
  });

  it('Should_MarkManually_When_RowQuickActionUsed', () => {
    createEnabledComponent();

    fixture.componentInstance.onRowMarkManual(finding());

    expect(api.changeFindingsStatus).toHaveBeenCalledWith({
      changes: [{ findingId: 'f1', targetStatus: 'MANUALLY_HANDLED' }],
    });
  });

  it('Should_PlanBeforeOpeningDialog_When_ObfuscateRequested', () => {
    createEnabledComponent();
    api.planObfuscation.mockClear();

    fixture.componentInstance.openConfirmDialog();

    expect(api.planObfuscation).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.dialogVisible()).toBe(true);
    expect(viewStateService().lastPlan()).toEqual(plan());
  });

  it('Should_CreateJobWithChecksumAndStartPolling_When_Confirmed', () => {
    createEnabledComponent();
    fixture.componentInstance.openConfirmDialog();

    fixture.componentInstance.confirmObfuscation();

    expect(api.createJob).toHaveBeenCalledWith({
      selection: selectionService().buildSelectionDto(),
      selectionChecksum: 'sum-1',
    });
    expect(fixture.componentInstance.dialogVisible()).toBe(false);
    expect(fixture.componentInstance.activeJobId()).toBe('job-1');
  });

  it('Should_ReplanAndKeepDialogOpen_When_JobCreationConflicts', () => {
    createEnabledComponent();
    fixture.componentInstance.openConfirmDialog();
    api.planObfuscation.mockClear();
    api.createJob.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' }))
    );

    fixture.componentInstance.confirmObfuscation();

    expect(api.planObfuscation).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.dialogVisible()).toBe(true);
    expect(fixture.componentInstance.activeJobId()).toBeNull();
  });

  it('Should_ToastClearSelectionAndRefresh_When_JobCompletes', () => {
    createEnabledComponent();
    selectionService().checkType('EMAIL');
    api.searchFindings.mockClear();

    fixture.componentInstance.onJobCompleted(
      runningJob({ status: 'COMPLETED', processed: 5, total: 5 })
    );

    expect(fixture.componentInstance.activeJobId()).toBeNull();
    expect(selectionService().checkedTypes().size).toBe(0);
    expect(api.searchFindings).toHaveBeenCalledTimes(1);
    const toast = messageService.add.mock.calls[0][0];
    expect(toast.severity).toBe('success');
    expect(toast.detail).toContain('5 finding(s) supprimé(s)');
    expect(toast.detail).not.toContain('<b>');
  });

  it('Should_WarnWithoutRecomputingCounts_When_JobCompletesWithErrors', () => {
    createEnabledComponent();

    fixture.componentInstance.onJobCompleted(
      runningJob({ status: 'COMPLETED_WITH_ERRORS', processed: 5, total: 5 })
    );

    const toast = messageService.add.mock.calls[0][0];
    expect(toast.severity).toBe('warn');
  });

  it('Should_MarkEntireSelectionServerSide_When_BulkMarkTreated', () => {
    createEnabledComponent();
    selectionService().checkType('EMAIL');
    api.changeFindingsStatusBySelection.mockReturnValue(
      of({ applied: ['f1', 'f2', 'f3'], rejected: [] })
    );

    fixture.componentInstance.markSelectionTreated();

    expect(api.changeFindingsStatusBySelection).toHaveBeenCalledWith({
      selection: {
        scope: { spaceKey: 'SPACE' },
        piiTypes: ['EMAIL'],
        severities: [],
        excludedFindingIds: [],
        includedFindingIds: [],
      },
      targetStatus: 'MANUALLY_HANDLED',
    });
    expect(api.changeFindingsStatus).not.toHaveBeenCalled();
    expect(selectionService().checkedTypes().size).toBe(0);
    const toast = messageService.add.mock.calls[0][0];
    expect(toast.detail).toContain('3 finding(s) marqué(s) traité(s)');
  });

  it('Should_NotCallBackend_When_BulkMarkTreatedWithoutSelectionCriteria', () => {
    createEnabledComponent();

    fixture.componentInstance.markSelectionTreated();

    expect(api.changeFindingsStatusBySelection).not.toHaveBeenCalled();
  });

  it('Should_ReportEntireSelectionServerSide_When_BulkReportFalsePositive', () => {
    createEnabledComponent();
    selectionService().checkType('EMAIL');
    api.changeFindingsStatusBySelection.mockReturnValue(
      of({ applied: ['f1', 'f2'], rejected: [] })
    );

    fixture.componentInstance.reportSelectionFalsePositive();

    expect(api.changeFindingsStatusBySelection).toHaveBeenCalledWith({
      selection: {
        scope: { spaceKey: 'SPACE' },
        piiTypes: ['EMAIL'],
        severities: [],
        excludedFindingIds: [],
        includedFindingIds: [],
      },
      targetStatus: 'FALSE_POSITIVE',
    });
    expect(api.changeFindingsStatus).not.toHaveBeenCalled();
    expect(selectionService().checkedTypes().size).toBe(0);
    const toast = messageService.add.mock.calls[0][0];
    expect(toast.detail).toContain('2 finding(s) signalé(s) comme faux positif(s)');
  });

  it('Should_NotCallBackend_When_BulkReportFalsePositiveWithoutSelectionCriteria', () => {
    createEnabledComponent();

    fixture.componentInstance.reportSelectionFalsePositive();

    expect(api.changeFindingsStatusBySelection).not.toHaveBeenCalled();
  });

  it('Should_ClearSelectionAndDropPlan_When_BulkCleared', () => {
    createEnabledComponent();
    selectionService().checkType('EMAIL');
    fixture.componentInstance.openConfirmDialog();
    fixture.componentInstance.dialogVisible.set(false);

    fixture.componentInstance.clearSelection();

    expect(selectionService().checkedTypes().size).toBe(0);
    expect(viewStateService().lastPlan()).toBeNull();
  });

  it('Should_ShowItemChipAndDropItemScope_When_ChipRemoved', () => {
    createEnabledComponent({ spaceKey: 'SPACE', pageId: 'p1' });
    expect(query('obfuscation-item-chip')?.textContent).toContain('p1');

    query('obfuscation-item-chip-remove')?.click();

    expect(selectionService().scope()).toEqual({ spaceKey: 'SPACE' });
    expect(lastSearchRequest().scope).toEqual({ spaceKey: 'SPACE' });
  });

  it('Should_ShowEmptyState_When_NoGroupReturned', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [], totals: { pending: 0, handled: 0, falsePositive: 0, total: 0 }, totalElements: 0, totalGroups: 0 }))
    );

    createEnabledComponent();

    expect(query('obfuscation-empty')?.textContent).toContain('Aucun finding');
  });

  it('Should_RenderPagerFromResponse_When_Loaded', () => {
    api.searchFindings.mockReturnValue(of(searchResponse({ page: 0, pageSize: 20, totalGroups: 44 })));

    createEnabledComponent();

    expect(query('obfuscation-pager-label')?.textContent).toContain('1–20 sur 44 groupes');
    // The caption paginator uses <p-button>: the testid is on the host, the disabled
    // state lives on the rendered inner <button>.
    expect(query('obfuscation-pager-prev')?.querySelector('button')?.disabled).toBe(true);
    expect(query('obfuscation-pager-next')?.querySelector('button')?.disabled).toBe(false);
  });

  it('Should_RequestNextPage_When_NextClicked', () => {
    api.searchFindings.mockReturnValue(of(searchResponse({ page: 0, pageSize: 20, totalGroups: 44 })));
    createEnabledComponent();

    query('obfuscation-pager-next')?.querySelector('button')?.click();

    expect(lastSearchRequest().page).toBe(1);
  });

  it('Should_ResetPageAndSearch_When_SearchTextTyped', () => {
    createEnabledComponent();
    viewStateService().page.set(2);
    const input = query<HTMLInputElement>('obfuscation-search');

    input!.value = 'john';
    input!.dispatchEvent(new Event('input'));

    expect(viewStateService().page()).toBe(0);
    expect(lastSearchRequest().searchText).toBe('john');
  });

  // Group-row rendering guarantees (backend values rendered verbatim), migrated from
  // the retired ObfuscationGroupListComponent now that the table lives in this template.
  it('Should_RenderMasterStateVerbatim_When_RowsWouldImplyOtherwise', async () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [group({ masterState: 'all', findings: [finding({ selected: false })] })] }))
    );

    createEnabledComponent();
    // NgModel writes its initial value in a microtask; flush it before asserting.
    await fixture.whenStable();
    fixture.detectChanges();

    // Master checkbox reflects the backend group state, not the (unselected) row.
    // PrimeNG applies the checked class to the p-checkbox host (the testid element).
    const master = query('obfuscation-group-master');
    expect(master?.classList.contains('p-checkbox-checked')).toBe(true);
  });

  it('Should_RenderGroupTotalVerbatim_When_VisibleRowCountDiffers', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [group({ total: 344, findings: [finding()] })] }))
    );

    createEnabledComponent();

    expect(query('obfuscation-group-count')?.textContent).toContain('344');
  });

  it('Should_ShowOccurrenceCount_When_MoreOccurrencesThanDistinctValues', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [group({ total: 4, occurrenceCount: 9 })] }))
    );

    createEnabledComponent();

    expect(query('obfuscation-group-occurrences')?.textContent).toContain('9 occurrences');
  });

  it('Should_ShowCrossPageHint_When_FewerRowsThanGroupTotal', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [group({ total: 12, findings: [finding()] })] }))
    );

    createEnabledComponent();

    expect(query('obfuscation-group-hint')?.textContent).toContain('1 sur 12');
  });

  it('Should_UseSeverityGroupKeyForDot_When_GroupedBySeverity', () => {
    api.searchFindings.mockReturnValue(
      of(searchResponse({ groups: [group({ key: 'high', label: 'Critique', severity: undefined })] }))
    );
    createEnabledComponent();

    fixture.componentInstance.setGroupBy('severity');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.ob-dot--high')).toBeTruthy();
  });
});
