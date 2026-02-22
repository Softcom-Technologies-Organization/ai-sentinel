import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-confidence-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="confidence-indicator"
         role="meter"
         [attr.aria-valuenow]="percentage()"
         aria-valuemin="0"
         aria-valuemax="100">
      <div class="confidence-bar">
        <div class="confidence-bar-fill"
             [class]="barClass()"
             [style.width.%]="percentage()">
        </div>
      </div>
      <span class="confidence-pct" [class]="pctClass()">
        {{ percentage() }}%
      </span>
    </div>
  `,
  styles: [`
    .confidence-indicator {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }
    .confidence-bar {
      width: 36px;
      height: 3px;
      border-radius: 2px;
      background: #e8ecf1;
    }
    .confidence-bar-fill {
      height: 100%;
      border-radius: 2px;
    }
    .confidence-bar-fill.confidence--high { background: #16a34a; }
    .confidence-bar-fill.confidence--medium { background: #ca8a04; }
    .confidence-bar-fill.confidence--low { background: #dc2626; }
    .confidence-pct {
      font-size: 11px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .confidence-pct.confidence--high { color: #16a34a; }
    .confidence-pct.confidence--medium { color: #ca8a04; }
    .confidence-pct.confidence--low { color: #dc2626; }
  `],
})
export class ConfidenceIndicatorComponent {
  readonly value = input.required<number>();

  readonly percentage = computed(() => Math.round(this.value() * 100));

  readonly barClass = computed(() =>
    `confidence-bar-fill ${this.levelClass()}`
  );

  readonly pctClass = computed(() =>
    `confidence-pct ${this.levelClass()}`
  );

  private levelClass(): string {
    const pct = this.percentage();
    if (pct >= 90) return 'confidence--high';
    if (pct >= 70) return 'confidence--medium';
    return 'confidence--low';
  }
}
