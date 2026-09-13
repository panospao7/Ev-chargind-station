/**
 * Supported UI locales (ARC-023 §2, ARC-FE-15): Greek is the default
 * locale; English is the second supported locale. Locale-prefixed routes
 * /el and /en are matched by `locale.matcher.ts`.
 */
export type Locale = 'el' | 'en';

export const SUPPORTED_LOCALES: readonly Locale[] = ['el', 'en'] as const;

export const DEFAULT_LOCALE: Locale = 'el';

export function isLocale(value: unknown): value is Locale {
  return value === 'el' || value === 'en';
}
