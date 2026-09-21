import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Colour identity of a panel: the blue scan controls or the violet LLM judge controls. */
export type ActionPanelAccent = 'scan' | 'judge';

/**
 * Segmented group of icon buttons sharing one rounded shell, with a spinner overlay while an
 * action is in flight. Buttons are projected, so each caller keeps its own wiring; only the
 * shell and the colour identity live here.
 */
@Component({
  selector: 'app-action-button-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './action-button-panel.component.html',
  styleUrl: './action-button-panel.component.css'
})
export class ActionButtonPanelComponent {
  readonly accent = input<ActionPanelAccent>('scan');
  readonly pending = input(false);
}
