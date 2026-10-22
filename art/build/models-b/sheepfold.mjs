// Sheepfold: an enclosed stone-and-thatch sheep cottage, 3 x 3 x 3 blocks, centred on the placed cell.
// Run: node art/build/models-b/sheepfold.mjs
import path from 'node:path';
import { box, rot, build, audit } from './lib.mjs';
import * as M from './materials.mjs';

const ROOT = path.resolve(import.meta.dirname, '../../..');
const OUT = path.join(ROOT, 'src/main/resources/Common/Blocks/CozyRanch/Houses/Sheepfold');

const materials = {
  stone: M.drystone({ base: '#7E7F78', moss: '#6B7847', mossLight: '#8C9A4F', seed: 4, mossAmount: 0.5 }),
  sill: M.fieldstone({ base: '#7A7C76', seed: 31, cw: 8, ch: 5 }),
  post: M.wood({ base: '#65482C', seed: 32 }),
  frame: M.wood({ base: '#563B24', seed: 33 }),
  thatch: M.thatch({ base: '#CFA454', under: '#7D622F', seed: 6 }),
  ridge: M.wood({ base: '#5A3E26', seed: 34 }),
  door: M.doorPlanks({ base: '#8A5A34', straps: [8, 20] }),
  shutter: M.boards({ base: '#7E5532', w: 4, seed: 35, amp: 0.9 }),
  metal: M.metal({ base: '#5C5F66' }),
  hay: M.hay({ base: '#D9B744', seed: 36 }),
  wool: M.wool({}),
};

const SIDES = ['front', 'back', 'left', 'right'];
const nodes = [];
const add = (n) => (nodes.push(n), n);

// --- stone body, timber frame ---
add(box('Walls', [0, 25, 0], [84, 50, 84], 'stone', { faces: [...SIDES, 'bottom'] }));
for (const sx of [-1, 1]) for (const sz of [-1, 1]) add(box('CornerPost', [sx * 42, 26, sz * 42], [8, 52, 8], 'post'));
for (const sz of [-1, 1]) add(box('WallPlate', [0, 50, sz * 42], [90, 5, 6], 'post', { data: { grain: 0 } }));
for (const sx of [-1, 1]) add(box('WallPlate', [sx * 42, 49.75, 0], [6, 4, 90], 'post', { data: { grain: 2 } }));
// knee braces from each post up to the plate, on every wall face
for (const sx of [-1, 1]) {
  for (const sz of [-1, 1]) {
    add(box('KneeBrace', [sx * 32, 41.75, sz * 43.3], [17, 4, 3], 'post', { q: rot('z', -sx * 45), data: { grain: 0 } }));
    add(box('KneeBrace', [sx * 43.3, 41.75, sz * 32], [3, 4, 17], 'post', { q: rot('x', sz * 45), data: { grain: 2 } }));
  }
}

// --- layered thatch hip roof: three tiers of four steps, a lip between tiers ---
// each tier: a thick fringe, then two shallower steps; [half-width, height]
const tiers = [
  { y: 53, s: [[48, 4], [42, 3], [36, 3]] },
  { y: 62, s: [[40, 4], [34, 3], [28, 3]] },
  { y: 71, s: [[31, 4], [25, 3], [19, 3]] },
  { y: 81, s: [[13, 3]] },
];
for (const t of tiers) {
  let y = t.y;
  t.s.forEach(([h, th], i) => {
    const faces = i === 0 ? [...SIDES, 'top', 'bottom'] : [...SIDES, 'top'];
    add(box('Thatch', [0, y + th / 2, 0], [2 * h, th, 2 * (h - 4)], 'thatch', { faces }));
    y += th;
  });
}
add(box('RidgeBeam', [0, 86.5, 0], [30, 5, 7], 'ridge', { data: { grain: 0 } }));

// --- arched door, closed ---
add(box('DoorStep', [0, 1, 43.5], [26, 2, 5], 'sill'));
add(box('Door', [0, 15, 43], [20, 26, 2], 'door'));
add(box('DoorArch', [0, 29.5, 43], [16, 3, 2], 'door'));
add(box('DoorArch', [0, 32, 43], [10, 2, 2], 'door'));
for (const sx of [-1, 1]) {
  add(box('DoorJamb', [sx * 11.5, 15, 43.5], [3, 26, 3], 'frame'));
  add(box('DoorArchFrame', [sx * 10.5, 29.5, 43.5], [5, 3, 3], 'frame'));
  add(box('DoorArchFrame', [sx * 6.5, 32, 43.5], [3, 2, 3], 'frame'));
}
add(box('DoorLintel', [0, 34.5, 43.5], [16, 3, 3], 'frame', { data: { grain: 0 } }));
add(box('DoorRing', [5, 15, 44.6], [2, 3, 2], 'metal'));

// --- hay rack on the outside wall, right of the door ---
add(box('RackBase', [26, 15.5, 44.5], [16, 3, 5], 'frame', { data: { grain: 0 } }));
for (const x of [17, 35]) add(box('RackEnd', [x, 21, 44.5], [2, 14, 5], 'frame'));
add(box('RackHay', [26, 21.5, 44], [14, 9, 4], 'hay'));
add(box('RackHayTop', [26, 27, 44.2], [13, 4, 4], 'hay', { q: rot('z', 4, 'x', -10) }));
for (const y of [20, 26]) add(box('RackRail', [26, y, 46.5], [14, 2, 2], 'frame', { data: { grain: 0 } }));
for (const x of [21, 26, 31]) add(box('RackSlat', [x, 21, 47.9], [2, 12, 1], 'frame'));

// --- shuttered windows on both side walls ---
for (const sx of [-1, 1]) {
  add(box('WindowFrame', [sx * 42.6, 30, 0], [1, 16, 16], 'frame'));
  for (const sz of [-1, 1]) add(box('Shutter', [sx * 44, 30, sz * 3.5], [2, 14, 7], 'shutter'));
}

// --- big wool tufts caught on the stones ---
const tuft = (c, s, c2, s2) => { add(box('Wool', c, s, 'wool')); add(box('Wool', c2, s2, 'wool')); };
tuft([-24, 12, 43.5], [9, 7, 4], [-19.5, 8.5, 43.8], [5, 5, 3]);
tuft([-29, 28, 43.5], [8, 6, 4], [-27, 32, 43.7], [4, 3, 3]);
tuft([43.5, 14, -22], [4, 7, 9], [43.8, 10.5, -18.5], [3, 4, 4]);
tuft([14, 22, -43.5], [9, 6, 4], [19, 18.5, -43.7], [5, 5, 3]);
tuft([-43.5, 10, 20], [4, 7, 9], [-43.7, 13.5, 24], [3, 4, 4]);
tuft([-42, 38, 47.2], [7, 6, 3], [-39, 35.5, 47], [3, 3, 2]);

const ao = (p, n) => {
  let d = 0;
  if (Math.abs(n[1]) < 0.5 && p[1] > 38 && p[1] < 53) d -= ((p[1] - 38) / 15) * 8;
  if (p[1] < 3 && Math.abs(n[1]) < 0.5) d -= (3 - p[1]) * 2;
  return d;
};

const res = build({
  roots: nodes, materials, ao,
  modelPath: path.join(OUT, 'Sheepfold.blockymodel'),
  texturePath: path.join(OUT, 'Sheepfold_Texture.png'),
});
console.log(JSON.stringify({ texture: `${res.W}x${res.H}`, ...audit(path.join(OUT, 'Sheepfold.blockymodel')) }));
