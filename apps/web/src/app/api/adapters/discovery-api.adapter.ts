import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';

import { ApiError } from '../../core/http/api-error';
import { mapError } from '../../core/http/problem-details.mapper';
import {
  StationDetails,
  StationSearchQuery,
  StationSummary,
} from './discovery.types';

/**
 * Discovery API adapter (ARC-008 §6.8: components never construct REST
 * URLs — all paths live here, relative to the same origin so the BFF
 * proxy serves them).
 *
 * Geo model reconciliation (planner decision, disclosed): the URL keeps
 * the ARC-023 §9.2 bounds contract (west/south/east/north); this adapter
 * maps bounds to the API's center+radius model via `boundsToGeoQuery`
 * (the circle covers the rectangle; slight over-fetch disclosed).
 */
/** Same-origin base path (ARC-008 §6.8: only adapters construct URLs). */
export const STATIONS_URL = '/api/v1/stations';

@Injectable({ providedIn: 'root' })
export class DiscoveryApiAdapter {
  private readonly http = inject(HttpClient);

  /** GET /api/v1/stations — listStations (public-discovery-api-v1.yaml). */
  listStations(query: StationSearchQuery): Observable<StationSummary[]> {
    const params: Record<string, string> = {};
    if (query.bounds) {
      const geo = boundsToGeoQuery(
        query.bounds.west,
        query.bounds.south,
        query.bounds.east,
        query.bounds.north,
      );
      params['latitude'] = geo.latitude;
      params['longitude'] = geo.longitude;
      params['radius'] = geo.radius;
    }
    if (query.connector) {
      params['connectorType'] = query.connector;
    }
    if (query.minimumPowerKw !== undefined) {
      params['minPowerW'] = String(query.minimumPowerKw * 1000);
    }
    // `view` is presentation state only — deliberately excluded.
    return this.http
      .get<StationSummary[]>(STATIONS_URL, { params })
      .pipe(catchError((error: unknown) => throwError(() => toApiError(error))));
  }

  /** GET /api/v1/stations/{stationRef} — getStationDetails. */
  getStationDetails(ref: string): Observable<StationDetails> {
    return this.http
      .get<StationDetails>(`${STATIONS_URL}/${encodeURIComponent(ref)}`)
      .pipe(catchError((error: unknown) => throwError(() => toApiError(error))));
  }
}

/** Maps an HttpErrorResponse (or unknown failure) into the ApiError taxonomy. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    return mapError(error.status, error.error);
  }
  return mapError(0, undefined);
}

export interface GeoQuery {
  latitude: string;
  longitude: string;
  radius: string;
}

const EARTH_RADIUS_KM = 6371.0088;

/**
 * Pure mapping from URL bounds to the API's center+radius geo model.
 *
 * - center = midpoint of the bounds rectangle;
 * - radius = ceil(haversine(center, farthest corner)) in whole kilometres;
 * - coordinates are serialized with 5-decimal bounded precision
 *   (ARC-023 §9.2 "coordinates use bounded precision").
 */
export function boundsToGeoQuery(
  west: number,
  south: number,
  east: number,
  north: number,
): GeoQuery {
  const centerLat = (south + north) / 2;
  const centerLng = (west + east) / 2;

  // The farthest corner from the center is the one with the largest
  // absolute latitude delta; longitude delta is symmetric for both
  // corners at that latitude, so pick one (west/south vs east/south).
  const cornerLat = Math.abs(north - centerLat) >= Math.abs(south - centerLat) ? north : south;
  const cornerLng = Math.abs(east - centerLng) >= Math.abs(west - centerLng) ? east : west;

  const radiusKm = haversineKm(centerLat, centerLng, cornerLat, cornerLng);
  const radius = Math.max(1, Math.ceil(radiusKm));

  return {
    latitude: centerLat.toFixed(5),
    longitude: centerLng.toFixed(5),
    radius: String(radius),
  };
}

/** Great-circle distance in kilometres (haversine, mean Earth radius). */
export function haversineKm(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
