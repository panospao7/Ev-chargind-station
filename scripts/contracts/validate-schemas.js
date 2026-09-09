const fs = require('fs');
const path = require('path');
// ajv v8's root export targets draft-07; the 2020 build bundles the
// draft-2020-12 meta-schema that "$schema" URIs resolve against.
const Ajv = require('ajv/dist/2020');

const ROOT = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const contractsDir = path.join(ROOT, 'contracts');
const DIALECT = 'https://json-schema.org/draft/2020-12/schema';
let exitCode = 0;

function findFiles(dir, pattern) {
  const results = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...findFiles(fullPath, pattern));
    } else if (entry.isFile() && pattern.test(entry.name)) {
      results.push(fullPath);
    }
  }
  return results;
}

function rel(file) {
  return path.relative(contractsDir, file);
}

const schemaFiles = findFiles(path.join(contractsDir, 'schemas'), /\.json$/);
console.log(`Found ${schemaFiles.length} JSON schema files`);

// Pass 1: parse once and validate structural requirements.
const parsedByPath = new Map();
for (const file of schemaFiles) {
  try {
    const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
    parsedByPath.set(file, parsed);
    if (!parsed.$schema) {
      console.error(`FAIL: ${rel(file)} missing $schema`);
      exitCode = 1;
    } else if (parsed.$schema !== DIALECT) {
      console.error(`FAIL: ${rel(file)} unsupported $schema dialect "${parsed.$schema}" (expected ${DIALECT})`);
      exitCode = 1;
    }
    if (!parsed.$id) {
      console.error(`FAIL: ${rel(file)} missing $id`);
      exitCode = 1;
    }
    console.log(`  OK: ${rel(file)}`);
  } catch (err) {
    console.error(`FAIL: ${rel(file)} — ${err.message}`);
    exitCode = 1;
  }
}

// Pass 2: $id uniqueness across the schema tree.
const idMap = new Map();
for (const file of schemaFiles) {
  const parsed = parsedByPath.get(file);
  if (!parsed || !parsed.$id) {
    continue;
  }
  if (idMap.has(parsed.$id)) {
    console.error(`FAIL: Duplicate $id "${parsed.$id}" in ${rel(file)} and ${idMap.get(parsed.$id)}`);
    exitCode = 1;
  }
  idMap.set(parsed.$id, rel(file));
}

// Pass 3: relative $ref targets must exist on disk.
const refPattern = /"\$ref"\s*:\s*"([^"]+)"/g;
const refsByFile = new Map();
for (const file of schemaFiles) {
  const content = fs.readFileSync(file, 'utf8');
  const refs = [];
  let match;
  while ((match = refPattern.exec(content)) !== null) {
    const ref = match[1];
    if (ref.startsWith('../') || ref.startsWith('./')) {
      const resolved = path.resolve(path.dirname(file), ref);
      refs.push({ ref, resolved, exists: fs.existsSync(resolved) });
      if (!refs[refs.length - 1].exists) {
        console.error(`FAIL: Unresolved $ref "${ref}" in ${rel(file)}`);
        exitCode = 1;
      }
    }
  }
  refsByFile.set(file, refs);
}

// Pass 4: every parsable schema must compile against the 2020-12 meta-schema.
// Files with unresolved refs are skipped here — the missing target is already
// reported in pass 3 and ajv would only add a noisier equivalent failure.
// Ref targets are pre-registered under the exact ref string so relative
// references resolve during compilation. strict is disabled because JSON
// Schema 2020-12 permits unknown (extension) keywords; meta-schema
// validation of known keywords is unaffected.
let compiled = 0;
const ajv = new Ajv({ allErrors: false, strict: false, addUsedSchema: false });
for (const file of schemaFiles) {
  const parsed = parsedByPath.get(file);
  if (!parsed) {
    continue;
  }
  const refs = refsByFile.get(file) || [];
  if (refs.some(ref => !ref.exists)) {
    continue;
  }
  try {
    for (const ref of refs) {
      ajv.addSchema(parsedByPath.get(ref.resolved), ref.ref);
    }
    ajv.compile(parsed);
    compiled++;
  } catch (err) {
    console.error(`FAIL: ${rel(file)} is not a valid ${DIALECT} schema — ${err.message}`);
    exitCode = 1;
  }
}
console.log(`  ${compiled} schemas compiled against the 2020-12 meta-schema`);

process.exit(exitCode);
