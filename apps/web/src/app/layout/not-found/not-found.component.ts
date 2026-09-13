import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LocaleService } from '../../core/localization/locale.service';
import { TranslatePipe } from '../../core/localization/translate.pipe';

/**
 * Localized wildcard screen for unknown routes (ARC-023 §9.5).
 */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, TranslatePipe],
  template: `
    <section class="not-found">
      <h1>{{ 'not_found_title' | translate }}</h1>
      <p>{{ 'not_found_hint' | translate }}</p>
      <a [routerLink]="['/', localeService.current(), 'stations']" queryParamsHandling="preserve">{{
        'back_to_search' | translate
      }}</a>
    </section>
  `,
  styles: `
    .not-found {
      padding: 3rem 1rem;
      text-align: center;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {
  protected readonly localeService = inject(LocaleService);
}
