import { readFileSync, existsSync, readdirSync } from 'fs';
import { join } from 'path';
import yaml from 'js-yaml';

const filePath = process.argv[2] || 'delivery/status.yaml';
const iterDir = process.argv[3] || 'delivery/iterations';
const taskDir = process.argv[4] || 'delivery/tasks';

let exitCode = 0;
const errors = [];

console.log('=== Delivery Control Plane Validation ===\n');

// Read and parse the status file
let content;
try {
  content = readFileSync(filePath, 'utf8');
} catch (e) {
  console.error(`FATAL: Cannot read ${filePath}: ${e.message}`);
  process.exit(1);
}

// ---- 1. YAML parse with duplicate-key rejection ----
console.log('[CHECK] YAML parse validity & duplicate keys...');
let parsed;
try {
  parsed = yaml.load(content, { filename: filePath });
  console.log('  OK: No duplicate mapping keys');
} catch (e) {
  errors.push(`Duplicate/Invalid YAML key: ${e.message}`);
  exitCode = 1;
}

// ---- 2. SHA field validation (from parsed YAML) ----
console.log('\n[CHECK] SHA hex content (40-char hex only)...');
const shaFields = [
  'baselineCommit', 'candidateCommit', 'mergeCommit',
  'approvedCandidateCommit', 'observedRemoteBaseline'
];

function collectShas(obj, path = '') {
  const shas = [];
  if (!obj || typeof obj !== 'object') return shas;
  for (const [key, value] of Object.entries(obj)) {
    const currentPath = path ? `${path}.${key}` : key;
    if (shaFields.includes(key) && typeof value === 'string') {
      shas.push({ field: currentPath, sha: value });
    } else if (typeof value === 'object') {
      shas.push(...collectShas(value, currentPath));
    }
  }
  return shas;
}

const allShas = parsed ? collectShas(parsed) : [];
for (const { field, sha } of allShas) {
  if (sha === 'null') continue;
  if (sha.length !== 40) {
    errors.push(`SHA length violation in '${field}': '${sha}' is ${sha.length} chars (expected 40)`);
    exitCode = 1;
  } else if (!/^[0-9a-f]{40}$/.test(sha)) {
    errors.push(`SHA hex violation in '${field}': '${sha}' contains non-hex characters`);
    exitCode = 1;
  }
}
if (errors.filter(e => e.includes('SHA')).length === 0) console.log('  All SHA values valid');

// ---- 3. Task state consistency (from parsed YAML) ----
console.log('\n[CHECK] Task state consistency...');
const validStates = [
  'BACKLOG', 'READY', 'CLAIMED', 'IMPLEMENTING', 'SELF_VERIFIED',
  'INDEPENDENT_REVIEW', 'CI_PENDING', 'HUMAN_REVIEW', 'MERGED',
  'VERIFIED', 'FIX_REQUIRED', 'BLOCKED', 'CLARIFICATION_REQUIRED',
  'SPEC_CONFLICT', 'SUPERSEDED', 'CANCELLED'
];

if (parsed && parsed.tasks) {
  const taskStates = [];
  for (const [taskId, taskData] of Object.entries(parsed.tasks)) {
    if (taskData.state) {
      taskStates.push(taskData.state);
      if (!validStates.includes(taskData.state)) {
        errors.push(`Unknown task state '${taskData.state}' in task '${taskId}'`);
        exitCode = 1;
      }
    }
  }
  if (errors.filter(e => e.includes('Unknown task')).length === 0) console.log('  All states valid');

  // Count consistency
  if (parsed.summary && parsed.summary.counts) {
    for (const state of validStates) {
      const actualCount = taskStates.filter(s => s === state).length;
      const declaredCount = parsed.summary.counts[state] || 0;
      if (actualCount !== declaredCount) {
        errors.push(`Count mismatch for '${state}': actual=${actualCount} declared=${declaredCount}`);
        exitCode = 1;
      }
    }
    console.log('  Counts match task states');
  }
}

// ---- 4. Handoff file references (from parsed YAML) ----
console.log('\n[CHECK] Handoff file references...');
let handoffOk = true;
if (parsed && parsed.tasks) {
  for (const [taskId, taskData] of Object.entries(parsed.tasks)) {
    if (taskData.latestHandoff && taskData.latestHandoff !== 'null') {
      if (!existsSync(taskData.latestHandoff)) {
        errors.push(`Missing handoff file for '${taskId}': ${taskData.latestHandoff}`);
        handoffOk = false;
        exitCode = 1;
      }
    }
  }
}
if (handoffOk) console.log('  All handoff references valid');

// ---- 5. Iteration task references ----
console.log('\n[CHECK] Iteration task references...');
let iterOk = true;

if (!existsSync(iterDir)) {
  errors.push(`Iteration directory '${iterDir}' does not exist`);
  iterOk = false;
  exitCode = 1;
} else {
  const iterFiles = readdirSync(iterDir).filter(f => f.endsWith('.yaml'));
  for (const f of iterFiles) {
    const iterContent = readFileSync(join(iterDir, f), 'utf8');
    let iterParsed;
    try {
      iterParsed = yaml.load(iterContent, { filename: join(iterDir, f) });
    } catch (e) {
      errors.push(`Iteration '${f}' parse error: ${e.message}`);
      iterOk = false;
      exitCode = 1;
      continue;
    }
    if (iterParsed && iterParsed.tasks) {
      for (const taskId of iterParsed.tasks) {
        const taskFile = join(taskDir, `${taskId}.yaml`);
        if (!existsSync(taskFile)) {
          errors.push(`Iteration '${f}' references '${taskId}' but ${taskFile} not found`);
          iterOk = false;
          exitCode = 1;
        }
      }
    }
  }
}
if (iterOk) console.log('  All iteration task references valid');

if (errors.length === 0) {
  console.log('\n=== ALL CHECKS PASSED ===');
} else {
  console.log(`\n=== ${errors.length} FAILURE(S) ===`);
  for (const e of errors) console.log(`  FAIL: ${e}`);
}

process.exit(exitCode);
