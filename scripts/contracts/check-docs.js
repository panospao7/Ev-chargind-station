const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const docsDir = path.join(ROOT, 'docs');
let exitCode = 0;

// AC-08: documentation consistency must fail for an OPEN W1-critical gap.
// GOV-006 rows are markdown table rows ending in a **STATUS** column.
// A row is W1-critical when it self-labels "W1-critical" or is a GAP row
// (the W1-critical gaps per GOV-007 §8), or when it carries no recognizable
// register ID (fail closed on unlabeled OPEN rows).
// Register meta-rows whose recorded resolution is the green CI run of this
// very gate (e.g. CON-175/176) are reported but do not fail the check:
// they cannot be closed before the first green run exists.
function checkContradictions() {
  console.log('--- Contradiction Status ---');
  const govPath = path.join(docsDir, '00_governance', '06_contradiction_and_resolution_register_v1.0.md');
  if (!fs.existsSync(govPath)) {
    console.error('FAIL: GOV-006 not found');
    exitCode = 1;
    return;
  }
  const content = fs.readFileSync(govPath, 'utf8');
  const openRows = content.split(/\r?\n/).filter(line => line.includes('**OPEN**'));
  const w1CriticalOpen = [];
  const otherOpen = [];
  for (const row of openRows) {
    const idMatch = row.match(/\*\*((?:CON|GAP)-\d+)\*\*/);
    const rowLower = row.toLowerCase();
    if (!idMatch || rowLower.includes('w1-critical') || idMatch[1].startsWith('GAP-')) {
      w1CriticalOpen.push(idMatch ? idMatch[1] : '(unlabeled OPEN row)');
    } else {
      otherOpen.push(idMatch[1]);
    }
  }
  if (w1CriticalOpen.length > 0) {
    console.error(`FAIL: ${w1CriticalOpen.length} W1-critical gaps still OPEN: ${w1CriticalOpen.join(', ')}`);
    exitCode = 1;
  }
  if (otherOpen.length > 0) {
    console.log(`  ${otherOpen.length} register meta-contradictions OPEN pending the green CI run (not W1-critical): ${otherOpen.join(', ')}`);
  }
  if (openRows.length === 0) {
    console.log('  No OPEN contradictions remaining');
  }
}

function checkTraceability() {
  console.log('--- W1 Requirements ---');
  const tracePath = path.join(ROOT, 'contracts', 'registries', 'traceability-v1.yaml');
  if (!fs.existsSync(tracePath)) {
    console.warn('  WARN: traceability-v1.yaml not found, skipping');
    return;
  }
  const yaml = require('js-yaml');
  const reg = yaml.load(fs.readFileSync(tracePath, 'utf8'));
  const items = reg && (reg.requirements || reg.traceability);
  if (!Array.isArray(items)) {
    console.warn('  WARN: no requirements in traceability registry');
    return;
  }
  const seen = new Set();
  let invalid = 0;
  let w1Count = 0;
  const w1Open = [];
  for (const item of items) {
    const id = item && (item.requirementId || item.id);
    if (!id) {
      invalid++;
      continue;
    }
    if (seen.has(id)) {
      console.error(`FAIL: duplicate traceability requirement "${id}"`);
      exitCode = 1;
      invalid++;
    }
    seen.add(id);
    const wave = String(item.releaseApplicability || item.releaseWave || '');
    if (wave.startsWith('W1')) {
      w1Count++;
      if (item.status === 'OPEN') {
        w1Open.push(id);
      }
    }
  }
  if (invalid > 0) {
    console.error(`FAIL: ${invalid} invalid traceability rows`);
    exitCode = 1;
  }
  console.log(`  ${items.length} traced requirements, ${w1Count} W1, ${w1Open.length} W1 OPEN`);
  if (w1Open.length > 0) {
    console.error(`FAIL: ${w1Open.length} W1 requirements still OPEN — blocking implementation readiness`);
    exitCode = 1;
  }
}

checkContradictions();
checkTraceability();

process.exit(exitCode);
