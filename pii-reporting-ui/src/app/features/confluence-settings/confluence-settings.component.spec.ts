import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ConfluenceSettingsComponent } from './confluence-settings.component';
import { ConfluenceConnectionConfig } from '../../core/models/confluence-connection-config.model';

const CONFIG_URL = '/api/v1/confluence/connection-config';

const MOCK_CONFIG: ConfluenceConnectionConfig = {
  baseUrl: 'https://confluence.example.com',
  username: 'user@example.com',
  connectTimeout: 30000,
  readTimeout: 60000,
  maxRetries: 3,
  pagesLimit: 25,
  deploymentType: 'CLOUD',
  configured: true,
};

const FR_TRANSLATIONS = {
  common: { success: 'Succès', error: 'Erreur', warning: 'Attention' },
  settings: {
    confluence: {
      placeholders: {
        baseUrlCloud: 'cloud-url',
        baseUrlDc: 'dc-url',
        usernameCloud: 'cloud-user',
        usernameDc: 'dc-user',
        apiTokenCloud: 'cloud-token',
        apiTokenDc: 'dc-token',
        apiTokenExisting: 'existing-token',
      },
      messages: {
        loadError: 'load-error',
        saveSuccess: 'save-ok',
        saveError: 'save-error',
        testSuccess: 'test-ok',
        testFailed: 'test-failed',
        testError: 'test-error',
      },
    },
  },
};

