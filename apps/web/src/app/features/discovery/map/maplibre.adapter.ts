// MapLibre GL CSS (verified: node_modules/maplibre-gl/dist/maplibre-gl.css).
// Lives in this lazily-reachable adapter module so the CSS ships with the
// map feature chunk — never in the initial bundle (ARC-023 lazy map).
import 'maplibre-gl/dist/maplibre-gl.css';

import { StationSummary } from '../../../api/adapters/discovery.types';
import { Locale } from '../../../core/localization/locale';
import {
  Bounds,
  MapPresentationAdapter,
} from './map-presentation-adapter';

/**
 * Minimal structural typing of the maplibre-gl surface we use. The real
 * library is loaded via dynamic import inside `initialize` so maplibre-gl
 * never enters the initial bundle (ARC-023 lazily-loaded map feature).
 */
interface MapLike {
  on(event: string, handler: () => void): void;
  remove(): void;
  fitBounds(bounds: number[][], options?: object): void;
  jumpTo(options: object): void;
  getBounds(): { getWest(): number; getSouth(): number; getEast(): number; getNorth(): number };
  isStyleLoaded(): boolean;
  once(event: string, handler: () => void): void;
}

interface MarkerLike {
  addTo(map: MapLike): MarkerLike;
  remove(): void;
  setLngLat(lngLat: [number, number]): MarkerLike;
  getElement(): HTMLElement | null;
}

interface MapLibreModule {
  Map: new (options: Record<string, unknown>) => MapLike;
  Marker: new (options: Record<string, unknown>) => MarkerLike;
}

const DEFAULT_CENTER: [number, number] = [23.72754, 37.98381]; // Athens

/**
 * MapLibre GL JS implementation of the map presentation port. All DOM and
 * library interaction stays here; the Angular component stays thin.
 */
export class MaplibreAdapter implements MapPresentationAdapter {
  private map: MapLike | null = null;
  private markers: MarkerLike[] = [];
  private module: MapLibreModule | null = null;
  private readonly style: object;
  private readonly attribution: string;
  private boundsHandler: ((bounds: Bounds) => void) | null = null;
  private stationClickHandler: ((ref: string) => void) | null = null;

  constructor(options?: { style?: object; attribution?: string }) {
    this.style = options?.style ?? {};
    this.attribution = options?.attribution ?? '';
  }

  onBoundsChange(handler: (bounds: Bounds) => void): void {
    this.boundsHandler = handler;
  }

  onStationClick(handler: (ref: string) => void): void {
    this.stationClickHandler = handler;
  }

  async initialize(container: HTMLElement, bounds?: Bounds): Promise<void> {
    // Dynamic import keeps maplibre-gl out of the initial bundle.
    const maplibre = (await import('maplibre-gl')) as unknown as MapLibreModule;
    this.module = maplibre;

    this.map = new maplibre.Map({
      container,
      style: this.style,
      attributionControl: { compact: false },
    });

    this.map.on('error', () => {
      // Surface runtime tile/style failures to the host component via a
      // rejected promise is impossible after init; degrade by destroying.
      this.destroy();
    });

    if (bounds) {
      this.fitBounds(bounds);
    } else {
      this.map.jumpTo({ center: DEFAULT_CENTER, zoom: 10 });
    }

    this.map.on('moveend', () => {
      if (!this.map) {
        return;
      }
      const b = this.map.getBounds();
      this.boundsHandler?.({
        west: round5(b.getWest()),
        south: round5(b.getSouth()),
        east: round5(b.getEast()),
        north: round5(b.getNorth()),
      });
    });
  }

  setResults(stations: StationSummary[]): void {
    if (!this.module || !this.map) {
      return;
    }
    for (const marker of this.markers) {
      marker.remove();
    }
    this.markers = [];
    for (const station of stations) {
      const marker = new this.module.Marker({})
        .setLngLat([station.longitude, station.latitude])
        .addTo(this.map);
      const element = marker.getElement();
      if (element) {
        element.setAttribute('data-station-ref', station.ref);
        element.addEventListener('click', () => this.stationClickHandler?.(station.ref));
      }
      this.markers.push(marker);
    }
  }

  selectStation(ref: string | null): void {
    for (const marker of this.markers) {
      const element = marker.getElement();
      if (!element) {
        continue;
      }
      const isTarget = ref !== null && element.getAttribute('data-station-ref') === ref;
      element.classList.toggle('map-marker--selected', isTarget);
    }
  }

  fitBounds(bounds: Bounds): void {
    this.map?.fitBounds(
      [
        [bounds.west, bounds.south],
        [bounds.east, bounds.north],
      ],
      { padding: 24, duration: 0 },
    );
  }

  setLocale(_locale: Locale): void {
    // Raster tiles are locale-independent; reserved for future label layers.
  }

  destroy(): void {
    for (const marker of this.markers) {
      marker.remove();
    }
    this.markers = [];
    this.map?.remove();
    this.map = null;
  }
}

function round5(value: number): number {
  return Number(value.toFixed(5));
}
