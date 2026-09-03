import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Params, RouterLink } from '@angular/router';
import { TranslocoModule } from '@jsverse/transloco';
import { TooltipModule } from 'primeng/tooltip';
import { RemediationConfigService } from '../../../../core/services/remediation-config.service';
import { TestIds } from '../../../test-ids.constants';

/**
 * Entry point button towards the obfuscation view, scoped to a space,
 * a page or an attachment. Hidden until the backend confirms the
 * pii.remediation.enabled feature flag.
 */
@Component({
  selector: 'app-obfuscation-entry-button',
  standalone: true,
  imports: [TranslocoModule, TooltipModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './obfuscation-entry-button.component.html',
  styleUrl: './obfuscation-entry-button.component.css',
})
export class ObfuscationEntryButtonComponent {
  readonly spaceKey = input.required<string>();
  readonly pageId = input<string | undefined>(undefined);
  readonly attachmentName = input<string | undefined>(undefined);
  readonly labeled = input(false);
  readonly disabled = input(false);

  readonly remediationConfig = inject(RemediationConfigService);

  readonly labelKey = computed(() => {
    if (this.attachmentName()) {
      return 'obfuscation.entry.attachment';
    }
    return this.pageId() ? 'obfuscation.entry.page' : 'obfuscation.entry.space';
  });

  readonly testId = computed(() => {
    if (this.attachmentName()) {
      return TestIds.obfuscation.entryButtons.attachment;
    }
    return this.pageId() ? TestIds.obfuscation.entryButtons.page : TestIds.obfuscation.entryButtons.space;
  });

  readonly queryParams = computed<Params>(() => {
    const params: Params = { spaceKey: this.spaceKey() };
    const pageId = this.pageId();
    if (pageId) {
      params['pageId'] = pageId;
    }
    const attachmentName = this.attachmentName();
    if (attachmentName) {
      params['attachmentName'] = attachmentName;
    }
    return params;
  });
}
