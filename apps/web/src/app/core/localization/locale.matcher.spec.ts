import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, UrlSegment } from '@angular/router';

import { localeMatcher } from './locale.matcher';
import { DEFAULT_LOCALE } from './locale';

describe('localeMatcher', () => {
  it('matches a supported locale prefix and consumes one segment', () => {
    const result = localeMatcher([new UrlSegment('el', {})]);
    expect(result).not.toBeNull();
    expect(result!.consumed.map((s) => s.path)).toEqual(['el']);
    const localeParam = result!.posParams?.['locale'];
    expect(localeParam?.path).toBe('el');
  });

  it('rejects a non-locale first segment', () => {
    expect(localeMatcher([new UrlSegment('fr', {})])).toBeNull();
  });

  it('returns null for zero segments so the bare-root redirect fires', () => {
    expect(localeMatcher([])).toBeNull();
  });
});

describe('bare root redirect (F-5)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([
          {
            matcher: localeMatcher,
            children: [{ path: '', component: class {}, pathMatch: 'full' }],
          },
          { path: '', pathMatch: 'full', redirectTo: DEFAULT_LOCALE },
        ]),
      ],
    }).compileComponents();
  });

  it("navigating '/' redirects to '/el'", async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/el');
  });
});
