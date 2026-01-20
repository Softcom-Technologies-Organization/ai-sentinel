import { Component, inject } from '@angular/core';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { SelectModule } from 'primeng/select';
import { FormsModule } from '@angular/forms';

/**
 * Language selector component for switching between available languages.
 * Business purpose: allows users to choose their preferred language (FR/EN).
 */
@Component({
  selector: 'app-language-selector',
  standalone: true,
  imports: [TranslocoModule, SelectModule, FormsModule],
  template: `
    <p-select
      [options]="availableLanguages"
      [(ngModel)]="activeLanguage"
      (ngModelChange)="changeLanguage($event)"
      optionLabel="label"
      optionValue="value"
      [style]="{ minWidth: '150px' }"
      aria-label="{{ 'language.select' | transloco }}"
    />
  `,
  styles: [`
    :host {
      display: block;
    }
  `]
})
export class LanguageSelectorComponent {
  private readonly translocoService = inject(TranslocoService);

  activeLanguage = this.translocoService.getActiveLang();

  availableLanguages = [
    { label: 'Français', value: 'fr' },
    { label: 'English', value: 'en' }
  ];

  changeLanguage(lang: string): void {
    this.translocoService.setActiveLang(lang);
  }
}
