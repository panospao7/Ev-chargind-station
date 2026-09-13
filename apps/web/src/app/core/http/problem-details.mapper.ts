import type { ApiError } from './api-error';

/**
 * Minimal RFC 9457 Problem Details view used by the public discovery UI.
 *
 * Mirrors contracts/schemas/common/problem-details.json (read-only view:
 * only the fields the UI needs; unknown fields are tolerated and ignored).
 */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}

/**
 * Maps an HTTP status + parsed body into the application ApiError taxonomy.
 *
 * Rules (task packet STEP 2):
 * - 404 → 'not-found'
 * - >= 500 → 'server'
 * - 0 (network failure, offline, aborted) → 'network'
 * - everything else → 'unknown'
 *
 * When the body looks like a Problem Details object (has a `title` or
 * `status` field), it is attached so screens can render `detail`/`title`.
 */
export function mapError(status: number, body: unknown): ApiError {
  const kind: ApiError['kind'] =
    status === 404
      ? 'not-found'
      : status >= 500
        ? 'server'
        : status === 0
          ? 'network'
          : 'unknown';

  const problem = isProblemDetails(body) ? body : undefined;
  return problem ? { kind, problem } : { kind };
}

function isProblemDetails(body: unknown): body is ProblemDetails {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const candidate = body as Record<string, unknown>;
  return (
    typeof candidate['title'] === 'string' || typeof candidate['status'] === 'number'
  );
}
