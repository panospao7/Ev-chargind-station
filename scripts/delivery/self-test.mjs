import { execSync } from 'child_process';

const VALIDATOR = 'node scripts/delivery/validate.mjs';

const tests = [
  {
    name: 'Duplicate YAML keys',
    fixture: 'scripts/delivery/tests/duplicate-key.yaml',
    expectExit: 1,
    expectMessage: 'duplicated mapping key',
  },
  {
    name: 'Invalid SHA (short)',
    fixture: 'scripts/delivery/tests/invalid-sha.yaml',
    expectExit: 1,
    expectMessage: 'SHA length violation',
  },
  {
    name: 'Unknown task state',
    fixture: 'scripts/delivery/tests/unknown-state.yaml',
    expectExit: 1,
    expectMessage: 'Unknown task state',
  },
  {
    name: 'Valid real status.yaml',
    fixture: 'delivery/status.yaml',
    expectExit: 0,
    expectMessage: 'ALL CHECKS PASSED',
  },
];

let passed = 0;
let failed = 0;

console.log('=== Delivery Validation Self-Test ===\n');

for (const test of tests) {
  const cmd = `${VALIDATOR} "${test.fixture}"`;
  let output = '';
  let exitCode = -1;

  try {
    output = execSync(cmd, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] });
    exitCode = 0;
  } catch (e) {
    output = e.stdout || '';
    exitCode = e.status;
  }

  const exitOk = exitCode === test.expectExit;
  const msgOk = output.includes(test.expectMessage);

  if (exitOk && msgOk) {
    console.log(`  PASS: ${test.name}`);
    console.log(`        Exit code ${exitCode}, found "${test.expectMessage}"`);
    passed++;
  } else {
    console.log(`  FAIL: ${test.name}`);
    if (!exitOk) console.log(`        Expected exit ${test.expectExit}, got ${exitCode}`);
    if (!msgOk) console.log(`        Expected message "${test.expectMessage}" not found in output`);
    console.log('        Full output:');
    for (const line of output.trim().split('\n')) console.log(`          ${line}`);
    failed++;
  }
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed > 0 ? 1 : 0);
