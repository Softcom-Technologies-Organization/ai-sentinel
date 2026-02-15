import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DataSource } from '../../core/models/data-source.model';

@Component({
  selector: 'app-source-empty-state',
  standalone: true,
  imports: [TranslocoModule, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-state">
      <div class="empty-state-icon">
        <i [class]="source.icon"></i>
      </div>
      <h3>{{ 'sources.notConfigured.title' | transloco }}</h3>
      <p>{{ 'sources.notConfigured.message' | transloco: { source: source.labelKey | transloco } }}</p>
      <p-button [label]="'sources.notConfigured.configure' | transloco"
                icon="pi pi-cog"
                (click)="configure.emit()" />
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 3.75rem 1.25rem;
      text-align: center;
    }

    .empty-state-icon i {
      font-size: 3rem;
      opacity: 0.3;
      color: var(--text-color-secondary);
      margin-bottom: 1rem;
    }

    h3 {
      margin: 0 0 0.5rem;
      font-weight: 600;
      color: #334155;
    }

    p {
      color: #94a3b8;
      font-size: 0.875rem;
      margin-bottom: 1.25rem;
      max-width: 400px;
    }

    :host ::ng-deep .p-button {
      background: #0f2b3c;
      border-color: #0f2b3c;
      border-radius: 8px;
      padding: 0.625rem 1.5rem;
      font-size: 0.8125rem;
      font-weight: 600;
    }
  `]
})
export class SourceEmptyStateComponent {
  @Input({ required: true }) source!: DataSource;
  @Output() configure = new EventEmitter<void>();
}
