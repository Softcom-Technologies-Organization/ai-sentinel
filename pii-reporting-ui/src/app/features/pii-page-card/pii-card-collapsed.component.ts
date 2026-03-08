import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { PersonallyIdentifiableInformationScanResult } from '../../core/models/personally-identifiable-information-scan-result';
import { SEVERITY_STYLES } from './severity.config';
import { PiiItemCardUtils } from '../pii-item-card/pii-item-card.utils';
import { PiiTypeTranslationService } from '../../shared/utils/pii-type-translation.service';

@Component({
  selector: 'app-pii-card-collapsed',
  standalone: true,
  imports: [TranslocoModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pii-card-collapsed.component.html',
  styleUrl: './pii-card-collapsed.component.css',
})
export class PiiCardCollapsedComponent {
  readonly item = input.required<PersonallyIdentifiableInformationScanResult>();
  readonly expand = output<void>();

  private readonly piiItemCardUtils = inject(PiiItemCardUtils);
  private readonly piiTypeTranslation = inject(PiiTypeTranslationService);

  readonly severityStyle = computed(() => SEVERITY_STYLES[this.item().severity] ?? SEVERITY_STYLES.low);

  readonly totalDetections = computed(() =>
    this.item().detectedPersonallyIdentifiableInformationList?.length ?? 0
  );

  readonly piiTypeBadges = computed(() =>
    this.piiTypeTranslation.computePiiTypeBadges(this.item().detectedPersonallyIdentifiableInformationList ?? [])
  );

  readonly attachmentKind = computed(() =>
    this.piiItemCardUtils.attachmentKind(this.item().attachmentType)
  );

  onCardClick(): void {
    this.expand.emit();
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.expand.emit();
    }
  }
}
