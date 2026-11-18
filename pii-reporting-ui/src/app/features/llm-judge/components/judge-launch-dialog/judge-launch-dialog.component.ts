import { ChangeDetectionStrategy, Component, computed, effect, inject, input, OnDestroy, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import { JudgeApplyMode, JudgeLaunchRequest, JudgeScopeDto } from '../../../../core/models/judge.model';
import { PiiDetectionConfigService } from '../../../../core/services/pii-detection-config.service';
import { TestIds } from '../../../test-ids.constants';
import { JUDGE_PROVIDERS, JudgeProvider, LmStudioModelListing } from '../../../../core/models/pii-detection-config.model';
import { Subscription, switchMap } from 'rxjs';

/** One selectable scope; labels are translation keys, the hint may be a key or a literal (space names). */
export interface JudgeScopeOption {
  key: string;
  labelKey: string;
  labelParams?: Record<string, unknown>;
  hintKey?: string;
  hintParams?: Record<string, unknown>;
  hintText?: string;
  count: number;
  scope: JudgeScopeDto;
}

interface ModelOption {
  id: string;
  label: string;
}

export const DEFAULT_CONFIDENCE_PERCENT = 95;
const SECONDS_PER_CALL = 2;

/**
 * Launch dialog shared by the dashboard and the remediation view: scope, judge model, how the
 * verdicts are applied, confidence threshold and a cost estimate. Emits the launch request only;
 * the caller talks to the backend.
 */
@Component({
  selector: 'app-judge-launch-dialog',
  standalone: true,
  imports: [TranslocoModule, FormsModule, ButtonModule, DialogModule, SelectModule, TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './judge-launch-dialog.component.html',
  styleUrl: './judge-launch-dialog.component.css',
})
export class JudgeLaunchDialogComponent implements OnDestroy {
  readonly visible = input.required<boolean>();
  readonly scopeOptions = input.required<JudgeScopeOption[]>();

  readonly disabledReason = input<string | null>(null);

  readonly launched = output<JudgeLaunchRequest>();
  readonly cancelled = output<void>();

  private readonly detectionConfig = inject(PiiDetectionConfigService);
  private readonly transloco = inject(TranslocoService);

  readonly testIds = TestIds.judge.dialog;
  readonly applyModes: { value: JudgeApplyMode; labelKey: string; hintKey: string }[] = [
    { value: 'PROPOSE', labelKey: 'judge.dialog.propose', hintKey: 'judge.dialog.proposeHint' },
    { value: 'AUTO_APPLY', labelKey: 'judge.dialog.autoApply', hintKey: 'judge.dialog.autoApplyHint' }
  ];

  readonly selectedScopeKey = signal<string | null>(null);
  readonly selectedModel = signal<string | null>(null);
  readonly applyMode = signal<JudgeApplyMode>('PROPOSE');
  readonly thresholdPercent = signal(DEFAULT_CONFIDENCE_PERCENT);
  readonly modelOptions = signal<ModelOption[]>([]);
  readonly modelsLoading = signal(false);
  readonly modelsError = signal<string | null>(null);
  readonly judgeProvider = signal<JudgeProvider | null>(null);
  readonly judgeEndpoint = signal<string | null>(null);
  readonly providerLabel = computed(() => JUDGE_PROVIDERS.find(provider => provider.value === this.judgeProvider())?.label);
  private modelsSubscription: Subscription | null = null;

  readonly selectedOption = computed(() => {
    const options = this.scopeOptions();
    return options.find((option) => option.key === this.selectedScopeKey()) ?? options[0] ?? null;
  });
  readonly selectedCount = computed(() => this.selectedOption()?.count ?? 0);
  readonly estimatedMinutes = computed(() => Math.max(1, Math.ceil((this.selectedCount() * SECONDS_PER_CALL) / 60)));
  readonly modelReady = computed(() => !this.modelsLoading() && !this.modelsError()
    && (!this.judgeProvider() || !!this.selectedModel()?.trim()));
  readonly canLaunch = computed(() => !this.disabledReason() && this.selectedCount() > 0 && this.modelReady());

  constructor() {
    effect(() => {
      if (this.visible()) {
        untracked(() => this.refreshModels());
      } else {
        this.modelsSubscription?.unsubscribe();
      }
    });
  }

  ngOnDestroy(): void {
    this.modelsSubscription?.unsubscribe();
  }

  onThresholdInput(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    if (Number.isFinite(value)) {
      this.thresholdPercent.set(Math.min(100, Math.max(0, Math.round(value))));
    }
  }

  launch(): void {
    const option = this.selectedOption();
    if (!this.canLaunch() || !option) {
      return;
    }
    this.launched.emit({
      scope: option.scope,
      model: this.selectedModel(),
      applyMode: this.applyMode(),
      confidenceThreshold: this.thresholdPercent() / 100
    });
  }

  refreshModels(): void {
    this.modelsSubscription?.unsubscribe();
    this.modelsLoading.set(true);
    this.modelsError.set(null);
    this.selectedModel.set(null);
    this.modelOptions.set([]);
    this.judgeEndpoint.set(null);
    this.judgeProvider.set(null);
    this.modelsSubscription = this.detectionConfig.getConfig().pipe(
      switchMap(config => {
        this.selectedModel.set(config.lmStudioJudgeModel ?? null);
        this.judgeProvider.set(config.judgeProvider ?? null);
        return this.detectionConfig.listLmStudioModels(
          config.judgeProvider ? config.judgeHost ?? undefined : undefined,
          config.judgeProvider ? config.judgePort ?? undefined : undefined,
          true);
      })
    ).subscribe({
      next: listing => this.applyModelListing(listing),
      error: () => {
        this.modelsError.set('judge.dialog.serverError');
        this.modelsLoading.set(false);
      }
    });
  }

  private applyModelListing(listing: LmStudioModelListing): void {
    const notLoaded = this.transloco.translate('judge.dialog.notLoaded');
    const hasLoadState = this.judgeProvider() === 'LM_STUDIO';
    const models = hasLoadState ? [...listing.models].sort((a, b) => Number(b.loaded) - Number(a.loaded)) : listing.models;
    this.modelOptions.set(models.map(model => ({
      id: model.id,
      label: !hasLoadState || model.loaded ? model.id : `${model.id} · ${notLoaded}`
    })));
    this.judgeEndpoint.set(listing.endpoint);
    this.modelsError.set(listing.error ? 'judge.dialog.serverError' : null);
    if (!listing.error && listing.models.length === 0) this.modelsError.set('judge.dialog.noModels');
    if (!this.selectedModel() && listing.models.length === 1) this.selectedModel.set(listing.models[0].id);
    this.modelsLoading.set(false);
  }
}
