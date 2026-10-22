// Chicken Coop: 2 x 2 footprint, 3 tall. Run: node coop.mjs
import { Model, build, quat, qmul } from './lib.mjs';
import { paint, planks, shingles, straw, inset } from './paint.mjs';
import { RES, report } from './common.mjs';

const m = new Model('ChickenCoop', [16, 0, 16]);
const bx = -6; // body centre x (the nesting box takes the right side of the footprint)
const hw = 23; // body half width
const zb = -28, zf = 12; // body back and front
const wallTop = 48, ridgeY = wallTop + hw;

// stilts and sill
for (const [n, x0, z0] of [['FL', bx - 22, 4], ['FR', bx + 15, 4], ['BL', bx - 22, -27], ['BR', bx + 15, -27]])
  m.box('Stilt_' + n, 'stilt', [x0, 0, z0], [x0 + 7, 12, z0 + 7]);
m.box('Sill', 'sill', [bx - 26, 12, -31], [bx + 26, 18, 15]);

// walls, corner posts, gable steps (hidden inside the roof at their outer corners)
m.box('Walls', 'wall', [bx - hw, 18, zb], [bx + hw, wallTop, zf], { worldBoards: true, variant: 'w' });
for (const [n, sx, sz] of [['FL', -1, 1], ['FR', 1, 1], ['BL', -1, -1], ['BR', 1, -1]]) {
  const x0 = sx < 0 ? bx - hw - 2 : bx + hw - 2, z0 = sz > 0 ? zf - 2 : zb - 2;
  m.box('Post_' + n, 'post', [x0, 17, z0], [x0 + 4, 49, z0 + 4]);
}
for (let k = 0; k < 6; k++) {
  const y0 = wallTop + 4 * k, y1 = y0 + 4;
  const h = hw - (y1 - wallTop) + 4;
  m.box('Gable_' + k, 'wall', [bx - h, y0, zb], [bx + h, y1, zf], { worldBoards: true, variant: 'g' + k });
}

// roof: four stepped shingle courses a side, upper courses thicker so each butt edge stands proud
const c45 = Math.SQRT1_2;
const roofZ0 = zb - 5, roofZ1 = zf + 5;
for (const side of [1, -1]) {
  for (let i = 0; i < 4; i++) {
    const s0 = i * 10, s1 = s0 + (i === 3 ? 8 : 10), t = 4 + (3 - i) * 1.5;
    const sm = (s0 + s1) / 2;
    const cx = bx + side * (sm * c45 + (t / 2) * c45);
    const cy = ridgeY - sm * c45 + (t / 2) * c45;
    m.part(`Roof_${side > 0 ? 'R' : 'L'}${i}`, 'shingle', [cx, cy, (roofZ0 + roofZ1) / 2], [s1 - s0, t, roofZ1 - roofZ0],
      { q: quat('z', -45 * side), mirrorX: side < 0, variant: 'c' + i });
  }
}
m.box('Ridge', 'ridge', [bx - 7, ridgeY + 1, roofZ0 - 1], [bx + 7, ridgeY + 9, roofZ1 + 1]);

// door: proud frame around a shallow dark inset
const dw = 7;
m.box('Door_Inset', 'inset', [bx - dw, 18, zf], [bx + dw, 40, zf + 1]);
m.box('Door_JambL', 'frame', [bx - dw - 3, 18, zf], [bx - dw, 40, zf + 2.5]);
m.box('Door_JambR', 'frame', [bx + dw, 18, zf], [bx + dw + 3, 40, zf + 2.5]);
m.box('Door_Lintel', 'frame', [bx - dw - 4, 40, zf], [bx + dw + 4, 43, zf + 3]);

// ramp from the door sill to the ground, with cross slats
{
  const z0 = 15, y0 = 19, z1 = 35, y1 = 1.5;
  const dz = z1 - z0, dy = y0 - y1, L = Math.hypot(dz, dy), a = (Math.atan2(dy, dz) * 180) / Math.PI;
  const q = quat('x', a), n = [0, dz / L, dy / L], d = [0, -dy / L, dz / L];
  const at = (f, off) => [bx, y0 + d[1] * L * f + n[1] * off, z0 + d[2] * L * f + n[2] * off];
  m.part('Ramp', 'ramp', at(0.5, -1), [12, 2, L], { q });
  for (let i = 0; i < 5; i++) m.part('Ramp_Slat' + i, 'frame', at(0.12 + i * 0.19, 1), [13, 2, 2], { q });
}

