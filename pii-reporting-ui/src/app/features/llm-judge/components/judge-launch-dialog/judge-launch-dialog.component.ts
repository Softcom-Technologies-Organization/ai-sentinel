import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import { JudgeApplyMode, JudgeLaunchRequest, JudgeScopeDto } from '../../../../core/models/judge.model';
import { PiiDetectionConfigService } from '../../../../core/services/pii-detection-config.service';
import { TestIds } from '../../../test-ids.constants';

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
const DEFAULT_JUDGE_MODEL = 'qwen3.8-flash-next-iq3_s';
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
export class JudgeLaunchDialogComponent {
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

  readonly selectedOption = computed(() => {
    const options = this.scopeOptions();
    return options.find((option) => option.key === this.selectedScopeKey()) ?? options[0] ?? null;
  });
  readonly selectedCount = computed(() => this.selectedOption()?.count ?? 0);
  readonly estimatedMinutes = computed(() => Math.max(1, Math.ceil((this.selectedCount() * SECONDS_PER_CALL) / 60)));
  readonly canLaunch = computed(() => !this.disabledReason() && this.selectedCount() > 0 && this.selectedOption() !== null);

  constructor() {
    effect(() => {
      if (this.visible()) {
        untracked(() => this.refreshModels());
      }
    });
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
    this.modelsLoading.set(true);
    this.detectionConfig.getConfig().subscribe({
      next: (config) => {
        this.selectedModel.set(this.selectedModel() ?? config.lmStudioJudgeModel ?? DEFAULT_JUDGE_MODEL);
        this.listModels(config.lmStudioHost, config.lmStudioPort);
      },
      error: () => this.modelsLoading.set(false)
    });
  }

  private listModels(host: string, port: number): void {
    this.detectionConfig.listLmStudioModels(host, port, true).subscribe({
      next: (listing) => {
        const notLoaded = this.transloco.translate('judge.dialog.notLoaded');
        const loadedFirst = [...listing.models].sort((a, b) => Number(b.loaded) - Number(a.loaded));
        this.modelOptions.set(loadedFirst.map((model) => ({
          id: model.id,
          label: model.loaded ? model.id : `${model.id} · ${notLoaded}`
        })));
        this.modelsLoading.set(false);
      },
      error: () => this.modelsLoading.set(false)
    });
  }
}
