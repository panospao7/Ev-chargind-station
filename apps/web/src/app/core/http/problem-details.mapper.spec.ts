import { provideZonelessChangeDetection } from '@angular/core';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { mapError } from './problem-details.mapper';

describe('problem-details.mapper', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClientTesting(),
      ],
    });
  });

  it('maps 404 to not-found', () => {
    expect(mapError(404, undefined).kind).toBe('not-found');
  });

  it('maps >= 500 to server', () => {
    expect(mapError(500, undefined).kind).toBe('server');
    expect(mapError(503, undefined).kind).toBe('server');
  });

  it('maps 0 (network failure) to network', () => {
    expect(mapError(0, undefined).kind).toBe('network');
  });

  it('maps other statuses (400) to unknown', () => {
    expect(mapError(400, undefined).kind).toBe('unknown');
  });

  it('attaches the body when it looks like a Problem Details object', () => {
    const problem = { type: 'about:blank', title: 'Not Found', status: 404 };
    const error = mapError(404, problem);
    expect(error.problem).toEqual(problem);
  });

  it('omits problem for non-problem bodies', () => {
    expect(mapError(404, { foo: 'bar' }).problem).toBeUndefined();
    expect(mapError(404, 'oops').problem).toBeUndefined();
  });
});
