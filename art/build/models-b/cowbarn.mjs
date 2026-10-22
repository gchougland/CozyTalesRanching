// Cow Barn: 4 x 4 x 4 blocks. Run: node art/build/models-b/cowbarn.mjs
// Designed centred on the footprint; written shifted by half a block so the 4 x 4 footprint covers
// cells -2..1 around the placed cell (hitbox -2..2 in block units).
import path from 'node:path';
import { box, slabXY, rot, build, audit } from './lib.mjs';
import * as M from './materials.mjs';

const ROOT = path.resolve(import.meta.dirname, '../../..');
const OUT = path.join(ROOT, 'src/main/resources/Common/Blocks/CozyRanch/Houses/CowBarn');

const materials = {
  red: M.boards({ base: '#B0452F', w: 8, batten: true, seed: 1 }),
  leaf: M.boards({ base: '#A9412C', w: 5, seed: 21 }),
  trim: M.wood({ base: '#E6D8AE', seed: 2, amp: 0.45 }),
  stone: M.fieldstone({ base: '#7E8386', seed: 3, groove: -16 }),
  shingle: M.shingles({ base: '#76613A', edge: '#4E3C26', moss: '#66723A', seed: 5, mossAmount: 0.32, mossScale: 7 }),
  cap: M.shingles({ base: '#76613A', edge: '#4E3C26', moss: '#7D8A3E', seed: 6, course: 3, sw: 5, mossAmount: 0.3 }),
  beam: M.wood({ base: '#6A4B2E', seed: 7 }),
  frame: M.wood({ base: '#5A3B26', seed: 8 }),
  dark: M.dark({}),
  hay: M.hay({ base: '#EDC255', seed: 9 }),
  bale: M.bale({ base: '#E8BE55', seed: 10 }),
  metal: M.metal({ base: '#B2B7BA' }),
  plaque: M.plaque({}),
  louvre: M.louvre({}),
};

const SIDES = ['front', 'back', 'left', 'right'];
const Z = -3; // the building sits 3 units back so the churn and bale fit in front
const nodes = [];
const add = (n) => (nodes.push(n), n);
const at = (x, y, z) => [x, y, z + Z];

// --- body ---
add(box('Plinth', at(0, 5, 0), [116, 10, 116], 'stone'));
add(box('Walls', at(0, 38.5, 0), [108, 59, 108], 'red', { faces: SIDES }));
for (const sx of [-1, 1]) for (const sz of [-1, 1]) add(box('CornerPost', at(sx * 54, 38.5, sz * 54), [7, 60, 7], 'trim'));
add(box('EaveBand', at(0, 65.5, 0), [110, 4, 110], 'trim'));

// --- gambrel gable (extruded along Z, closes the attic) ---
const EAVE = [54, 68], KNEE = [36, 92], RIDGE = [0, 103];
const halfW = (y) => (y <= KNEE[1] ? EAVE[0] - ((y - EAVE[1]) * (EAVE[0] - KNEE[0])) / (KNEE[1] - EAVE[1]) : KNEE[0] - ((y - KNEE[1]) * KNEE[0]) / (RIDGE[1] - KNEE[1]));
const layers = [];
for (let y = 68; y < 92; y += 3) layers.push([y, 3]);
for (let y = 92; y < 103; y += 2) layers.push([y, Math.min(2, 103 - y)]);
for (const [y, h] of layers) {
  const w = Math.max(2, 2 * Math.round(halfW(y + h / 2)));
  add(box('Gable', at(0, y + h / 2, 0), [w, h, 108], 'red', { faces: SIDES }));
}

// --- roof ---
const T = 4, D = 122;
for (const sx of [-1, 1]) {
  const e = [sx * EAVE[0], EAVE[1]], k = [sx * KNEE[0], KNEE[1]], r = [0, RIDGE[1]];
  add(slabXY('RoofLower', e, k, { ext0: 7, ext1: 2.5, thick: T, depth: D, z: Z, inside: [0, 60] }, 'shingle'));
  add(slabXY('RoofUpper', k, r, { ext0: 2, ext1: 2, thick: T, depth: D, z: Z, inside: [0, 60] }, 'shingle'));
}
add(box('RidgeBeam', at(0, 107, 0), [8, 6, 122], 'beam', { data: { grain: 2 } }));

// gable trim tracing the two-pitch outline, front and back
for (const zf of [1, -1]) {
  for (const sx of [-1, 1]) {
    const e = [sx * EAVE[0], EAVE[1]], k = [sx * KNEE[0], KNEE[1]], r = [0, RIDGE[1]];
    add(slabXY('GableTrim', e, k, { ext0: 1, ext1: 1, thick: 5, depth: 3, z: Z + zf * 55.6, inside: [0, 200] }, 'trim', { data: { grain: 0 } }));
    add(slabXY('GableTrim', k, r, { ext0: 1, ext1: 0.5, thick: 5, depth: 3, z: Z + zf * 55.6 + zf * 0.3, inside: [0, 200] }, 'trim', { data: { grain: 0 } }));
  }
}

