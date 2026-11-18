import { Component, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AppShellComponent } from './app-shell.component';
import { AppHeaderComponent } from '../app-header/app-header.component';
import { ConfluenceDashboardComponent } from '../confluence-dashboard/confluence-dashboard.component';
import { PiiSettingsComponent } from '../pii-settings/pii-settings.component';
import { PiiDetectionConfig } from '../../core/models/pii-detection-config.model';
import fr from '../../../assets/i18n/fr.json';

@Component({selector: 'app-header', standalone: true, template: ''})
class HeaderStub {
  readonly openSettings = output<void>();
}

@Component({selector: 'app-confluence-dashboard', standalone: true, template: ''})
class DashboardStub {
  readonly openSettings = output<number>();
}

const CONFIG: PiiDetectionConfig = {
  presidioEnabled: true, regexEnabled: true, ministralEnabled: false,
  trioEnabled: true, bardsEnabled: true, cbxEnabled: true, ettinEnabled: true,
  postfilterEnabled: false, ministralChunkSize: 1024, ministralOverlap: 128,
  ministralConcurrency: 1, ministralConcurrencyAuto: false, defaultThreshold: 0.5,
  lmStudioHost: 'localhost', lmStudioPort: 1234, lmStudioModel: null, lmStudioJudgeModel: null, judgeProvider: null, judgeHost: null, judgePort: null
};

const CONFIG_URL = '/api/v1/pii-detection/config';
const TYPES_URL = '/api/v1/pii-detection/pii-types/grouped';

describe('AppShell settings lifecycle', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppShellComponent, TranslocoTestingModule.forRoot({
        langs: {fr}, translocoConfig: {availableLangs: ['fr'], defaultLang: 'fr'}
      })],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), MessageService, ConfirmationService]
    }).overrideComponent(AppShellComponent, {
      remove: {imports: [AppHeaderComponent, ConfluenceDashboardComponent]},
      add: {imports: [HeaderStub, DashboardStub]}
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AppShellComponent);
  });

  afterEach(() => http.verify({ignoreCancelled: true}));

  function respondWithTypes(): void {
    http.expectOne(CONFIG_URL).flush(CONFIG);
    http.expectOne(TYPES_URL).flush([{
      detector: 'BARDS', categories: [{category: 'DIGITAL', types: [{
        id: 1, detector: 'BARDS', piiType: 'AUTH_SECRET', detectorLabel: 'AUTH_SECRET',
        category: 'DIGITAL', enabled: true, threshold: 0
      }]}]
    }]);
  }

  it('Should_RenderFetchedTypes_When_SettingsAreFirstOpened', async () => {
    fixture.detectChanges();
    http.expectNone(CONFIG_URL);
    http.expectNone(TYPES_URL);

    fixture.componentInstance.openSettingsDialog(2);
    fixture.detectChanges();
    respondWithTypes();
    fixture.detectChanges();
    await fixture.whenStable();

    const settings = fixture.debugElement.query(By.directive(PiiSettingsComponent));
    expect(settings.nativeElement.querySelector('.detector-badge').textContent).toContain('Trio PII');
    expect(settings.nativeElement.querySelector('.type-name').textContent).toContain('Secret d’authentification');
  });

  it('Should_RetryFailedLoad_When_SettingsAreReopened', async () => {
    fixture.componentInstance.openSettingsDialog(2);
    fixture.detectChanges();
    http.expectOne(TYPES_URL);
    http.expectOne(CONFIG_URL).flush('Unavailable', {status: 503, statusText: 'Unavailable'});
    await fixture.whenStable();

    fixture.componentInstance.closeSettingsDialog();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.openSettingsDialog(2);
    fixture.detectChanges();
    respondWithTypes();
    fixture.detectChanges();
    await fixture.whenStable();

    const settings = fixture.debugElement.query(By.directive(PiiSettingsComponent));
    expect(settings.nativeElement.querySelectorAll('.pii-type-row')).toHaveLength(1);
  });
});
