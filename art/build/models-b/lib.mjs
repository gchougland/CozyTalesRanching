// Shared generator for the models-b block models: a node tree of boxes in block-model units
// (32 per block, origin bottom centre), written as a .blockymodel with whole part sizes plus
// stretch, and a painted atlas at one texel per unit.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire('C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleMCP/package.json');
const { PNG } = require('pngjs');

// ---------- vectors and quaternions ----------
export const v3 = (x, y, z) => [x, y, z];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
export const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const norm = (a) => { const l = Math.hypot(...a) || 1; return mul(a, 1 / l); };
export function qAxis(axis, deg) {
  const r = (deg * Math.PI) / 360;
  const s = Math.sin(r);
  const a = { x: [1, 0, 0], y: [0, 1, 0], z: [0, 0, 1] }[axis];
  return [a[0] * s, a[1] * s, a[2] * s, Math.cos(r)];
}
export function qMul(a, b) {
  const [ax, ay, az, aw] = a, [bx, by, bz, bw] = b;
  return [
    aw * bx + ax * bw + ay * bz - az * by,
    aw * by - ax * bz + ay * bw + az * bx,
    aw * bz + ax * by - ay * bx + az * bw,
    aw * bw - ax * bx - ay * by - az * bz,
  ];
}
/** Rotations applied in the order given, e.g. rot('x', 20, 'y', 45): first about x, then y. */
export function rot(...args) {
  let q = [0, 0, 0, 1];
  for (let i = 0; i < args.length; i += 2) q = qMul(qAxis(args[i], args[i + 1]), q);
  return q;
}
export function qRot(q, v) {
  const [x, y, z, w] = q;
  const u = [x, y, z];
  const t = mul([u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]], 2);
  const c = [u[1] * t[2] - u[2] * t[1], u[2] * t[0] - u[0] * t[2], u[0] * t[1] - u[1] * t[0]];
  return add(add(v, mul(t, w)), c);
}

// ---------- the node tree ----------
/**
 * box(name, centre, size, mat, opts): centre is relative to the parent's box centre in the
 * parent's frame (world for roots). size may be fractional; it is written as a whole size plus stretch.
 * opts: { q: quaternion, children: [...], faces: [...], tag, data }
 */
export function box(name, centre, size, mat, opts = {}) {
  return { name, centre, size, mat, q: opts.q || [0, 0, 0, 1], children: opts.children || [],
    faces: opts.faces || ['front', 'back', 'left', 'right', 'top', 'bottom'], tag: opts.tag, data: opts.data || {} };
}

/** A slab lying along a 2D segment in the X-Y plane (a roof plane), outward normal away from `inside`. */
export function slabXY(name, p0, p1, { ext0 = 0, ext1 = 0, thick, depth, z = 0, inside = [0, 0] }, mat, opts = {}) {
  let a = p0, b = p1;
  let d = norm([b[0] - a[0], b[1] - a[1], 0]);
  let n = [-d[1], d[0], 0];
  const mid0 = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, 0];
  if (dot(n, sub(mid0, [inside[0], inside[1], 0])) < 0) { [a, b] = [b, a]; [ext0, ext1] = [ext1, ext0]; d = mul(d, -1); n = mul(n, -1); }
  const A = sub([a[0], a[1], 0], mul(d, ext0));
  const B = add([b[0], b[1], 0], mul(d, ext1));
  const L = Math.hypot(B[0] - A[0], B[1] - A[1]);
  const mid = add(mul(add(A, B), 0.5), mul(n, thick / 2));
  const deg = (Math.atan2(d[1], d[0]) * 180) / Math.PI;
  return box(name, [mid[0], mid[1], z], [L, thick, depth], mat, { ...opts, q: qAxis('z', deg),
    data: { ...(opts.data || {}), slope: d[1] >= 0 ? d : mul(d, -1) } });
}

