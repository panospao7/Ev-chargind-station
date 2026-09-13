import { provideZonelessChangeDetection } from '@angular/core';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Pub01EntryComponent } from '../app/features/discovery/pub-01-entry.component';
import { Pub02SearchComponent } from '../app/features/discovery/pub-02-search.component';
import { Pub03StationDetailsComponent } from '../app/features/station-details/pub-03-station-details.component';
import { STATIONS_URL } from '../app/api/adapters/discovery-api.adapter';
import { DETAILS_FIXTURE, STATION_FIXTURE } from './fixtures';
import { expectNoCriticalOrSeriousViolations, runAxe } from './run-axe';

/**
 * AC-06 automated accessibility baseline: zero critical+serious axe
 * violations on the three public screens rendered with their fixtures.
 * (color-contrast is disabled — jsdom has no layout engine; disclosed in
 * run-axe.ts.)
 */
describe('axe accessibility baseline (AC-06)', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(
          [
            { path: '', component: Pub01EntryComponent },
            { path: ':locale/stations', component: Pub02SearchComponent },
            {
              path: ':locale/stations/:stationRef',
              component: Pub03StationDetailsComponent,
            },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('PUB-01 entry has zero critical+serious violations', async () => {
    const fixture = TestBed.createComponent(Pub01EntryComponent);
    await fixture.whenStable();
    httpMock
      .expectOne((req) => req.url === STATIONS_URL, STATIONS_URL)
      .flush(STATION_FIXTURE);
    await fixture.whenStable();

    const results = await runAxe(fixture.nativeElement);
    expectNoCriticalOrSeriousViolations(results);
    httpMock.verify();
  });

  it('PUB-02 search has zero critical+serious violations', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(
      '/el/stations?west=23.5&south=37.9&east=23.8&north=38.0&connector=CCS&minimumPowerKw=50&view=list',
      Pub02SearchComponent,
    );
    await harness.detectChanges();
    // Zoneless: the URL-parse effect → store.search → timer(0) → HTTP
    // chain needs one macrotask turn (the timer is not a tracked pending
    // task) plus a stability cycle before the request is observable.
    await new Promise((resolve) => setTimeout(resolve, 0));
    await harness.fixture.whenStable();
    httpMock
      .expectOne((req) => req.url === STATIONS_URL, STATIONS_URL)
      .flush(STATION_FIXTURE);
    await harness.fixture.whenStable();

    const results = await runAxe(harness.fixture.nativeElement);
    expectNoCriticalOrSeriousViolations(results);
    httpMock.verify();
  });

  it('PUB-03 details has zero critical+serious violations', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(
      '/el/stations/ST-001',
      Pub03StationDetailsComponent,
    );
    await harness.detectChanges();
    httpMock.expectOne(`${STATIONS_URL}/ST-001`).flush(DETAILS_FIXTURE);
    await harness.fixture.whenStable();

    const results = await runAxe(harness.fixture.nativeElement);
    expectNoCriticalOrSeriousViolations(results);
    httpMock.verify();
  });
});
