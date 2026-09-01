import { Component, computed, input, OnDestroy, OnInit, output, SecurityContext, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer } from '@angular/platform-browser';
import {
    AbstractControlOptions,
    FormBuilder,
    FormControl,
    FormGroup,
    FormsModule,
    ReactiveFormsModule,
    Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { ProgressBarModule } from 'primeng/progressbar';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ToastModule } from 'primeng/toast';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';
import { PiiDetectionConfigService } from '../../core/services/pii-detection-config.service';
import {
    CategoryGroup,
    ConcurrencyBenchStatus,
    GroupedPiiTypes,
    PiiDetectionConfig,
    PiiTypeConfig,
    UpdatePiiTypeConfigRequest
} from '../../core/models/pii-detection-config.model';
import { forkJoin, Observable, Subscription, switchMap, timer } from 'rxjs';
import { ConfluenceSettingsComponent } from '../confluence-settings/confluence-settings.component';

type SettingsSection = 'detectors' | 'thresholds' | 'pii_types' | 'lm_studio' | 'confluence';

/**
 * Settings page for PII detection configuration.
 * Manages detector-level settings and individual PII type configurations.
 */
@Component({
  selector: 'app-pii-settings',
  templateUrl: './pii-settings.component.html',
  styleUrl: './pii-settings.component.css',
  standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        TranslocoModule,
        ButtonModule,
        ToggleSwitchModule,
        InputNumberModule,
        MessageModule,
        ProgressBarModule,
        ProgressSpinnerModule,
        ToastModule,
        IconFieldModule,
        InputIconModule,
        InputTextModule,
        ConfluenceSettingsComponent
    ],
  providers: [MessageService]
})
export class PiiSettingsComponent implements OnInit, OnDestroy {
  readonly dialogMode = input(false);
  readonly initialTab = input(0);
  readonly closeDialog = output();
  readonly settingsSaved = output();

  readonly confluenceSettings = viewChild(ConfluenceSettingsComponent);

  configForm!: FormGroup;
  loading = signal(false);
  saving = signal(false);
  currentConfig = signal<PiiDetectionConfig | null>(null);

  // PII type configurations
  groupedPiiTypes = signal<GroupedPiiTypes[]>([]);
  originalPiiTypes = signal<Map<string, PiiTypeConfig>>(new Map());
  modifiedPiiTypes = signal<Map<string, PiiTypeConfig>>(new Map());

  // Sidebar navigation
  activeSection = signal<SettingsSection>('detectors');

