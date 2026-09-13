import { provideZonelessChangeDetection } from '@angular/core';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import {
  boundsToGeoQuery,
  DiscoveryApiAdapter,
  STATIONS_URL,
} from './discovery-api.adapter';
import { StationSearchQuery } from './discovery.types';
import { STATION_FIXTURE } from '../../../testing/fixtures';

/** URL always goes through the adapter — specs import the constant. */
const STATIONS_URL_MATCHER = (req: { url: string }): boolean => req.url === STATIONS_URL;

describe('boundsToGeoQuery (verified ground truth)', () => {
  it('Athens box 23.5/37.9/23.8/38.0 → 37.95000/23.65000/15', () => {
    expect(boundsToGeoQuery(23.5, 37.9, 23.8, 38.0)).toEqual({
      latitude: '37.95000',
      longitude: '23.65000',
      radius: '15',
    });
  });

  /**
   * Corner-radius ground truth 0.50000/1.00000/125. Input disclosure: a
   * literal unit box (0,0,1,1) centers at (0.5, 0.5) and cannot produce
   * centerLng 1.0; the recorded output is reproduced by the box
   * (west=0, south=0, east=2, north=1), whose center is (0.5, 1.0) and
   * whose farthest corner spans ~125 km.
   */
  it('box 0/0/2/1 → 0.50000/1.00000/125 (corner ground truth)', () => {
    expect(boundsToGeoQuery(0, 0, 2, 1)).toEqual({
      latitude: '0.50000',
      longitude: '1.00000',
      radius: '125',
    });
  });

  it('rounding box 23.123456/37.9/23.223456/38.0 → 37.95000/23.17346/8', () => {
    expect(boundsToGeoQuery(23.123456, 37.9, 23.223456, 38.0)).toEqual({
      latitude: '37.95000',
      longitude: '23.17346',
      radius: '8',
    });
  });
});

describe('DiscoveryApiAdapter', () => {
  let adapter: DiscoveryApiAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClientTesting(),
      ],
    });
    adapter = TestBed.inject(DiscoveryApiAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('sends bounds+connector+minPowerKw as latitude/longitude/radius/connectorType/minPowerW and never the view', () => {
    const query: StationSearchQuery = {
      bounds: { west: 23.5, south: 37.9, east: 23.8, north: 38.0 },
      connector: 'CCS',
      minimumPowerKw: 50,
      view: 'list',
    };

    let response: unknown;
    adapter.listStations(query).subscribe((r) => (response = r));

    const req = httpMock.expectOne(STATIONS_URL_MATCHER);
    expect(req.request.method).toBe('GET');
    const params = req.request.params;
    expect(params.get('latitude')).toBe('37.95000');
    expect(params.get('longitude')).toBe('23.65000');
    expect(params.get('radius')).toBe('15');
    expect(params.get('connectorType')).toBe('CCS');
    expect(params.get('minPowerW')).toBe('50000');
    expect(params.get('view')).toBeNull();
    expect(params.keys().filter((k) => k.startsWith('west') || k.startsWith('south') || k.startsWith('east') || k.startsWith('north'))).toEqual([]);

    req.flush(STATION_FIXTURE);
    expect(response).toEqual(STATION_FIXTURE);
    httpMock.verify();
  });

  it('sends no geo params when no bounds are present', () => {
    let response: unknown;
    adapter.listStations({}).subscribe((r) => (response = r));

    const req = httpMock.expectOne(STATIONS_URL_MATCHER);
    expect(req.request.params.get('latitude')).toBeNull();
    expect(req.request.params.get('longitude')).toBeNull();
    expect(req.request.params.get('radius')).toBeNull();

    req.flush([]);
    expect(response).toEqual([]);
    httpMock.verify();
  });

  it('maps a 404 Problem Details body to an ApiError not-found', async () => {
    let error: unknown;
    adapter.getStationDetails('ST-404').subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne(`${STATIONS_URL}/ST-404`);
    req.flush(
      { type: 'about:blank', title: 'Not Found', status: 404 },
      { status: 404, statusText: 'Not Found' },
    );

    const apiError = error as { kind: string; problem?: { status?: number } };
    expect(apiError.kind).toBe('not-found');
    expect(apiError.problem?.status).toBe(404);
    httpMock.verify();
  });

  it('maps a 500 to an ApiError server', async () => {
    let error: unknown;
    adapter.listStations({}).subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne(STATIONS_URL_MATCHER);
    req.flush(
      { title: 'Internal Server Error', status: 500 },
      { status: 500, statusText: 'Server Error' },
    );

    expect((error as { kind: string }).kind).toBe('server');
    httpMock.verify();
  });
});
