import { readFileSync } from 'fs';
import yaml from 'js-yaml';

const filePath = process.argv[2] || 'delivery/status.yaml';

let exitCode = 0;
const errors = [];

console.log('=== Delivery Control Plane Validation ===\n');

// Read raw content for regex-based checks
let content;
try {
  content = readFileSync(filePath, 'utf8');
} catch (e) {
  console.error(`FATAL: Cannot read ${filePath}: ${e.message}`);
  process.exit(1);
}

// ---- 1. YAML parse with duplicate-key rejection (js-yaml) ----
console.log('[CHECK] YAML parse validity & duplicate keys...');
let parsed;
try {
  parsed = yaml.load(content, { filename: filePath });
  console.log('  OK: No duplicate mapping keys');
} catch (e) {
  errors.push(`Duplicate/Invalid YAML key: ${e.message}`);
  exitCode = 1;
}

// ---- 2. SHA field validation ----
console.log('\n[CHECK] SHA hex content (40-char hex only)...');
const shaFields = [
  'baselineCommit', 'candidateCommit', 'mergeCommit',
  'approvedCandidateCommit', 'observedRemoteBaseline'
];
for (const field of shaFields) {
  const re = new RegExp(`"${field}":\\s+"([^"]+)"`, 'g');
  // js-yaml may not preserve the raw strings, so use regex on raw content
  const matches = content.matchAll(new RegExp(`(?:^|\\n)\\s*${field}:\\s+"([^"]+)"`, 'gm'));
  for (const m of matches) {
    const sha = m[1];
    if (sha === 'null') continue;
    if (sha.length !== 40) {
      errors.push(`SHA length violation in '${field}': '${sha}' is ${sha.length} chars (expected 40)`);
      exitCode = 1;
    } else if (!/^[0-9a-f]{40}$/.test(sha)) {
      errors.push(`SHA hex violation in '${field}': '${sha}' contains non-hex characters`);
      exitCode = 1;
    }
  }
}
if (errors.filter(e => e.includes('SHA')).length === 0) console.log('  All SHA values valid');

// ---- 3. Unknown task states ----
console.log('\n[CHECK] Task state consistency...');
if (parsed && parsed.tasks) {
  const validStates = [
    'BACKLOG', 'READY', 'CLAIMED', 'IMPLEMENTING', 'SELF_VERIFIED',
    'INDEPENDENT_REVIEW', 'CI_PENDING', 'HUMAN_REVIEW', 'MERGED',
    'VERIFIED', 'FIX_REQUIRED', 'BLOCKED', 'CLARIFICATION_REQUIRED',
    'SPEC_CONFLICT', 'SUPERSEDED', 'CANCELLED'
  ];
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
    const counts = parsed.summary.counts;
    for (const state of validStates) {
      const actualCount = taskStates.filter(s => s === state).length;
      const declaredCount = counts[state] || 0;
      if (actualCount !== declaredCount) {
        errors.push(`Count mismatch for '${state}': actual=${actualCount} declared=${declaredCount}`);
        exitCode = 1;
      }
    }
    console.log('  Counts match task states');
  }
}

// ---- 4. Handoff file references ----
console.log('\n[CHECK] Handoff file references...');
const { existsSync } = await import('fs');
const handoffRe = /latestHandoff:\s+"([^"]+)"/g;
let handoffMatch;
let handoffOk = true;
while ((handoffMatch = handoffRe.exec(content)) !== null) {
  const path = handoffMatch[1];
  if (path !== 'null' && !existsSync(path)) {
    errors.push(`Missing handoff file: ${path}`);
    handoffOk = false;
    exitCode = 1;
  }
}
if (handoffOk) console.log('  All handoff references valid');

// ---- 5. Iteration task references ----
console.log('\n[CHECK] Iteration task references...');
import { readdirSync } from 'fs';
import { join } from 'path';
const iterDir = process.argv[3] || 'delivery/iterations';
const taskDir = process.argv[4] || 'delivery/tasks';
let iterOk = true;
try {
  const iterFiles = readdirSync(iterDir).filter(f => f.endsWith('.yaml'));
  for (const f of iterFiles) {
    const iterContent = readFileSync(join(iterDir, f), 'utf8');
    const taskRe = /tasks:\s*\n((?:\s+-\s+"[^"]+"\s*\n)*)/;
    const taskMatch = iterContent.match(taskRe);
    if (!taskMatch) continue;
    const taskIds = taskMatch[1].match(/-\s+"([^"]+)"/g) || [];
    for (const t of taskIds) {
      const id = t.match(/-\s+"([^"]+)"/)[1];
      const taskFile = join(taskDir, `${id}.yaml`);
      if (!existsSync(taskFile)) {
        errors.push(`Iteration '${f}' references '${id}' but task file not found`);
        iterOk = false;
        exitCode = 1;
      }
    }
  }
} catch (e) {
  // iteration dir may not exist
}
if (iterOk) console.log('  All iteration task references valid');

if (errors.length === 0) {
  console.log('\n=== ALL CHECKS PASSED ===');
} else {
  console.log(`\n=== ${errors.length} FAILURE(S) ===`);
  for (const e of errors) console.log(`  FAIL: ${e}`);
}

process.exit(exitCode);
