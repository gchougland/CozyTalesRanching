import { readFileSync } from 'node:fs';
const A = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleSourceCode/hytale-shared-source/HytaleAssets/Common/';
const r = v => v ? [v.x, v.y, v.z].map(n => +(+n).toFixed(1)).join(',') : '';
const q = o => o && (Math.abs(o.w - 1) > 1e-3) ? ' q=' + [o.x, o.y, o.z, o.w].map(n => (+n).toFixed(2)).join(',') : '';
for (const p of process.argv.slice(2)) {
  const m = JSON.parse(readFileSync(A + p, 'utf8'));
  console.log('### ' + p);
  const walk = (n, d) => {
    const s = n.shape || {};
    const sz = s.settings && s.settings.size ? ' size=' + r(s.settings.size) : '';
    console.log('  '.repeat(d) + n.name + ' pos=' + r(n.position) + q(n.orientation) + (s.type && s.type !== 'none' ? ' ' + s.type + sz + ' off=' + r(s.offset) : ''));
    for (const c of n.children || []) walk(c, d + 1);
  };
  for (const n of m.nodes) walk(n, 0);
}
