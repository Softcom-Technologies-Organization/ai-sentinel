import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TranslocoModule } from '@jsverse/transloco';

/**
 * Dashboard-wide notice with one call to action and a dismiss button.
 *
 * <p>Wording and action are supplied by the parent so that two situations the
 * operator must tell apart — Confluence not configured, server not answering —
 * never share a message.
 */
@Component({
  selector: 'app-notice-banner',
  standalone: true,
  imports: [ButtonModule, TranslocoModule],
  templateUrl: './notice-banner.component.html',
  styleUrl: './notice-banner.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NoticeBannerComponent {
  @Input() showBanner = false;
  @Input({ required: true }) messageKey = '';
  @Input({ required: true }) actionKey = '';
  @Input({ required: true }) closeAriaLabelKey = '';
  @Input() actionTestId = '';
  @Input() dismissTestId = '';

  @Output() action = new EventEmitter<void>();
  @Output() dismiss = new EventEmitter<void>();

  onAction(): void {
    this.action.emit();
  }

  onDismiss(): void {
    this.dismiss.emit();
  }
}
