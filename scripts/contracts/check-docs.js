const fs = require('fs');
const path = require('path');

const docsDir = path.resolve(__dirname, '../../docs');
let exitCode = 0;

function checkContradictions() {
  console.log('--- Contradiction Status ---');
  const govPath = path.join(docsDir, '00_governance', '06_contradiction_and_resolution_register_v1.0.md');
  if (!fs.existsSync(govPath)) {
    console.error('FAIL: GOV-006 not found');
    exitCode = 1;
    return;
  }
  const content = fs.readFileSync(govPath, 'utf8');
  const openMatches = content.match(/\*\*OPEN\*\*/g);
  const openCount = openMatches ? openMatches.length : 0;
  const w1CriticalOpen = content.match(/\*\*OPEN\*\*/g);
  if (openCount > 0) {
    console.error(`FAIL: ${openCount} contradictions still OPEN`);
    exitCode = 1;
  } else {
    console.log('  No OPEN contradictions remaining');
  }
}

function checkTraceability() {
  console.log('--- W1 Requirements ---');
  const tracePath = path.resolve(__dirname, '../../contracts/registries/traceability-v1.yaml');
  if (!fs.existsSync(tracePath)) {
    console.warn('  WARN: traceability-v1.yaml not found, skipping');
    return;
  }
  const yaml = require('js-yaml');
  const reg = yaml.load(fs.readFileSync(tracePath, 'utf8'));
  if (!reg.requirements) {
    console.warn('  WARN: no requirements in traceability registry');
    return;
  }
  const w1Items = reg.requirements.filter(r => r.releaseApplicability && r.releaseApplicability.startsWith('W1'));
  const w1Open = w1Items.filter(r => r.status === 'OPEN');
  console.log(`  ${w1Items.length} W1 requirements, ${w1Open.length} OPEN`);
  if (w1Open.length > 0) {
    console.error(`FAIL: ${w1Open.length} W1 requirements still OPEN — blocking implementation readiness`);
    exitCode = 1;
  }
}

checkContradictions();
checkTraceability();

process.exit(exitCode);
