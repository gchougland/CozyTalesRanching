// Frog Pond: 3 x 3 footprint, 2 tall. Run: node frogpond.mjs
import { Model, build, quat, qmul, qrot } from './lib.mjs';
import { paint, cobble, water, spotted, planks, inset } from './paint.mjs';
import { RES, report } from './common.mjs';

const m = new Model('FrogPond', [0, 0, 0]);
const rimH = 18, rimT = 11, O = 48, I = O - rimT, waterY = 14;

// mossy cobble rim, basin floor, translucent water surface (top face only)
m.box('Rim_Front', 'rim', [-O, 0, I], [O, rimH, O]);
m.box('Rim_Back', 'rim', [-O, 0, -O], [O, rimH, -I]);
m.box('Rim_Left', 'rim', [-O, 0, -I], [-I, rimH, I]);
m.box('Rim_Right', 'rim', [I, 0, -I], [O, rimH, I]);
m.box('Basin', 'basin', [-I, 0, -I], [I, 3, I]);
m.box('Water', 'water', [-I, waterY - 1, -I], [I, waterY, I], { faces: ['top'] });

// toadstool hut on a stone footing in the back left corner: the frogs' closed house with a little door
const tx = -25, tz = -25;
m.box('Toad_Footing', 'rim', [-I - 1, 1, -I - 1], [-12, rimH - 1, -12], { variant: 'f' });
const capY = rimH + 23;
m.box('Toad_StemLow', 'stem', [tx - 11, rimH - 1, tz - 11], [tx + 11, rimH + 14, tz + 11]);
m.box('Toad_StemHigh', 'stem', [tx - 9, rimH + 14, tz - 9], [tx + 9, capY + 1, tz + 9]);
m.box('Toad_CapLow', 'cap', [tx - 18, capY, tz - 18], [tx + 18, capY + 8, tz + 18], { unique: true });
m.box('Toad_CapHigh', 'cap', [tx - 13, capY + 8, tz - 13], [tx + 13, capY + 16, tz + 13], { unique: true });
m.box('Toad_CapUnder', 'gills', [tx - 17, capY - 1, tz - 17], [tx + 17, capY, tz + 17]);
{
  const z = tz + 11, y0 = rimH - 1;
  m.box('Toad_Door', 'inset', [tx - 5, y0, z], [tx + 5, y0 + 13, z + 1]);
  m.box('Toad_DoorJambL', 'frame', [tx - 7, y0, z], [tx - 5, y0 + 13, z + 2]);
  m.box('Toad_DoorJambR', 'frame', [tx + 5, y0, z], [tx + 7, y0 + 13, z + 2]);
  m.box('Toad_DoorLintel', 'frame', [tx - 7.5, y0 + 13, z], [tx + 7.5, y0 + 15, z + 2.5]);
  m.box('Toad_Step', 'step', [tx - 6, y0, z + 2.5], [tx + 6, y0 + 1, z + 5]);
}

// notched square lily pads; one carries a flower
const pads = [[-22, 10, 16, 20], [2, -18, 14, -15], [12, 6, 14, 35], [26, 22, 12, -30]];
pads.forEach(([x, z, w, yaw], i) => {
  const q = quat('y', yaw), y = waterY + 0.5;
  const put = (name, lx0, lz0, lx1, lz1) => {
    const lc = [(lx0 + lx1) / 2, 0, (lz0 + lz1) / 2], r = qrot(q, lc);
    m.part(`Lily${i}_${name}`, 'lily', [x + r[0], y + 0.5, z + r[2]], [lx1 - lx0, 1, lz1 - lz0], { q, variant: name });
  };
  const h = w / 2;
  put('Back', -h, -h, h, 0);
  put('FrontL', -h, 0, -1, h);
  put('FrontR', 1, 0, h, h - 1);
  if (i === 2) {
    const fy = y + 1;
    m.box('Flower_Heart', 'flowerheart', [x - 1, fy, z - 3], [x + 1, fy + 3, z - 1]);
    for (const [n, dx, dz] of [['N', 0, -4], ['S', 0, 2], ['E', 3, -1], ['W', -3, -1]])
      m.part('Flower_Petal' + n, 'petal', [x + dx * 0.9, fy + 1, z - 2 + dz * 0.6], [dx ? 3 : 4, 2, dx ? 4 : 3], { q: quat('y', 45) });
  }
});

// flat stepping stone at the front, at water level
m.box('SteppingStone', 'stone', [-8, 3, I - 15], [8, waterY + 2, I]);

// cattail clump in the back right corner
const tails = [[28, -30, 34, 3, -4], [33, -26, 28, -5, 3], [24, -34, 38, 4, 5], [31, -33, 24, -3, -6], [36, -31, 31, 6, 2]];
tails.forEach(([x, z, L, ax, az], i) => {
  const q = qmul(quat('x', ax), quat('z', az)), base = [x, 3, z];
  const at = (d) => { const r = qrot(q, [0, d, 0]); return [base[0] + r[0], base[1] + r[1], base[2] + r[2]]; };
  m.part('Cattail_Stalk' + i, 'reed', at(L / 2), [2, L, 2], { q });
  m.part('Cattail_Head' + i, 'cattail', at(L + 3), [3, 7, 3], { q });
  m.part('Cattail_Tip' + i, 'reed', at(L + 8), [1, 3, 1], { q });
});
[[26, -27, 22, -14, 10], [34, -35, 26, 12, -12], [30, -24, 18, -8, 18], [37, -27, 20, 16, 8]].forEach(([x, z, L, ax, yaw], i) => {
  const q = qmul(quat('y', yaw), quat('x', ax)), r = qrot(q, [0, L / 2, 0]);
  m.part('Reed_Blade' + i, 'blade', [x + r[0], 3 + r[1], z + r[2]], [3, L, 1], { q });
});

const mats = {
  rim: cobble('#B39A6A', '#8C8E44', { moss: '#7C8A3A', deepMoss: '#4C6A2A', mortar: '#6E6248', rowH: 9, stoneW: 14, mossAmt: 0.55 }),
  basin: paint('#3E4A38', { amp: 4.5, scale: 5 }),
  water: water('#3C826E', '#5FA595', { alpha: 0.72 }),
  stem: paint('#D2B682', { amp: 4, scale: 5 }),
  cap: spotted('#D9592A', '#F3E6D2', { r0: 2.5, r1: 4.2, density: 60 }),
  gills: paint('#C9A574', { amp: 3.5 }),
  inset: inset('#1A1F2C'),
  frame: planks('#7A5432', { bw: 2, dir: 'v', groove: 0, joints: 200 }),
  step: paint('#A89A80', { amp: 3.5 }),
  lily: paint('#4F8424', { amp: 5, scale: 3 }),
  flowerheart: paint('#E3B23C', { amp: 3.5 }),
  petal: paint('#F1E6D6', { amp: 3 }),
  stone: paint('#B8AA90', { amp: 4.5, scale: 4 }),
  reed: paint('#6B8523', { amp: 4, scale: 3 }),
  cattail: paint('#81441B', { amp: 4.5, scale: 2.5 }),
  blade: paint('#5F8A2A', { amp: 4.5, scale: 3 }),
};

const r = build(m, mats, {
  outModel: `${RES}/Common/Blocks/CozyRanch/Houses/FrogPond/FrogPond.blockymodel`,
  outTexture: `${RES}/Common/Blocks/CozyRanch/Houses/FrogPond/FrogPond_Texture.png`,
  width: 256,
});
report('FrogPond', r);
