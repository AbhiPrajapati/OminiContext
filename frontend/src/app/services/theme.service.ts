import { Injectable, signal, computed, effect } from '@angular/core';

export type ThemeMode = 'dark' | 'light';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly STORAGE_KEY = 'omnicontext_theme';

  readonly currentTheme = signal<ThemeMode>(this.getInitialTheme());
  readonly isDark = computed(() => this.currentTheme() === 'dark');

  constructor() {
    effect(() => {
      const theme = this.currentTheme();
      if (typeof document !== 'undefined') {
        document.documentElement.setAttribute('data-theme', theme);
      }
      try {
        localStorage.setItem(this.STORAGE_KEY, theme);
      } catch (e) {
        // Ignore localStorage quota/private mode restrictions
      }
    });
  }

  toggleTheme(): void {
    this.currentTheme.update(current => current === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: ThemeMode): void {
    this.currentTheme.set(theme);
  }

  private getInitialTheme(): ThemeMode {
    if (typeof window === 'undefined') return 'dark';
    try {
      const saved = localStorage.getItem(this.STORAGE_KEY) as ThemeMode | null;
      if (saved === 'dark' || saved === 'light') {
        return saved;
      }
      if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
        return 'light';
      }
    } catch (e) {
      // Fallback to default
    }
    return 'dark';
  }
}
