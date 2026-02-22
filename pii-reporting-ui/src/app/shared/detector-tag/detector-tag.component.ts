import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DetectorSource } from '../../core/models/detected-personally-identifiable-information';

@Component({
  selector: 'app-detector-tag',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="detector-tag"
          [class]="cssClass()"
          [attr.aria-label]="detector()">
      {{ detector() }}
    </span>
  `,
  styles: [`
    .detector-tag {
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.4px;
      padding: 2px 7px;
      border-radius: 4px;
      display: inline-block;
    }
    .detector-tag--small {
      font-size: 9px;
      padding: 1px 5px;
    }
    .detector-gliner {
      background: #f3f0ff;
      color: #6d28d9;
      border: 1px solid #e0d6ff;
    }
    .detector-presidio {
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
    }
    .detector-regex {
      background: #fffbeb;
      color: #92400e;
      border: 1px solid #fde68a;
    }
    .detector-unknown {
      background: #f4f4f5;
      color: #71717a;
      border: 1px solid #e4e4e7;
    }
  `],
})
export class DetectorTagComponent {
  readonly detector = input.required<DetectorSource>();
  readonly small = input(false);

  readonly cssClass = computed(() => {
    const base = 'detector-tag';
    const variant = this.detectorVariant();
    const size = this.small() ? `${base}--small` : '';
    return `${base} ${variant} ${size}`.trim();
  });

  private detectorVariant(): string {
    switch (this.detector()) {
      case 'GLINER': return 'detector-gliner';
      case 'PRESIDIO': return 'detector-presidio';
      case 'REGEX': return 'detector-regex';
      default: return 'detector-unknown';
    }
  }
}
