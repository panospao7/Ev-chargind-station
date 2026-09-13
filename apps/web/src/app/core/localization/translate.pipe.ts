import { inject, Pipe, PipeTransform } from '@angular/core';
import { LocaleService } from './locale.service';

/**
 * Translates a dictionary key with the active locale signal.
 * Usage: {{ 'stations_title' | translate }}.
 *
 * Unknown keys render the raw key (fail-visible, never silent empty).
 *
 * `pure: false` is required: a pure pipe caches by input identity, so
 * the locale-signal read inside transform would never invalidate the
 * cache and a runtime locale switch would not re-translate. Impure
 * evaluation re-runs transform on every CD pass, picking up the signal
 * read (planner decision: signal-dictionary i18n with runtime locale
 * switch).
 */
@Pipe({ name: 'translate', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly localeService = inject(LocaleService);

  transform(key: string): string {
    return this.localeService.text(key);
  }
}
