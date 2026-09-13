import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MapComponent } from './map.component';
import {
  MAP_PRESENTATION_ADAPTER,
} from './map-presentation-adapter';
import { MapPresentationAdapterMock } from './map-presentation-adapter.mock';
import { STATION_FIXTURE } from '../../../../testing/fixtures';

/**
 * Map host behaviour (ARC-023 §9.5): initialization failure degrades to
 * the fallback panel; success renders the map container and pushes the
 * station collection to the adapter.
 */
describe('MapComponent (mocked presentation adapter)', () => {
  function setup(): {
    fixture: ReturnType<typeof TestBed.createComponent<MapComponent>>;
    adapter: MapPresentationAdapterMock;
  } {
    const adapter = new MapPresentationAdapterMock();
    TestBed.configureTestingModule({
      imports: [MapComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: MAP_PRESENTATION_ADAPTER, useValue: adapter },
      ],
    });
    const fixture = TestBed.createComponent(MapComponent);
    return { fixture, adapter };
  }

  it('renders the map-fallback-panel when initialize() rejects', async () => {
    const { fixture, adapter } = setup();
    adapter.queueInitialize(Promise.reject(new Error('tiles unavailable')));

    fixture.componentRef.setInput('stations', STATION_FIXTURE);
    await fixture.whenStable();
    // Zoneless CD: the catch handler's signal write needs one more cycle
    // to re-render the degraded template.
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-map-fallback-panel')).toBeTruthy();
    expect(el.querySelector('.map-container')).toBeNull();
    expect(el.textContent).toContain('Ο χάρτης δεν είναι διαθέσιμος');
  });

  it('renders no fallback when initialize() resolves', async () => {
    const { fixture, adapter } = setup();
    adapter.queueInitialize(Promise.resolve());

    fixture.componentRef.setInput('stations', STATION_FIXTURE);
    await fixture.whenStable();
    // Zoneless CD: ngAfterViewInit → initialize() → .then(setResults) is
    // an async chain that completes only on the next stability cycle.
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-map-fallback-panel')).toBeNull();
    expect(el.querySelector('.map-container')).toBeTruthy();
    expect(adapter.initializeCalls).toBe(1);
    expect(adapter.setResultRefs.length).toBe(1);
    expect(adapter.setResultRefs[0]).toEqual(STATION_FIXTURE);
  });
});
