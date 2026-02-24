import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DetectorSource } from '../../core/models/detected-personally-identifiable-information';

@Component({
  selector: 'app-detector-tag',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './detector-tag.component.html',
  styleUrl: './detector-tag.component.css',
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
