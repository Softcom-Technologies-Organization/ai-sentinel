import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TranslocoModule } from '@jsverse/transloco';

@Component({
  selector: 'app-confluence-config-banner',
  standalone: true,
  imports: [ButtonModule, TranslocoModule],
  templateUrl: './confluence-config-banner.component.html',
  styleUrl: './confluence-config-banner.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfluenceConfigBannerComponent {
  @Input() showBanner = false;
  @Output() openSettings = new EventEmitter<void>();
  @Output() dismiss = new EventEmitter<void>();

  onOpenSettings(): void {
    this.openSettings.emit();
  }

  onDismiss(): void {
    this.dismiss.emit();
  }
}
