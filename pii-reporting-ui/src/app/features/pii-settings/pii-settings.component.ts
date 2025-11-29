import {Component, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule, FormGroup, FormBuilder, Validators} from '@angular/forms';
import {Router, RouterLink} from '@angular/router';
import {TranslocoModule, TranslocoService} from '@jsverse/transloco';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {InputNumberModule} from 'primeng/inputnumber';
import {MessageModule} from 'primeng/message';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {ToastModule} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {PiiDetectionConfigService} from '../../core/services/pii-detection-config.service';
import {PiiDetectionConfig} from '../../core/models/pii-detection-config.model';

/**
 * Settings page for PII detection configuration.
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
    this.loadConfig();
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

  private loadConfig(): void {
    this.loading.set(true);
    this.configService.getConfig().subscribe({
      next: (config) => {
        this.currentConfig.set(config);
        this.configForm.patchValue({
          glinerEnabled: config.glinerEnabled,
          presidioEnabled: config.presidioEnabled,
          regexEnabled: config.regexEnabled,
          defaultThreshold: config.defaultThreshold
        });
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load configuration:', err);
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

  onSave(): void {
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
        // Reset form to mark it as pristine
        this.configForm.markAsPristine();
      },
      error: (err) => {
        console.error('Failed to update configuration:', err);
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

  onCancel(): void {
    this.router.navigate(['/']);
  }

  onReset(): void {
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

  get hasUnsavedChanges(): boolean {
    return this.configForm.dirty;
  }

  get atLeastOneDetectorError(): boolean {
    return this.configForm.hasError('atLeastOneDetector') && this.configForm.touched;
  }
}
