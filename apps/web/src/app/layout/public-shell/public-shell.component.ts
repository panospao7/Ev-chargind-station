import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { LocaleService } from '../../core/localization/locale.service';
import { TranslatePipe } from '../../core/localization/translate.pipe';

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
  protected readonly localeService = inject(LocaleService);
}
