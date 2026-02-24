import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-confidence-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './confidence-indicator.component.html',
  styleUrl: './confidence-indicator.component.css',
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
