import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { LocaleService } from '../../core/localization/locale.service';
import { TranslatePipe } from '../../core/localization/translate.pipe';
import type { Locale } from '../../core/localization/locale';

/**
 * Public shell (ARC-023 §4.1): localized header with the app name, an
 * EL/EN locale switch preserving route + query state (ARC-FE-15), a
 * skip-to-content link, and the router outlet. This component is the
 * application host (App) — one shell for all public screens.
 */
@Component({
  selector: 'app-public-shell',
  imports: [RouterOutlet, RouterLink, TranslatePipe],
  templateUrl: './public-shell.component.html',
  styleUrl: './public-shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicShellComponent {
  private readonly router = inject(Router);
  protected readonly localeService = inject(LocaleService);

  /**
   * Locale-switch link segments for the *current* URL with the locale
   * segment swapped (ARC-FE-15: the switch preserves the route and the
   * query). The first path segment is the locale prefix; everything
   * after it is kept verbatim.
   */
  protected localeLink(locale: Locale): string[] {
    const path = this.router.url.split(/[?#]/)[0];
    const segments = path.split('/').filter((segment) => segment.length > 0);
    if (segments.length > 0 && this.isLocaleSegment(segments[0])) {
      segments[0] = locale;
    } else {
      segments.unshift(locale);
    }
    return ['/', ...segments];
  }

  private isLocaleSegment(value: string): boolean {
    return value === 'el' || value === 'en';
  }
}
