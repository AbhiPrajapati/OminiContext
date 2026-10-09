import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterModule } from '@angular/router';
import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'OmniContext';
  private readonly themeService = inject(ThemeService);

  readonly isDark = this.themeService.isDark;

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }
}
