import {
  DestroyRef,
  effect,
  inject,
  Injectable,
  signal,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
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
 * (/el default, /en): once on construction (deep link / hard reload,
 * AC-04) and again on every NavigationEnd, so in-app navigation between
 * locale roots keeps the signal, `<html lang>` and Intl formatting in
 * sync (ARC-008 §12). The NavigationEnd subscription is the single
 * navigation-time writer — `setLocale` remains for tests and programmatic
 * use and is never wired to UI controls.
 */
@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly localeSignal = signal<Locale>(DEFAULT_LOCALE);

  constructor() {
    effect(() => {
      this.document.documentElement.lang = this.localeSignal();
    });

    // Initial load: derive the locale from the current URL prefix. A hard
    // reload of /en/... must render English (AC-04).
    this.localeSignal.set(this.localeFromUrl());

    // In-app navigation: re-derive on every NavigationEnd so the signal
    // always mirrors the URL (fixes stale-locale desync after locale
    // switches or cross-locale navigation).
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.localeSignal.set(this.localeFromUrl());
      });
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

  /** Locale from the URL's first path segment (fallback: default). */
  private localeFromUrl(): Locale {
    const firstSegment = this.router.url.split(/[?#]/)[0].split('/')[1];
    return isLocale(firstSegment) ? firstSegment : DEFAULT_LOCALE;
  }
}
