import { UrlSegment, UrlMatchResult } from '@angular/router';
import { DEFAULT_LOCALE, isLocale } from './locale';

/**
 * Route matcher accepting only /{locale} prefixes where locale ∈ {el, en}
 * (ARC-023 §9 locale routing). Everything else falls through to the
 * wildcard not-found route.
 */
export function localeMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length === 0) {
    // Bare root redirects to the default locale.
    return {
      consumed: [],
      posParams: {},
    };
  }
  const first = segments[0].path;
  if (!isLocale(first)) {
    return null;
  }
  return {
    consumed: segments.slice(0, 1),
    posParams: { locale: segments[0] },
  };
}

export { DEFAULT_LOCALE };
