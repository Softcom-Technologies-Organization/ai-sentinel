import { Component, computed, inject, OnInit, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  form, required, email, pattern, min, max, hidden, validate,
  FormField, FieldTree
} from '@angular/forms/signals';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { FieldsetModule } from 'primeng/fieldset';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RadioButtonModule } from 'primeng/radiobutton';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ConfluenceConnectionConfigService } from '../../core/services/confluence-connection-config.service';
import {
  ConfluenceConnectionConfig,
  ConfluenceDeploymentType,
  UpdateConfluenceConnectionConfigRequest,
  TestConnectionRequest
} from '../../core/models/confluence-connection-config.model';

interface ConfluenceFormModel {
  deploymentType: ConfluenceDeploymentType;
  baseUrl: string;
  username: string;
  apiToken: string;
  connectTimeout: number;
  readTimeout: number;
  maxRetries: number;
  pagesLimit: number;
}

const DEFAULT_MODEL: ConfluenceFormModel = {
  deploymentType: 'CLOUD',
  baseUrl: '',
  username: '',
  apiToken: '',
  connectTimeout: 30000,
  readTimeout: 60000,
  maxRetries: 3,
  pagesLimit: 25
};

@Component({
  selector: 'app-confluence-settings',
  templateUrl: './confluence-settings.component.html',
  styleUrl: './confluence-settings.component.css',
  standalone: true,
  imports: [
    CommonModule,
    FormField,
    TranslocoModule,
    ButtonModule,
    FieldsetModule,
    InputNumberModule,
    InputTextModule,
    PasswordModule,
    ProgressSpinnerModule,
    RadioButtonModule,
    ToastModule
  ],
  providers: [MessageService]
})
export class ConfluenceSettingsComponent implements OnInit {
  readonly saved = output();

  private readonly configService = inject(ConfluenceConnectionConfigService);
  private readonly messageService = inject(MessageService);
  private readonly translocoService = inject(TranslocoService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly currentConfig = signal<ConfluenceConnectionConfig | null>(null);

  readonly model = signal<ConfluenceFormModel>({...DEFAULT_MODEL});

  readonly configForm: FieldTree<ConfluenceFormModel> = form(this.model, (path) => {
    // Deployment type
    required(path.deploymentType);

    // Base URL
    required(path.baseUrl);
    pattern(path.baseUrl, /^https?:\/\/.+/);

    // Username: hidden (excluded from validation) for Data Center
    hidden(path.username, () => this.model().deploymentType === 'DATA_CENTER');
    required(path.username);
    email(path.username);

    // API token: required unless an existing token is stored for the current deployment type
    validate(path.apiToken, () => {
      const value = this.model().apiToken;
      if (value) return null;
      const config = this.currentConfig();
      const formType = this.model().deploymentType;
      if (config?.configured && config.deploymentType === formType) return null;
      return {kind: 'required'};
    });

    // Timeouts
    required(path.connectTimeout);
    min(path.connectTimeout, 1000);
    max(path.connectTimeout, 120000);

    required(path.readTimeout);
    min(path.readTimeout, 5000);
    max(path.readTimeout, 300000);

    required(path.maxRetries);
    min(path.maxRetries, 0);
    max(path.maxRetries, 10);

    // Pagination
    required(path.pagesLimit);
    min(path.pagesLimit, 1);
    max(path.pagesLimit, 1000);
  });

  readonly isCloud = computed(() => this.model().deploymentType === 'CLOUD');

  readonly hasExistingTokenForCurrentType = computed(() => {
    const config = this.currentConfig();
    return !!config && config.configured && config.deploymentType === this.model().deploymentType;
  });

  readonly baseUrlPlaceholder = computed(() =>
    this.isCloud()
      ? this.translocoService.translate('settings.confluence.placeholders.baseUrlCloud')
      : this.translocoService.translate('settings.confluence.placeholders.baseUrlDc')
  );

  readonly usernamePlaceholder = computed(() =>
    this.isCloud()
      ? this.translocoService.translate('settings.confluence.placeholders.usernameCloud')
      : this.translocoService.translate('settings.confluence.placeholders.usernameDc')
  );

  readonly apiTokenPlaceholder = computed(() => {
    if (this.hasExistingTokenForCurrentType()) {
      return this.translocoService.translate('settings.confluence.placeholders.apiTokenExisting');
    }
    return this.isCloud()
      ? this.translocoService.translate('settings.confluence.placeholders.apiTokenCloud')
      : this.translocoService.translate('settings.confluence.placeholders.apiTokenDc');
  });

  ngOnInit(): void {
    this.loadConfig();
  }

  private loadConfig(): void {
    this.loading.set(true);

    this.configService.getConfig().subscribe({
      next: (config) => {
        this.currentConfig.set(config);
        const deploymentType = config.deploymentType || 'CLOUD';
        this.model.set({
          deploymentType,
          baseUrl: config.baseUrl,
          username: deploymentType === 'CLOUD' ? config.username : '',
          apiToken: '',
          connectTimeout: config.connectTimeout,
          readTimeout: config.readTimeout,
          maxRetries: config.maxRetries,
          pagesLimit: config.pagesLimit
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
    const formState = this.configForm();
    if (formState.invalid()) return;

    this.saving.set(true);

    const value = formState.value();
    const request: UpdateConfluenceConnectionConfigRequest = {
      baseUrl: value.baseUrl,
      username: value.username,
      apiToken: value.apiToken,
      connectTimeout: value.connectTimeout,
      readTimeout: value.readTimeout,
      maxRetries: value.maxRetries,
      pagesLimit: value.pagesLimit,
      deploymentType: value.deploymentType
    };

    this.configService.updateConfig(request).subscribe({
      next: (config) => {
        this.currentConfig.set(config);
        this.model.update(m => ({...m, apiToken: ''}));
        this.messageService.add({
          severity: 'success',
          summary: this.translocoService.translate('common.success'),
          detail: this.translocoService.translate('settings.confluence.messages.saveSuccess'),
          life: 3000
        });
        this.saving.set(false);
        this.saved.emit();
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
    const formState = this.configForm();
    const baseUrlState = this.configForm.baseUrl();
    const apiTokenState = this.configForm.apiToken();

    if (baseUrlState.invalid() || apiTokenState.invalid()) return;

    if (this.isCloud()) {
      const usernameState = this.configForm.username();
      if (usernameState.invalid()) return;
    }

    this.testing.set(true);

    const value = formState.value();
    const request: TestConnectionRequest = {
      baseUrl: value.baseUrl,
      username: value.username,
      apiToken: value.apiToken,
      deploymentType: value.deploymentType
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
    const config = this.currentConfig();
    if (config) {
      const deploymentType = config.deploymentType || 'CLOUD';
      this.model.set({
        deploymentType,
        baseUrl: config.baseUrl,
        username: deploymentType === 'CLOUD' ? config.username : '',
        apiToken: '',
        connectTimeout: config.connectTimeout,
        readTimeout: config.readTimeout,
        maxRetries: config.maxRetries,
        pagesLimit: config.pagesLimit
      });
    } else {
      this.model.set({...DEFAULT_MODEL});
    }
  }

  get hasUnsavedChanges(): boolean {
    return this.configForm().dirty();
  }

  get isFormValid(): boolean {
    return this.configForm().valid();
  }

  get isConnectionFieldsValid(): boolean {
    if (this.configForm.baseUrl().invalid()) return false;
    if (!this.model().apiToken) return false;
    if (this.isCloud() && this.configForm.username().invalid()) return false;
    return true;
  }
}
