// Shared builder for the models-a houses: boxes -> .blockymodel, per-face painted atlas -> PNG.
// Units: 32 per block, origin at the footprint's bottom centre (the root node carries the cell offset).
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire('C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleMCP/package.json');
const { PNG } = require('pngjs');

// ---------- colour ----------
const s2l = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const l2s = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);
export function hexToLab(hex) {
  const n = parseInt(hex.replace('#', ''), 16);
  const r = s2l(((n >> 16) & 255) / 255), g = s2l(((n >> 8) & 255) / 255), b = s2l((n & 255) / 255);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s];
}
export function labToRgb([L, a, b]) {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  const r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
  const g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
  const bb = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s;
  // never pure black or white
  return [r, g, bb].map((c) => Math.max(6, Math.min(249, Math.round(l2s(Math.max(0, Math.min(1, c))) * 255))));
}
// Shift lightness by dL (in L* units); darker shades turn cooler (toward blue-purple) and keep chroma.
export function shade(lab, dL) {
  const d = dL / 100;
  if (d >= 0) return [lab[0] + d, lab[1] - d * 0.02, lab[2] + d * 0.06];
  const k = -d;
  return [lab[0] + d, lab[1] + k * 0.05, lab[2] - k * 0.24];
}
export const mix = (p, q, t) => [p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, p[2] + (q[2] - p[2]) * t];

// ---------- noise ----------
export function hash(i, j, seed = 0) {
  let h = (i * 374761393 + j * 668265263 + seed * 2246822519) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
const sm = (t) => t * t * (3 - 2 * t);
export function vnoise(x, y, seed = 0) {
  const i = Math.floor(x), j = Math.floor(y), fx = sm(x - i), fy = sm(y - j);
  const a = hash(i, j, seed), b = hash(i + 1, j, seed), c = hash(i, j + 1, seed), d = hash(i + 1, j + 1, seed);
  return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
}
// fbm in [-1,1]
export function fbm(x, y, seed = 0) {
  return (vnoise(x, y, seed) * 0.65 + vnoise(x * 2.1 + 17, y * 2.1 + 5, seed + 1) * 0.35) * 2 - 1;
}
export const smoothstep = (e0, e1, x) => sm(Math.max(0, Math.min(1, (x - e0) / (e1 - e0))));

// ---------- quaternions ----------
export function quat(axis, deg) {
  const r = (deg * Math.PI) / 360, s = Math.sin(r);
  const v = { x: [1, 0, 0], y: [0, 1, 0], z: [0, 0, 1] }[axis];
  return [v[0] * s, v[1] * s, v[2] * s, Math.cos(r)];
}
export function qmul(a, b) {
  return [a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
    a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
    a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
    a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]];
}
export function qrot(q, v) {
  const [x, y, z, w] = q;
  const u = [x, y, z];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const t = cross(u, v).map((c) => 2 * c);
  const c2 = cross(u, t);
  return [v[0] + w * t[0] + c2[0], v[1] + w * t[1] + c2[1], v[2] + w * t[2] + c2[2]];
}

// ---------- model ----------
const FACES = ['front', 'back', 'left', 'right', 'top', 'bottom'];
export class Model {
  constructor(name, rootOffset) { this.name = name; this.root = rootOffset; this.parts = []; }
  // axis-aligned box from min/max corners
  box(name, mat, min, max, opt = {}) {
    const c = [0, 1, 2].map((i) => (min[i] + max[i]) / 2), s = [0, 1, 2].map((i) => max[i] - min[i]);
    return this.part(name, mat, c, s, opt);
  }
  // box by centre and size, optional rotation quaternion q, mirror x
  part(name, mat, c, s, opt = {}) {
    if (this.parts.some((p) => p.name === name)) throw new Error('duplicate part name ' + name);
    const whole = s.map((v) => Math.max(1, Math.round(v)));
    const stretch = s.map((v, i) => +(v / whole[i]).toFixed(4));
    if (opt.mirrorX) stretch[0] = -stretch[0];
    for (const st of stretch) if (Math.abs(st) < 0.5 || Math.abs(st) > 2) throw new Error(`stretch out of range on ${name}: ${stretch}`);
    const p = { name, mat, c, s, whole, stretch, q: opt.q || [0, 0, 0, 1], faces: opt.faces || FACES, opt };
    this.parts.push(p);
    return p;
  }
}