/** Same idea in the Z-Y plane (a slab whose slope runs along Z), centred on x. */
export function slabZY(name, p0, p1, { ext0 = 0, ext1 = 0, thick, width, x = 0, inside = [0, 0] }, mat, opts = {}) {
  // p are [z, y]
  let a = p0, b = p1;
  let d = norm([b[0] - a[0], b[1] - a[1]]);
  let n = [-d[1], d[0]];
  const m0 = [(a[0] + b[0]) / 2 - inside[0], (a[1] + b[1]) / 2 - inside[1]];
  if (n[0] * m0[0] + n[1] * m0[1] < 0) { [a, b] = [b, a]; [ext0, ext1] = [ext1, ext0]; d = [-d[0], -d[1]]; n = [-n[0], -n[1]]; }
  const A = [a[0] - d[0] * ext0, a[1] - d[1] * ext0];
  const B = [b[0] + d[0] * ext1, b[1] + d[1] * ext1];
  const L = Math.hypot(B[0] - A[0], B[1] - A[1]);
  const mz = (A[0] + B[0]) / 2 + n[0] * thick / 2, my = (A[1] + B[1]) / 2 + n[1] * thick / 2;
  // local X along world X, local Z along the slope: rotate about X by -angle
  const deg = -(Math.atan2(d[1], d[0]) * 180) / Math.PI;
  const slope = d[1] >= 0 ? [0, d[1], d[0]] : [0, -d[1], -d[0]];
  return box(name, [x, my, mz], [width, thick, L], mat, { ...opts, q: qAxis('x', deg), data: { ...(opts.data || {}), slope } });
}

// ---------- colour ----------
function srgbToLin(c) { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; }
function linToSrgb(c) { const v = c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055; return Math.max(0, Math.min(255, Math.round(v * 255))); }
export function hexLab(hex) {
  const h = hex.replace('#', '');
  const r = srgbToLin(parseInt(h.slice(0, 2), 16)), g = srgbToLin(parseInt(h.slice(2, 4), 16)), b = srgbToLin(parseInt(h.slice(4, 6), 16));
  const X = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, Y = 0.2126 * r + 0.7152 * g + 0.0722 * b, Z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
  const f = (t) => (t > 216 / 24389 ? Math.cbrt(t) : (24389 / 27 * t + 16) / 116);
  return [116 * f(Y) - 16, 500 * (f(X) - f(Y)), 200 * (f(Y) - f(Z))];
}
export function labRgb([L, a, b]) {
  const fy = (L + 16) / 116, fx = fy + a / 500, fz = fy - b / 200;
  const fi = (t) => (t ** 3 > 216 / 24389 ? t ** 3 : (116 * t - 16) / (24389 / 27));
  const X = fi(fx) * 0.95047, Y = fi(fy), Z = fi(fz) * 1.08883;
  const r = 3.2406 * X - 1.5372 * Y - 0.4986 * Z, g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z, bb = 0.0557 * X - 0.204 * Y + 1.057 * Z;
  return [linToSrgb(r), linToSrgb(g), linToSrgb(bb)];
}
/** Lighten/darken by dL in L*; darkening also turns the colour cooler (toward blue-purple) but keeps chroma. */
export function shade(lab, dL) {
  const [L, a, b] = lab;
  if (dL >= 0) return [L + dL, a, b + dL * 0.12];
  const k = -dL;
  return [L - k, a + k * 0.12, b - k * 0.38];
}
export const mixLab = (p, q, t) => [p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, p[2] + (q[2] - p[2]) * t];

