import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { FieldsetModule } from 'primeng/fieldset';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ConfluenceConnectionConfigService } from '../../core/services/confluence-connection-config.service';
import {
  ConfluenceConnectionConfig,
  UpdateConfluenceConnectionConfigRequest,
  TestConnectionRequest
} from '../../core/models/confluence-connection-config.model';

@Component({
  selector: 'app-confluence-settings',
  templateUrl: './confluence-settings.component.html',
  styleUrl: './confluence-settings.component.scss',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslocoModule,
    ButtonModule,
    FieldsetModule,
    InputNumberModule,
    InputTextModule,
    PasswordModule,
    ProgressSpinnerModule,
    ToastModule
  ],
  providers: [MessageService]
})
export class ConfluenceSettingsComponent implements OnInit {
  configForm!: FormGroup;
  loading = signal(false);
  saving = signal(false);
  testing = signal(false);
  currentConfig = signal<ConfluenceConnectionConfig | null>(null);

  constructor(
    private readonly fb: FormBuilder,
    private readonly configService: ConfluenceConnectionConfigService,
    private readonly messageService: MessageService,
    private readonly translocoService: TranslocoService
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadConfig();
  }

  private initForm(): void {
    this.configForm = this.fb.group({
      baseUrl: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
      username: ['', [Validators.required]],
      apiToken: [''],
      connectTimeout: [30000, [Validators.required, Validators.min(1000), Validators.max(120000)]],
      readTimeout: [60000, [Validators.required, Validators.min(5000), Validators.max(300000)]],
      maxRetries: [3, [Validators.required, Validators.min(0), Validators.max(10)]],
      pagesLimit: [25, [Validators.required, Validators.min(1), Validators.max(1000)]],
      maxPages: [1000, [Validators.required, Validators.min(1), Validators.max(10000)]]
    });
  }

  private loadConfig(): void {
    this.loading.set(true);

    this.configService.getConfig().subscribe({
      next: (config) => {
        this.currentConfig.set(config);
        this.configForm.patchValue({
          baseUrl: config.baseUrl,
          username: config.username,
          apiToken: '',
          connectTimeout: config.connectTimeout,
          readTimeout: config.readTimeout,
          maxRetries: config.maxRetries,
          pagesLimit: config.pagesLimit,
          maxPages: config.maxPages
        });
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load Confluence connection config:', err);
        this.messageService.add({
          severity: 'error',
          summary: this.translocoService.translate('common.error'),
          detail: this.translocoService.translate('settings.confluence.messages.loadError', {error: err.message || 'Unknown error'}),
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

    // If config already exists and apiToken is empty, require user to confirm
    const formValue = this.configForm.value;
    if (!this.currentConfig() && !formValue.apiToken) {
      this.configForm.get('apiToken')?.setErrors({required: true});
      this.configForm.get('apiToken')?.markAsTouched();
      return;
    }

    this.saving.set(true);

    const request: UpdateConfluenceConnectionConfigRequest = {
      baseUrl: formValue.baseUrl,
      username: formValue.username,
      apiToken: formValue.apiToken,
      connectTimeout: formValue.connectTimeout,
      readTimeout: formValue.readTimeout,
      maxRetries: formValue.maxRetries,
      pagesLimit: formValue.pagesLimit,
      maxPages: formValue.maxPages
    };

    this.configService.updateConfig(request).subscribe({
      next: (config) => {
        this.currentConfig.set(config);
        this.configForm.get('apiToken')?.setValue('');
        this.configForm.markAsPristine();
        this.messageService.add({
          severity: 'success',
          summary: this.translocoService.translate('common.success'),
          detail: this.translocoService.translate('settings.confluence.messages.saveSuccess'),
          life: 3000
        });
        this.saving.set(false);
      },
      error: (err) => {
        console.error('Failed to save Confluence connection config:', err);
        this.messageService.add({
          severity: 'error',
          summary: this.translocoService.translate('common.error'),
          detail: this.translocoService.translate('settings.confluence.messages.saveError', {error: err.message || 'Unknown error'}),
          life: 5000
        });
        this.saving.set(false);
      }
    });
  }

  onTestConnection(): void {
    const baseUrl = this.configForm.get('baseUrl');
    const username = this.configForm.get('username');
    const apiToken = this.configForm.get('apiToken');

    // Validate only connection fields
    if (baseUrl?.invalid || username?.invalid) {
      baseUrl?.markAsTouched();
      username?.markAsTouched();
      return;
    }

    // Token is required for testing
    if (!apiToken?.value && !this.currentConfig()) {
      apiToken?.setErrors({required: true});
      apiToken?.markAsTouched();
      return;
    }

    this.testing.set(true);

    const request: TestConnectionRequest = {
      baseUrl: baseUrl?.value,
      username: username?.value,
      apiToken: apiToken?.value
    };

    this.configService.testConnection(request).subscribe({
      next: (response) => {
        if (response.success) {
          this.messageService.add({
            severity: 'success',
            summary: this.translocoService.translate('common.success'),
            detail: this.translocoService.translate('settings.confluence.messages.testSuccess'),
            life: 3000
          });
        } else {
          this.messageService.add({
            severity: 'warn',
            summary: this.translocoService.translate('common.warning'),
            detail: this.translocoService.translate('settings.confluence.messages.testFailed', {message: response.message || ''}),
            life: 5000
          });
        }
        this.testing.set(false);
      },
      error: (err) => {
        console.error('Connection test failed:', err);
        this.messageService.add({
          severity: 'error',
          summary: this.translocoService.translate('common.error'),
          detail: this.translocoService.translate('settings.confluence.messages.testError', {error: err.message || 'Unknown error'}),
          life: 5000
        });
        this.testing.set(false);
      }
    });
  }

  onReset(): void {
    if (this.currentConfig()) {
      const config = this.currentConfig()!;
      this.configForm.patchValue({
        baseUrl: config.baseUrl,
        username: config.username,
        apiToken: '',
        connectTimeout: config.connectTimeout,
        readTimeout: config.readTimeout,
        maxRetries: config.maxRetries,
        pagesLimit: config.pagesLimit,
        maxPages: config.maxPages
      });
      this.configForm.markAsPristine();
    } else {
      this.configForm.reset();
      this.initForm();
    }
  }

  get hasUnsavedChanges(): boolean {
    return this.configForm.dirty;
  }
}
