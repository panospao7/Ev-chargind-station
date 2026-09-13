import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LocaleService } from './locale.service';

describe('LocaleService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter([])],
    });
  });

  it('defaults to el and exposes the supported locales', () => {
    const service = TestBed.inject(LocaleService);
    expect(service.current()).toBe('el');
    expect(service.locales()).toEqual(['el', 'en']);
  });

  it('setLocale switches the signal and only accepts supported locales', () => {
    const service = TestBed.inject(LocaleService);
    service.setLocale('en');
    expect(service.current()).toBe('en');
    service.setLocale('fr' as never);
    expect(service.current()).toBe('en');
  });

  it('text() localizes keys and falls back to the raw key (fail-visible)', () => {
    const service = TestBed.inject(LocaleService);
    expect(service.text('stations_title')).toBe('Σταθμοί φόρτισης');
    service.setLocale('en');
    expect(service.text('stations_title')).toBe('Charging stations');
    expect(service.text('definitely_missing_key')).toBe(
      'definitely_missing_key',
    );
  });
});