// ---------- atlas ----------
function faceDims(p, f) {
  const [x, y, z] = p.whole;
  if (f === 'front' || f === 'back') return [x, y];
  if (f === 'left' || f === 'right') return [z, y];
  return [x, z];
}
const kindOf = (f) => (f === 'top' ? 'top' : f === 'bottom' ? 'bottom' : 'side');

export function build(model, materials, { outModel, outTexture, width = 256, lightScale = 0.6 }) {
  // collect unique face rects
  const rects = new Map();
  const faceRect = [];
  for (const p of model.parts) {
    for (const f of p.faces) {
      const [W, H] = faceDims(p, f);
      const kind = kindOf(f);
      const mat = materials[p.mat];
      if (!mat) throw new Error('no material ' + p.mat);
      const variant = p.opt.unique ? p.name + ':' + f : (p.opt.variant ?? 0);
      const key = [p.mat, W, H, kind, f === 'front' || f === 'back' ? 'fb' : f === 'left' || f === 'right' ? 'lr' : 'y', variant, p.opt.light ?? ''].join('|');
      if (!rects.has(key)) rects.set(key, { key, W, H, kind, face: f, mat: p.mat, part: p, seed: rects.size * 7 + 3 });
      faceRect.push([p, f, rects.get(key)]);
    }
  }
  // shelf pack with 1 texel padding
  const list = [...rects.values()].sort((a, b) => b.H - a.H || b.W - a.W);
  let x = 0, y = 0, rowH = 0;
  for (const r of list) {
    const w = r.W + 2, h = r.H + 2;
    if (w > width) throw new Error('face wider than atlas: ' + r.key);
    if (x + w > width) { x = 0; y += rowH; rowH = 0; }
    r.x = x + 1; r.y = y + 1; x += w; rowH = Math.max(rowH, h);
  }
  const height = Math.max(32, Math.ceil((y + rowH) / 32) * 32);
  const img = new Float32Array(width * height * 4);
  const filled = new Uint8Array(width * height);
  for (const r of list) {
    const mat = materials[r.mat];
    const painter = mat.paint({ W: r.W, H: r.H, kind: r.kind, face: r.face, seed: r.seed, part: r.part });
    const L = (r.part.opt.light ?? mat.light ?? 1) * lightScale;
    for (let v = 0; v < r.H; v++) for (let u = 0; u < r.W; u++) {
      let [lab, alpha = 1] = painter(u, v);
      let dL = 0;
      if (r.kind === 'side') {
        const t = r.H > 1 ? v / (r.H - 1) : 0.5;
        dL += (2.5 - 5 * t) * L;
        if (v === 0 && r.H > 3) dL += 3 * L;
        if (v === r.H - 1 && r.H > 3) dL -= 2 * L;
        const ao = r.part.opt.ao ?? mat.ao ?? 3;
        if (ao && r.H > ao * 2) dL -= 7 * L * smoothstep(r.H - ao - 1, r.H - 1, v);
      } else if (r.kind === 'top') dL += 2.5 * L;
      else dL -= 5 * L;
      lab = shade(lab, dL);
      const rgb = labToRgb(lab);
      const i = ((r.y + v) * width + r.x + u) * 4;
      img[i] = rgb[0]; img[i + 1] = rgb[1]; img[i + 2] = rgb[2]; img[i + 3] = Math.round(alpha * 255);
      filled[(r.y + v) * width + r.x + u] = 1;
    }
    // dilate edges into padding
    for (let v = -1; v <= r.H; v++) for (let u = -1; u <= r.W; u++) {
      if (u >= 0 && u < r.W && v >= 0 && v < r.H) continue;
      const su = Math.max(0, Math.min(r.W - 1, u)), sv = Math.max(0, Math.min(r.H - 1, v));
      const si = ((r.y + sv) * width + r.x + su) * 4, di = ((r.y + v) * width + r.x + u) * 4;
      for (let k = 0; k < 4; k++) img[di + k] = img[si + k];
    }
  }
  // fill the unused atlas with a neutral mid colour from the first material (keeps alpha-fringe checks quiet)
  const png = new PNG({ width, height });
  for (let i = 0; i < width * height; i++) {
    const o = i * 4;
    const used = img[o + 3] > 0 || filled[i];
    png.data[o] = used ? img[o] : 120; png.data[o + 1] = used ? img[o + 1] : 100; png.data[o + 2] = used ? img[o + 2] : 80;
    png.data[o + 3] = used ? img[o + 3] : 0;
  }
  fs.mkdirSync(path.dirname(outTexture), { recursive: true });
  fs.writeFileSync(outTexture, PNG.sync.write(png));

  // nodes
  let id = 1;
  const layoutOf = (r) => ({ offset: { x: r.x, y: r.y }, mirror: { x: false, y: false }, angle: 0 });
  const children = model.parts.map((p) => {
    const tl = {};
    for (const [pp, f, r] of faceRect) if (pp === p) tl[f] = layoutOf(r);
    return {
      id: String(++id), name: p.name, children: [],
      position: { x: +p.c[0].toFixed(4), y: +p.c[1].toFixed(4), z: +p.c[2].toFixed(4) },
      orientation: { x: +p.q[0].toFixed(6), y: +p.q[1].toFixed(6), z: +p.q[2].toFixed(6), w: +p.q[3].toFixed(6) },
      shape: {
        type: 'box', offset: { x: 0, y: 0, z: 0 },
        stretch: { x: p.stretch[0], y: p.stretch[1], z: p.stretch[2] },
        settings: { size: { x: p.whole[0], y: p.whole[1], z: p.whole[2] } },
        visible: true, doubleSided: false, shadingMode: p.opt.shading || 'standard', unwrapMode: 'custom', textureLayout: tl,
      },
    };
  });
  const root = {
    id: '1', name: model.name, children,
    position: { x: model.root[0], y: model.root[1], z: model.root[2] }, orientation: { x: 0, y: 0, z: 0, w: 1 },
    shape: { type: 'none', offset: { x: 0, y: 0, z: 0 }, stretch: { x: 1, y: 1, z: 1 }, settings: { isPiece: false }, visible: true, doubleSided: false, shadingMode: 'standard', unwrapMode: 'custom', textureLayout: {} },
  };
  fs.mkdirSync(path.dirname(outModel), { recursive: true });
  fs.writeFileSync(outModel, JSON.stringify({ nodes: [root], lod: 'auto' }, null, 2));
  return { nodes: children.length + 1, width, height, rects: rects.size, report: check(model) };
}

