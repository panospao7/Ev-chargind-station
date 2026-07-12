const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const contractsDir = path.resolve(__dirname, '../../contracts');
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

const schemaFiles = findFiles(path.join(contractsDir, 'schemas'), /\.json$/);
console.log(`Found ${schemaFiles.length} JSON schema files`);

for (const file of schemaFiles) {
  try {
    const content = fs.readFileSync(file, 'utf8');
    const parsed = JSON.parse(content);
    if (!parsed.$schema) {
      console.error(`FAIL: ${path.relative(contractsDir, file)} missing $schema`);
      exitCode = 1;
    }
    if (!parsed.$id) {
      console.error(`FAIL: ${path.relative(contractsDir, file)} missing $id`);
      exitCode = 1;
    }
    console.log(`  OK: ${path.relative(contractsDir, file)}`);
  } catch (err) {
    console.error(`FAIL: ${path.relative(contractsDir, file)} — ${err.message}`);
    exitCode = 1;
  }
}

const idMap = {};
for (const file of schemaFiles) {
  const content = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (content.$id) {
    if (idMap[content.$id]) {
      console.error(`FAIL: Duplicate $id "${content.$id}" in ${path.relative(contractsDir, file)} and ${idMap[content.$id]}`);
      exitCode = 1;
    }
    idMap[content.$id] = path.relative(contractsDir, file);
  }
}

const refPattern = /\$ref:\s*"([^"]+)"/g;
const allRefs = {};
for (const file of schemaFiles) {
  const content = fs.readFileSync(file, 'utf8');
  let match;
  while ((match = refPattern.exec(content)) !== null) {
    const ref = match[1];
    if (ref.startsWith('../') || ref.startsWith('./')) {
      const resolved = path.resolve(path.dirname(file), ref);
      if (!fs.existsSync(resolved)) {
        console.error(`FAIL: Unresolved $ref "${ref}" in ${path.relative(contractsDir, file)}`);
        exitCode = 1;
      }
    }
  }
}

process.exit(exitCode);
