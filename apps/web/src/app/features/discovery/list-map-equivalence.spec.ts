import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MapComponent } from './map/map.component';
import {
  MAP_PRESENTATION_ADAPTER,
} from './map/map-presentation-adapter';
import { MapPresentationAdapterMock } from './map/map-presentation-adapter.mock';
import { ResultsListComponent } from './results-list.component';
import { STATION_FIXTURE } from '../../../testing/fixtures';

/**
 * AC-02 list/map equivalence: the map and the list derive from the same
 * result collection. With a real store both receive the same array
 * reference; the structural assertion holds for any source that renders
 * identical station collections.
 */
describe('list/map equivalence (AC-02)', () => {
  function setup(): {
    mapFixture: ReturnType<typeof TestBed.createComponent<MapComponent>>;
    listFixture: ReturnType<typeof TestBed.createComponent<ResultsListComponent>>;
    adapter: MapPresentationAdapterMock;
  } {
    const adapter = new MapPresentationAdapterMock();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: MAP_PRESENTATION_ADAPTER, useValue: adapter },
      ],
    });
    const mapFixture = TestBed.createComponent(MapComponent);
    const listFixture = TestBed.createComponent(ResultsListComponent);
    return { mapFixture, listFixture, adapter };
  }

  it('the map adapter receives the same result collection the list renders', async () => {
    const { mapFixture, listFixture, adapter } = setup();
    adapter.queueInitialize(Promise.resolve());

    // The shared store hands both consumers the same reference.
    const shared = STATION_FIXTURE;
    mapFixture.componentRef.setInput('stations', shared);
    listFixture.componentRef.setInput('stations', shared);
    await mapFixture.whenStable();
    await listFixture.whenStable();
    // Zoneless CD: the map's ngAfterViewInit → initialize() →
    // .then(setResults) chain completes only on a further cycle.
    await mapFixture.whenStable();
    await listFixture.whenStable();

    // Every ref the adapter captured must be one the list also rendered.
    const listRefs = listFixture.componentInstance.stations().map((s) => s.ref);
    const mapRefs = adapter.setResultRefs.at(-1)!.map((s) => s.ref);
    expect(mapRefs.length).toBeGreaterThan(0);
    for (const ref of mapRefs) {
      expect(listRefs).toContain(ref);
    }
    expect(mapRefs).toEqual(listRefs);
  });
});
