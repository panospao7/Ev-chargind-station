import { UrlSegment, UrlMatchResult } from '@angular/router';
import { DEFAULT_LOCALE, isLocale } from './locale';

/**
 * Route matcher accepting only /{locale} prefixes where locale ∈ {el, en}
 * (ARC-023 §9 locale routing). Zero segments (bare root) and non-locale
 * prefixes return null so the bare-root redirectTo route fires and
 * everything else falls through to the wildcard not-found route.
 */
export function localeMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length === 0) {
    // Bare root must fall through to the redirectTo route → /el.
    return null;
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
