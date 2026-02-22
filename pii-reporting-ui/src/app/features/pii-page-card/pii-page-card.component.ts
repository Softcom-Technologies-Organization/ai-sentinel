import { ChangeDetectionStrategy, ChangeDetectorRef, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { PersonallyIdentifiableInformationScanResult } from '../../core/models/personally-identifiable-information-scan-result';
import { RevealedSecret, SentinelleApiService } from '../../core/services/sentinelle-api.service';
import { CardExpansionStateService } from '../../core/services/card-expansion-state.service';
import { PiiCardCollapsedComponent } from './pii-card-collapsed.component';
import { PiiCardExpandedComponent } from './pii-card-expanded.component';
import { TestIds } from '../test-ids.constants';

@Component({
  selector: 'app-pii-page-card',
  standalone: true,
  imports: [PiiCardCollapsedComponent, PiiCardExpandedComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pii-page-card.component.html',
  styleUrls: ['./pii-page-card.component.scss'],
})
export class PiiPageCardComponent implements OnInit {
  readonly item = input.required<PersonallyIdentifiableInformationScanResult>();
  readonly maskByDefault = input(true);

  readonly expanded = signal(false);
  readonly revealed = signal(false);
  readonly isRevealing = signal(false);

  private readonly sentinelleApi = inject(SentinelleApiService);
  private readonly expansionState = inject(CardExpansionStateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly revealedSecrets = signal<RevealedSecret[]>([]);
  readonly testIds = TestIds;

  private cardKey(): string {
    const it = this.item();
    return `${it.pageId}-${it.attachmentName || 'page'}`;
  }

  ngOnInit(): void {
    if (this.expansionState.isExpanded(this.cardKey())) {
      this.expanded.set(true);
    }
  }

  readonly enrichedItem = computed(() => {
    const base = this.item();
    const secrets = this.revealedSecrets();
    if (secrets.length === 0) return base;

    const enrichedEntities = base.detectedPersonallyIdentifiableInformationList.map(entity => {
      const secret = secrets.find(
        s => s.startPosition === entity.startPosition &&
          s.endPosition === entity.endPosition &&
          s.maskedContext === entity.maskedContext
      );
      return secret
        ? { ...entity, sensitiveValue: secret.sensitiveValue, sensitiveContext: secret.sensitiveContext }
        : entity;
    });

    return { ...base, detectedPersonallyIdentifiableInformationList: enrichedEntities };
  });

  onExpand(): void {
    this.expanded.set(true);
    this.expansionState.expand(this.cardKey());
  }

  onCollapse(): void {
    this.expanded.set(false);
    this.expansionState.collapse(this.cardKey());
  }

  onRevealRequested(): void {
    if (this.revealed()) {
      this.revealed.set(false);
      return;
    }

    if (this.revealedSecrets().length > 0) {
      this.revealed.set(true);
      return;
    }

    const currentItem = this.item();
    const hasSecrets = currentItem.detectedPersonallyIdentifiableInformationList?.some(
      e => e.sensitiveValue != null
    );

    if (hasSecrets) {
      this.revealed.set(true);
      return;
    }

    if (!this.sentinelleApi.revealAllowed() || !currentItem.pageId) {
      return;
    }

    this.isRevealing.set(true);
    this.sentinelleApi.revealPageSecrets(currentItem.scanId, currentItem.pageId).subscribe({
      next: (response) => {
        this.revealedSecrets.set(response.secrets ?? []);
        this.revealed.set(true);
        this.isRevealing.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.isRevealing.set(false);
        this.cdr.markForCheck();
      },
    });
  }
}
