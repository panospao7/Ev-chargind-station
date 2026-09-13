import { provideZonelessChangeDetection } from '@angular/core';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Pub01EntryComponent } from './pub-01-entry.component';
import { STATIONS_URL } from '../../api/adapters/discovery-api.adapter';
import { STATION_FIXTURE } from '../../../testing/fixtures';
import { DiscoveryStore } from './discovery.store';

describe('PUB-01 entry screen', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Pub01EntryComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    // The store is root-provided; reset the sequence so a prior test's
    // stale request counter cannot swallow this test's response.
    TestBed.inject(DiscoveryStore).reset();
  });

  function flushList(): void {
    httpMock
      .expectOne(
        (req) =>
          req.url === STATIONS_URL &&
          req.params.get('latitude') === null &&
          req.params.get('connectorType') === null,
        STATIONS_URL,
      )
      .flush(STATION_FIXTURE);
  }

  it('renders the 2-station fixture list with localized Greek labels', async () => {
    const fixture = TestBed.createComponent(Pub01EntryComponent);
    await fixture.whenStable();
    flushList();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-results-list')).toBeTruthy();
    const cards = el.querySelectorAll('.card-link');
    expect(cards.length).toBe(2);
    expect(el.textContent).toContain('Σταθμός Αθήνας');
    expect(el.textContent).toContain('Σταθμός Πειραιά');
    // Localized chrome strings come from the dictionary.
    expect(el.textContent).toContain('Βρείτε σταθμούς φόρτισης');
    expect(el.textContent).toContain('Αποτελέσματα');
    expect(el.textContent).toContain('Σύνολο παροχών');
    httpMock.verify();
  });

  it('shows the empty state for an empty result set', async () => {
    const fixture = TestBed.createComponent(Pub01EntryComponent);
    await fixture.whenStable();
    httpMock
      .expectOne((req) => req.url === STATIONS_URL, STATIONS_URL)
      .flush([]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')).toBeTruthy();
    expect(el.textContent).toContain('Δεν βρέθηκαν σταθμοί');
    httpMock.verify();
  });
});
