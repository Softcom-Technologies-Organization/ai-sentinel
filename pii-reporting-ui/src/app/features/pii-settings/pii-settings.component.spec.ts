import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PiiSettingsComponent } from './pii-settings.component';
import { CategoryGroup, ConcurrencyBenchStatus, PiiDetectionConfig, PiiTypeConfig } from '../../core/models/pii-detection-config.model';
import { ConfluenceConnectionConfig } from '../../core/models/confluence-connection-config.model';

const MOCK_DETECTOR_CONFIG: PiiDetectionConfig = {
  presidioEnabled: true,
  regexEnabled: true,
  postfilterEnabled: false,
  ministralEnabled: false,
  ministralChunkSize: 1024,
  ministralOverlap: 128,
  ministralConcurrency: 4,
  ministralConcurrencyAuto: false,
  ministralConcurrencyTunedSignature: null,
  defaultThreshold: 0.75,
  lmStudioHost: 'localhost',
  lmStudioPort: 1234,
  updatedAt: '2026-03-16T10:00:00',
};

const MOCK_CONFLUENCE_CONFIG: ConfluenceConnectionConfig = {
  baseUrl: 'https://confluence.example.com',
  username: 'user',
  connectTimeout: 30000,
  readTimeout: 60000,
  maxRetries: 3,
  pagesLimit: 25,
  maxPages: 1000,
  deploymentType: 'CLOUD',
  configured: true,
};

const FR_TRANSLATIONS = {
  common: { success: 'Succès', error: 'Erreur', warning: 'Attention', close: 'Fermer', yes: 'Oui', no: 'Non' },
  settings: {
    nav: { detectors: 'Détecteurs', thresholds: 'Seuils', piiTypes: 'Types PII', confluence: 'Confluence' },
    sections: { detectors: 'Détecteurs', threshold: 'Seuils', performance: 'Perf' },
    detectors: {
      presidio: { label: 'Presidio', description: 'desc' },
      regex: { label: 'Regex', description: 'desc' },
      ministral: {
        label: 'Ministral-PII',
        description: 'desc',
        advancedParams: 'Paramètres avancés',
        chunkSize: 'Taille de fenêtre',
        chunkSizeHint: 'hint',
        overlap: 'Chevauchement',
        overlapHint: 'hint',
        overlapError: 'erreur',
        concurrencyTitle: 'Concurrence Ministral',
        concurrencyAuto: 'Auto-réglage au démarrage',
        concurrency: 'Requêtes simultanées',
        concurrencyHint: 'hint',
        concurrencyCurrent: 'Concurrence actuelle',
        concurrencySignature: 'Signature du réglage',
        concurrencyNotTuned: 'pas encore réglé',
        retune: 'Re-régler au prochain redémarrage',
        retuneHint: 'hint',
        runBenchmark: 'Lancer le benchmark maintenant',
        runBenchmarkHint: 'hint',
        benchmarkRunning: 'Benchmark en cours…',
        benchmarkDone: 'Benchmark terminé — concurrence réglée à {{concurrency}}.',
        benchmarkFailed: 'Le benchmark a échoué : {{error}}',
      },
      postfilter: { label: 'Post-filtre déterministe', description: 'desc' },
      detectorLabel: 'Détecteur',
    },
    messages: { loadError: 'Erreur', saveAllSuccess: 'Sauvegardé', saveAllError: 'Erreur' },
    confluence: { messages: { loadError: 'Erreur', saveSuccess: 'OK', saveError: 'Erreur' } },
    actions: { resetAll: 'Reset', cancel: 'Annuler', saveAll: 'Sauvegarder' },
    validation: { atLeastOneDetector: 'Au moins un' },
  },
};

