import { DOCUMENT } from '@angular/common';
import { inject, Injectable, signal } from '@angular/core';

type Theme = 'light' | 'dark';

const STORAGE_KEY = 'sentinel-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  readonly isDark = signal(false);

  constructor() {
    const stored = localStorage.getItem(STORAGE_KEY) as Theme | null;
    const prefersDark = globalThis.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = stored ?? (prefersDark ? 'dark' : 'light');
    this.applyTheme(theme);
  }

  toggle(): void {
    this.applyTheme(this.isDark() ? 'light' : 'dark');
  }

  private applyTheme(theme: Theme): void {
    this.isDark.set(theme === 'dark');
    this.doc.documentElement.dataset.theme = theme;
    localStorage.setItem(STORAGE_KEY, theme);
  }
}
