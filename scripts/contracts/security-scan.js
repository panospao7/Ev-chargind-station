const fs = require('fs');
const path = require('path');
const glob = require('glob');

const ROOT = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const contractsDir = path.join(ROOT, 'contracts');
let exitCode = 0;

const sensitiveFields = ['api_key', 'apiKey', 'ApiKey', 'password', 'token_secret', 'private_key'];
// glob returns paths relative to ROOT; join with ROOT so reads are
// ROOT-relative and independent of the process working directory.
const schemaFiles = glob
  .sync('contracts/**/*.{yaml,json}', { cwd: ROOT })
  .map(file => path.join(ROOT, file));

console.log(`Scanning ${schemaFiles.length} contract files for sensitive fields...`);

for (const file of schemaFiles) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  lines.forEach((line, i) => {
    const lineLower = line.toLowerCase();
    // type/scheme declaration lines carry spec vocabulary (e.g. type:
    // userPassword), not credential values — only field keys and free text
    // are treated as sensitive-field suspects.
    if (/^\s*(type|scheme)\s*:/i.test(line)) {
      return;
    }
    for (const field of sensitiveFields) {
      if (lineLower.includes(field) && !/example|description|x-/.test(lineLower)) {
        console.error(`FAIL: Possible sensitive field "${field}" in ${file}:${i + 1}`);
        exitCode = 1;
      }
    }
  });
}

const discoveryFiles = schemaFiles.filter(f =>
  f.toLowerCase().includes('discovery') && (f.endsWith('.yaml') || f.endsWith('.json'))
);
for (const file of discoveryFiles) {
  const content = fs.readFileSync(file, 'utf8');
  const contentLower = content.toLowerCase();
  const subjectIdentifiers = ['accountref', 'driverid', 'subjectid', 'driverref', 'accountid'];
  for (const id of subjectIdentifiers) {
    if (contentLower.includes(id)) {
      console.error(`FAIL: Discovery contract ${file} contains subject identifier "${id}"`);
      exitCode = 1;
    }
  }
}

console.log('Security scan complete');
process.exit(exitCode);