// nesting box on the right side, straw spilling out of its opening, lid tilted up
const nx0 = bx + hw, nx1 = nx0 + 12;
m.box('Nest', 'nest', [nx0, 24, -22], [nx1, 40, -2]);
m.box('Nest_Opening', 'inset', [nx1, 28, -19], [nx1 + 1, 37, -5]);
m.box('Nest_Straw', 'straw', [nx1 + 0.5, 27.5, -18], [nx1 + 2, 31, -6]);
for (let i = 0; i < 10; i++) {
  const z = -17.5 + i * 1.3, yaw = (i - 4.5) * 8, pitch = [12, -16, 26, -6, 18, -22, 6, 30, -10, 20][i];
  m.part('Straw_Tuft' + i, 'straw', [nx1 + 2.5, 30.5 + (i % 3) * 1.2, z], [8, 1, 2], { q: qmul(quat('y', yaw), quat('z', pitch)) });
}
{
  const a = 9, r = (a * Math.PI) / 180, hx = nx0 - 1, hy = 40;
  const lx = 16, lt = 3;
  m.part('Nest_Lid', 'nestlid', [hx + Math.cos(r) * lx / 2 - Math.sin(r) * lt / 2, hy + Math.sin(r) * lx / 2 + Math.cos(r) * lt / 2, -12], [lx, lt, 22], { q: quat('z', a) });
}

// rooster weathervane on the front of the ridge
const vz = 12;
m.box('Vane_Post', 'vane', [bx - 1, ridgeY + 9, vz - 1], [bx + 1, ridgeY + 17, vz + 1]);
m.box('Vane_ArmX', 'vane', [bx - 6, ridgeY + 12, vz - 0.5], [bx + 6, ridgeY + 13, vz + 0.5]);
m.box('Vane_ArmZ', 'vane', [bx - 0.5, ridgeY + 13.5, vz - 7], [bx + 0.5, ridgeY + 14.5, vz + 6]);
m.box('Vane_Head', 'vane', [bx - 1, ridgeY + 12.5, vz + 5], [bx + 1, ridgeY + 15.5, vz + 8]);
m.box('Vane_Fin', 'vane', [bx - 1, ridgeY + 12, vz - 9], [bx + 1, ridgeY + 16, vz - 6]);
const ry = ridgeY + 17;
m.box('Rooster_Body', 'rooster', [bx - 1.5, ry, vz - 4], [bx + 1.5, ry + 5, vz + 4]);
m.box('Rooster_Tail', 'rooster', [bx - 1, ry + 2, vz - 7], [bx + 1, ry + 9, vz - 3]);
m.box('Rooster_Head', 'rooster', [bx - 1, ry + 4, vz + 2], [bx + 1, ry + 9, vz + 6]);
m.box('Rooster_Comb', 'comb', [bx - 1, ry + 9, vz + 2], [bx + 1, ry + 11, vz + 5]);
m.box('Rooster_Wattle', 'comb', [bx - 0.5, ry + 3, vz + 5], [bx + 0.5, ry + 5, vz + 7]);
m.box('Rooster_Beak', 'comb', [bx - 0.5, ry + 6, vz + 6], [bx + 0.5, ry + 7, vz + 8]);

const mats = {
  stilt: planks('#73370E', { bw: 7, dir: 'v', groove: 0, tone: 3 }),
  sill: planks('#7A3E16', { bw: 6, dir: 'h', topDir: 'z' }),
  wall: planks('#B97A32', { bw: 7, dir: 'h', topDir: 'x', joints: 70 }),
  post: planks('#9C6226', { bw: 4, dir: 'v', groove: 0, tone: 2 }),
  frame: planks('#8A5424', { bw: 3, dir: 'v', topDir: 'x', groove: 0 }),
  ramp: planks('#A86E30', { bw: 4, dir: 'h', topDir: 'z' }),
  shingle: shingles('#A64B35', { rowAxis: 'u', rowH: 10, tileW: 9 }),
  ridge: shingles('#8E3F2C', { rowAxis: 'v', rowH: 9, tileW: 7 }),
  inset: inset('#1A1F2C'),
  nest: planks('#B07434', { bw: 5, dir: 'h', topDir: 'z' }),
  nestlid: planks('#A06A2E', { bw: 5, dir: 'h', topDir: 'z' }),
  straw: straw('#D9AE55', { along: 'u' }),
  vane: paint('#754F27', { amp: 2 }),
  rooster: paint('#8A5C2C', { amp: 2.5, scale: 3 }),
  comb: paint('#B23A2A', { amp: 2, scale: 3 }),
};

const r = build(m, mats, {
  outModel: `${RES}/Common/Blocks/CozyRanch/Houses/ChickenCoop/ChickenCoop.blockymodel`,
  outTexture: `${RES}/Common/Blocks/CozyRanch/Houses/ChickenCoop/ChickenCoop_Texture.png`,
  width: 256,
});
report('ChickenCoop', r);
