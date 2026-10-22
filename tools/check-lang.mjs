// Lang key check for Cozy Tales: Ranching.
//   node tools/check-lang.mjs            report keys used in Java/.ui but missing from en-US, and keys es-ES / pt-BR lack
//   node tools/check-lang.mjs --strict   exit 1 on any key missing from en-US
// Keys at runtime are <file name without .lang>.<key>.
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const langRoot = join(root, 'src/main/resources/Server/Languages');
const locales = ['en-US', 'es-ES', 'pt-BR'];

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out); else out.push(p);
  }
  return out;
}

function readLocale(locale) {
  const keys = new Map();
  for (const file of walk(join(langRoot, locale)).filter(f => f.endsWith('.lang'))) {
    const prefix = file.split(/[\\/]/).pop().replace(/\.lang$/, '');
    for (const line of readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
      const m = line.match(/^\s*([^#=\s][^=]*?)\s*=/);
      if (m) keys.set(prefix + '.' + m[1], relative(root, file));
    }
  }
  return keys;
}

const en = readLocale('en-US');
const used = new Map();
const keyPattern = /["%](cozyranch_[a-z0-9_]+\.[A-Za-z0-9_.]+)/g;
for (const file of walk(join(root, 'src/main')).filter(f => /\.(java|ui|json)$/.test(f))) {
  const text = readFileSync(file, 'utf8');
  for (const m of text.matchAll(keyPattern)) if (!used.has(m[1])) used.set(m[1], relative(root, file));
}
const server = /^server\./;
let missing = 0;
for (const [key, file] of used) {
  const bare = key.replace(server, '');
  if (!en.has(bare) && !en.has(key)) { missing++; console.log(`MISSING en-US  ${key}  (used in ${file})`); }
}
for (const locale of locales.slice(1)) {
  const other = readLocale(locale);
  const lacking = [...en.keys()].filter(k => !other.has(k));
  console.log(`${locale}: ${en.size - lacking.length}/${en.size} keys translated`);
  if (process.argv.includes('--list')) for (const k of lacking) console.log(`  ${locale} needs ${k}`);
}
console.log(`${used.size} keys used in code, ${en.size} in en-US, ${missing} missing from en-US`);
if (missing && process.argv.includes('--strict')) process.exit(1);
