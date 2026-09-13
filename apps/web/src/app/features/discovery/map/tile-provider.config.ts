import { InjectionToken } from '@angular/core';

/**
 * Tile provider configuration (OQ-UI-03: provider selection deferred —
 * local dev uses free OSM raster tiles with required attribution). The
 * style is a plain maplibre style object so no vendor SDK is hardcoded.
 */
export interface TileProviderConfig {
  /** MapLibre style object (raster source for local dev). */
  style: object;
  /** Required attribution string shown with the map. */
  attribution: string;
}

export const TILE_PROVIDER_CONFIG = new InjectionToken<TileProviderConfig>(
  'TILE_PROVIDER_CONFIG',
);

/** Local-dev OSM raster configuration (attribution is mandatory). */
export const LOCAL_DEV_OSM_TILE_CONFIG: TileProviderConfig = {
  style: {
    version: 8,
    sources: {
      osm: {
        type: 'raster',
        tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
        tileSize: 256,
        maxzoom: 19,
        attribution: '© OpenStreetMap contributors',
      },
    },
    layers: [
      {
        id: 'osm',
        type: 'raster',
        source: 'osm',
      },
    ],
  },
  attribution: '© OpenStreetMap contributors',
};
