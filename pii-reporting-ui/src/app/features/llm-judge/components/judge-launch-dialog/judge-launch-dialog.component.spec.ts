import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { JudgeLaunchDialogComponent, JudgeScopeOption } from './judge-launch-dialog.component';
import { PiiDetectionConfigService } from '../../../../core/services/pii-detection-config.service';
import { JudgeLaunchRequest } from '../../../../core/models/judge.model';
import { JudgeProvider, PiiDetectionConfig } from '../../../../core/models/pii-detection-config.model';

const FR_TRANSLATIONS = {
  settings: { judge: { inherited: 'Configuration existante' }, lmStudio: { judgeModelRefresh: 'Actualiser les modèles du juge' } },
  judge: {
    unavailable: { scanInProgress: 'Disponible après la fin du scan' },
    scope: {
      allPending: 'Toutes les détections à traiter',
      currentSelection: 'Sélection actuelle',
    },
    dialog: {
      title: 'Évaluer les détections par juge LLM',
      scope: 'Périmètre',
      model: 'Modèle juge',
      modelLoading: 'Chargement…',
      modelPlaceholder: 'Identifiant du modèle',
      notLoaded: 'non chargé',
      serverError: 'Vérifiez le serveur dans Paramètres → Serveurs LLM, puis actualisez la liste.',
      noModels: 'Chargez un modèle dans le serveur, puis actualisez la liste.',
      apply: 'Application des verdicts',
      propose: 'Proposer pour validation',
      proposeHint: 'hint',
      autoApply: 'Appliquer automatiquement',
      autoApplyHint: 'hint',
      calls: '<b>{{count}}</b> appels au modèle',
      duration: 'Durée estimée <b>~{{minutes}} min</b>',
      threshold: 'Seuil de confiance',
      cancel: 'Annuler',
      launch: "Lancer l'évaluation ({{count}})",
    },
  },
};

const OPTIONS: JudgeScopeOption[] = [
  { key: 'all', labelKey: 'judge.scope.allPending', count: 95, scope: { spaceKeys: ['AS'] } },
  { key: 'selection', labelKey: 'judge.scope.currentSelection', hintText: 'Jeton', count: 11, scope: { spaceKeys: ['AS', 'B'] } },
];

const LISTING = {
  endpoint: 'http://strata:1235/v1', family: '', error: '',
  models: [
    { id: 'qwen3.8-flash-next-q8_0', quantization: 'Q8_0', publisher: 'Qwen', loaded: false },
    { id: 'qwen3.8-flash-next-iq3_s', quantization: 'IQ3_S', publisher: 'Qwen', loaded: true },
  ],
};
const listLmStudioModels = vi.fn(() => of(LISTING));
const CONFIG: Pick<PiiDetectionConfig, 'lmStudioHost' | 'lmStudioPort' | 'lmStudioModel' | 'lmStudioJudgeModel' | 'judgeProvider' | 'judgeHost' | 'judgePort'> = {
  lmStudioHost: 'localhost', lmStudioPort: 1234, lmStudioModel: 'ministral@q8',
  lmStudioJudgeModel: 'qwen3.8-flash-next-iq3_s', judgeProvider: null, judgeHost: null, judgePort: null
};
const getConfig = vi.fn(() => of(CONFIG));

