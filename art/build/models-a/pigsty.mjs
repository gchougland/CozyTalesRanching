// Pigsty: 3 x 3 footprint, 2 tall. Run: node pigsty.mjs
import { Model, build, quat, qmul, qrot } from './lib.mjs';
import { paint, planks, mud, endgrain, pigface, inset } from './paint.mjs';
import { RES, report } from './common.mjs';

const m = new Model('Pigsty', [0, 0, 0]);
const deck = 11;

// chunky plank platform with end-grain corner blocks
m.box('Deck', 'deck', [-46, 1, -46], [46, deck, 46]);
for (const [n, x0, z0] of [['FL', -48, 35], ['FR', 35, 35], ['BL', -48, -48], ['BR', 35, -48]])
  m.box('Corner_' + n, 'endgrain', [x0, 0, z0], [x0 + 13, deck + 1, z0 + 13]);
for (const [n, a, b] of [['F', 1, 0], ['B', -1, 0], ['L', 0, -1], ['R', 0, 1]]) {
  // a proud lower beam along each side, between the corner blocks
  if (a) m.box('Beam_' + n, 'beam', [-35, 0, a > 0 ? 46 : -47], [35, 5, a > 0 ? 47 : -46]);
  else m.box('Beam_' + n, 'beam', [b > 0 ? 46 : -47, 0, -35], [b > 0 ? 47 : -46, 5, 35]);
}

// closed hut: walls box, a sloped wedge under the lean-to roof (front low, back high)
const hx0 = -44, hx1 = 6, hz0 = -46, hz1 = 4, front = 46, back = 56;
m.box('Hut', 'hut', [hx0, deck, hz0], [hx1, front, hz1]);
const run = hz1 - (hz0 + 2), rise = back - front, th = Math.atan2(rise, run), thd = (th * 180) / Math.PI;
const n = [0, Math.cos(th), Math.sin(th)];
const slope = (z) => front + (hz1 - z) * (rise / run);
{
  const L = Math.hypot(run, rise), T = 11, zm = (hz1 + hz0 + 2) / 2;
  m.part('Hut_Wedge', 'hut', [(hx0 + hx1) / 2, slope(zm) - n[1] * T / 2, zm - n[2] * T / 2], [hx1 - hx0 - 1, T, L], { q: quat('x', thd) });
}
// mismatched planks patched onto the walls
m.box('Patch_F1', 'hutpatch', [-8, 14, hz1], [-2, 40, hz1 + 1]);
m.box('Patch_F2', 'hutpatch', [-41, 22, hz1], [-35, 44, hz1 + 1]);
m.box('Patch_L1', 'pinkplank', [hx0 - 1, 26, -30], [hx0, 32, -14]);
m.box('Patch_L2', 'hutpatch', [hx0 - 1, 16, -40], [hx0, 24, -26]);
m.box('Patch_B1', 'hutpatch', [-20, 13, hz0 - 1], [-14, 36, hz0]);

// lean-to roof: five crooked boards
const boards = [[-41, 0, 1.5, -1.2], [-30.5, 0.6, -1, 0.8], [-20, 0.1, 2, -0.4], [-9.5, 0.7, -1.5, 1.4], [1, 0.2, 1, -0.6]];
boards.forEach(([x, dy, yaw, roll], i) => {
  const z0 = hz1 + 7, z1 = hz0 - 3, zm = (z0 + z1) / 2, L = Math.hypot(z0 - z1, (z0 - z1) * (rise / run)), t = 3;
  const c = [x, slope(zm) + n[1] * (t / 2 + dy), zm + n[2] * (t / 2 + dy)];
  m.part('Roof_' + i, 'roof', c, [11, t, L], { q: qmul(quat('y', yaw), qmul(quat('x', thd), quat('z', roll))), variant: i });
});

// doorway: shallow dark inset in a pink frame
const dx0 = -31, dx1 = -14, dtop = deck + 26;
m.box('Door_Inset', 'inset', [dx0, deck, hz1], [dx1, dtop, hz1 + 1]);
m.box('Door_JambL', 'pink', [dx0 - 3, deck, hz1], [dx0, dtop, hz1 + 2]);
m.box('Door_JambR', 'pink', [dx1, deck, hz1], [dx1 + 3, dtop, hz1 + 2]);
m.box('Door_Lintel', 'pink', [dx0 - 4, dtop, hz1], [dx1 + 4, dtop + 4, hz1 + 2.5]);