describe('ConfluenceSettingsComponent', () => {
  let component: ConfluenceSettingsComponent;
  let fixture: ComponentFixture<ConfluenceSettingsComponent>;
  let httpMock: HttpTestingController;

  function fillValidCloudForm(): void {
    component.model.set({
      deploymentType: 'CLOUD',
      baseUrl: 'https://confluence.example.com',
      username: 'user@example.com',
      apiToken: 'secret-token',
      connectTimeout: 30000,
      readTimeout: 60000,
      maxRetries: 3,
      pagesLimit: 25,
    });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ConfluenceSettingsComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: FR_TRANSLATIONS },
          translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' },
        }),
      ],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConfluenceSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock.expectOne(CONFIG_URL).flush(MOCK_CONFIG);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ========== loadConfig ==========

  it('Should_PopulateModel_When_ConfigLoaded', () => {
    expect(component.currentConfig()).toEqual(MOCK_CONFIG);
    expect(component.model().baseUrl).toBe(MOCK_CONFIG.baseUrl);
    expect(component.model().apiToken).toBe('');
    expect(component.loading()).toBe(false);
  });

  it('Should_StopLoading_When_LoadConfigFails', () => {
    const failingFixture = TestBed.createComponent(ConfluenceSettingsComponent);
    failingFixture.detectChanges();

    httpMock.expectOne(CONFIG_URL).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(failingFixture.componentInstance.loading()).toBe(false);
  });

  // ========== Computed signals ==========

  it('Should_ReportCloud_When_DeploymentTypeIsCloud', () => {
    expect(component.isCloud()).toBe(true);

    component.model.update((m) => ({ ...m, deploymentType: 'DATA_CENTER' }));

    expect(component.isCloud()).toBe(false);
  });

  it('Should_DetectExistingToken_When_ConfiguredForSameType', () => {
    expect(component.hasExistingTokenForCurrentType()).toBe(true);

    component.model.update((m) => ({ ...m, deploymentType: 'DATA_CENTER' }));

    expect(component.hasExistingTokenForCurrentType()).toBe(false);
  });

  it('Should_ResolvePlaceholders_When_DeploymentTypeToggled', () => {
    expect(component.baseUrlPlaceholder()).toBe('cloud-url');
    expect(component.usernamePlaceholder()).toBe('cloud-user');
    // Existing token for current type wins over deployment-type placeholder
    expect(component.apiTokenPlaceholder()).toBe('existing-token');

    component.model.update((m) => ({ ...m, deploymentType: 'DATA_CENTER' }));

    expect(component.baseUrlPlaceholder()).toBe('dc-url');
    expect(component.usernamePlaceholder()).toBe('dc-user');
    expect(component.apiTokenPlaceholder()).toBe('dc-token');
  });

  // ========== onSave ==========

  it('Should_AbortSave_When_FormInvalid', () => {
    component.model.update((m) => ({ ...m, baseUrl: 'not-a-url' }));

    component.onSave();

    expect(component.saving()).toBe(false);
    httpMock.expectNone((req) => req.method === 'PUT');
  });

  it('Should_PersistConfigAndClearToken_When_SaveSucceeds', () => {
    fillValidCloudForm();
    let saved = false;
    component.saved.subscribe(() => (saved = true));

    component.onSave();
    expect(component.saving()).toBe(true);

    const req = httpMock.expectOne(CONFIG_URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.pagesLimit).toBe(25);
    expect(req.request.body).not.toHaveProperty('maxPages');
    req.flush(MOCK_CONFIG);

    expect(component.saving()).toBe(false);
    expect(component.model().apiToken).toBe('');
    expect(saved).toBe(true);
  });

  it('Should_StopSaving_When_SaveFails', () => {
    fillValidCloudForm();

    component.onSave();
    httpMock.expectOne(CONFIG_URL).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.saving()).toBe(false);
  });

  // ========== onTestConnection ==========

  it('Should_AbortTest_When_BaseUrlInvalid', () => {
    component.model.update((m) => ({ ...m, baseUrl: 'invalid' }));

    component.onTestConnection();

    expect(component.testing()).toBe(false);
    httpMock.expectNone(`${CONFIG_URL}/test`);
  });

  it('Should_NotifySuccess_When_TestConnectionSucceeds', () => {
    fillValidCloudForm();

    component.onTestConnection();
    expect(component.testing()).toBe(true);

    const req = httpMock.expectOne(`${CONFIG_URL}/test`);
    expect(req.request.method).toBe('POST');
    req.flush({ success: true });

    expect(component.testing()).toBe(false);
  });

  it('Should_NotifyWarning_When_TestConnectionReturnsFailure', () => {
    fillValidCloudForm();

    component.onTestConnection();
    httpMock.expectOne(`${CONFIG_URL}/test`).flush({ success: false, message: 'invalid creds' });

    expect(component.testing()).toBe(false);
  });

  it('Should_StopTesting_When_TestConnectionFails', () => {
    fillValidCloudForm();

    component.onTestConnection();
    httpMock.expectOne(`${CONFIG_URL}/test`).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.testing()).toBe(false);
  });

  // ========== onReset ==========

  it('Should_RestoreModelFromCurrentConfig_When_Reset', () => {
    component.model.update((m) => ({ ...m, baseUrl: 'https://changed.example.com', apiToken: 'temp' }));

    component.onReset();

    expect(component.model().baseUrl).toBe(MOCK_CONFIG.baseUrl);
    expect(component.model().apiToken).toBe('');
  });

  it('Should_RestoreDefaults_When_ResetWithoutCurrentConfig', () => {
    component.currentConfig.set(null);
    component.model.update((m) => ({ ...m, baseUrl: 'https://changed.example.com' }));

    component.onReset();

    expect(component.model().baseUrl).toBe('');
    expect(component.model().deploymentType).toBe('CLOUD');
  });

  // ========== Getters ==========

  it('Should_BeFormValid_When_ModelValid', () => {
    fillValidCloudForm();

    expect(component.isFormValid).toBe(true);
  });

  it('Should_ValidateConnectionFields_When_RequiredFieldsPresent', () => {
    fillValidCloudForm();

    expect(component.isConnectionFieldsValid).toBe(true);

    component.model.update((m) => ({ ...m, apiToken: '' }));

    expect(component.isConnectionFieldsValid).toBe(false);
  });
});
