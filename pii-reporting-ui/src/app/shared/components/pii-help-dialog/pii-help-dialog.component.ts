import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { DialogModule } from 'primeng/dialog';
import { BadgeModule } from 'primeng/badge';

@Component({
  selector: 'app-pii-help-dialog',
  standalone: true,
  imports: [TranslocoModule, DialogModule, BadgeModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pii-help-dialog.component.html',
})
export class PiiHelpDialogComponent {
  readonly visible = input(false);
  readonly hide = output<void>();
}
