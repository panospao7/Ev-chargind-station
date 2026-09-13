import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  provideHttpClient,
  withFetch,
  withInterceptors,
} from '@angular/common/http';
import {
  provideRouter,
  withComponentInputBinding,
  withViewTransitions,
} from '@angular/router';

import { routes } from './app.routes';
import { correlationInterceptor } from './core/http/correlation.interceptor';
import { LocaleTitleStrategy } from './core/localization/locale-title.strategy';
import { TitleStrategy } from '@angular/router';

/**
 * Public discovery SPA configuration (ARC-023): fetch-based HttpClient with
 * the correlation interceptor, locale-prefixed routing with component
 * input binding, localized title strategy, and a gentle view transition.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions()),
    { provide: TitleStrategy, useClass: LocaleTitleStrategy },
    provideHttpClient(withFetch(), withInterceptors([correlationInterceptor])),
  ],
};