describe('PiiSettingsComponent', () => {
  let component: PiiSettingsComponent;
  let fixture: ComponentFixture<PiiSettingsComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PiiSettingsComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: FR_TRANSLATIONS },
          translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PiiSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // Respond to initial config loads
    httpMock.expectOne('/api/v1/pii-detection/config').flush(MOCK_DETECTOR_CONFIG);
    httpMock.expectOne('/api/v1/pii-detection/pii-types/grouped').flush([]);
    // Confluence child may or may not load depending on section visibility
    httpMock.match('/api/v1/confluence/connection-config').forEach(
      (req: TestRequest) => req.flush(MOCK_CONFLUENCE_CONFIG)
    );

    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('Should_NotStickOnSpinner_When_OnlyConfluenceSettingsChanged', () => {
    // Given - Navigate to Confluence section to render child component
    component.setActiveSection('confluence');
    fixture.detectChanges();

    // Handle Confluence config load triggered by section change
    httpMock.match('/api/v1/confluence/connection-config').forEach(
      (req: TestRequest) => req.flush(MOCK_CONFLUENCE_CONFIG)
    );
    fixture.detectChanges();

    // Simulate Confluence form dirty state via model signal update
    const confluenceChild = component.confluenceSettings();
    expect(confluenceChild).toBeTruthy();
    confluenceChild!.model.update(m => ({ ...m, baseUrl: 'https://new-url.example.com' }));

    // Ensure parent form is NOT dirty (only Confluence changed)
    expect(component.configForm.dirty).toBe(false);
    expect(component.hasUnsavedTypeChanges()).toBe(false);

    // When - User clicks "Save All"
    component.onSaveAll();

    // Then - Parent saving signal should remain false (nothing to save on parent side)
    // onSaveAll() returns immediately when neither configForm is dirty nor types have changed
    expect(component.saving()).toBe(false);

    // Confluence child handles its own save via its own HTTP call
    const confluencePutReqs = httpMock.match('/api/v1/confluence/connection-config');
    confluencePutReqs.forEach(
      (req: TestRequest) =>
        req.flush({ ...MOCK_CONFLUENCE_CONFIG, baseUrl: 'https://new-url.example.com' })
    );

    fixture.detectChanges();

    // Verify Confluence child saving is also resolved
    expect(confluenceChild!.saving()).toBe(false);
  });

  it('Should_SaveDetectorChanges_When_DetectorConfigModified', () => {
    // Given - Detector config changed
    component.configForm.patchValue({ defaultThreshold: 0.9 });
    component.configForm.markAsDirty();

    // When
    component.onSaveAll();

    // Then - saving should be true while request in flight
    expect(component.saving()).toBe(true);

    const req = httpMock.expectOne('/api/v1/pii-detection/config');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...MOCK_DETECTOR_CONFIG, defaultThreshold: 0.9 });

    // saving should be false after response (flush is synchronous)
    expect(component.saving()).toBe(false);
  });

  it('Should_FailAtLeastOneDetectorValidation_When_AllDetectorsDisabled', () => {
    // Given - All detectors disabled
    component.configForm.patchValue({
      presidioEnabled: false,
      regexEnabled: false,
      ministralEnabled: false,
    });

    // Then - Form must carry atLeastOneDetector error
    expect(component.configForm.hasError('atLeastOneDetector')).toBe(true);
  });

  it('Should_CreatePostfilterEnabledControl_When_FormInitialized', () => {
    const prefilterControl = component.configForm.get('postfilterEnabled');
    expect(prefilterControl).toBeTruthy();
    expect(prefilterControl?.value).toBe(false);
  });

  it('Should_FailOverlapValidation_When_OverlapNotLessThanChunkSize', () => {
    // Given - overlap equal to (>=) chunk size
    component.configForm.patchValue({ ministralChunkSize: 512, ministralOverlap: 512 });

    // Then - Form must carry the cross-field error and be invalid
    expect(component.configForm.hasError('ministralOverlapGteChunkSize')).toBe(true);
    expect(component.configForm.invalid).toBe(true);
  });

  it('Should_PassOverlapValidation_When_OverlapLessThanChunkSize', () => {
    // Given - overlap strictly below chunk size
    component.configForm.patchValue({ ministralChunkSize: 1024, ministralOverlap: 128 });

    // Then - Form must not carry the cross-field error
    expect(component.configForm.hasError('ministralOverlapGteChunkSize')).toBe(false);
  });

  it('Should_ReflectDetectorMasterState_When_DetectorEnabledQueried', () => {
    // Given - presidio master on, ministral master off (from MOCK config)
    expect(component.isDetectorEnabled('PRESIDIO')).toBe(true);
    expect(component.isDetectorEnabled('MINISTRAL')).toBe(false);
    // Unknown detector defaults to enabled
    expect(component.isDetectorEnabled('UNKNOWN')).toBe(true);
  });

  it('Should_CollapseDetectorSubSection_When_MasterToggledOff', () => {
    // Given - PRESIDIO master is on and its sub-section expanded
    expect(component.isDetectorCollapsed('PRESIDIO')).toBe(false);

    // When - the master toggle is turned off then the handler runs
    component.configForm.patchValue({ presidioEnabled: false });
    component.onDetectorMasterToggle('PRESIDIO');

    // Then - the PRESIDIO sub-section is collapsed
    expect(component.isDetectorCollapsed('PRESIDIO')).toBe(true);

    // When - the master toggle is turned back on
    component.configForm.patchValue({ presidioEnabled: true });
    component.onDetectorMasterToggle('PRESIDIO');

    // Then - the PRESIDIO sub-section is expanded again
    expect(component.isDetectorCollapsed('PRESIDIO')).toBe(false);
  });

  it('Should_CollapseDisabledDetectorsOnLoad_When_ConfigLoaded', () => {
    // The MOCK config has ministral disabled -> its sub-section must start
    // collapsed; the enabled ones must start expanded.
    expect(component.isDetectorCollapsed('MINISTRAL')).toBe(true);
    expect(component.isDetectorCollapsed('PRESIDIO')).toBe(false);
    expect(component.isDetectorCollapsed('REGEX')).toBe(false);
  });

  it('Should_RenderTypeToggleOff_When_DetectorDisabled', async () => {
    // Given - an enabled PRESIDIO type rendered in the "Types d'IPI" section
    seedPresidioType({ enabled: true });
    component.setActiveSection('pii_types');
    fixture.detectChanges();
    await fixture.whenStable();

    // When - the PRESIDIO master goes off and its group is expanded back
    component.configForm.patchValue({ presidioEnabled: false });
    component.onDetectorMasterToggle('PRESIDIO');
    component.toggleDetectorCollapse('PRESIDIO');
    fixture.detectChanges();
    // NgModel writes the new value to the switch in a microtask, so the DOM only
    // reflects it on the change detection pass that follows.
    await fixture.whenStable();
    fixture.detectChanges();

    // Then - the switch reads off and locked, while the stored value is untouched
    const toggle: HTMLInputElement = fixture.nativeElement
      .querySelector('.pii-type-switches input[type="checkbox"]');
    expect(toggle.checked).toBe(false);
    expect(toggle.disabled).toBe(true);
    expect(component.groupedPiiTypes()[0].categories[0].types[0].enabled).toBe(true);
  });

  // ========== Helpers ==========

  function seedPresidioType(overrides: Partial<PiiTypeConfig> = {}): PiiTypeConfig {
    const type: PiiTypeConfig = {
      id: 1,
      piiType: 'EMAIL',
      detector: 'PRESIDIO',
      enabled: true,
      threshold: 0.5,
      category: 'CONTACT',
      ...overrides,
    };
    component.groupedPiiTypes.set([
      { detector: 'PRESIDIO', categories: [{ category: 'CONTACT', types: [{ ...type }] }] },
    ]);
    (component as unknown as { originalPiiTypes: { set: (m: Map<string, PiiTypeConfig>) => void } })
      .originalPiiTypes.set(new Map([['PRESIDIO:EMAIL', { ...type }]]));
    return type;
  }

  // ========== PII type modification handlers ==========

  it('Should_TrackModification_When_PiiTypeToggleChanged', () => {
    const type = seedPresidioType({ enabled: true });

    component.onPiiTypeToggleChange(type, false);

    expect(component.hasUnsavedTypeChanges()).toBe(true);
    expect(component.modifiedTypesCount).toBe(1);
  });

  it('Should_TrackModification_When_PiiTypeThresholdChanged', () => {
    const type = seedPresidioType({ threshold: 0.5 });

    component.onPiiTypeThresholdChange(type, 0.9);

    expect(component.hasUnsavedTypeChanges()).toBe(true);
  });

  it('Should_DropModification_When_ThresholdRevertedToOriginal', () => {
    const type = seedPresidioType({ threshold: 0.5 });

    component.onPiiTypeThresholdChange(type, 0.9);
    component.onPiiTypeThresholdChange({ ...type, threshold: 0.9 }, 0.5);

    expect(component.hasUnsavedTypeChanges()).toBe(false);
  });

  it('Should_IgnoreModification_When_OriginalNotFound', () => {
    const orphan: PiiTypeConfig = {
      id: 99,
      piiType: 'UNKNOWN',
      detector: 'REGEX',
      enabled: true,
      threshold: 0.5,
      category: 'CONTACT',
    };

    component.onPiiTypeToggleChange(orphan, false);

    expect(component.hasUnsavedTypeChanges()).toBe(false);
  });

  // ========== Category master toggle ==========

  it('Should_ReportCategoryEnabled_When_AllTypesEnabled', () => {
    const group: CategoryGroup = { category: 'CONTACT', types: [
      { id: 1, piiType: 'EMAIL', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
      { id: 2, piiType: 'PHONE', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
    ] };

    expect(component.isCategoryEnabled(group)).toBe(true);
  });

  it('Should_ReportCategoryDisabled_When_OneTypeDisabled', () => {
    const group: CategoryGroup = { category: 'CONTACT', types: [
      { id: 1, piiType: 'EMAIL', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
      { id: 2, piiType: 'PHONE', detector: 'PRESIDIO', enabled: false, threshold: 0.5, category: 'CONTACT' },
    ] };

    expect(component.isCategoryEnabled(group)).toBe(false);
  });

  it('Should_DisableEveryTypeOfCategory_When_CategoryToggledOff', () => {
    component.groupedPiiTypes.set([
      { detector: 'PRESIDIO', categories: [{ category: 'CONTACT', types: [
        { id: 1, piiType: 'EMAIL', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
        { id: 2, piiType: 'PHONE', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
      ] }] },
    ]);
    (component as unknown as { originalPiiTypes: { set: (m: Map<string, PiiTypeConfig>) => void } })
      .originalPiiTypes.set(new Map([
        ['PRESIDIO:EMAIL', { id: 1, piiType: 'EMAIL', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' }],
        ['PRESIDIO:PHONE', { id: 2, piiType: 'PHONE', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' }],
      ]));

    const group = component.groupedPiiTypes()[0].categories[0];
    component.onCategoryToggleChange(group, false);

    expect(component.modifiedTypesCount).toBe(2);
    const enabledStates = component.groupedPiiTypes()[0].categories[0].types.map(t => t.enabled);
    expect(enabledStates).toEqual([false, false]);
  });

  // ========== onSavePiiTypes ==========

  it('Should_NotCallBackend_When_SavePiiTypesWithoutModifications', () => {
    component.onSavePiiTypes();

    expect(component.saving()).toBe(false);
    httpMock.expectNone('/api/v1/pii-detection/pii-types/bulk');
  });

  it('Should_PersistTypes_When_SavePiiTypesWithModifications', () => {
    const type = seedPresidioType({ threshold: 0.5 });
    component.onPiiTypeThresholdChange(type, 0.9);

    component.onSavePiiTypes();
    expect(component.saving()).toBe(true);

    const req = httpMock.expectOne('/api/v1/pii-detection/pii-types/bulk');
    expect(req.request.method).toBe('PUT');
    req.flush([{ ...type, threshold: 0.9 }]);

    expect(component.saving()).toBe(false);
    expect(component.hasUnsavedTypeChanges()).toBe(false);
  });

  it('Should_StopSaving_When_SavePiiTypesFails', () => {
    const type = seedPresidioType({ threshold: 0.5 });
    component.onPiiTypeThresholdChange(type, 0.9);

    component.onSavePiiTypes();
    httpMock.expectOne('/api/v1/pii-detection/pii-types/bulk').flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.saving()).toBe(false);
  });

  // ========== Ministral: confidence threshold is inert (hidden) ==========

  it('Should_HideThresholdInput_When_MinistralTypeRow', () => {
    // Given - one Presidio type (real score → threshold applies) and one Ministral
    // type (fixed score 1.0 → threshold inert), both enabled and expanded.
    component.groupedPiiTypes.set([
      { detector: 'PRESIDIO', categories: [{ category: 'CONTACT', types: [
        { id: 1, piiType: 'EMAIL', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
      ] }] },
      { detector: 'MINISTRAL', categories: [{ category: 'IDENTITY', types: [
        { id: 2, piiType: 'FIRST_NAME', detector: 'MINISTRAL', enabled: true, threshold: 0.5, category: 'IDENTITY' },
      ] }] },
    ]);
    component.collapsedDetectors.set(new Set());
    component.setActiveSection('pii_types');

    // When
    fixture.detectChanges();

    // Then - only the Presidio row renders a threshold input; the Ministral group
    // shows the explanatory note instead.
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('p-inputnumber')).toHaveLength(1);
    expect(el.querySelector('.ministral-no-threshold-hint')).not.toBeNull();
  });

  // ========== Ministral concurrency ==========

  it('Should_PatchConcurrencyControls_When_ConfigLoaded', () => {
    expect(component.configForm.get('ministralConcurrency')?.value).toBe(4);
    expect(component.configForm.get('ministralConcurrencyAuto')?.value).toBe(false);
    expect(component.configForm.get('ministralConcurrencyTunedSignature')?.value).toBeNull();
  });

  it('Should_FailConcurrencyValidation_When_OutOfRange', () => {
    component.configForm.patchValue({ ministralConcurrency: 0 });
    expect(component.configForm.get('ministralConcurrency')?.invalid).toBe(true);

    component.configForm.patchValue({ ministralConcurrency: 17 });
    expect(component.configForm.get('ministralConcurrency')?.invalid).toBe(true);

    component.configForm.patchValue({ ministralConcurrency: 8 });
    expect(component.configForm.get('ministralConcurrency')?.valid).toBe(true);
  });

  it('Should_ShowManualConcurrencyInput_When_AutoTuneDisabled', () => {
    // Given - Ministral enabled with auto-tuning off (from MOCK config)
    component.configForm.patchValue({ ministralEnabled: true });
    fixture.detectChanges();

    // Then - the numeric input renders, the read-only display does not
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#ministralConcurrency')).not.toBeNull();
    expect(el.querySelector('.concurrency-readonly')).toBeNull();
  });

  it('Should_ShowReadonlyTunedState_When_AutoTuneEnabled', () => {
    // Given - Ministral enabled with auto-tuning on and no signature yet
    component.configForm.patchValue({ ministralEnabled: true, ministralConcurrencyAuto: true });
    fixture.detectChanges();

    // Then - the numeric input is gone; current value and placeholder show instead
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#ministralConcurrency')).toBeNull();
    const readonlyValues = el.querySelectorAll('.concurrency-readonly');
    expect(readonlyValues).toHaveLength(2);
    expect(readonlyValues[0].textContent).toContain('4');
    expect(readonlyValues[1].textContent).toContain('pas encore réglé');
  });

  it('Should_ShowTunedSignature_When_SignaturePresent', () => {
    component.configForm.patchValue({
      ministralEnabled: true,
      ministralConcurrencyAuto: true,
      ministralConcurrencyTunedSignature: 'cpu16-mem32',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const readonlyValues = el.querySelectorAll('.concurrency-readonly');
    expect(readonlyValues[1].textContent).toContain('cpu16-mem32');
  });

  it('Should_ClearSignatureAndSave_When_RetuneRequested', () => {
    // Given - a previously tuned signature loaded in the form
    component.configForm.patchValue({ ministralConcurrencyTunedSignature: 'cpu16-mem32' });

    // When
    component.onRetuneConcurrency();

    // Then - the signature is cleared and persisted through the save flow
    expect(component.saving()).toBe(true);
    const req = httpMock.expectOne('/api/v1/pii-detection/config');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.ministralConcurrencyTunedSignature).toBeNull();
    req.flush({ ...MOCK_DETECTOR_CONFIG, ministralConcurrencyTunedSignature: null });

    expect(component.saving()).toBe(false);
    expect(component.configForm.get('ministralConcurrencyTunedSignature')?.value).toBeNull();
  });

  // ========== Concurrency benchmark ==========

  describe('Concurrency benchmark', () => {
    const BENCH_RUN_URL = '/api/v1/pii-detection/concurrency-benchmark/run';
    const BENCH_STATUS_URL = '/api/v1/pii-detection/concurrency-benchmark/status';

    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    function benchStatus(overrides: Partial<ConcurrencyBenchStatus> = {}): ConcurrencyBenchStatus {
      return {
        status: 'RUNNING',
        progress: 0,
        message: null,
        concurrency: 4,
        tunedSignature: null,
        ...overrides,
      };
    }

    it('Should_PostRunAndPollStatus_When_BenchmarkButtonClicked', () => {
      // Given - Ministral enabled so the concurrency section renders
      component.configForm.patchValue({ ministralEnabled: true });
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;
      const button = el.querySelector('[data-testid="runBenchmarkButton"] button') as HTMLButtonElement;
      expect(button).not.toBeNull();

      // When - the button is clicked
      button.click();

      // Then - the run endpoint is POSTed and polling starts
      const runReq = httpMock.expectOne(BENCH_RUN_URL);
      expect(runReq.request.method).toBe('POST');
      runReq.flush(null);

      vi.advanceTimersByTime(0);
      httpMock.expectOne(BENCH_STATUS_URL).flush(benchStatus({ progress: 40, message: 'measuring' }));
      fixture.detectChanges();

      // Progress bar renders the polled progress and the button is disabled
      const progress = el.querySelector('[data-testid="benchProgress"]');
      expect(progress).not.toBeNull();
      expect(progress!.textContent).toContain('measuring');
      expect(progress!.textContent).toContain('40');
      expect(button.disabled).toBe(true);

      // When - the next poll reports DONE
      vi.advanceTimersByTime(1000);
      httpMock.expectOne(BENCH_STATUS_URL).flush(
        benchStatus({ status: 'DONE', progress: 100, concurrency: 6, tunedSignature: 'cpu16-mem32' })
      );

      // Then - the config is reloaded so the tuned concurrency is displayed
      httpMock.expectOne('/api/v1/pii-detection/config').flush({
        ...MOCK_DETECTOR_CONFIG,
        ministralEnabled: true,
        ministralConcurrency: 6,
        ministralConcurrencyTunedSignature: 'cpu16-mem32',
      });
      httpMock.expectOne('/api/v1/pii-detection/pii-types/grouped').flush([]);
      fixture.detectChanges();

      expect(component.benchRunning()).toBe(false);
      expect(component.configForm.get('ministralConcurrency')?.value).toBe(6);
      expect(el.querySelector('[data-testid="benchProgress"]')).toBeNull();
      expect(button.disabled).toBe(false);

      // And - polling has stopped
      vi.advanceTimersByTime(3000);
      httpMock.expectNone(BENCH_STATUS_URL);
    });

    it('Should_StopPollingAndShowError_When_BenchmarkFails', () => {
      component.onRunBenchmark();
      httpMock.expectOne(BENCH_RUN_URL).flush(null);

      vi.advanceTimersByTime(0);
      httpMock.expectOne(BENCH_STATUS_URL).flush(
        benchStatus({ status: 'FAILED', progress: 30, message: 'LM Studio unreachable' })
      );

      expect(component.benchRunning()).toBe(false);

      vi.advanceTimersByTime(3000);
      httpMock.expectNone(BENCH_STATUS_URL);
    });

    it('Should_IgnoreRunRequest_When_BenchmarkAlreadyRunning', () => {
      component.onRunBenchmark();
      httpMock.expectOne(BENCH_RUN_URL).flush(null);
      vi.advanceTimersByTime(0);
      httpMock.expectOne(BENCH_STATUS_URL).flush(benchStatus({ progress: 10 }));

      // When - a second run is requested while the first is still running
      component.onRunBenchmark();

      // Then - no second POST is issued
      expect(httpMock.match(BENCH_RUN_URL)).toHaveLength(0);

      // Cleanup - let the running benchmark fail to stop the polling loop
      vi.advanceTimersByTime(1000);
      httpMock.expectOne(BENCH_STATUS_URL).flush(benchStatus({ status: 'FAILED', message: 'boom' }));
    });

    it('Should_StopPollingAndShowError_When_StatusPollFails', () => {
      component.onRunBenchmark();
      httpMock.expectOne(BENCH_RUN_URL).flush(null);

      vi.advanceTimersByTime(0);
      httpMock.expectOne(BENCH_STATUS_URL).flush('boom', { status: 500, statusText: 'Server Error' });

      expect(component.benchRunning()).toBe(false);

      vi.advanceTimersByTime(3000);
      httpMock.expectNone(BENCH_STATUS_URL);
    });

    it('Should_NotStartPolling_When_RunRequestFails', () => {
      component.onRunBenchmark();
      httpMock.expectOne(BENCH_RUN_URL).flush('boom', { status: 500, statusText: 'Server Error' });

      expect(component.benchRunning()).toBe(false);

      vi.advanceTimersByTime(3000);
      httpMock.expectNone(BENCH_STATUS_URL);
    });

    it('Should_StopPolling_When_ComponentDestroyed', () => {
      component.onRunBenchmark();
      httpMock.expectOne(BENCH_RUN_URL).flush(null);
      vi.advanceTimersByTime(0);
      httpMock.expectOne(BENCH_STATUS_URL).flush(benchStatus({ progress: 10 }));

      // When - the component is destroyed mid-benchmark
      fixture.destroy();

      // Then - no further poll is issued
      vi.advanceTimersByTime(3000);
      expect(httpMock.match(BENCH_STATUS_URL)).toHaveLength(0);
    });
  });

  // ========== onSaveDetectorConfig ==========

  it('Should_MarkPristine_When_SaveDetectorConfigSucceeds', () => {
    component.configForm.patchValue({ defaultThreshold: 0.8 });
    component.configForm.markAsDirty();

    component.onSaveDetectorConfig();
    expect(component.saving()).toBe(true);

    httpMock.expectOne('/api/v1/pii-detection/config').flush({ ...MOCK_DETECTOR_CONFIG, defaultThreshold: 0.8 });

    expect(component.saving()).toBe(false);
    expect(component.configForm.pristine).toBe(true);
  });

  it('Should_AbortSave_When_DetectorConfigInvalid', () => {
    component.configForm.patchValue({ defaultThreshold: 5 });

    component.onSaveDetectorConfig();

    expect(component.saving()).toBe(false);
    httpMock.expectNone('/api/v1/pii-detection/config');
  });

  it('Should_StopSaving_When_SaveDetectorConfigFails', () => {
    component.configForm.patchValue({ defaultThreshold: 0.8 });
    component.configForm.markAsDirty();

    component.onSaveDetectorConfig();
    httpMock.expectOne('/api/v1/pii-detection/config').flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.saving()).toBe(false);
  });

  // ========== onSaveAll ==========

  it('Should_DoNothing_When_SaveAllWithoutAnyChange', () => {
    component.onSaveAll();

    expect(component.saving()).toBe(false);
    httpMock.expectNone('/api/v1/pii-detection/config');
    httpMock.expectNone('/api/v1/pii-detection/pii-types/bulk');
  });

  it('Should_SaveBothRequests_When_DetectorAndTypesChanged', () => {
    component.configForm.patchValue({ defaultThreshold: 0.8 });
    component.configForm.markAsDirty();
    const type = seedPresidioType({ threshold: 0.5 });
    component.onPiiTypeThresholdChange(type, 0.9);

    let saved = false;
    component.settingsSaved.subscribe(() => (saved = true));

    component.onSaveAll();
    expect(component.saving()).toBe(true);

    httpMock.expectOne('/api/v1/pii-detection/config').flush({ ...MOCK_DETECTOR_CONFIG, defaultThreshold: 0.8 });
    httpMock.expectOne('/api/v1/pii-detection/pii-types/bulk').flush([{ ...type, threshold: 0.9 }]);

    expect(component.saving()).toBe(false);
    expect(component.hasUnsavedTypeChanges()).toBe(false);
    expect(component.configForm.pristine).toBe(true);
    expect(saved).toBe(true);
  });

  it('Should_StopSaving_When_SaveAllFails', () => {
    component.configForm.patchValue({ defaultThreshold: 0.8 });
    component.configForm.markAsDirty();

    component.onSaveAll();
    httpMock.expectOne('/api/v1/pii-detection/config').flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.saving()).toBe(false);
  });

  // ========== Reset ==========

  it('Should_RestoreFormFromCurrentConfig_When_ResetDetectorConfig', () => {
    component.configForm.patchValue({ defaultThreshold: 0.1, presidioEnabled: false });
    component.configForm.markAsDirty();

    component.onResetDetectorConfig();

    expect(component.configForm.get('defaultThreshold')?.value).toBe(MOCK_DETECTOR_CONFIG.defaultThreshold);
    expect(component.configForm.get('presidioEnabled')?.value).toBe(MOCK_DETECTOR_CONFIG.presidioEnabled);
    expect(component.configForm.pristine).toBe(true);
  });

  it('Should_ClearModifications_When_ResetPiiTypes', () => {
    const type = seedPresidioType({ threshold: 0.5 });
    component.onPiiTypeThresholdChange(type, 0.9);
    expect(component.hasUnsavedTypeChanges()).toBe(true);

    component.onResetPiiTypes();

    expect(component.hasUnsavedTypeChanges()).toBe(false);
    const restored = component.groupedPiiTypes()[0].categories[0].types[0];
    expect(restored.threshold).toBe(0.5);
  });

  it('Should_ResetEverything_When_ResetAll', () => {
    component.configForm.patchValue({ defaultThreshold: 0.1 });
    component.configForm.markAsDirty();
    const type = seedPresidioType({ threshold: 0.5 });
    component.onPiiTypeThresholdChange(type, 0.9);

    component.onResetAll();

    expect(component.configForm.pristine).toBe(true);
    expect(component.hasUnsavedTypeChanges()).toBe(false);
  });

  // ========== Navigation / cancel ==========

  it('Should_EmitCloseDialog_When_CancelInDialogMode', () => {
    fixture.componentRef.setInput('dialogMode', true);
    let closed = false;
    component.closeDialog.subscribe(() => (closed = true));

    component.onCancel();

    expect(closed).toBe(true);
  });

  // ========== Getters ==========

  it('Should_ReflectUnsavedChanges_When_FormDirtyOrTypesModified', () => {
    expect(component.hasUnsavedChanges).toBe(false);

    component.configForm.markAsDirty();

    expect(component.hasUnsavedChanges).toBe(true);
    expect(component.hasDetectorChanges).toBe(true);
  });

  it('Should_BeFormValid_When_FormControlsValid', () => {
    expect(component.isFormValid).toBe(true);
  });

  it('Should_ExposeAtLeastOneDetectorError_When_AllDisabledAndTouched', () => {
    component.configForm.patchValue({
      presidioEnabled: false,
      regexEnabled: false,
      ministralEnabled: false,
    });
    component.configForm.markAllAsTouched();

    expect(component.atLeastOneDetectorError).toBe(true);
  });

  // ========== Search ==========

  it('Should_FilterTypes_When_SearchTermMatches', () => {
    seedPresidioType({ piiType: 'EMAIL' });

    component.onSearchChange('EMAIL');

    expect(component.hasNoSearchResults()).toBe(false);
    expect(component.filteredPiiTypes().length).toBeGreaterThan(0);
  });

  it('Should_ReportNoResults_When_SearchTermDoesNotMatch', () => {
    seedPresidioType({ piiType: 'EMAIL', countryCode: 'CH' });

    component.onSearchChange('zzz-no-match-zzz');

    expect(component.hasNoSearchResults()).toBe(true);
  });

  it('Should_ResetSearch_When_ClearSearch', () => {
    component.onSearchChange('term');
    expect(component.searchTerm()).toBe('term');

    component.clearSearch();

    expect(component.searchTerm()).toBe('');
    expect(component.hasNoSearchResults()).toBe(false);
  });

  it('Should_ReturnPlainText_When_HighlightWithoutSearchTerm', () => {
    const result = component.highlightText('ignored', 'common.close');

    expect(result).toBe('Fermer');
  });

  it('Should_WrapMatchInMark_When_HighlightWithSearchTerm', () => {
    component.onSearchChange('Fer');

    const result = component.highlightText('ignored', 'common.close');

    expect(result).toContain('<mark class="search-highlight">Fer</mark>');
  });

  // ========== Collapse ==========

  it('Should_ToggleCollapseState_When_ToggleDetectorCollapse', () => {
    expect(component.isDetectorCollapsed('REGEX')).toBe(false);

    component.toggleDetectorCollapse('REGEX');
    expect(component.isDetectorCollapsed('REGEX')).toBe(true);

    component.toggleDetectorCollapse('REGEX');
    expect(component.isDetectorCollapsed('REGEX')).toBe(false);
  });

});