// pig-face sign hung from a nail, right of the door
m.box('Sign', 'pigsign', [-7, 30, hz1 + 1], [6, 41, hz1 + 2]);
m.box('Sign_EarL', 'pinkdark', [-7, 41, hz1 + 1.2], [-4, 44, hz1 + 1.8]);
m.box('Sign_EarR', 'pinkdark', [3, 41, hz1 + 1.2], [6, 44, hz1 + 1.8]);
m.box('Sign_Nail', 'iron', [-1, 47, hz1], [1, 49, hz1 + 2]);
m.part('Sign_StringL', 'string', [-2.5, 44.5, hz1 + 1.5], [1, 7, 1], { q: quat('z', -35) });
m.part('Sign_StringR', 'string', [2.5, 44.5, hz1 + 1.5], [1, 7, 1], { q: quat('z', 35) });

// trough right of the hut front
{
  const x0 = 9, x1 = 29, z0 = -5, z1 = 7, y1 = deck + 10;
  m.box('Trough_Front', 'trough', [x0, deck, z1 - 2], [x1, y1, z1]);
  m.box('Trough_Back', 'trough', [x0, deck, z0], [x1, y1, z0 + 2]);
  m.box('Trough_EndL', 'trough', [x0, deck, z0 + 2], [x0 + 2, y1, z1 - 2]);
  m.box('Trough_EndR', 'trough', [x1 - 2, deck, z0 + 2], [x1, y1, z1 - 2]);
  m.box('Trough_Feed', 'feed', [x0 + 2, deck, z0 + 2], [x1 - 2, deck + 6, z1 - 2]);
}

// mud wallow in the back right quarter: plank rim, mud just below it
{
  const x0 = 9, x1 = 46, z0 = -46, z1 = -9, y1 = deck + 9, r = 4;
  m.box('Wallow_RimF', 'rim', [x0, deck, z1 - r], [x1, y1, z1]);
  m.box('Wallow_RimB', 'rim', [x0, deck, z0], [x1, y1, z0 + r]);
  m.box('Wallow_RimL', 'rim', [x0, deck, z0 + r], [x0 + r, y1, z1 - r]);
  m.box('Wallow_RimR', 'rim', [x1 - r, deck, z0 + r], [x1, y1, z1 - r]);
  m.box('Wallow_Mud', 'mud', [x0 + r, deck, z0 + r], [x1 - r, y1 - 2, z1 - r]);
}

// short stubby fence at the front left
{
  const z0 = 38, posts = [-44, -31, -18];
  posts.forEach((x, i) => m.box('Fence_Post' + i, 'fence', [x - 2.5, deck, z0], [x + 2.5, deck + 19 - (i % 2), z0 + 5]));
  m.box('Fence_RailLow', 'fence', [-46, deck + 6, z0 + 1.5], [-16, deck + 9, z0 + 3.5]);
  m.box('Fence_RailHigh', 'fence', [-46, deck + 13, z0 + 1], [-16, deck + 16, z0 + 4]);
}

const mats = {
  deck: planks('#C98048', { bw: 7, dir: 'h', topDir: 'z', joints: 60, ragged: 2, splash: '#6E4A30' }),
  beam: planks('#A96A3C', { bw: 5, dir: 'h', joints: 50, splash: '#6E4A30' }),
  endgrain: endgrain('#C0814C', { ring: '#93603A' }),
  hut: planks('#9C6A43', { bw: 7, dir: 'v', topDir: 'z', joints: 200, ragged: 3, splash: '#6E4A30' }),
  hutpatch: planks('#8A5A38', { bw: 6, dir: 'v', joints: 200, ragged: 3 }),
  pinkplank: planks('#C07272', { bw: 6, dir: 'h', joints: 200 }),
  roof: planks('#C27E4C', { bw: 11, dir: 'h', topDir: 'z', groove: 0, joints: 200, ragged: 3 }),
  inset: inset('#1A1F2C'),
  pink: planks('#C47374', { bw: 3, dir: 'v', groove: 0, joints: 200 }),
  pinkdark: paint('#C56E6E', { amp: 2 }),
  pigsign: pigface('#D47C78'),
  iron: paint('#5E5A58', { amp: 1.5 }),
  string: paint('#6B5236', { amp: 1 }),
  trough: planks('#A8703F', { bw: 5, dir: 'h', topDir: 'x', joints: 200 }),
  feed: paint('#B59A4E', { amp: 4, scale: 2.5 }),
  rim: planks('#B87A46', { bw: 4, dir: 'h', topDir: 'x', joints: 60, splash: '#6E4A30' }),
  mud: mud('#864D30', { gloss: '#B8806A' }),
  fence: planks('#9A6440', { bw: 5, dir: 'v', topDir: 'x', groove: 0, joints: 200 }),
};

const r = build(m, mats, {
  outModel: `${RES}/Common/Blocks/CozyRanch/Houses/Pigsty/Pigsty.blockymodel`,
  outTexture: `${RES}/Common/Blocks/CozyRanch/Houses/Pigsty/Pigsty_Texture.png`,
  width: 512,
});
report('Pigsty', r);
