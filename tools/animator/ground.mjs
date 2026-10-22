// Ground check: forward kinematics of a clip on its vanilla rig, frame by frame, reporting the lowest box corner.
//   node tools/animator/ground.mjs <filter> [--wool] [--all]
// --node=<regex> limits the check to matching nodes. Ground is y = 0 in model units (Origin). --wool adds the sheep's Wool_Attachment pieces. --all prints every frame.
import { readFileSync } from 'node:fs';
import { CLIPS } from './clips.mjs';

const A = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleSourceCode/hytale-shared-source/HytaleAssets/';
const MODEL = { Cow: 'Cow', Calf: 'Calf', Chicken: 'Chicken', Chick: 'Chick', Pig: 'Pig', Piglet: 'Piglet', Sheep: 'Sheep', Lamb: 'Lamb', Frog: 'Frog' };
const load = p => JSON.parse(readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
const modelFile = rig => {
  const asset = load(A + 'Server/Models/' + (rig === 'Frog' ? 'Critter/Frog_Green' : 'Livestock/' + MODEL[rig]) + '.json');
  return A + 'Common/' + asset.Model;
};

const qmul = (a, b) => [
  a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
  a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
  a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
  a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]];
const qrot = (q, v) => { const p = qmul(qmul(q, [...v, 0]), [-q[0], -q[1], -q[2], q[3]]); return [p[0], p[1], p[2]]; };
const axis = (i, deg) => { const h = deg * Math.PI / 360, q = [0, 0, 0, Math.cos(h)]; q[i] = Math.sin(h); return q; };
const euler = r => qmul(qmul(axis(0, r[0]), axis(1, r[1])), axis(2, r[2]));
const v3 = o => o ? [o.x ?? 0, o.y ?? 0, o.z ?? 0] : [0, 0, 0];

function nodes(rig, wool) {
  const m = load(modelFile(rig)).nodes;
  if (wool) {
    const w = load(A + 'Common/NPC/Livestock/Sheep/Models/Wool_Attachment.blockymodel').nodes;
    const byName = {}; const index = n => { byName[n.name] ??= n; (n.children || []).forEach(index); }; m.forEach(index);
    const graft = n => {
      if (n.shape?.settings?.isPiece && byName[n.name]) {
        for (const c of n.children || []) (c.shape?.settings?.isPiece ? graft(c) : (byName[n.name].children ??= []).push(c));
      } else (n.children || []).forEach(graft);
    };
    w.forEach(graft);
  }
  return m;
}

const only = (process.argv.find(a => a.startsWith('--node=')) ?? '').slice(7);
function lowest(tree, pose) {
  let best = { y: Infinity, node: '' };
  const walk = (n, pp, pq) => {
    const e = pose[n.name] ?? {};
    const pos = v3(n.position).map((v, i) => v + (e.position?.[i] ?? 0));
    const o = n.orientation ? [n.orientation.x, n.orientation.y, n.orientation.z, n.orientation.w] : [0, 0, 0, 1];
    const q = qmul(pq, e.rotation ? qmul(o, euler(e.rotation)) : o);
    const wp = qrot(pq, pos).map((v, i) => v + pp[i]);
    const s = n.shape;
    if (s && s.type !== 'none' && s.visible !== false && s.settings?.size && (!only || new RegExp(only).test(n.name))) {
      const st = v3(s.stretch ?? { x: 1, y: 1, z: 1 }).map((v, i) => v * (e.stretch?.[i] ?? 1));
      let size = v3(s.settings.size);
      if (s.type === 'quad') { const nrm = (s.settings.normal ?? '+Z')[1]; size = nrm === 'X' ? [0, size[1], size[0]] : nrm === 'Y' ? [size[0], 0, size[1]] : [size[0], size[1], 0]; }
      const off = v3(s.offset);
      for (const sx of [-1, 1]) for (const sy of [-1, 1]) for (const sz of [-1, 1]) {
        const c = [off[0] + sx * size[0] / 2 * st[0], off[1] + sy * size[1] / 2 * st[1], off[2] + sz * size[2] / 2 * st[2]];
        const y = qrot(q, c)[1] + wp[1];
        if (y < best.y) best = { y, node: n.name };
      }
    }
    for (const c of n.children || []) walk(c, wp, q);
  };
  tree.forEach(n => walk(n, [0, 0, 0], [0, 0, 0, 1]));
  return best;
}

const args = process.argv.slice(2);
const filter = args.find(a => !a.startsWith('--')) ?? '';
const wool = args.includes('--wool'), all = args.includes('--all');
const { Pose } = await import('./lib.mjs');
for (const c of CLIPS.filter(c => !filter || filter.split(',').some(f => (c.rig + '_' + c.name).toLowerCase().includes(f.toLowerCase())))) {
  const tree = nodes(c.rig, wool && c.rig === 'Sheep');
  const rest = lowest(tree, {});
  let worst = { y: Infinity }, rows = [];
  for (let t = 0; t <= c.dur; t++) {
    const l = lowest(tree, c.f(t, new Pose()).m);
    if (l.y < worst.y) worst = { ...l, t };
    if (all && t % 6 === 0) rows.push(`  t=${t} min=${l.y.toFixed(1)} ${l.node}`);
  }
  // the bind pose's feet are the ground: some rigs stand a few units above Origin
  const depth = worst.y - rest.y;
  console.log(`${c.rig} ${c.name}${wool && c.rig === 'Sheep' ? ' +wool' : ''}: feet at ${rest.y.toFixed(1)} (${rest.node}); lowest ${worst.y.toFixed(1)} at t=${worst.t} (${worst.node}), ${depth.toFixed(1)} from the ground${depth < -0.75 ? '  BELOW GROUND' : ''}`);
  if (all) console.log(rows.join('\n'));
}
