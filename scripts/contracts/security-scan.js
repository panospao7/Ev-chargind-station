const fs = require('fs');
const path = require('path');
const glob = require('glob');

const contractsDir = path.resolve(__dirname, '../../contracts');
let exitCode = 0;

const sensitiveFields = ['api_key', 'apiKey', 'ApiKey', 'password', 'token_secret', 'private_key'];
const schemaFiles = glob.sync('contracts/**/*.{yaml,json}', { cwd: path.resolve(__dirname, '../..') });

console.log(`Scanning ${schemaFiles.length} contract files for sensitive fields...`);

for (const file of schemaFiles) {
  const content = fs.readFileSync(file, 'utf8');
  const contentLower = content.toLowerCase();
  for (const field of sensitiveFields) {
    if (contentLower.includes(field)) {
      if (/example|description|x-/.test(contentLower)) {
        continue;
      }
      console.error(`FAIL: Possible sensitive field "${field}" in ${file}`);
      exitCode = 1;
    }
  }
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