// ---------- noise ----------
function hash(ix, iy, seed = 0) {
  let h = (ix * 374761393 + iy * 668265263 + seed * 982451653) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
export const rnd = (i, seed = 0) => hash(i, 7919, seed);
export const rnd2 = (i, j, seed = 0) => hash(i, j, seed);
const sm = (t) => t * t * (3 - 2 * t);
/** Smooth value noise in [0,1) with cell sizes px, py (texels). */
export function vnoise(x, y, px, py = px, seed = 0) {
  const fx = x / px, fy = y / py;
  const ix = Math.floor(fx), iy = Math.floor(fy);
  const tx = sm(fx - ix), ty = sm(fy - iy);
  const a = hash(ix, iy, seed), b = hash(ix + 1, iy, seed), c = hash(ix, iy + 1, seed), d = hash(ix + 1, iy + 1, seed);
  return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
}
export function fbm(x, y, p, seed = 0, oct = 3) {
  let s = 0, w = 1, tw = 0;
  for (let i = 0; i < oct; i++) { s += vnoise(x, y, p, p, seed + i * 31) * w; tw += w; w *= 0.5; p /= 2; if (p < 2.5) break; }
  return s / tw;
}

// ---------- faces ----------
// Face table (research/model-formats.md A4): corners TL, TR, BL as fractions of the half size.
const FACE = {
  front: { tl: [-1, 1, 1], tr: [1, 1, 1], bl: [-1, -1, 1], dims: (s) => [s[0], s[1]], n: [0, 0, 1] },
  back: { tl: [1, 1, -1], tr: [-1, 1, -1], bl: [1, -1, -1], dims: (s) => [s[0], s[1]], n: [0, 0, -1] },
  right: { tl: [1, 1, 1], tr: [1, 1, -1], bl: [1, -1, 1], dims: (s) => [s[2], s[1]], n: [1, 0, 0] },
  left: { tl: [-1, 1, -1], tr: [-1, 1, 1], bl: [-1, -1, -1], dims: (s) => [s[2], s[1]], n: [-1, 0, 0] },
  top: { tl: [-1, 1, -1], tr: [1, 1, -1], bl: [-1, 1, 1], dims: (s) => [s[0], s[2]], n: [0, 1, 0] },
  bottom: { tl: [-1, -1, 1], tr: [1, -1, 1], bl: [-1, -1, -1], dims: (s) => [s[0], s[2]], n: [0, -1, 0] },
};

function whole(size) {
  const w = size.map((s) => Math.max(1, Math.round(s)));
  const st = size.map((s, i) => s / w[i]);
  return { w, st };
}

function flatten(roots) {
  const out = [];
  const walk = (n, pc, pq, parent) => {
    const wq = qMul(pq, n.q);
    const wc = add(pc, qRot(pq, n.centre));
    const rec = { n, wq, wc, parent, ...whole(n.size) };
    out.push(rec);
    for (const c of n.children) walk(c, wc, wq, rec);
  };
  for (const r of roots) walk(r, [0, 0, 0], [0, 0, 0, 1], null);
  return out;
}

// ---------- atlas packing ----------
function pack(rects, pad) {
  const sorted = [...rects].sort((a, b) => b.h - a.h || b.w - a.w);
  const area = sorted.reduce((s, r) => s + (r.w + 2 * pad) * (r.h + 2 * pad), 0);
  const maxW = Math.max(...sorted.map((r) => r.w + 2 * pad));
  let best = null;
  for (let W = Math.max(64, Math.ceil(maxW / 32) * 32); W <= 2048; W += 32) {
    let x = 0, y = 0, rowH = 0;
    const pos = [];
    for (const r of sorted) {
      const rw = r.w + 2 * pad, rh = r.h + 2 * pad;
      if (x + rw > W) { x = 0; y += rowH; rowH = 0; }
      pos.push([r, x + pad, y + pad]);
      x += rw; rowH = Math.max(rowH, rh);
    }
    const H = Math.ceil((y + rowH) / 32) * 32;
    const score = W * H + Math.abs(W - H) * 8;
    if (!best || score < best.score) best = { W, H, pos, score };
    if (W * W > area * 6) break;
  }
  for (const [r, x, y] of best.pos) { r.x = x; r.y = y; }
  return { W: best.W, H: best.H };
}

// ---------- lighting ----------
/** The light painted on top of a material: from above, half strength (standard shading adds facing). */
function light(lab, ctx, extraAo) {
  const { wn, p, yMin, yMax } = ctx;
  let dL = 0;
  if (wn[1] > 0.7) dL += 2;
  else if (wn[1] < -0.7) dL -= 5;
  else {
    const span = yMax - yMin;
    if (span > 2) {
      const t = (p[1] - yMin) / span; // 0 bottom, 1 top
      dL += (t - 0.5) * 4.5;
      if (yMax - p[1] < 1) dL += 3;
      if (p[1] - yMin < 1) dL -= 2;
    }
  }
  if (extraAo) dL += extraAo(p, wn, ctx) || 0;
  return shade(lab, dL);
}

// ---------- build ----------
/**
 * build({ roots, materials, out: {model, texture}, ao, shift, filterTag })
 * materials: name -> (ctx) => Lab. ctx: { p (world point), lp (local point), wn (world normal), face,
 *   a, b, W, H, ud, vd (world unit dirs of +u, +v on the face), u, v (dot(p, ud), dot(p, vd)), node, rec }.
 * Returns { nodes, W, H, layout } and writes the files. Pass `atlasFrom` (an earlier result) to emit a
 * subset of a model against an atlas already painted.
 */
export function build({ roots, materials, modelPath, texturePath, ao, shift = [0, 0, 0], pad = 1, exclude = null, atlasFrom = null }) {
  const recs = flatten(roots);
  let rects, W, H;
  if (atlasFrom) {
    ({ W, H } = atlasFrom);
    rects = atlasFrom.rects;
  } else {
    rects = [];
    for (const rec of recs) {
      for (const face of rec.n.faces) {
        const [fw, fh] = FACE[face].dims(rec.w);
        rects.push({ rec, face, w: fw, h: fh, key: `${recs.indexOf(rec)}:${face}` });
      }
    }
    ({ W, H } = pack(rects, pad));
  }
  const byKey = new Map(rects.map((r) => [r.key, r]));

  if (!atlasFrom && texturePath) {
    const png = new PNG({ width: W, height: H });
    png.data.fill(0);
    for (const r of rects) paintRect(png, r, materials, ao, pad);
    fs.mkdirSync(path.dirname(texturePath), { recursive: true });
    fs.writeFileSync(texturePath, PNG.sync.write(png));
  }

  // emit
  let id = 0;
  let count = 0;
  const seen = new Map();
  const uniqueName = (name) => {
    const k = (seen.get(name) || 0) + 1;
    seen.set(name, k);
    return k === 1 ? name : `${name}${k}`;
  };
  const emit = (n, recIndex, isRoot) => {
    const rec = recs[recIndex];
    const layout = {};
    for (const face of n.faces) {
      const r = byKey.get(`${recIndex}:${face}`);
      layout[face] = { offset: { x: r.x, y: r.y }, mirror: { x: false, y: false }, angle: 0 };
    }
    const pos = isRoot ? add(n.centre, shift) : n.centre;
    count++;
    const node = {
      id: String(++id), name: uniqueName(n.name),
      position: { x: r6(pos[0]), y: r6(pos[1]), z: r6(pos[2]) },
      orientation: { x: r6(n.q[0]), y: r6(n.q[1]), z: r6(n.q[2]), w: r6(n.q[3]) },
      shape: {
        type: 'box', offset: { x: 0, y: 0, z: 0 },
        stretch: { x: r6(rec.st[0]), y: r6(rec.st[1]), z: r6(rec.st[2]) },
        settings: { size: { x: rec.w[0], y: rec.w[1], z: rec.w[2] } },
        textureLayout: layout, unwrapMode: 'custom', visible: true, doubleSided: false, shadingMode: 'standard',
      },
      children: [],
    };
    return node;
  };
  // walk the tree in the same order as flatten so indexes line up
  let idx = 0;
  const walk = (n, isRoot) => {
    const my = idx++;
    const node = emit(n, my, isRoot);
    for (const c of n.children) {
      const child = walk(c, false);
      if (child && !(exclude && exclude(c))) node.children.push(child);
    }
    return node;
  };
  const nodes = [];
  for (const r of roots) { const nn = walk(r, true); if (!(exclude && exclude(r))) nodes.push(nn); }
  let kept = 0;
  const countKept = (list) => { for (const n of list) { kept++; countKept(n.children); } };
  countKept(nodes);
  const doc = { nodes, format: 'prop', lod: 'auto' };
  fs.mkdirSync(path.dirname(modelPath), { recursive: true });
  fs.writeFileSync(modelPath, JSON.stringify(doc, null, 2));
  return { W, H, rects, nodeCount: kept };
}
const r6 = (x) => Math.round(x * 1e6) / 1e6;

function paintRect(png, r, materials, ao, pad) {
  const { rec, face } = r;
  const F = FACE[face];
  const half = rec.w.map((s, i) => (s * rec.st[i]) / 2);
  const corner = (c) => [c[0] * half[0], c[1] * half[1], c[2] * half[2]];
  const TL = corner(F.tl), TR = corner(F.tr), BL = corner(F.bl);
  const toW = (lp) => add(rec.wc, qRot(rec.wq, lp));
  const wTL = toW(TL), wTR = toW(TR), wBL = toW(BL);
  const ud = norm(sub(wTR, wTL)), vd = norm(sub(wBL, wTL));
  const wn = norm(qRot(rec.wq, F.n));
  const BR = add(wTR, sub(wBL, wTL));
  const ys = [wTL[1], wTR[1], wBL[1], BR[1]];
  const yMin = Math.min(...ys), yMax = Math.max(...ys);
  const mat = materials[typeof rec.n.mat === 'string' ? rec.n.mat : rec.n.mat[face] || rec.n.mat.default];
  if (!mat) throw new Error(`no material ${JSON.stringify(rec.n.mat)} for ${rec.n.name}`);
  for (let b = -pad; b < r.h + pad; b++) {
    for (let a = -pad; a < r.w + pad; a++) {
      const ca = Math.min(r.w - 1, Math.max(0, a)), cb = Math.min(r.h - 1, Math.max(0, b));
      const fa = (ca + 0.5) / r.w, fb = (cb + 0.5) / r.h;
      const lp = add(add(TL, mul(sub(TR, TL), fa)), mul(sub(BL, TL), fb));
      const p = toW(lp);
      const ctx = { p, lp, wn, face, a: ca, b: cb, W: r.w, H: r.h, ud, vd, u: dot(p, ud), v: dot(p, vd), node: rec.n, rec, yMin, yMax, half };
      const lab = light(mat(ctx), ctx, ao);
      const [R, G, B] = labRgb(lab);
      const x = r.x + a, y = r.y + b;
      if (x < 0 || y < 0 || x >= png.width || y >= png.height) continue;
      const o = (y * png.width + x) * 4;
      png.data[o] = R; png.data[o + 1] = G; png.data[o + 2] = B; png.data[o + 3] = 255;
    }
  }
}

/** Checks every size is whole and counts nodes. */
export function audit(modelPath) {
  const doc = JSON.parse(fs.readFileSync(modelPath, 'utf8'));
  let n = 0, frac = 0, badStretch = 0;
  const walk = (list) => {
    for (const x of list) {
      n++;
      const s = x.shape.settings.size;
      for (const k of ['x', 'y', 'z']) if (s[k] !== undefined && s[k] !== Math.round(s[k])) frac++;
      for (const k of ['x', 'y', 'z']) { const v = Math.abs(x.shape.stretch[k]); if (v < 0.5 || v > 2) badStretch++; }
      walk(x.children || []);
    }
  };
  walk(doc.nodes);
  return { nodes: n, fractionalSizes: frac, stretchOutside: badStretch };
}
