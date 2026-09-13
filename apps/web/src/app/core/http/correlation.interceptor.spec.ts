import { provideZonelessChangeDetection } from '@angular/core';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { STATIONS_URL } from '../../api/adapters/discovery-api.adapter';
import { correlationInterceptor } from './correlation.interceptor';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * The interceptor is registered globally via src/testing/test-providers.ts
 * (the unit-test builder's providersFile), mirroring app.config.ts.
 */
describe('correlation interceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('adds an X-Correlation-Id header in UUID v4 format to every request', () => {
    http
      .get(STATIONS_URL)
      .subscribe({ next: () => undefined, error: () => undefined });

    const req = httpMock.expectOne(STATIONS_URL);
    const header = req.request.headers.get('X-Correlation-Id');
    expect(typeof header).toBe('string');
    expect(header as string).toMatch(UUID_V4);
    req.flush([]);
    httpMock.verify();
  });

  it('generates a distinct id per request', () => {
    http.get('/a').subscribe({ next: () => undefined, error: () => undefined });
    http.get('/b').subscribe({ next: () => undefined, error: () => undefined });

    const first = httpMock.expectOne('/a');
    const second = httpMock.expectOne('/b');
    const firstId = first.request.headers.get('X-Correlation-Id') as string;
    const secondId = second.request.headers.get('X-Correlation-Id') as string;
    expect(firstId).toMatch(UUID_V4);
    expect(secondId).toMatch(UUID_V4);
    expect(firstId).not.toBe(secondId);
    first.flush([]);
    second.flush([]);
    httpMock.verify();
  });
});
