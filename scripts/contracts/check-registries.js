const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const contractsDir = path.resolve(__dirname, '../../contracts');
let exitCode = 0;

function loadYaml(relPath) {
  const full = path.join(contractsDir, relPath);
  return yaml.load(fs.readFileSync(full, 'utf8'));
}

function checkMessageRegistry() {
  console.log('--- Message Registry ---');
  const reg = loadYaml('registries/messages-v1.yaml');
  if (!reg.messages || !Array.isArray(reg.messages)) {
    console.error('FAIL: messages-v1.yaml has no messages array');
    exitCode = 1;
    return;
  }
  const names = reg.messages.map(m => m.name);
  const dups = names.filter((n, i) => names.indexOf(n) !== i);
  if (dups.length > 0) {
    console.error('FAIL: Duplicate names:', [...new Set(dups)]);
    exitCode = 1;
  }
  console.log(`  ${names.length} message names are unique`);
  const bad = reg.messages.filter(m => !m.type || !m.type.startsWith('com.evplatform.'));
  if (bad.length > 0) {
    console.error('FAIL: Non-compliant namespaces:', bad.map(m => `${m.name}: ${m.type || 'missing'}`));
    exitCode = 1;
  }
  console.log(`  ${reg.messages.length} messages use com.evplatform namespace`);
  const missing = reg.messages.filter(m => m.schema && !fs.existsSync(path.join(contractsDir, 'schemas', m.schema)));
  if (missing.length > 0) {
    console.error('FAIL: Missing schemas:', missing.map(m => `${m.name}: schemas/${m.schema}`));
    exitCode = 1;
  }
  console.log('  All referenced schemas exist');
  const noHandler = reg.messages.filter(m => m.command && !m.handler);
  if (noHandler.length > 0) {
    console.error('FAIL: Commands without handler:', noHandler.map(m => m.name));
    exitCode = 1;
  }
  console.log('  Command handlers checked');
  if (!reg.release_waves) {
    console.error('FAIL: Missing release_waves');
    exitCode = 1;
  }
  console.log('  Release waves defined');
  if (!reg['x-data-classification']) {
    console.warn('  WARN: No x-data-classification defined');
  }
  console.log('  Message registry checks passed');
}

function checkProblemCodes() {
  console.log('--- Problem Codes Registry ---');
  const reg = loadYaml('registries/problem-codes-v1.yaml');
  if (!reg.problemCodes || !Array.isArray(reg.problemCodes)) {
    console.error('FAIL: problem-codes-v1.yaml has no problemCodes array');
    exitCode = 1;
    return;
  }
  const seen = {};
  reg.problemCodes.forEach(c => {
    if (seen[c.code]) {
      console.error('FAIL: Duplicate code:', c.code);
      exitCode = 1;
    }
    seen[c.code] = true;
  });
  console.log(`  ${reg.problemCodes.length} problem codes are unique`);
  reg.problemCodes.forEach(c => {
    if (!c.httpStatus) {
      console.error(`FAIL: Code ${c.code} missing httpStatus`);
      exitCode = 1;
    }
  });
  console.log('  All codes have HTTP status');
}

function checkLifecycles() {
  console.log('--- Lifecycle Registry ---');
  const reg = loadYaml('registries/lifecycles-v1.yaml');
  if (!reg.lifecycles || !Array.isArray(reg.lifecycles)) {
    console.error('FAIL: lifecycles-v1.yaml has no lifecycles array');
    exitCode = 1;
    return;
  }
  reg.lifecycles.forEach(a => {
    const stateNames = new Set((a.states || []).map(s => s.name));
    (a.permittedTransitions || []).forEach(t => {
      if (!stateNames.has(t.from)) {
        console.error(`FAIL: ${a.name}: unknown source state "${t.from}"`);
        exitCode = 1;
      }
      if (!stateNames.has(t.to)) {
        console.error(`FAIL: ${a.name}: unknown target state "${t.to}"`);
        exitCode = 1;
      }
    });
    console.log(`  ${a.name}: ${(a.states || []).length} states, ${(a.permittedTransitions || []).length} transitions valid`);
  });
}

function checkTraceability() {
  console.log('--- Traceability Registry ---');
  const reg = loadYaml('registries/traceability-v1.yaml');
  if (!reg.requirements || !Array.isArray(reg.requirements)) {
    console.error('FAIL: traceability-v1.yaml has no requirements array');
    exitCode = 1;
    return;
  }
  const w1Open = reg.requirements.filter(r => r.releaseApplicability === 'W1' && r.status === 'OPEN');
  if (w1Open.length > 0) {
    console.error(`FAIL: ${w1Open.length} W1 requirements OPEN:`, w1Open.map(r => r.id));
    exitCode = 1;
  }
  console.log(`  ${reg.requirements.length} requirements, ${w1Open.length} W1 open`);
}

checkMessageRegistry();
checkProblemCodes();
checkLifecycles();
checkTraceability();

process.exit(exitCode);
