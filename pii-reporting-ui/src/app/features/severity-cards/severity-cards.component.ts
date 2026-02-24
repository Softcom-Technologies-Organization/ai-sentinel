import { ChangeDetectionStrategy, Component, computed, Input, signal } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { SeverityCounts } from '../../core/models/severity-counts';

interface SeverityCard {
  labelKey: string;
  count: number;
  color: string;
  bg: string;
}

@Component({
  selector: 'app-severity-cards',
  standalone: true,
  imports: [TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './severity-cards.component.html',
  styleUrl: './severity-cards.component.css'
})
export class SeverityCardsComponent {
  private readonly _counts = signal<SeverityCounts>({ total: 0, high: 0, medium: 0, low: 0 });

  @Input({ required: true })
  set counts(value: SeverityCounts) {
    this._counts.set(value);
  }

  readonly cards = computed<SeverityCard[]>(() => {
    const c = this._counts();
    return [
      { labelKey: 'severity.high',   count: c.high,   color: '#ef4444', bg: '#fef2f2' },
      { labelKey: 'severity.medium', count: c.medium, color: '#f97316', bg: '#fff7ed' },
      { labelKey: 'severity.low',    count: c.low,    color: '#eab308', bg: '#fefce8' },
      { labelKey: 'severity.total',  count: c.total,  color: '#22c55e', bg: '#f0fdf4' },
    ];
  });
}
