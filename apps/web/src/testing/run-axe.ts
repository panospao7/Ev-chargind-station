import axe from 'axe-core';

/** Shape of a single axe violation (subset used by the gate). */
interface AxeViolationSummary {
  id: string;
  impact: string | null;
  nodes: { target: string[] }[];
}

/** Shape of the axe.run result (subset used by the gate). */
interface AxeRunResult {
  violations: AxeViolationSummary[];
}

/**
 * Runs axe-core against a rendered fixture element.
 *
 * jsdom disclosure (task AC-06): `color-contrast` is disabled in the run
 * options because jsdom has no layout engine and cannot compute rendered
 * contrast — this is the standard jsdom exclusion for axe. The gate itself
 * is "zero critical+serious violations"; the assertion helper filters to
 * exactly those impacts so moderate/page-level findings (e.g. `region`,
 * which is meaningless for a component rendered in isolation) are reported
 * by axe but cannot fail the gate.
 *
 * axe-core ships `export = axe` typings; under `esModuleInterop` the
 * default import binds the namespace object. The result is typed
 * structurally here so the gate does not depend on axe's full type graph.
 */
export async function runAxe(element: Element): Promise<AxeRunResult> {
  const run = axe.run as unknown as (
    context: Element,
    options: Record<string, unknown>,
  ) => Promise<AxeRunResult>;
  return run(element, {
    rules: {
      'color-contrast': { enabled: false },
    },
  });
}

/** Asserts zero critical+serious violations (AC-06 gate). */
export function expectNoCriticalOrSeriousViolations(
  results: AxeRunResult,
): void {
  const blocking = results.violations.filter(
    (v) => v.impact === 'critical' || v.impact === 'serious',
  );
  const summary = blocking.map(
    (v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`,
  );
  expect(summary).toEqual([]);
}
