import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Adds a fresh `X-Correlation-Id` (UUID v4) to every outgoing API request
 * (ARC-008 §7 BFF integration; the BFF forwards or generates its own).
 */
export const correlationInterceptor: HttpInterceptorFn = (req, next) =>
  next(
    req.clone({
      setHeaders: { 'X-Correlation-Id': crypto.randomUUID() },
    }),
  );
