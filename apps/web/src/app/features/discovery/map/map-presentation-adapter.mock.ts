import {
  Bounds,
  MapPresentationAdapter,
} from './map-presentation-adapter';
import { StationSummary } from '../../../api/adapters/discovery.types';

/**
 * Test double of the map presentation port (ARC-023: the feature depends
 * only on this interface). Records calls so specs can assert list/map
 * equivalence and degrade behaviour without a real maplibre instance.
 */
export class MapPresentationAdapterMock implements MapPresentationAdapter {
  initializeCalls = 0;
  /** Result-array references passed to setResults, in order. */
  setResultRefs: StationSummary[][] = [];
  fitBoundsCalls: Bounds[] = [];
  selectCalls: (string | null)[] = [];
  destroyed = false;

  private boundsHandler: ((bounds: Bounds) => void) | null = null;
  private stationClickHandler: ((ref: string) => void) | null = null;
  private initializeOutcome: Promise<void> = Promise.resolve();
  private rejectNextInitialize = false;

  /** Queue the outcome of the next initialize() call. */
  queueInitialize(outcome: Promise<void>): void {
    this.initializeOutcome = outcome;
    // Classify the outcome asynchronously (microtask) so initialize()
    // re-creates the rejection per call while a resolved outcome stays
    // resolved. The classification completes before the component's
    // ngAfterViewInit runs (microtasks drain before the next CD pass).
    this.rejectNextInitialize = false;
    outcome.then(
      () => undefined,
      () => {
        this.rejectNextInitialize = true;
      },
    );
  }

  onBoundsChange(handler: (bounds: Bounds) => void): void {
    this.boundsHandler = handler;
  }

  onStationClick(handler: (ref: string) => void): void {
    this.stationClickHandler = handler;
  }

  /** Test seam: simulate a marker activation from the (fake) map. */
  emitStationClick(ref: string): void {
    this.stationClickHandler?.(ref);
  }

  initialize(): Promise<void> {
    this.initializeCalls++;
    // Re-create the rejection each call so every consumer gets its own
    // rejected promise (a pre-rejected promise is only handled once).
    if (this.rejectNextInitialize) {
      return Promise.reject(new Error('map unavailable (queued)'));
    }
    return this.initializeOutcome;
  }

  setResults(stations: StationSummary[]): void {
    this.setResultRefs.push(stations);
  }

  selectStation(ref: string | null): void {
    this.selectCalls.push(ref);
  }

  fitBounds(bounds: Bounds): void {
    this.fitBoundsCalls.push(bounds);
  }

  setLocale(): void {
    /* not asserted */
  }

  destroy(): void {
    this.destroyed = true;
  }
}
