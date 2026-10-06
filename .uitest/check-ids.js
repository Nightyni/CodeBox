/*
 * Static check: every id the JS looks up must exist in index.html.
 *
 * A single bad id reference caused two unrelated features to die silently (the throw
 * happened at module top level, so all later bindings were skipped). This catches that
 * class of typo without needing a browser.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(ROOT, 'src/main/resources/static/index.html'), 'utf8');
const js = fs.readFileSync(path.join(ROOT, 'src/main/resources/static/app.js'), 'utf8');

const htmlIds = new Set([...html.matchAll(/id="([^"]+)"/g)].map((m) => m[1]));
const idLookup = /[$]\('#([A-Za-z0-9_-]+)'\)/g;
const referenced = new Set([...js.matchAll(idLookup)].map((m) => m[1]));

const missing = [...referenced].filter((id) => !htmlIds.has(id)).sort();

console.log('html ids:', htmlIds.size, ' js lookups:', referenced.size);

if (missing.length) {
  console.log('');
  console.log('[FAIL] JS references ids that do not exist in index.html:');
  missing.forEach((id) => console.log('   -', id));
  process.exitCode = 1;
} else {
  console.log('');
  console.log('[OK] every id lookup resolves');
}
