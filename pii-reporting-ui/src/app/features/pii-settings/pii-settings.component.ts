import {Component, computed, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterLink} from '@angular/router';
import {TranslocoModule, TranslocoService} from '@jsverse/transloco';
import {AccordionModule} from 'primeng/accordion';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {InputNumberModule} from 'primeng/inputnumber';
import {MessageModule} from 'primeng/message';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {ToastModule} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {PiiDetectionConfigService} from '../../core/services/pii-detection-config.service';
import {
  GroupedPiiTypes,
  PiiDetectionConfig,
  PiiTypeConfig
} from '../../core/models/pii-detection-config.model';
import {forkJoin} from 'rxjs';

/**
 * Settings page for PII detection configuration.
 * Manages detector-level settings and individual PII type configurations.
 */
@Component({
  selector: 'app-pii-settings',
  templateUrl: './pii-settings.component.html',
  styleUrl: './pii-settings.component.scss',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    TranslocoModule,
    AccordionModule,
    ButtonModule,
    CardModule,
    ToggleSwitchModule,
    InputNumberModule,
    MessageModule,
    ProgressSpinnerModule,
    ToastModule
  ],
  providers: [MessageService]
})
export class PiiSettingsComponent implements OnInit {
  configForm!: FormGroup;
  loading = signal(false);
  saving = signal(false);
  currentConfig = signal<PiiDetectionConfig | null>(null);

  // PII type configurations
  groupedPiiTypes = signal<GroupedPiiTypes[]>([]);
  originalPiiTypes = signal<Map<string, PiiTypeConfig>>(new Map());
  modifiedPiiTypes = signal<Map<string, PiiTypeConfig>>(new Map());

  // Computed signal for unsaved changes
  hasUnsavedTypeChanges = computed(() => this.modifiedPiiTypes().size > 0);

  constructor(
    private readonly fb: FormBuilder,
    private readonly configService: PiiDetectionConfigService,
    private readonly messageService: MessageService,
    private readonly translocoService: TranslocoService,
    private readonly router: Router
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadAllConfigs();
  }

  private initForm(): void {
    this.configForm = this.fb.group({
      glinerEnabled: [true],
      presidioEnabled: [true],
      regexEnabled: [true],
      defaultThreshold: [0.75, [Validators.required, Validators.min(0), Validators.max(1)]]
    }, {
      validators: [this.atLeastOneDetectorValidator]
    });
  }

  /**
   * Custom validator: at least one detector must be enabled.
   */
  private atLeastOneDetectorValidator(group: FormGroup): {[key: string]: boolean} | null {
    const gliner = group.get('glinerEnabled')?.value;
    const presidio = group.get('presidioEnabled')?.value;
    const regex = group.get('regexEnabled')?.value;

    if (!gliner && !presidio && !regex) {
      return {atLeastOneDetector: true};
    }
    return null;
  }

  /**
   * Load all configurations: detector-level and PII types.
   */
  private loadAllConfigs(): void {
    this.loading.set(true);

    forkJoin({
      detectorConfig: this.configService.getConfig(),
      piiTypes: this.configService.getPiiTypesGroupedForUI()
    }).subscribe({
      next: ({detectorConfig, piiTypes}) => {
        // Set detector config
        this.currentConfig.set(detectorConfig);
        this.configForm.patchValue({
          glinerEnabled: detectorConfig.glinerEnabled,
          presidioEnabled: detectorConfig.presidioEnabled,
          regexEnabled: detectorConfig.regexEnabled,
          defaultThreshold: detectorConfig.defaultThreshold
        });

        // Set PII types
        this.groupedPiiTypes.set(piiTypes);
        this.initializeOriginalPiiTypes(piiTypes);

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

    // Check if different from original
    if (modified.enabled !== original.enabled || modified.threshold !== original.threshold) {
      this.modifiedPiiTypes().set(key, modified);
    } else {
      this.modifiedPiiTypes().delete(key);
    }

    // Trigger change detection
    this.modifiedPiiTypes.set(new Map(this.modifiedPiiTypes()));

    // Update the grouped data to reflect changes
    this.updateGroupedPiiTypes(type.detector, type.piiType, {enabled});
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

    // Check if different from original
    if (modified.enabled !== original.enabled || modified.threshold !== original.threshold) {
      this.modifiedPiiTypes().set(key, modified);
    } else {
      this.modifiedPiiTypes().delete(key);
    }

    // Trigger change detection
    this.modifiedPiiTypes.set(new Map(this.modifiedPiiTypes()));

    // Update the grouped data to reflect changes
    this.updateGroupedPiiTypes(type.detector, type.piiType, {threshold});
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
   * Save all modified PII type configurations.
   */
  onSavePiiTypes(): void {
    const modifications = Array.from(this.modifiedPiiTypes().values());

    if (modifications.length === 0) {
      return;
    }

    this.saving.set(true);

    const updates = modifications.map(type => ({
      piiType: type.piiType,
      detector: type.detector,
      enabled: type.enabled,
      threshold: type.threshold
    }));

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

    if (!hasDetectorChanges && !hasTypeChanges) {
      return;
    }

    this.saving.set(true);

    const requests: any[] = [];
    let detectorRequestIndex = -1;
    let typesRequestIndex = -1;

    if (hasDetectorChanges && this.configForm.valid) {
      detectorRequestIndex = requests.length;
      requests.push(this.configService.updateConfig(this.configForm.value));
    }

    if (hasTypeChanges) {
      const modifications = Array.from(this.modifiedPiiTypes().values());
      const updates = modifications.map(type => ({
        piiType: type.piiType,
        detector: type.detector,
        enabled: type.enabled,
        threshold: type.threshold
      }));
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

  onCancel(): void {
    this.router.navigate(['/']);
  }

  /**
   * Reset detector configuration.
   */
  onResetDetectorConfig(): void {
    if (this.currentConfig()) {
      this.configForm.patchValue({
        glinerEnabled: this.currentConfig()!.glinerEnabled,
        presidioEnabled: this.currentConfig()!.presidioEnabled,
        regexEnabled: this.currentConfig()!.regexEnabled,
        defaultThreshold: this.currentConfig()!.defaultThreshold
      });
      this.configForm.markAsPristine();
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
  }

  get hasUnsavedChanges(): boolean {
    return this.configForm.dirty || this.hasUnsavedTypeChanges();
  }

  get hasDetectorChanges(): boolean {
    return this.configForm.dirty;
  }

  get atLeastOneDetectorError(): boolean {
    return this.configForm.hasError('atLeastOneDetector') && this.configForm.touched;
  }

  /**
   * Get count of modified PII types.
   */
  get modifiedTypesCount(): number {
    return this.modifiedPiiTypes().size;
  }
}
