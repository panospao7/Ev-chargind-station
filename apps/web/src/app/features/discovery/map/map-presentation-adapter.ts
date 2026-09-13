import { StationSummary } from '../../../api/adapters/discovery.types';
import { Locale } from '../../../core/localization/locale';

/**
 * Map bounds rectangle (ARC-023 §9.2 URL contract unit).
 */
export interface Bounds {
  west: number;
  south: number;
  east: number;
  north: number;
}

/**
 * Port for map presentation (ARC-023: MapLibre behind a replaceable
 * adapter; provider selection deferred per OQ-UI-03). The feature code
 * depends only on this interface — never on maplibre-gl types.
 */
export interface MapPresentationAdapter {
  /** Initialize the map inside the given container element. */
  initialize(container: HTMLElement, bounds?: Bounds): Promise<void>;

  /** Replace the rendered markers with the given stations. */
  setResults(stations: StationSummary[]): void;

  /** Highlight (or clear) the selected station by public ref. */
  selectStation(ref: string | null): void;

  /** Fit the viewport to the given bounds. */
  fitBounds(bounds: Bounds): void;

  /** React to locale changes (labels/attribution handling). */
  setLocale(locale: Locale): void;

  /** Register the viewport-change callback (moveend → URL bounds). */
  onBoundsChange(handler: (bounds: Bounds) => void): void;

  /** Register the marker-activation callback (click → selection). */
  onStationClick(handler: (ref: string) => void): void;

  /** Release all map resources (idempotent). */
  destroy(): void;
}

export const MAP_PRESENTATION_ADAPTER = new InjectionToken<MapPresentationAdapter>(
  'MAP_PRESENTATION_ADAPTER',
);

import { InjectionToken } from '@angular/core';
