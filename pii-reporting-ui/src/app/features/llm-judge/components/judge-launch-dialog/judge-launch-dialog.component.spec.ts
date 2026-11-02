import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { JudgeLaunchDialogComponent, JudgeScopeOption } from './judge-launch-dialog.component';
import { PiiDetectionConfigService } from '../../../../core/services/pii-detection-config.service';
import { JudgeLaunchRequest } from '../../../../core/models/judge.model';

const FR_TRANSLATIONS = {
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
const CONFIG: { lmStudioHost: string; lmStudioPort: number; lmStudioModel: string; lmStudioJudgeModel: string | null } =
  { lmStudioHost: 'localhost', lmStudioPort: 1234, lmStudioModel: 'ministral@q8', lmStudioJudgeModel: null };
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
    expect(listLmStudioModels).toHaveBeenCalledWith('localhost', 1234, true);
    expect(fixture.componentInstance.modelOptions().map((option) => option.label))
      .toEqual(['qwen3.8-flash-next-iq3_s', 'qwen3.8-flash-next-q8_0 · non chargé']);
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

  it('Should_KeepQwenFallbackWithoutRetrying_When_JudgeModelListingFails', () => {
    listLmStudioModels.mockReturnValueOnce(throwError(() => new Error('Judge unavailable')));

    createComponent();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedModel()).toBe('qwen3.8-flash-next-iq3_s');
    expect(fixture.componentInstance.modelsLoading()).toBe(false);
    expect(listLmStudioModels).toHaveBeenCalledTimes(1);
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

  it('Should_ReloadModelsAndKeepChoice_When_RefreshClicked', () => {
    createComponent();
    fixture.componentInstance.selectedModel.set('qwen3.8-flash-next-q8_0');

    query<HTMLButtonElement>('judge-refresh-models').click();
    fixture.detectChanges();

    expect(listLmStudioModels).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.selectedModel()).toBe('qwen3.8-flash-next-q8_0');
    expect(fixture.componentInstance.modelOptions()).toHaveLength(2);
  });
});
