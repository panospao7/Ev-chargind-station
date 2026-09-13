import { provideZonelessChangeDetection } from '@angular/core';
import {
  provideHttpClient,
  withFetch,
  withInterceptors,
} from '@angular/common/http';

import { correlationInterceptor } from '../app/core/http/correlation.interceptor';

/**
 * Global providers for the unit-test builder (angular.json test target
 * `providersFile`). Mirrors the production providers that are safe in
 * tests: zoneless change detection and the fetch-based HttpClient with
 * the correlation interceptor.
 *
 * Router providers are intentionally per-spec: only specs that actually
 * navigate provide the real `routes`; the rest provide `provideRouter([])`
 * so `LocaleService` (which reads Router events) has a Router to observe.
 *
 * Specs that issue HTTP requests add `provideHttpClientTesting()`, which
 * overrides the fetch backend while keeping the interceptor chain active.
 */
export default [
  provideZonelessChangeDetection(),
  provideHttpClient(withFetch(), withInterceptors([correlationInterceptor])),
];