// ---------- checks: fractional sizes, coplanar same-facing overlaps among axis-aligned parts ----------
export function check(model) {
  const frac = model.parts.filter((p) => p.whole.some((v) => !Number.isInteger(v)));
  const aa = model.parts.filter((p) => Math.abs(p.q[3]) > 0.99999);
  const ext = (p) => p.c.map((c, i) => [c - (p.s[i]) / 2, c + (p.s[i]) / 2]);
  const fights = [];
  for (let i = 0; i < aa.length; i++) for (let j = i + 1; j < aa.length; j++) {
    const A = ext(aa[i]), B = ext(aa[j]);
    for (let ax = 0; ax < 3; ax++) for (const side of [0, 1]) {
      if (Math.abs(A[ax][side] - B[ax][side]) > 0.05) continue;
      const fname = [['left', 'right'], ['bottom', 'top'], ['back', 'front']][ax][side];
      if (!aa[i].faces.includes(fname) || !aa[j].faces.includes(fname)) continue;
      const o = [0, 1, 2].filter((k) => k !== ax).map((k) => Math.min(A[k][1], B[k][1]) - Math.max(A[k][0], B[k][0]));
      if (o[0] > 0.01 && o[1] > 0.01) fights.push(`${aa[i].name} / ${aa[j].name} axis ${'xyz'[ax]}${side ? '+' : '-'} @ ${A[ax][side].toFixed(2)}`);
    }
  }
  return { fractional: frac.map((p) => p.name), zfight: fights };
}
