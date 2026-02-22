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
  template: `
    <div class="severity-grid">
      @for (card of cards(); track card.labelKey) {
        <div class="severity-card"
             [style.background]="card.bg"
             [style.border-color]="card.color + '33'">
          <span class="severity-count" [style.color]="card.color">{{ card.count }}</span>
          <span class="severity-label" [style.color]="card.color">{{ card.labelKey | transloco }}</span>
        </div>
      }
    </div>
  `,
  styles: [`
    .severity-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 0.75rem;
      margin-bottom: 1rem;
    }

    .severity-card {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      border-radius: 8px;
      padding: 0.375rem 0.75rem;
      border: 1px solid;
    }

    .severity-count {
      font-size: 1rem;
      font-weight: 700;
      line-height: 1;
    }

    .severity-label {
      font-size: 0.75rem;
      font-weight: 500;
      opacity: 0.8;
    }

    @media (max-width: 768px) {
      .severity-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }

    @media (max-width: 480px) {
      .severity-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
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
