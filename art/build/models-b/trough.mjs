// Feeding trough: 2 x 1 x 1 blocks, an empty model and a filled one sharing one atlas.
// Designed centred; written shifted half a block in -X so it covers the placed cell and the one to its west.
// Run: node art/build/models-b/trough.mjs
import path from 'node:path';
import { box, rot, build, audit } from './lib.mjs';
import * as M from './materials.mjs';

const ROOT = path.resolve(import.meta.dirname, '../../..');
const OUT = path.join(ROOT, 'src/main/resources/Common/Blocks/CozyRanch/Trough');

const plainWood = M.wood({ base: '#8C5E38', seed: 41 });
const materials = {
  // two planks per wall: a groove halfway up the outside and inside faces
  plank: (c) => {
    const lab = plainWood(c);
    return Math.abs(c.wn[1]) < 0.5 && Math.abs(c.p[1] - 12.5) < 0.5 ? [lab[0] - 12, lab[1] + 1, lab[2] - 4] : lab;
  },
  wood: plainWood,
  dark: M.wood({ base: '#6A4528', seed: 42 }),
  rim: M.wood({ base: '#9A6A40', seed: 43, amp: 0.8 }),
  metal: M.metal({ base: '#5C5F66' }),
  feed: M.feed({}),
  hay: M.hay({ base: '#DDB451', seed: 44 }),
  carrot: M.carrot({}),
  apple: M.apple({}),
};

const nodes = [];
const add = (n) => (nodes.push(n), n);
const FOOD = { tag: 'food' };

for (const sx of [-1, 1]) {
  add(box('Foot', [sx * 20, 1.5, 0], [6, 3, 26], 'dark'));
  for (const sz of [-1, 1]) add(box('Leg', [sx * 20, 5.5, sz * 8], [4, 5, 4], 'dark'));
}
add(box('Bottom', [0, 8.5, 0], [54, 3, 20], 'wood', { data: { grain: 0 } }));
for (const sz of [-1, 1]) {
  add(box('Side', [0, 12.5, sz * 11.5], [60, 11, 3], 'plank', { data: { grain: 0 } }));
  add(box('SideRim', [0, 19, sz * 11.5], [62, 2, 5], 'rim', { data: { grain: 0 } }));
  for (const sx of [-1, 1]) add(box('Band', [sx * 14, 12.5, sz * 13.6], [3, 11, 1], 'metal'));
}
for (const sx of [-1, 1]) {
  add(box('End', [sx * 28.5, 12.5, 0], [3, 11, 20], 'plank', { data: { grain: 2 } }));
  add(box('EndRim', [sx * 28.5, 19, 0], [5, 2, 18], 'rim', { data: { grain: 2 } }));
}

// food (the Filled state only)
add(box('Feed', [0, 12, 0], [52, 5, 18], 'feed', FOOD));
add(box('FeedMound', [-2, 15, 1], [30, 3, 12], 'hay', { ...FOOD, q: rot('y', 5) }));
add(box('Carrot', [-14, 16.5, 2], [9, 3, 3], 'carrot', { ...FOOD, q: rot('z', -8, 'y', 30),
  children: [box('CarrotLeaf', [6, 0.5, 0], [3, 2, 4], 'carrot', { tag: 'food', data: { leaf: true } })] }));
add(box('Carrot', [9, 16, -3], [8, 3, 3], 'carrot', { ...FOOD, q: rot('z', 6, 'y', -40),
  children: [box('CarrotLeaf', [-5.5, 0.5, 0], [3, 2, 4], 'carrot', { tag: 'food', data: { leaf: true } })] }));
add(box('Apple', [18, 16.5, 3], [5, 5, 5], 'apple', { ...FOOD, q: rot('y', 20) }));

const ao = (p, n) => {
  let d = 0;
  if (Math.abs(n[1]) < 0.5 && p[1] < 3) d -= (3 - p[1]) * 2;
  if (Math.abs(n[1]) < 0.5 && p[1] > 9 && p[1] < 13 && Math.abs(p[2]) < 10.1 && Math.abs(p[0]) < 27.1) d -= ((13 - p[1]) / 4) * 6; // inside the tub, near the floor
  return d;
};

const shift = [-16, 0, 0];
const filled = build({
  roots: nodes, materials, ao, shift,
  modelPath: path.join(OUT, 'Trough_Filled.blockymodel'),
  texturePath: path.join(OUT, 'Trough_Texture.png'),
});
build({
  roots: nodes, materials, ao, shift, atlasFrom: filled, exclude: (n) => n.tag === 'food',
  modelPath: path.join(OUT, 'Trough.blockymodel'),
});
console.log(JSON.stringify({ texture: `${filled.W}x${filled.H}`,
  empty: audit(path.join(OUT, 'Trough.blockymodel')), filled: audit(path.join(OUT, 'Trough_Filled.blockymodel')) }));
