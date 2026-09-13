/**
 * Application-level API error taxonomy (ARC-023 §9.5 screen states).
 *
 * Adapters map transport/HTTP failures into this type so that feature
 * components never inspect raw HttpErrorResponse objects. The mapping rules
 * live in `problem-details.mapper.ts`.
 */
import type { ProblemDetails } from './problem-details.mapper';

export type ApiErrorKind = 'not-found' | 'server' | 'network' | 'unknown';

export interface ApiError {
  kind: ApiErrorKind;
  /** Present when the downstream returned an RFC 9457 Problem Details body. */
  problem?: ProblemDetails;
}
