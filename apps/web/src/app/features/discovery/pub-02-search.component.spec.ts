import { provideZonelessChangeDetection } from '@angular/core';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Pub02SearchComponent } from './pub-02-search.component';
import { STATIONS_URL } from '../../api/adapters/discovery-api.adapter';
import { STATION_FIXTURE } from '../../../testing/fixtures';
import { DiscoveryStore } from './discovery.store';

/**
 * PUB-02 URL-contract tests (ARC-023 §9.2, ARC-FE-08): filters live in the
 * URL, invalid values are stripped and announced, and the view toggle
 * writes `view` back to the URL. Route shape mirrors app.routes.ts: the
 * locale prefix is a consumed :locale parameter.
 */
describe('PUB-02 search screen (URL contract)', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Pub02SearchComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([
          { path: ':locale/stations', component: Pub02SearchComponent },
        ]),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    TestBed.inject(DiscoveryStore).reset();
  });

  async function navigate(url: string) {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(url, Pub02SearchComponent);
    await harness.detectChanges();
    // Zoneless: the URL-parse effect → store.search → timer(0) → HTTP
    // chain only completes after a stability cycle, so the testing
    // backend sees the request only here (never straight after
    // navigateByUrl).
    await harness.fixture.whenStable();
    return { harness, component };
  }

  /**
   * One macrotask turn. The store defers search() via timer(0), which
   * Angular's scheduler does not track as a pending task — whenStable()
   * resolves on microtasks alone and would never let the timer fire
   * once rendering has settled. Yielding one macrotask lets the timer
   * callback (and thus the HTTP request) reach the testing backend.
   */
  async function nextMacroTask(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }

  function flushStations(): void {
    httpMock
      .expectOne((req) => req.url === STATIONS_URL, STATIONS_URL)
      .flush(STATION_FIXTURE);
  }

  it('renders filters from the URL, the flushed fixture list, and leaves the URL unchanged', async () => {
    const { harness } = await navigate(
      '/el/stations?west=23.5&south=37.9&east=23.8&north=38.0&connector=CCS&minimumPowerKw=50&view=list',
    );

    await nextMacroTask();
    flushStations();
    await harness.fixture.whenStable();

    const el = document.body;
    expect(el.querySelector('app-results-list')).toBeTruthy();

    const connector = el.querySelector(
      '#connector-filter',
    ) as HTMLSelectElement;
    expect(connector.value).toBe('CCS');
    const power = el.querySelector('#power-filter') as HTMLSelectElement;
    expect(power.value).toBe('50');

    expect(el.textContent).toContain('Σταθμός Αθήνας');
    expect(el.textContent).toContain('Σταθμός Πειραιά');

    expect(router.url).toBe(
      '/el/stations?west=23.5&south=37.9&east=23.8&north=38.0&connector=CCS&minimumPowerKw=50&view=list',
    );
    httpMock.verify();
  });

  it('strips invalid query values, announces it via aria-live, and still renders results', async () => {
    const { harness } = await navigate('/el/stations?connector=BOGUS&minimumPowerKw=-5');

    // The strip navigation replaces the URL (replaceUrl).
    await nextMacroTask();
    flushStations();
    await harness.fixture.whenStable();

    expect(router.url).toBe('/el/stations');

    const el = document.body;
    const live = el.querySelector('[aria-live="polite"]');
    expect(live).toBeTruthy();
    expect(live!.textContent).toContain('Μη έγκυρες παράμετροι');

    const connector = el.querySelector(
      '#connector-filter',
    ) as HTMLSelectElement;
    expect(connector.value).toBe('');
    expect(el.querySelector('app-results-list')).toBeTruthy();
    httpMock.verify();
  });

  it('view toggle writes view=map into the URL', async () => {
    const { harness } = await navigate('/el/stations');

    await nextMacroTask();
    flushStations();
    await harness.fixture.whenStable();

    const el = document.body;
    const mapButton = (
      Array.from(el.querySelectorAll('button')) as HTMLButtonElement[]
    ).find((b) => b.textContent.trim() === 'Χάρτης');
    expect(mapButton).toBeTruthy();
    mapButton!.click();
    await harness.fixture.whenStable();

    expect(router.url).toBe('/el/stations?view=map');
    httpMock.verify();
  });
});
