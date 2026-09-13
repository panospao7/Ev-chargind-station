import { provideZonelessChangeDetection } from '@angular/core';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Pub03StationDetailsComponent } from './pub-03-station-details.component';
import { STATIONS_URL } from '../../api/adapters/discovery-api.adapter';
import { DETAILS_FIXTURE } from '../../../testing/fixtures';
import { LocaleService } from '../../core/localization/locale.service';

/**
 * PUB-03 station details (ARC-023 §10): loaded state renders name, EVSEs
 * with connector power, tariff via Intl and the freshness timestamp;
 * 404 renders the localized not-found screen; 500 renders the error panel
 * and retry re-issues the request. Route shape mirrors app.routes.ts
 * (locale prefix is a consumed :locale parameter).
 */
describe('PUB-03 station details', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Pub03StationDetailsComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(
          [
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

  async function navigate(url: string) {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(
      url,
      Pub03StationDetailsComponent,
    );
    await harness.detectChanges();
    return harness;
  }

  it('renders name, totalEvses=2, connector power and localized tariff + freshness (el)', async () => {
    const harness = await navigate('/el/stations/ST-001');
    httpMock.expectOne(`${STATIONS_URL}/ST-001`).flush(DETAILS_FIXTURE);
    await harness.fixture.whenStable();
    const el = document.body;
    expect(el.textContent).toContain('Σταθμός Αθήνας');
    expect(el.textContent).toContain('Σύνολο παροχών');
    expect(el.textContent).toContain('150 kW');
    expect(el.textContent).toContain('22 kW');
    // Verified Intl ground truth: el-GR currency 480 minor units →
    // "4,80 €" with a U+00A0 no-break space before the € symbol (ICU
    // el-GR pattern); assert against Intl itself to stay encoding-safe.
    expect(el.textContent).toContain(
      new Intl.NumberFormat('el-GR', {
        style: 'currency',
        currency: 'EUR',
      }).format(4.8),
    );
    // Verified freshness ground truth: el-GR medium/short Europe/Athens.
    expect(el.textContent).toContain('12 Σεπ 2026, 1:00 μ.μ.');
    // Tariff label is localized.
    expect(el.textContent).toContain('Ενέργεια ανά kWh');
    httpMock.verify();
  });

  it('renders the en-GB Intl ground truth for the /en deep link', async () => {
    const harness = await navigate('/en/stations/ST-001');
    httpMock.expectOne(`${STATIONS_URL}/ST-001`).flush(DETAILS_FIXTURE);
    await harness.fixture.whenStable();

    // The URL-derived locale must be en (hard reload /en deep link).
    expect(TestBed.inject(LocaleService).current()).toBe('en');
    const el = document.body;
    expect(el.textContent).toContain('€4.80');
    expect(el.textContent).toContain('12 Sept 2026, 13:00');
    httpMock.verify();
  });

  it('renders the localized not-found screen on 404', async () => {
    const harness = await navigate('/el/stations/ST-404');
    httpMock.expectOne(`${STATIONS_URL}/ST-404`).flush(
      { title: 'Not Found', status: 404 },
      { status: 404, statusText: 'Not Found' },
    );
    await harness.fixture.whenStable();

    const el = document.body;
    expect(el.querySelector('.not-found')).toBeTruthy();
    expect(el.textContent).toContain('Ο σταθμός δεν βρέθηκε');
    httpMock.verify();
  });

  it('renders the error panel on 500 and retry re-issues the request', async () => {
    const harness = await navigate('/el/stations/ST-001');

    const first = httpMock.expectOne(`${STATIONS_URL}/ST-001`);
    first.flush(
      { title: 'Internal Server Error', status: 500 },
      { status: 500, statusText: 'Server Error' },
    );
    await harness.fixture.whenStable();

    let el = document.body;
    expect(el.querySelector('app-error-panel')).toBeTruthy();

    el.querySelector<HTMLButtonElement>('.error-panel button')!.click();
    await harness.fixture.whenStable();

    const second = httpMock.expectOne(`${STATIONS_URL}/ST-001`);
    second.flush(DETAILS_FIXTURE);
    await harness.fixture.whenStable();

    el = document.body;
    expect(el.querySelector('app-error-panel')).toBeNull();
    expect(el.textContent).toContain('Σταθμός Αθήνας');
    httpMock.verify();
  });
});
