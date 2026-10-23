// Renders every ui_render step of tests/hytale/journal.test.json into notes/pictures/journal/<golden name>.png.
//   node notes/pictures/journal/render.mjs [filter]
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { join, basename } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';

const mcp = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleMCP';
const root = new URL('../../..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const out = join(root, 'notes/pictures/journal');
const filter = process.argv[2] ?? '';
const tests = JSON.parse(readFileSync(join(root, 'tests/hytale/journal.test.json'), 'utf8'));
const tmp = mkdtempSync(join(tmpdir(), 'journal-'));

for (const c of tests.cases) {
  for (const step of c.steps) {
    if (step.tool !== 'ui_render' || !step.golden?.includes(filter)) continue;
    const name = basename(step.golden);
    const args = join(tmp, name + '.json');
    writeFileSync(args, JSON.stringify(step.arguments));
    const r = spawnSync('node', ['scripts/call-tool.mjs', 'ui_render', '@' + args, '--out', join(out, name)], { cwd: mcp, encoding: 'utf8' });
    let summary = r.stdout;
    try {
      const j = JSON.parse(r.stdout);
      summary = JSON.stringify({ valid: j.valid, errors: j.errors, warnings: j.warnings, notes: j.notes, problems: j.problems ?? j.diagnostics });
    } catch { /* not JSON: print as is */ }
    console.log(`${name}: ${summary.slice(0, 1500)}${r.stderr ? ' STDERR ' + r.stderr.slice(0, 500) : ''}`);
  }
}
