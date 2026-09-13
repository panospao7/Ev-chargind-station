import { Component, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LocaleService } from './locale.service';
import { TranslatePipe } from './translate.pipe';

/** Minimal host rendering the pipe with a known and an unknown key. */
@Component({
  imports: [TranslatePipe],
  template: `<span id="known">{{ 'stations_title' | translate }}</span><span id="unknown">{{ 'definitely_missing_key' | translate }}</span>`,
})
class TranslateHost {}

/**
 * Translate pipe behaviour for both locales. The pipe reads the
 * LocaleService signal inside transform, so a locale switch must
 * re-translate without any explicit change-detection nudge.
 */
describe('TranslatePipe', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [TranslateHost],
      providers: [provideZonelessChangeDetection(), provideRouter([])],
    });
    await TestBed.compileComponents();
  });

  it('translates with the default (el) locale and fail-visible for unknown keys', async () => {
    const fixture = TestBed.createComponent(TranslateHost);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('#known')!.textContent).toBe(
      'Σταθμοί φόρτισης',
    );
    expect(element.querySelector('#unknown')!.textContent).toBe(
      'definitely_missing_key',
    );
  });

  it('re-translates when the locale signal switches to en', async () => {
    const localeService = TestBed.inject(LocaleService);
    const fixture = TestBed.createComponent(TranslateHost);
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('#known')!
        .textContent,
    ).toBe('Σταθμοί φόρτισης');

    localeService.setLocale('en');
    // whenStable resolves with no pending tasks; the pure pipe re-runs
    // only on the next change-detection pass.
    fixture.detectChanges();
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('#known')!
        .textContent,
    ).toBe('Charging stations');
  });
});
