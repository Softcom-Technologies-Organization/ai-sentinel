import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { TranslocoModule } from '@jsverse/transloco';

@Component({
  selector: 'app-scan-controls',
  standalone: true,
  imports: [ButtonModule, TooltipModule, TranslocoModule],
  templateUrl: './scan-controls.component.html',
  styleUrl: './scan-controls.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScanControlsComponent {
  readonly isStreaming = input.required<boolean>();
  readonly canStartScan = input.required<boolean>();
  readonly canResumeScan = input.required<boolean>();
  readonly selectedCount = input<number>(0);

  readonly startAll = output<void>();
  readonly startSelected = output<void>();
  readonly pauseScan = output<void>();
  readonly resumeScan = output<void>();

  onPlayClick(): void {
    if (this.selectedCount() > 0) {
      this.startSelected.emit();
    } else {
      this.startAll.emit();
    }
  }
}