  // On-demand Ministral concurrency benchmark
  benchStatus = signal<ConcurrencyBenchStatus | null>(null);
  benchRunning = signal(false);
  benchProgress = computed(() => this.benchStatus()?.progress ?? 0);
  benchCancelling = signal(false);
  benchCancelRequested = computed(() => this.benchStatus()?.status === 'CANCEL_REQUESTED');
  readonly benchConcurrencyMin = 2;
  readonly benchConcurrencyMax = 20;
  /** Highest concurrency level the on-demand benchmark measures. Every level replays
   *  the whole sample on the detector service, so the run time grows with this bound. */
  readonly benchMaxConcurrency = new FormControl<number>(4, {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.min(this.benchConcurrencyMin),
      Validators.max(this.benchConcurrencyMax)
    ]
  });
  private benchPollSubscription: Subscription | null = null;

  // Collapsible detector groups in PII types section
  collapsedDetectors = signal<Set<string>>(new Set());

  // Search functionality
  searchTerm = signal<string>('');

  // Computed signal for filtered PII types based on search
  filteredPiiTypes = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    if (!term) {
      return this.groupedPiiTypes();
    }

    return this.groupedPiiTypes()
      .map(detectorGroup => {
        const filteredCategories = detectorGroup.categories
          .map(categoryGroup => {
            const filteredTypes = categoryGroup.types.filter(type => {
              // Get translated name and description
              const typeName = this.translocoService.translate(`settings.piiTypes.typeNames.${type.piiType}`).toLowerCase();
              const typeDescription = this.translocoService.translate(`settings.piiTypes.typeDescriptions.${type.piiType}`).toLowerCase();
              const countryCode = (type.countryCode || '').toLowerCase();

              // Search in name, description, and country code
              return typeName.includes(term) ||
                typeDescription.includes(term) ||
                countryCode.includes(term);
            });

            return filteredTypes.length > 0 ? {
              ...categoryGroup,
              types: filteredTypes
            } : null;
          })
          .filter(cat => cat !== null) as typeof detectorGroup.categories;

        return filteredCategories.length > 0 ? {
          ...detectorGroup,
          categories: filteredCategories
        } : null;
      })
      .filter(det => det !== null) as GroupedPiiTypes[];
  });

  // Computed signal for checking if there are no results
  hasNoSearchResults = computed(() => {
    const term = this.searchTerm().trim();
    if (!term) return false;

    const filtered = this.filteredPiiTypes();
    return filtered.every(det => det.categories.every(cat => cat.types.length === 0));
  });

  // Computed signal for unsaved changes
  hasUnsavedTypeChanges = computed(() => this.modifiedPiiTypes().size > 0);

  constructor(
    private readonly fb: FormBuilder,
    private readonly configService: PiiDetectionConfigService,
    private readonly messageService: MessageService,
    private readonly translocoService: TranslocoService,
    private readonly router: Router,
    private readonly sanitizer: DomSanitizer
  ) {
    this.initForm();
  }

  /** Maps tab indices to sidebar section identifiers. */
  private static readonly TAB_TO_SECTION: ReadonlyArray<SettingsSection> = [
    'detectors', 'thresholds', 'pii_types', 'lm_studio', 'confluence'
  ];

  /**
   * Maps a "Types d'IPI" detector group to its master toggle in the
   * "Moteur de détection" section. The PII-type rows of a detector are
   * derived (disabled + collapsed) from the state of this control so the
   * two sections stay consistent without ever overwriting the per-type
   * stored values (customisations are preserved by construction).
   */
  private static readonly DETECTOR_MASTER_CONTROL: Readonly<Record<string, string>> = {
    PRESIDIO: 'presidioEnabled',
    REGEX: 'regexEnabled',
    MINISTRAL: 'ministralEnabled'
  };

  ngOnInit(): void {
    const tabIndex = this.initialTab();
    const section = PiiSettingsComponent.TAB_TO_SECTION[tabIndex];
    if (section) {
      this.activeSection.set(section);
    }

    this.loadAllConfigs();
  }

  ngOnDestroy(): void {
    this.stopBenchPolling();
  }

  private initForm(): void {
    this.configForm = this.fb.group({
      presidioEnabled: [true],
      regexEnabled: [true],
      postfilterEnabled: [false],
      ministralEnabled: [false],
      ministralChunkSize: [2048, [Validators.required, Validators.min(256), Validators.max(4096)]],
      ministralOverlap: [410, [Validators.required, Validators.min(0), Validators.max(512)]],
      ministralConcurrency: [1, [Validators.required, Validators.min(1), Validators.max(this.benchConcurrencyMax)]],
      ministralConcurrencyAuto: [true],
      ministralConcurrencyTunedSignature: [null as string | null],
      defaultThreshold: [0.75, [Validators.required, Validators.min(0), Validators.max(1)]],
      lmStudioHost: ['localhost', [Validators.required, Validators.pattern(/^[^\s/]+$/)]],
      lmStudioPort: [1234, [Validators.required, Validators.min(1), Validators.max(65535)]]
    }, {
      validators: [this.atLeastOneDetectorValidator, this.overlapLessThanChunkSizeValidator]
    } as AbstractControlOptions);
  }

  /**
   * Custom validator: at least one detector must be enabled.
   */
  private atLeastOneDetectorValidator(group: FormGroup): {[key: string]: boolean} | null {
    const presidio = group.get('presidioEnabled')?.value;
    const regex = group.get('regexEnabled')?.value;
    const ministral = group.get('ministralEnabled')?.value;

    if (!presidio && !regex && !ministral) {
      return {atLeastOneDetector: true};
    }
    return null;
  }

  /**
   * Cross-field validator: the Ministral overlap must stay strictly below the
   * chunk size, otherwise consecutive chunks would never advance.
   */
  private overlapLessThanChunkSizeValidator(group: FormGroup): {[key: string]: boolean} | null {
    const chunkSize = group.get('ministralChunkSize')?.value;
    const overlap = group.get('ministralOverlap')?.value;

    if (chunkSize != null && overlap != null && overlap >= chunkSize) {
      return {ministralOverlapGteChunkSize: true};
    }
    return null;
  }

  /**
   * Load all configurations: detector-level and PII types.
   */
  private loadAllConfigs(): void {
    this.loading.set(true);

    forkJoin([
      this.configService.getConfig(),
      this.configService.getPiiTypesGroupedForUI()
    ]).subscribe({
      next: ([detectorConfig, piiTypes]) => {
        // Set detector config
        this.currentConfig.set(detectorConfig);
        this.configForm.patchValue({
          presidioEnabled: detectorConfig.presidioEnabled,
          regexEnabled: detectorConfig.regexEnabled,
          postfilterEnabled: detectorConfig.postfilterEnabled,
          ministralEnabled: detectorConfig.ministralEnabled,
          ministralChunkSize: detectorConfig.ministralChunkSize,
          ministralOverlap: detectorConfig.ministralOverlap,
          ministralConcurrency: detectorConfig.ministralConcurrency,
          ministralConcurrencyAuto: detectorConfig.ministralConcurrencyAuto,
          ministralConcurrencyTunedSignature: detectorConfig.ministralConcurrencyTunedSignature ?? null,
          defaultThreshold: detectorConfig.defaultThreshold,
          lmStudioHost: detectorConfig.lmStudioHost,
          lmStudioPort: detectorConfig.lmStudioPort
        });

        // Set PII types
        this.groupedPiiTypes.set(piiTypes);
        this.initializeOriginalPiiTypes(piiTypes);

        // Collapse the sub-sections of detectors that are disabled, so the
        // "Types d'IPI" view reflects the "Moteur de détection" state on load.
        this.syncCollapsedWithDetectorState();

        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load configurations:', err);
        const errorMsg = this.translocoService.translate('settings.messages.loadError', {error: err.message || 'Unknown error'});
        this.messageService.add({
          severity: 'error',
          summary: this.translocoService.translate('common.error'),
          detail: errorMsg,
          life: 5000
        });
        this.loading.set(false);
      }
    });
  }

  /**
   * Store original PII types for comparison.
   */
  private initializeOriginalPiiTypes(grouped: GroupedPiiTypes[]): void {
    const originalMap = new Map<string, PiiTypeConfig>();

    grouped.forEach(detectorGroup => {
      detectorGroup.categories.forEach(categoryGroup => {
        categoryGroup.types.forEach(type => {
          const key = this.getPiiTypeKey(type.detector, type.piiType);
          originalMap.set(key, {...type});
        });
      });
    });

    this.originalPiiTypes.set(originalMap);
    this.modifiedPiiTypes.set(new Map()); // Clear modifications
  }

  /**
   * Generate unique key for PII type.
   */
  private getPiiTypeKey(detector: string, piiType: string): string {
    return `${detector}:${piiType}`;
  }

  /**
   * Handle toggle change for a PII type.
   */
  onPiiTypeToggleChange(type: PiiTypeConfig, enabled: boolean): void {
    const key = this.getPiiTypeKey(type.detector, type.piiType);
    const original = this.originalPiiTypes().get(key);

    if (!original) return;

    // Create modified config
    const modified: PiiTypeConfig = {
      ...type,
      enabled
    };

    this.trackPiiTypeModification(key, original, modified);

    // Update the grouped data to reflect changes
    this.updateGroupedPiiTypes(type.detector, type.piiType, {enabled});
  }

  /**
   * Whether every PII type of a category is currently enabled. Drives the
   * category-level master toggle in the "Types d'IPI" section. An empty
   * category reads as disabled so its toggle stays off.
   */
  isCategoryEnabled(categoryGroup: CategoryGroup): boolean {
    return categoryGroup.types.length > 0 && categoryGroup.types.every(type => type.enabled);
  }

  /**
   * Enable or disable every PII type of a category at once, reusing the
   * per-type change tracking so the bulk switch is staged exactly like a
   * series of manual toggles.
   */
  onCategoryToggleChange(categoryGroup: CategoryGroup, enabled: boolean): void {
    categoryGroup.types
      .filter(type => type.enabled !== enabled)
      .forEach(type => this.onPiiTypeToggleChange(type, enabled));
  }

  /**
   * Handle threshold change for a PII type.
   */
  onPiiTypeThresholdChange(type: PiiTypeConfig, threshold: number): void {
    const key = this.getPiiTypeKey(type.detector, type.piiType);
    const original = this.originalPiiTypes().get(key);

    if (!original) return;

    // Create modified config
    const modified: PiiTypeConfig = {
      ...type,
      threshold
    };

    this.trackPiiTypeModification(key, original, modified);

    // Update the grouped data to reflect changes
    this.updateGroupedPiiTypes(type.detector, type.piiType, {threshold});
  }

  /**
   * Track a PII-type row modification: record it when it differs from the
   * original (enabled or threshold), drop it when it matches again.
   * Always emits a new Map reference so the signal recomputes.
   */
  private trackPiiTypeModification(
    key: string,
    original: PiiTypeConfig,
    modified: PiiTypeConfig
  ): void {
    const changed =
      modified.enabled !== original.enabled ||
      modified.threshold !== original.threshold;

    if (changed) {
      this.modifiedPiiTypes().set(key, modified);
    } else {
      this.modifiedPiiTypes().delete(key);
    }

    this.modifiedPiiTypes.set(new Map(this.modifiedPiiTypes()));
  }

  /**
   * Build a bulk-update payload entry.
   */
  private toUpdateRequest(type: PiiTypeConfig): UpdatePiiTypeConfigRequest {
    return {
      piiType: type.piiType,
      detector: type.detector,
      enabled: type.enabled,
      threshold: type.threshold
    };
  }

  /**
   * Update the grouped PII types data structure.
   */
  private updateGroupedPiiTypes(detector: string, piiType: string, updates: Partial<PiiTypeConfig>): void {
    const currentGrouped = this.groupedPiiTypes();
    const updatedGrouped = currentGrouped.map(detectorGroup => {
      if (detectorGroup.detector !== detector) return detectorGroup;

      return {
        ...detectorGroup,
        categories: detectorGroup.categories.map(categoryGroup => ({
          ...categoryGroup,
          types: categoryGroup.types.map(type =>
            type.piiType === piiType ? {...type, ...updates} : type
          )
        }))
      };
    });

    this.groupedPiiTypes.set(updatedGrouped);
  }

  /**
   * Save detector-level configuration.
   */
  onSaveDetectorConfig(): void {
    if (this.configForm.invalid) {
      this.configForm.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const request = this.configForm.value;

    this.configService.updateConfig(request).subscribe({
      next: (config) => {
        this.currentConfig.set(config);
        this.messageService.add({
          severity: 'success',
          summary: this.translocoService.translate('common.success'),
          detail: this.translocoService.translate('settings.messages.saveSuccess'),
          life: 3000
        });
        this.saving.set(false);
        this.configForm.markAsPristine();
      },
      error: (err) => {
        console.error('Failed to update detector configuration:', err);
        const errorMsg = this.translocoService.translate('settings.messages.saveError', {error: err.message || 'Unknown error'});
        this.messageService.add({
          severity: 'error',
          summary: this.translocoService.translate('common.error'),
          detail: errorMsg,
          life: 5000
        });
        this.saving.set(false);
      }
    });
  }

  /**
   * Clear the tuned concurrency signature so the auto-tuner runs again on the
   * next detector-service restart, then persist through the regular save flow.
   */
  onRetuneConcurrency(): void {
    this.configForm.patchValue({ministralConcurrencyTunedSignature: null});
    this.configForm.markAsDirty();
    this.onSaveDetectorConfig();
  }

  /**
   * Trigger the concurrency benchmark on the detector service without a
   * restart, then poll its status until it terminates.
   */
  onRunBenchmark(): void {
    if (this.benchRunning() || this.benchMaxConcurrency.invalid) {
      return;
    }

    this.benchRunning.set(true);
    this.benchCancelling.set(false);
    this.benchStatus.set(null);

    this.configService.runConcurrencyBenchmark(this.benchMaxConcurrency.value).subscribe({
      next: () => this.startBenchPolling(),
      error: (err) => {
        console.error('Failed to start concurrency benchmark:', err);
        this.benchRunning.set(false);
        this.showBenchError(err.error?.message || err.message || 'Unknown error');
      }
    });
  }

  /**
   * Ask the detector service to stop the running benchmark. Polling continues
   * until the service confirms (CANCELLED); the applied concurrency is left as is.
   */
  onCancelBenchmark(): void {
    if (!this.benchRunning() || this.benchCancelling()) {
      return;
    }

    this.benchCancelling.set(true);
    this.configService.cancelConcurrencyBenchmark().subscribe({
      next: (status) => this.handleBenchStatus(status),
      error: (err) => {
        console.error('Failed to cancel concurrency benchmark:', err);
        this.benchCancelling.set(false);
        this.showBenchError(err.error?.message || err.message || 'Unknown error');
      }
    });
  }

  private static readonly BENCH_POLL_INTERVAL_MS = 1000;

  private startBenchPolling(): void {
    this.stopBenchPolling();
    this.benchPollSubscription = timer(0, PiiSettingsComponent.BENCH_POLL_INTERVAL_MS)
      .pipe(switchMap(() => this.configService.getConcurrencyBenchStatus()))
      .subscribe({
        next: (status) => this.handleBenchStatus(status),
        error: (err) => {
          console.error('Failed to poll concurrency benchmark status:', err);
          this.stopBenchPolling();
          this.benchRunning.set(false);
          this.showBenchError(err.error?.message || err.message || 'Unknown error');
        }
      });
  }

  private handleBenchStatus(status: ConcurrencyBenchStatus): void {
    this.benchStatus.set(status);

    if (status.status === 'DONE') {
      this.stopBenchPolling();
      this.benchRunning.set(false);
      this.messageService.add({
        severity: 'success',
        summary: this.translocoService.translate('common.success'),
        detail: this.translocoService.translate('settings.detectors.ministral.benchmarkDone', {concurrency: status.concurrency}),
        life: 3000
      });
      // Reload so the displayed concurrency reflects the tuned value.
      this.loadAllConfigs();
    } else if (status.status === 'FAILED') {
      this.stopBenchPolling();
      this.benchRunning.set(false);
      this.benchCancelling.set(false);
      this.showBenchError(status.message || 'Unknown error');
    } else if (status.status === 'CANCELLED') {
      this.stopBenchPolling();
      this.benchRunning.set(false);
      this.benchCancelling.set(false);
      this.messageService.add({
        severity: 'info',
        summary: this.translocoService.translate('common.info'),
        detail: this.translocoService.translate('settings.detectors.ministral.benchmarkCancelled', {concurrency: status.concurrency}),
        life: 3000
      });
    }
  }

  private showBenchError(error: string): void {
    this.messageService.add({
      severity: 'error',
      summary: this.translocoService.translate('common.error'),
      detail: this.translocoService.translate('settings.detectors.ministral.benchmarkFailed', {error}),
      life: 5000
    });
  }

  private stopBenchPolling(): void {
    this.benchPollSubscription?.unsubscribe();
    this.benchPollSubscription = null;
  }

  /**
   * Save all modified PII type configurations.
   */
  onSavePiiTypes(): void {
    const modifications = Array.from(this.modifiedPiiTypes().values());

    if (modifications.length === 0) {
      return;
    }

    this.saving.set(true);

    const updates = modifications.map(type => this.toUpdateRequest(type));

    this.configService.bulkUpdatePiiTypeConfigs(updates).subscribe({
      next: (updatedConfigs) => {
        // Update original types with new values
        updatedConfigs.forEach(config => {
          const key = this.getPiiTypeKey(config.detector, config.piiType);
          this.originalPiiTypes().set(key, config);
        });

        // Clear modifications
        this.modifiedPiiTypes.set(new Map());

        this.messageService.add({
          severity: 'success',
          summary: this.translocoService.translate('common.success'),
          detail: this.translocoService.translate('settings.piiTypes.messages.bulkSaveSuccess', {count: updatedConfigs.length}),
          life: 3000
        });

        this.saving.set(false);
      },
      error: (err) => {
        console.error('Failed to update PII type configurations:', err);
        const errorMsg = this.translocoService.translate('settings.piiTypes.messages.bulkSaveError', {error: err.message || 'Unknown error'});
        this.messageService.add({
          severity: 'error',
          summary: this.translocoService.translate('common.error'),
          detail: errorMsg,
          life: 5000
        });
        this.saving.set(false);
      }
    });
  }

  /**
   * Save all configurations (detector + PII types).
   */
  onSaveAll(): void {
    const hasDetectorChanges = this.configForm.dirty;
    const hasTypeChanges = this.hasUnsavedTypeChanges();
    const hasConfluenceChanges = this.confluenceSettings()?.hasUnsavedChanges ?? false;

    if (!hasDetectorChanges && !hasTypeChanges && !hasConfluenceChanges) {
      return;
    }

    if (hasConfluenceChanges) {
      this.confluenceSettings()!.onSave();
    }

    // If only Confluence changed, it handles its own saving signal — nothing else to do
    if (!hasDetectorChanges && !hasTypeChanges) {
      return;
    }

    this.saving.set(true);

    const requests: Observable<any>[] = [];
    let detectorRequestIndex = -1;
    let typesRequestIndex = -1;

    if (hasDetectorChanges && this.configForm.valid) {
      detectorRequestIndex = requests.length;
      requests.push(this.configService.updateConfig(this.configForm.value));
    }

    if (hasTypeChanges) {
      const modifications = Array.from(this.modifiedPiiTypes().values());
      const updates = modifications.map(type => this.toUpdateRequest(type));
      typesRequestIndex = requests.length;
      requests.push(this.configService.bulkUpdatePiiTypeConfigs(updates));
    }

    forkJoin(requests).subscribe({
      next: (results) => {
        // Update detector config if it was saved
        if (detectorRequestIndex >= 0 && results[detectorRequestIndex]) {
          this.currentConfig.set(results[detectorRequestIndex]);
          this.configForm.markAsPristine();
        }

        // Update PII types if they were saved
        if (typesRequestIndex >= 0 && results[typesRequestIndex]) {
          const piiTypesResult = results[typesRequestIndex];
          piiTypesResult.forEach((config: PiiTypeConfig) => {
            const key = this.getPiiTypeKey(config.detector, config.piiType);
            this.originalPiiTypes().set(key, config);
          });
          this.modifiedPiiTypes.set(new Map());
        }

        this.messageService.add({
          severity: 'success',
          summary: this.translocoService.translate('common.success'),
          detail: this.translocoService.translate('settings.messages.saveAllSuccess'),
          life: 3000
        });

        this.saving.set(false);
        this.settingsSaved.emit();
      },
      error: (err) => {
        console.error('Failed to save configurations:', err);
        const errorMsg = this.translocoService.translate('settings.messages.saveAllError', {error: err.message || 'Unknown error'});
        this.messageService.add({
          severity: 'error',
          summary: this.translocoService.translate('common.error'),
          detail: errorMsg,
          life: 5000
        });
        this.saving.set(false);
      }
    });
  }

  /**
   * Cancel and navigate back.
   * In dialog mode, emits close event. In standalone mode, navigates to home.
   */
  onCancel(): void {
    this.onResetAll();
    if (this.dialogMode()) {
      this.closeDialog.emit();
    } else {
      this.router.navigate(['/']);
    }
  }

  /**
   * Reset detector configuration.
   */
  onResetDetectorConfig(): void {
    if (this.currentConfig()) {
      this.configForm.patchValue({
        presidioEnabled: this.currentConfig()!.presidioEnabled,
        regexEnabled: this.currentConfig()!.regexEnabled,
        postfilterEnabled: this.currentConfig()!.postfilterEnabled,
        ministralEnabled: this.currentConfig()!.ministralEnabled,
        ministralChunkSize: this.currentConfig()!.ministralChunkSize,
        ministralOverlap: this.currentConfig()!.ministralOverlap,
        ministralConcurrency: this.currentConfig()!.ministralConcurrency,
        ministralConcurrencyAuto: this.currentConfig()!.ministralConcurrencyAuto,
        ministralConcurrencyTunedSignature: this.currentConfig()!.ministralConcurrencyTunedSignature ?? null,
        defaultThreshold: this.currentConfig()!.defaultThreshold,
        lmStudioHost: this.currentConfig()!.lmStudioHost,
        lmStudioPort: this.currentConfig()!.lmStudioPort
      });
      this.configForm.markAsPristine();
      // Restore the collapse state to match the reset detector toggles.
      this.syncCollapsedWithDetectorState();
    }
  }

  /**
   * Reset PII type configurations.
   */
  onResetPiiTypes(): void {
    // Clear all modifications
    this.modifiedPiiTypes.set(new Map());

    // Restore original values in grouped data
    const currentGrouped = this.groupedPiiTypes();
    const updatedGrouped = currentGrouped.map(detectorGroup => ({
      ...detectorGroup,
      categories: detectorGroup.categories.map(categoryGroup => ({
        ...categoryGroup,
        types: categoryGroup.types.map(type => {
          const key = this.getPiiTypeKey(type.detector, type.piiType);
          const original = this.originalPiiTypes().get(key);
          return original ? {...original} : type;
        })
      }))
    }));

    this.groupedPiiTypes.set(updatedGrouped);
  }

  /**
   * Reset all configurations.
   */
  onResetAll(): void {
    this.onResetDetectorConfig();
    this.onResetPiiTypes();
    this.confluenceSettings()?.onReset();
  }

  get hasUnsavedChanges(): boolean {
    return this.configForm.dirty || this.hasUnsavedTypeChanges() || (this.confluenceSettings()?.hasUnsavedChanges ?? false);
  }

  get isFormValid(): boolean {
    return this.configForm.valid && (this.confluenceSettings()?.isFormValid ?? true);
  }

  get hasDetectorChanges(): boolean {
    return this.configForm.dirty;
  }

  get atLeastOneDetectorError(): boolean {
    return this.configForm.hasError('atLeastOneDetector') && this.configForm.touched;
  }

  get ministralOverlapError(): boolean {
    return this.configForm.hasError('ministralOverlapGteChunkSize');
  }

  /**
   * Get count of modified PII types.
   */
  get modifiedTypesCount(): number {
    return this.modifiedPiiTypes().size;
  }

  /**
   * Handle search term change.
   */
  onSearchChange(term: string): void {
    this.searchTerm.set(term);
  }

  /**
   * Clear search term.
   */
  clearSearch(): void {
    this.searchTerm.set('');
  }

  /**
   * Highlight search term in text.
   */
  highlightText(text: string, originalKey: string): string {
    // Get translated text first
    const translatedText = this.translocoService.translate(originalKey);

    const term = this.searchTerm().trim();
    if (!term) {
      // No search term, return plain translated text
      return this.sanitizer.sanitize(SecurityContext.HTML, translatedText) || translatedText;
    }

    // Escape special regex characters
    const escapedTerm = term.replaceAll(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);
    const regex = new RegExp(`(${escapedTerm})`, 'gi');

    // Replace matches with highlighted version
    const highlighted = translatedText.replaceAll(regex, '<mark class="search-highlight">$1</mark>');

    return this.sanitizer.sanitize(SecurityContext.HTML, highlighted) || '';
  }

  /**
   * Set the active sidebar section.
   */
  setActiveSection(section: SettingsSection): void {
    this.activeSection.set(section);
  }

  /**
   * Toggle collapse state of a detector group.
   */
  toggleDetectorCollapse(detector: string): void {
    const current = new Set(this.collapsedDetectors());
    if (current.has(detector)) {
      current.delete(detector);
    } else {
      current.add(detector);
    }
    this.collapsedDetectors.set(current);
  }

  /**
   * Check if a detector group is collapsed.
   */
  isDetectorCollapsed(detector: string): boolean {
    return this.collapsedDetectors().has(detector);
  }

  /**
   * Whether the detector that owns a "Types d'IPI" sub-section is enabled
   * in the "Moteur de détection" section. Drives the disabled state of the
   * per-type toggles so a disabled detector cannot be edited there.
   * Unknown detectors (no master toggle) default to enabled.
   */
  isDetectorEnabled(detector: string): boolean {
    const control = PiiSettingsComponent.DETECTOR_MASTER_CONTROL[detector];
    if (!control) {
      return true;
    }
    return !!this.configForm.get(control)?.value;
  }

  /**
   * React to a detector master toggle in "Moteur de détection": collapse its
   * "Types d'IPI" sub-section when disabled, expand it when re-enabled. The
   * per-type values are never touched — only the visual state is derived.
   */
  onDetectorMasterToggle(detector: string): void {
    const collapsed = new Set(this.collapsedDetectors());
    if (this.isDetectorEnabled(detector)) {
      collapsed.delete(detector);
    } else {
      collapsed.add(detector);
    }
    this.collapsedDetectors.set(collapsed);
  }

  /**
   * Collapse every detector sub-section whose master toggle is currently
   * disabled, leaving enabled ones in their current state. Called after a
   * config load so the two sections start consistent.
   */
  private syncCollapsedWithDetectorState(): void {
    const collapsed = new Set(this.collapsedDetectors());
    for (const detector of Object.keys(PiiSettingsComponent.DETECTOR_MASTER_CONTROL)) {
      if (this.isDetectorEnabled(detector)) {
        collapsed.delete(detector);
      } else {
        collapsed.add(detector);
      }
    }
    this.collapsedDetectors.set(collapsed);
  }
}
