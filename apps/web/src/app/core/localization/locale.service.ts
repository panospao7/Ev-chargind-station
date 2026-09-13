import { effect, inject, Injectable, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Router } from '@angular/router';
import {
  DEFAULT_LOCALE,
  isLocale,
  SUPPORTED_LOCALES,
  type Locale,
} from './locale';
import { EN } from './i18n/en';
import { EL } from './i18n/el';

/**
 * Signal-based runtime locale state (planner decision: signal dictionary
 * i18n with runtime locale switch preserving route + query).
 *
 * The active locale is derived from the URL's locale prefix segment
 * (/el default, /en) so a deep link or hard reload to /en/... renders in
 * English without relying on component wiring; the effect keeps
 * `<html lang>` in sync for accessibility (ARC-008 §12).
 */
@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);

  private readonly localeSignal = signal<Locale>(DEFAULT_LOCALE);

  constructor() {
    effect(() => {
      this.document.documentElement.lang = this.localeSignal();
    });

    // Derive the initial locale from the current URL prefix. A hard reload
    // of /en/... must render English (AC-04); there is no locale change
    // signal on pure navigation between sibling locale roots, so reading
    // the URL once per service construction is sufficient for the public
    // full-page-load flows this SPA serves.
    const firstSegment = this.router.url.split(/[?#]/)[0].split('/')[1];
    if (isLocale(firstSegment)) {
      this.localeSignal.set(firstSegment);
    }
  }

  /** Narrow read-only view for template binding. */
  readonly current = this.localeSignal.asReadonly();

  locales(): readonly Locale[] {
    return SUPPORTED_LOCALES;
  }

  setLocale(locale: Locale): void {
    if (isLocale(locale)) {
      this.localeSignal.set(locale);
    }
  }

  /** Translate a dictionary key with the active locale (fail-visible). */
  text(key: string): string {
    const dictionary = this.localeSignal() === 'en' ? EN : EL;
    return dictionary[key] ?? key;
  }
}