// --- double doors, closed, a one-unit sliver onto a dark inset ---
add(box('DoorInset', at(0, 30, 54.6), [44, 40, 1], 'dark'));
add(box('DoorHeader', at(0, 52, 55.6), [52, 4, 3], 'trim', { data: { grain: 0 } }));
for (const sx of [-1, 1]) add(box('DoorJamb', at(sx * 24, 30, 55.6), [4, 40, 3], 'trim'));
for (const sx of [-1, 1]) {
  const cx = sx * 11;
  add(box('DoorLeaf', at(cx, 30, 56.2), [21, 39, 2], 'leaf'));
  add(box('LeafFrame', at(cx, 48, 57.7), [21, 3, 1], 'trim', { data: { grain: 0 } }));
  add(box('LeafFrame', at(cx, 12, 57.7), [21, 3, 1], 'trim', { data: { grain: 0 } }));
  add(box('LeafFrame', at(cx - 9, 30, 57.7), [3, 33, 1], 'trim'));
  add(box('LeafFrame', at(cx + 9, 30, 57.7), [3, 33, 1], 'trim'));
  const ang = (Math.atan2(33, 15) * 180) / Math.PI;
  add(box('LeafBrace', at(cx, 30, 57.9), [36, 3, 1], 'trim', { q: rot('z', ang), data: { grain: 0 } }));
  add(box('LeafBrace', at(cx, 30, 58.2), [36, 3, 1], 'trim', { q: rot('z', -ang), data: { grain: 0 } }));
  add(box('DoorHandle', at(sx * 3, 30, 58.6), [2, 6, 2], 'metal'));
}

// --- cow-spot plaque ---
add(box('PlaqueFrame', at(0, 59, 54.9), [17, 11, 1], 'frame'));
add(box('Plaque', at(0, 59, 56), [14, 9, 2], 'plaque'));

// --- hayloft with hay spilling out ---
add(box('HayloftInset', at(0, 81, 54.6), [20, 18, 1], 'dark'));
add(box('HayloftTop', at(0, 91.5, 55.6), [26, 3, 3], 'trim', { data: { grain: 0 } }));
for (const sx of [-1, 1]) add(box('HayloftSide', at(sx * 11.5, 81, 55.6), [3, 18, 3], 'trim'));
add(box('HayloftSill', at(0, 70.5, 56.1), [26, 3, 4], 'trim', { data: { grain: 0 } }));
add(box('HayPile', at(0, 76, 55.8), [18, 9, 3], 'hay'));
add(box('HayMound', at(1, 74.5, 58), [16, 6, 5], 'hay', { q: rot('x', 15, 'z', 4) }));
add(box('HaySpill', at(-1, 69.5, 59.5), [19, 6, 3], 'hay', { q: rot('x', 28, 'z', -5) }));
add(box('HayTuft', at(-6, 65.5, 60.5), [7, 6, 2], 'hay', { q: rot('x', 20, 'z', 14) }));
add(box('HayTuft', at(5, 66, 60.2), [6, 5, 2], 'hay', { q: rot('x', 24, 'z', -20) }));

// --- cupola on the ridge ---
add(box('Cupola', at(0, 111, 0), [14, 18, 14], 'red', { faces: [...SIDES, 'top'] }));
for (const sx of [-1, 1]) for (const sz of [-1, 1]) add(box('CupolaPost', at(sx * 7, 111, sz * 7), [2, 18, 2], 'trim'));
add(box('CupolaVent', at(0, 113, 0), [8, 7, 15], 'louvre'));
add(box('CupolaVent', at(0, 113, 0), [15, 7, 8], 'louvre'));
add(box('CupolaRing', at(0, 120, 0), [18, 2, 18], 'trim'));
add(box('CupolaCap', at(0, 122.5, 0), [22, 3, 22], 'cap'));
add(box('CupolaCap', at(0, 125, 0), [16, 2, 16], 'cap'));
add(box('CupolaCap', at(0, 127, 0), [9, 2, 9], 'cap'));

// --- milk churn and hay bale beside the door (in front of the plinth) ---
add(box('Churn', [34, 5.5, 58.5], [9, 11, 9], 'metal'));
add(box('Churn', [34, 5.5, 58.5], [9, 10, 9], 'metal', { q: rot('y', 45) }));
add(box('ChurnShoulder', [34, 11.5, 58.5], [7, 2, 7], 'metal'));
add(box('ChurnNeck', [34, 13.5, 58.5], [5, 3, 5], 'metal'));
add(box('ChurnLid', [34, 15.5, 58.5], [7, 1, 7], 'metal'));
add(box('HayBale', [49, 4.5, 58.5], [14, 9, 9], 'bale', { q: rot('y', -12) }));

// soft occlusion: under the eaves on the side walls, and where things meet the ground
const ao = (p, n) => {
  let d = 0;
  if (Math.abs(n[1]) < 0.5 && Math.abs(n[0]) > 0.7 && p[1] > 56 && p[1] < 69) d -= ((p[1] - 56) / 13) * 7;
  if (p[1] < 3 && Math.abs(n[1]) < 0.5) d -= (3 - p[1]) * 2;
  return d;
};

const res = build({
  roots: nodes, materials, ao, shift: [-16, 0, -16],
  modelPath: path.join(OUT, 'CowBarn.blockymodel'),
  texturePath: path.join(OUT, 'CowBarn_Texture.png'),
});
console.log(JSON.stringify({ texture: `${res.W}x${res.H}`, ...audit(path.join(OUT, 'CowBarn.blockymodel')) }));