describe('JudgeLaunchDialogComponent', () => {
  let fixture: ComponentFixture<JudgeLaunchDialogComponent>;

  beforeEach(async () => {
    listLmStudioModels.mockClear();
    getConfig.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        JudgeLaunchDialogComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS },
        }),
      ],
      providers: [
        {
          provide: PiiDetectionConfigService,
          useValue: {
            getConfig: getConfig,
            listLmStudioModels: listLmStudioModels,
          },
        },
      ],
    }).compileComponents();
  });

  function createComponent(options: JudgeScopeOption[] = OPTIONS): void {
    fixture = TestBed.createComponent(JudgeLaunchDialogComponent);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('scopeOptions', options);
    fixture.detectChanges();
  }

  function query<T extends HTMLElement>(testId: string): T {
    return document.querySelector(`[data-testid="${testId}"]`) as T;
  }

  it('Should_PreselectFirstScopeAndConfiguredModel_When_Opened', () => {
    createComponent();

    expect(fixture.componentInstance.selectedOption()?.key).toBe('all');
    expect(fixture.componentInstance.selectedModel()).toBe('qwen3.8-flash-next-iq3_s');
    expect(listLmStudioModels).toHaveBeenCalledWith(undefined, undefined, true);
    expect(query('judge-server').textContent).toContain('Configuration existante');
    expect(query('judge-server').textContent).toContain(LISTING.endpoint);
    expect(fixture.componentInstance.modelOptions().map((option) => option.label))
      .toEqual(['qwen3.8-flash-next-q8_0', 'qwen3.8-flash-next-iq3_s']);
    expect(query('judge-estimate').textContent).toContain('95');
  });

  it('Should_PreselectConfiguredJudgeModel_When_ItDiffersFromTheDetectionModel', () => {
    getConfig.mockReturnValueOnce(of({ ...CONFIG, lmStudioJudgeModel: 'qwen3.8-flash-next-q8_0' }));

    createComponent();

    expect(fixture.componentInstance.selectedModel()).toBe('qwen3.8-flash-next-q8_0');
  });

  it('Should_ProposeAt95PercentWithQwen_When_LaunchingWithDefaults', () => {
    createComponent();
    const launched = vi.fn();
    fixture.componentInstance.launched.subscribe(launched);

    query<HTMLButtonElement>('judge-launch').click();

    expect(launched).toHaveBeenCalledWith({
      scope: { spaceKeys: ['AS'] },
      model: 'qwen3.8-flash-next-iq3_s',
      applyMode: 'PROPOSE',
      confidenceThreshold: 0.95,
    });
  });

  it('Should_ShowRecoveryInstructionsAndBlockLaunch_When_JudgeModelListingFails', () => {
    listLmStudioModels.mockReturnValueOnce(throwError(() => new Error('Judge unavailable')));

    createComponent();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedModel()).toBe('qwen3.8-flash-next-iq3_s');
    expect(fixture.componentInstance.modelsLoading()).toBe(false);
    expect(listLmStudioModels).toHaveBeenCalledTimes(1);
    expect(query('judge-model-error').textContent).toContain('Vérifiez le serveur');
    expect(query<HTMLButtonElement>('judge-launch').disabled).toBe(true);
  });

  it('Should_EmitLaunchRequestFromChoices_When_Launching', () => {
    createComponent();
    const launched = vi.fn();
    fixture.componentInstance.launched.subscribe(launched);

    query<HTMLInputElement>('judge-scope-option-selection').click();
    query<HTMLInputElement>('judge-apply-option-AUTO_APPLY').click();
    const threshold = query<HTMLInputElement>('judge-threshold');
    threshold.value = '95';
    threshold.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    query<HTMLButtonElement>('judge-launch').click();

    const request: JudgeLaunchRequest = launched.mock.calls[0][0];
    expect(request).toEqual({
      scope: { spaceKeys: ['AS', 'B'] },
      model: 'qwen3.8-flash-next-iq3_s',
      applyMode: 'AUTO_APPLY',
      confidenceThreshold: 0.95,
    });
  });

  it('Should_DisableLaunch_When_ScopeIsEmpty', () => {
    createComponent([{ key: 'all', labelKey: 'judge.scope.allPending', count: 0, scope: { spaceKeys: ['AS'] } }]);

    expect(query<HTMLButtonElement>('judge-launch').disabled).toBe(true);
  });

  it('Should_DisableLaunchAndBlockEmission_When_ScanStartsWithDialogOpen', () => {
    createComponent();
    const launched = vi.fn();
    fixture.componentInstance.launched.subscribe(launched);
    expect(query<HTMLButtonElement>('judge-launch').disabled).toBe(false);
    expect(fixture.componentInstance.applyMode()).toBe('PROPOSE');

    fixture.componentRef.setInput('disabledReason', 'judge.unavailable.scanInProgress');
    fixture.detectChanges();
    expect(query<HTMLButtonElement>('judge-launch').disabled).toBe(true);
    expect(document.body.textContent).toContain('Disponible après la fin du scan');
    expect(fixture.nativeElement.querySelector('output')?.textContent)
      .toContain('Disponible après la fin du scan');
    fixture.componentInstance.launch();
    expect(launched).not.toHaveBeenCalled();

    fixture.componentRef.setInput('disabledReason', null);
    fixture.detectChanges();
    expect(query<HTMLButtonElement>('judge-launch').disabled).toBe(false);
  });

  it('Should_ReplaceStaleSelectionAndEndpoint_When_ConfigChangesBeforeRefresh', () => {
    createComponent();
    fixture.componentInstance.selectedModel.set('qwen3.8-flash-next-q8_0');
    getConfig.mockReturnValueOnce(of({ ...CONFIG, judgeProvider: 'OMLX', judgeHost: '127.0.0.1', judgePort: 8000,
      lmStudioJudgeModel: 'Qwen3.8-Flash-Next-oQ4e-mtp' }));
    listLmStudioModels.mockReturnValueOnce(of({ ...LISTING, endpoint: 'http://127.0.0.1:8000/v1',
      models: [{ ...LISTING.models[0], id: 'Qwen3.8-Flash-Next-oQ4e-mtp' }] }));

    query<HTMLButtonElement>('judge-refresh-models').click();
    fixture.detectChanges();

    expect(listLmStudioModels).toHaveBeenCalledTimes(2);
    expect(listLmStudioModels).toHaveBeenLastCalledWith('127.0.0.1', 8000, true);
    expect(fixture.componentInstance.selectedModel()).toBe('Qwen3.8-Flash-Next-oQ4e-mtp');
    expect(query('judge-server').textContent).toContain('oMLX');
    expect(query('judge-server').textContent).toContain('http://127.0.0.1:8000/v1');
    expect(fixture.componentInstance.modelOptions()).toHaveLength(1);
  });

  it('Should_AllowServerDefaultsWithoutInventingAModel_When_LegacyConfigHasNoModel', () => {
    getConfig.mockReturnValueOnce(of({ ...CONFIG, lmStudioJudgeModel: null }));
    createComponent();
    const launched = vi.fn();
    fixture.componentInstance.launched.subscribe(launched);
    fixture.componentInstance.launch();
    expect(launched).toHaveBeenCalledWith(expect.objectContaining({ model: null }));
  });

  it('Should_RequireAModel_When_ProviderIsExplicitAndSeveralModelsAreListed', () => {
    getConfig.mockReturnValueOnce(of({ ...CONFIG, judgeProvider: 'STRATA', judgeHost: 'strata', judgePort: 8080, lmStudioJudgeModel: null }));
    createComponent();
    expect(fixture.componentInstance.canLaunch()).toBe(false);
    fixture.componentInstance.selectedModel.set('custom-model');
    expect(fixture.componentInstance.canLaunch()).toBe(true);
    fixture.componentInstance.selectedModel.set('   ');
    expect(fixture.componentInstance.canLaunch()).toBe(false);
  });

  it('Should_PreselectOnlyListedModel_When_NoJudgeModelIsConfigured', () => {
    getConfig.mockReturnValueOnce(of({ ...CONFIG, lmStudioJudgeModel: null }));
    listLmStudioModels.mockReturnValueOnce(of({ ...LISTING, models: [LISTING.models[0]] }));
    createComponent();
    expect(fixture.componentInstance.selectedModel()).toBe(LISTING.models[0].id);
  });

  it.each<JudgeProvider | null>(['OMLX', 'STRATA', null])('Should_DisplayOnlyModelIdsInServerOrder_When_JudgeUses_%s', provider => {
    getConfig.mockReturnValueOnce(of({ ...CONFIG, judgeProvider: provider, judgeHost: 'judge', judgePort: 8000 }));
    createComponent();
    expect(fixture.componentInstance.modelOptions().map(option => option.label)).toEqual(LISTING.models.map(model => model.id));
    const refresh = query<HTMLButtonElement>('judge-refresh-models');
    expect(refresh.tagName).toBe('BUTTON');
    expect(refresh.getAttribute('aria-label')).toBe('Actualiser les modèles du juge');
  });

  it('Should_KeepLoadState_When_JudgeUsesLmStudio', () => {
    getConfig.mockReturnValueOnce(of({ ...CONFIG, judgeProvider: 'LM_STUDIO', judgeHost: 'judge', judgePort: 1234 }));
    createComponent();
    expect(fixture.componentInstance.modelOptions().map(option => option.label))
      .toEqual(['qwen3.8-flash-next-iq3_s', 'qwen3.8-flash-next-q8_0 · non chargé']);
  });

  it.each([
    { ...LISTING, models: [], error: '' },
    { ...LISTING, error: 'backend.raw.error.key' }
  ])('Should_ShowActionableError_When_ListingCannotBeUsed', listing => {
    listLmStudioModels.mockReturnValueOnce(of(listing));
    createComponent();
    const message = query('judge-model-error').textContent;
    expect(message).toContain('actualisez la liste');
    expect(message).not.toContain('backend.raw.error.key');
    expect(fixture.componentInstance.canLaunch()).toBe(false);
  });

  it('Should_ClearPreviousServerAndModel_When_ConfigReloadFails', () => {
    createComponent();
    getConfig.mockReturnValueOnce(throwError(() => new Error('Config unavailable')));
    query<HTMLButtonElement>('judge-refresh-models').click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedModel()).toBeNull();
    expect(fixture.componentInstance.judgeEndpoint()).toBeNull();
    expect(query('judge-model-error').textContent).toContain('Vérifiez le serveur');
    expect(fixture.componentInstance.canLaunch()).toBe(false);
  });

  it('Should_IgnoreAnOldEndpointResponse_When_RefreshedWhileLoading', () => {
    const pending = new Subject<typeof LISTING>();
    listLmStudioModels.mockReturnValueOnce(pending);
    createComponent();
    expect(fixture.componentInstance.canLaunch()).toBe(false);
    fixture.componentInstance.refreshModels();
    pending.next({ ...LISTING, endpoint: 'http://old-server/v1' });
    expect(fixture.componentInstance.judgeEndpoint()).toBe(LISTING.endpoint);
    expect(fixture.componentInstance.canLaunch()).toBe(true);
  });

  it('Should_ReloadConfiguredModel_When_DialogIsReopened', () => {
    createComponent();
    fixture.componentInstance.selectedModel.set('temporary-selection');
    fixture.componentRef.setInput('visible', false);
    fixture.detectChanges();
    getConfig.mockReturnValueOnce(of({ ...CONFIG, lmStudioJudgeModel: LISTING.models[0].id }));
    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedModel()).toBe(LISTING.models[0].id);
  });
});
