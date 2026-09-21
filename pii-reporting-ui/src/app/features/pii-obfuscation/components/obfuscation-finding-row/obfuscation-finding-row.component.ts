import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { DetectorSource } from '../../../../core/models/detected-personally-identifiable-information';
import {
  FindingStatus,
  RemediationFindingDto,
  RemediationGroupBy
} from '../../../../core/models/remediation.model';
import { ConfidenceIndicatorComponent } from '../../../../shared/confidence-indicator/confidence-indicator.component';
import { DetectorTagComponent } from '../../../../shared/detector-tag/detector-tag.component';
import { PiiValueDisplayService } from '../../../../shared/pii-value-display/pii-value-display.service';
import { PiiItemCardUtils } from '../../../pii-item-card/pii-item-card.utils';
import { TestIds } from '../../../test-ids.constants';

interface StatusMeta {
  labelKey: string;
  icon: string;
  variant: string;
}

const STATUS_META: Record<FindingStatus, StatusMeta> = {
  PENDING: { labelKey: 'obfuscation.status.pending', icon: 'pi-clock', variant: 'pending' },
  REDACTED: { labelKey: 'obfuscation.status.redacted', icon: 'pi-check-circle', variant: 'redacted' },
  MANUALLY_HANDLED: { labelKey: 'obfuscation.status.manual', icon: 'pi-check', variant: 'manual' },
  FALSE_POSITIVE: { labelKey: 'obfuscation.status.fp', icon: 'pi-flag', variant: 'fp' },
  PROPOSED_FALSE_POSITIVE: { labelKey: 'obfuscation.status.proposedFp', icon: 'pi-sparkles', variant: 'proposed' }
};

const JUDGED_KEPT_META: StatusMeta = {
  labelKey: 'obfuscation.status.judgedKept',
  icon: 'pi-sparkles',
  variant: 'judged-keep'
};

/**
 * Single finding occurrence row. Renders exactly what the backend provides
 * (selected flag, status, eligibility) and emits pure gestures — it never
 * decides selection or transition semantics itself.
 */
@Component({
  selector: 'app-obfuscation-finding-row',
  standalone: true,
  imports: [TranslocoModule, ConfidenceIndicatorComponent, DetectorTagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './obfuscation-finding-row.component.html',
  styleUrl: './obfuscation-finding-row.component.css',
})
export class ObfuscationFindingRowComponent {
  readonly finding = input.required<RemediationFindingDto>();
  readonly groupBy = input<RemediationGroupBy>('type');

  readonly toggled = output<void>();
  readonly markManual = output<void>();
  readonly restore = output<void>();
  readonly confirmFalsePositive = output<void>();

  private readonly itemCardUtils = inject(PiiItemCardUtils);
  private readonly valueDisplay = inject(PiiValueDisplayService);

  readonly testIds = TestIds.obfuscation.row;

  // Reviewers need the plaintext value in its detection context to judge false positives.
  // The backend only sends sensitiveValue/sensitiveContext when secret reveal is allowed
  // (the whole remediation feature is gated on it), so a present value means "revealed"; the
  // masked context with [TYPE] token badges is shown when it is absent.
  readonly display = computed(() =>
    this.valueDisplay.build({
      sensitiveValue: this.finding().sensitiveValue,
      sensitiveContext: this.finding().sensitiveContext,
      maskedContext: this.finding().maskedContext,
      revealed: true,
    })
  );

  readonly status = computed(() => this.finding().status);
  readonly judgedKept = computed(() => {
    const current = this.finding();
    return current.status === 'PENDING' && current.judgeVerdict !== undefined && current.judgeVerdict !== 'FALSE_POSITIVE';
  });
  readonly statusMeta = computed(() => (this.judgedKept() ? JUDGED_KEPT_META : STATUS_META[this.status()]));
  readonly judgeConfidencePercent = computed(() => {
    const confidence = this.finding().judgeConfidence;
    if (confidence === undefined || !(this.judgedKept() || this.status() === 'PROPOSED_FALSE_POSITIVE')) {
      return null;
    }
    return Math.round(confidence * 100);
  });
  readonly detectorSource = computed(() => this.finding().detector as DetectorSource);
  readonly confidenceAvailable = computed(() => this.detectorSource() !== 'MINISTRAL');
  readonly attachmentKind = computed(() =>
    this.itemCardUtils.attachmentKind(this.finding().attachmentName)
  );

  // Findings that cannot be auto-redacted (attachments) stay selectable: the checkbox
  // is also the false-positive gesture, and reviewers must be able to flag them.
  readonly checkboxDisabled = computed(() => {
    const current = this.finding();
    return current.status === 'REDACTED' || current.status === 'MANUALLY_HANDLED'
      || current.status === 'PROPOSED_FALSE_POSITIVE';
  });

  readonly treated = computed(
    () => this.status() === 'REDACTED' || this.status() === 'MANUALLY_HANDLED'
  );
}
