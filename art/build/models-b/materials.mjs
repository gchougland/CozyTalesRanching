// Painted materials. Each returns (ctx) => Lab before the shared light pass in lib.mjs.
// Grain comes in clusters of 2+ texels (smooth value noise), grooves are one texel 10-15 L* darker
// and cooler, and no colour reaches pure black or white.
import { hexLab, shade, mixLab, vnoise, fbm, rnd, rnd2, qRot, dot } from './lib.mjs';

const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
const isTop = (c) => c.wn[1] > 0.7;
const isBottom = (c) => c.wn[1] < -0.7;
const isSide = (c) => Math.abs(c.wn[1]) <= 0.7;
const tint = (lab, da, db) => [lab[0], lab[1] + da, lab[2] + db];

/** Longest axis of the box in world space, for wood grain. */
function grainAxis(c) {
  const s = c.rec.w.map((w, i) => w * Math.abs(c.rec.st[i]));
  const k = c.node.data.grain ?? (s[0] >= s[1] && s[0] >= s[2] ? 0 : s[1] >= s[2] ? 1 : 2);
  const e = [0, 0, 0]; e[k] = 1;
  return qRot(c.rec.wq, e);
}

/** Vertical boards (barn walls, door leaves); batten adds a raised strip over each seam. */
export function boards({ base, w = 8, batten = false, seed = 1, amp = 1 }) {
  const B = hexLab(base);
  return (c) => {
    const across = (isSide(c) ? c.u : c.p[0]) + 1024;
    const along = isSide(c) ? c.p[1] : c.p[2];
    const i = Math.floor(across / w), l = across - i * w;
    let dL = (rnd(i, seed) - 0.5) * 5 * amp;
    dL += (vnoise(across, along, 3, 16, seed + 3) - 0.5) * 5 * amp;
    dL += (fbm(across, along, 18, seed + 9) - 0.5) * 5 * amp;
    // a few darker streaks running along the board
    const streak = vnoise(across, along, 2, 30, seed + 17);
    if (streak > 0.78) dL -= (streak - 0.78) * 25 * amp;
    if (batten) {
      if (l < 2) dL += 3.5; else if (l < 3) dL -= 11;
    } else if (l < 1) dL -= 12;
    const col = tint(B, (rnd(i, seed + 5) - 0.5) * 3, (rnd(i, seed + 6) - 0.5) * 3);
    return shade(col, dL);
  };
}

/** Plain wood with grain along the part's length; end grain shows rings. */
export function wood({ base, seed = 2, amp = 1, ring = true }) {
  const B = hexLab(base);
  return (c) => {
    const ax = grainAxis(c);
    const along = dot(c.p, ax);
    const endGrain = Math.abs(dot(c.wn, ax)) > 0.7;
    if (endGrain && ring) {
      const lc = c.lp; // local point, centre of the part is 0
      const k = [0, 1, 2].filter((i) => Math.abs(dot(qRot(c.rec.wq, [i === 0, i === 1, i === 2].map(Number)), ax)) < 0.5);
      const r = Math.hypot(lc[k[0]], lc[k[1]]);
      const dL = 4 + Math.sin(r * 1.4) * 2.5 * amp + (fbm(c.a, c.b, 6, seed) - 0.5) * 3;
      return shade(tint(B, 0, 3), dL);
    }
    const across = Math.abs(dot(c.ud, ax)) > 0.7 ? c.v : c.u;
    let dL = (vnoise(across + 512, along + 512, 2.5, 18, seed) - 0.5) * 7 * amp;
    dL += (fbm(across + 512, along + 512, 14, seed + 4) - 0.5) * 5 * amp;
    const s = vnoise(across + 512, along + 512, 2, 26, seed + 11);
    if (s > 0.75) dL -= (s - 0.75) * 30 * amp;
    return shade(B, dL);
  };
}

/** Irregular fieldstones (barn plinth). */
export function fieldstone({ base, seed = 3, cw = 11, ch = 6, groove = -14 }) {
  const B = hexLab(base);
  return (c) => {
    const s = (isSide(c) ? c.u : c.p[0]) + 2048;
    const t = (isSide(c) ? -c.p[1] : c.p[2]) + 2048;
    const gy = Math.floor(t / ch);
    let d1 = 1e9, d2 = 1e9, id = null, pt = null;
    for (let jy = gy - 1; jy <= gy + 1; jy++) {
      const off = (jy % 2) * 0.5;
      const gx = Math.floor(s / cw - off);
      for (let jx = gx - 1; jx <= gx + 1; jx++) {
        const px = (jx + off + 0.2 + 0.6 * rnd2(jx, jy, seed)) * cw;
        const py = (jy + 0.25 + 0.5 * rnd2(jx, jy, seed + 1)) * ch;
        const d = Math.hypot((s - px) / cw * 6, (t - py) / ch * 6);
        if (d < d1) { d2 = d1; d1 = d; id = [jx, jy]; pt = [px, py]; } else if (d < d2) d2 = d;
      }
    }
    const edge = d2 - d1;
    const r = rnd2(id[0], id[1], seed + 2);
    let col = tint(B, (r - 0.5) * 3, (rnd2(id[0], id[1], seed + 3) - 0.5) * 5);
    let dL = (r - 0.5) * 9 + (fbm(s, t, 5, seed + 4) - 0.5) * 3;
    dL += clamp((pt[1] - t) / 2.5, -1, 1) * 3; // light on the upper part of each stone
    if (edge < 0.9) dL += groove;
    else if (edge < 1.8) dL += groove * 0.35;
    return shade(col, dL);
  };
}

/** Coursed dry stone with moss (sheepfold walls). */
export function drystone({ base, moss = '#6B7847', mossLight = '#8C9A4F', seed = 4, rowH = 7, mossAmount = 0.5 }) {
  const B = hexLab(base), M = hexLab(moss), ML = hexLab(mossLight);
  return (c) => {
    const s = (isSide(c) ? c.u : c.p[0] + c.p[2] * 0.37) + 2048;
    const t = (isSide(c) ? -c.p[1] : c.p[2]) + 2048;
    const row = Math.floor(t / rowH), lt = t - row * rowH;
    const L = 10 + Math.floor(rnd(row, seed) * 6);
    const ss = s + rnd(row, seed + 1) * 23;
    const j = Math.floor(ss / L), ls = ss - j * L;
    const r = rnd2(row, j, seed + 2);
    let col = tint(B, (r - 0.5) * 2, (rnd2(row, j, seed + 3) - 0.5) * 5);
    let dL = (r - 0.5) * 10 + (fbm(s, t, 5, seed + 4) - 0.5) * 3.5;
    let groove = false;
    if (lt < 1 || ls < 1) { dL -= 13; groove = true; }
    else if (lt < 2) dL += 4;
    else if (lt > rowH - 2) dL -= 3;
    const mossN = fbm(s, t, 9, seed + 7) + (groove ? 0.08 : 0) + (lt < 2.5 ? 0.06 : 0) + (isTop(c) ? 0.15 : 0);
    const thr = 1 - mossAmount * 0.75;
    let out = shade(col, dL);
    if (mossN > thr) {
      const k = clamp((mossN - thr) / 0.08, 0, 1);
      const mm = mixLab(M, ML, vnoise(s, t, 3, 3, seed + 8));
      out = mixLab(out, shade(mm, groove ? -6 : 0), k * 0.9);
    }
    return out;
  };
}

/** Mossy wood shingles. Slab tops use the slab's slope; other faces read as dark wood edges. */
export function shingles({ base, edge, moss = '#7D8A3E', seed = 5, course = 5, sw = 7, mossAmount = 0.45, mossScale = 11 }) {
  const B = hexLab(base), M = hexLab(moss), E = hexLab(edge || base);
  return (c) => {
    let s, r;
    const slope = c.node.data.slope;
    if (slope && c.face === 'top') {
      s = dot(c.p, slope);
      const across = Math.abs(dot(c.ud, slope)) > 0.7 ? c.vd : c.ud;
      r = dot(c.p, across);
    } else if (slope) {
      return shade(E, (vnoise(c.u, c.v, 3, 8, seed) - 0.5) * 4 - 2);
    } else if (isSide(c)) { s = c.p[1]; r = c.u; }
    else if (isTop(c)) {
      const cx = c.rec.wc[0], cz = c.rec.wc[2];
      s = -Math.max(Math.abs(c.p[0] - cx), Math.abs(c.p[2] - cz));
      r = Math.abs(c.p[0] - cx) > Math.abs(c.p[2] - cz) ? c.p[2] : c.p[0];
    } else return shade(E, -4);
    s += 1024; r += 1024;
    const ci = Math.floor(s / course), l = s - ci * course; // l: 0 at the downhill butt edge
    const rr = r + (ci % 2) * sw * 0.5 + rnd(ci, seed) * 3;
    const j = Math.floor(rr / sw), lj = rr - j * sw;
    const q = rnd2(ci, j, seed + 1);
    let col = tint(B, (q - 0.5) * 4, (rnd2(ci, j, seed + 2) - 0.5) * 5);
    let dL = (q - 0.5) * 9 + (vnoise(rr, s, 2, 6, seed + 3) - 0.5) * 4;
    dL += (1 - l / course) * 3; // butt end catches the light
    let groove = false;
    if (l > course - 1) { dL -= 12; groove = true; } // shadow under the course above
    if (lj < 1) { dL -= 9; groove = true; }
    let out = shade(col, dL);
    const mn = fbm(r, s, mossScale, seed + 6) + (groove ? 0.1 : 0);
    const thr = 1 - mossAmount * 0.8;
    if (mn > thr) {
      const k = clamp((mn - thr) / 0.1, 0, 1);
      out = mixLab(out, shade(M, (vnoise(r, s, 3, 3, seed + 7) - 0.5) * 6 + (groove ? -5 : 0)), k * 0.7);
    }
    return out;
  };
}

/** Thatch: straw strands running down the roof, dark fringe at each step's lower edge. */
export function thatch({ base, under = '#7D622F', seed = 6 }) {
  const B = hexLab(base), U = hexLab(under);
  return (c) => {
    if (isBottom(c)) return shade(U, (vnoise(c.u, c.v, 3, 6, seed) - 0.5) * 5);
    let r, s;
    if (isSide(c)) { r = c.u; s = c.p[1]; }
    else {
      const cx = c.rec.wc[0], cz = c.rec.wc[2];
      const dx = c.p[0] - cx, dz = c.p[2] - cz;
      const xs = Math.abs(dx) / (c.half[0] || 1), zs = Math.abs(dz) / (c.half[2] || 1);
      r = xs > zs ? c.p[2] : c.p[0];
      s = -(xs > zs ? Math.abs(dx) : Math.abs(dz));
    }
    r += 1024; s += 1024;
    const bundle = Math.floor(r / 4);
    let dL = (vnoise(r, s, 2, 9, seed) - 0.5) * 10 + (rnd(bundle, seed + 1) - 0.5) * 5 + (fbm(r, s, 14, seed + 2) - 0.5) * 4;
    const st = vnoise(r, s, 2, 14, seed + 3);
    if (st > 0.72) dL -= (st - 0.72) * 30;
    let col = tint(B, 0, (rnd(bundle, seed + 4) - 0.5) * 6);
    if (isSide(c)) {
      const fromBottom = c.p[1] - c.yMin;
      if (fromBottom < 1) dL -= 9; else if (fromBottom < 2) dL -= 4;
      if (c.yMax - c.p[1] < 1) dL += 3;
    }
    return shade(col, dL);
  };
}

/** Loose hay. */
export function hay({ base = '#E2B64E', seed = 7, contrast = 1 }) {
  const B = hexLab(base);
  return (c) => {
    const r = c.u * 0.8 + c.v * 0.6 + 1024, s = -c.u * 0.6 + c.v * 0.8 + 1024;
    let dL = (vnoise(r, s, 2, 7, seed) - 0.5) * 12 * contrast + (fbm(c.u + 99, c.v + 99, 8, seed + 1) - 0.5) * 6;
    const st = vnoise(s, r, 2, 9, seed + 2);
    if (st > 0.7) dL -= (st - 0.7) * 35 * contrast;
    return shade(tint(B, 0, (vnoise(r, s, 5, 5, seed + 3) - 0.5) * 8), dL);
  };
}

/** A hay bale: hay with two twine bands around the part's long axis. */
export function bale({ base = '#E8BE55', twine = '#8A5A33', seed = 8 }) {
  const H = hay({ base, seed, contrast: 0.8 }), T = hexLab(twine);
  return (c) => {
    const x = c.lp[0];
    if (!(c.face === 'left' || c.face === 'right') && Math.abs(Math.abs(x) - 3.5) < 1) {
      return shade(T, (vnoise(c.a, c.b, 2, 2, seed) - 0.5) * 6);
    }
    return H(c);
  };
}

export function wool({ base = '#EEEADF', seed = 9 }) {
  const B = hexLab(base);
  return (c) => {
    const dL = (vnoise(c.u + 64, c.v + 64, 3, 3, seed) - 0.5) * 7 + (fbm(c.u + 64, c.v + 64, 6, seed + 1) - 0.5) * 6 - 1;
    return shade(B, Math.min(dL, 2));
  };
}

/** The near-black-blue of a shallow doorway inset. */
export function dark({ base = '#1C2030', seed = 10 }) {
  const B = hexLab(base);
  return (c) => shade(B, (vnoise(c.u + 64, c.v + 64, 4, 4, seed) - 0.5) * 2.5 - (isSide(c) ? clamp((c.p[1] - c.yMin) / (c.yMax - c.yMin + 1e-6), 0, 1) * 2 : 0));
}

/** Brushed metal (churn, handles, straps) with a soft vertical highlight. */
export function metal({ base = '#AEB4B8', seed = 11 }) {
  const B = hexLab(base);
  return (c) => {
    const f = c.a / Math.max(1, c.W - 1);
    let dL = isSide(c) ? Math.exp(-((f - 0.32) ** 2) / 0.02) * 9 - Math.abs(f - 0.5) * 6 : 2;
    dL += (vnoise(c.a, c.b, 3, 6, seed) - 0.5) * 3;
    return shade(B, dL);
  };
}

/** Cream board with black cow spots on its front face; the rest is the frame wood. */
export function plaque({ cream = '#F0EBDD', spot = '#2E2B2A', frame = '#5A3B26', seed = 12 }) {
  const C = hexLab(cream), S = hexLab(spot), F = hexLab(frame);
  const spots = [[0.25, 0.3, 0.2], [0.68, 0.25, 0.17], [0.55, 0.72, 0.22], [0.15, 0.8, 0.12], [0.88, 0.7, 0.1]];
  return (c) => {
    if (c.face !== 'front') return shade(F, (vnoise(c.u, c.v, 3, 3, seed) - 0.5) * 4);
    const fx = (c.a + 0.5) / c.W, fy = (c.b + 0.5) / c.H;
    let inSpot = false;
    for (const [x, y, r] of spots) {
      const wob = (vnoise(c.a * 3, c.b * 3, 4, 4, seed + 1) - 0.5) * 0.08;
      if (Math.hypot((fx - x) * c.W / c.H * 0.8, fy - y) < r + wob) inSpot = true;
    }
    const n = (vnoise(c.a, c.b, 3, 3, seed + 2) - 0.5) * 3;
    return inSpot ? shade(S, n) : shade(C, n - 1);
  };
}

/** Louvred vent: dark gaps between slanted red slats. */
export function louvre({ slat = '#8C3424', gap = '#1C2030', seed = 13 }) {
  const S = hexLab(slat), G = hexLab(gap);
  return (c) => {
    if (!isSide(c)) return shade(S, -4);
    const k = (c.b % 3);
    return k === 0 ? shade(S, 3 + (vnoise(c.u, c.v, 3, 3, seed) - 0.5) * 3) : k === 1 ? shade(S, -6) : shade(G, 0);
  };
}

/** Planked door with two iron straps (sheepfold). */
export function doorPlanks({ base = '#8A5A34', strap = '#4A4C55', w = 5, straps = [6, 22], seed = 14 }) {
  const P = boards({ base, w, seed, amp: 0.9 }), S = hexLab(strap);
  return (c) => {
    if (c.face === 'front' || c.face === 'back') {
      const y = c.p[1];
      for (const sy of straps) if (y >= sy && y < sy + 2) return shade(S, (y - sy < 1 ? 3 : -3) + (vnoise(c.u, c.v, 3, 3, seed) - 0.5) * 3);
    }
    return P(c);
  };
}

/** Feed mash: hay with clusters of grain. */
export function feed({ hayBase = '#D9AE4E', grain = '#B98A3A', seed = 15 }) {
  const H = hay({ base: hayBase, seed }), G = hexLab(grain);
  return (c) => {
    const g = vnoise(c.u + 200, c.v + 200, 2.5, 2.5, seed + 3);
    if (g > 0.66) return shade(G, (g - 0.66) * 30 - 4);
    return H(c);
  };
}

/** Carrot: orange with faint rings, green leafy top face. */
export function carrot({ base = '#E07A2A', leaf = '#5E8A2E', seed = 16 }) {
  const O = hexLab(base), L = hexLab(leaf);
  return (c) => {
    if (c.node.data.leaf) return shade(L, (vnoise(c.u, c.v, 2, 4, seed) - 0.5) * 8);
    const ax = grainAxis(c);
    const along = dot(c.p, ax);
    const ring = Math.abs(((along + 512) % 4) - 2) < 0.6 ? -6 : 0;
    return shade(O, ring + (vnoise(c.u, c.v, 3, 3, seed + 1) - 0.5) * 4);
  };
}

export function apple({ base = '#B8402E', seed = 17 }) {
  const A = hexLab(base);
  return (c) => shade(tint(A, (vnoise(c.u, c.v, 3, 3, seed) - 0.5) * 6, 0), (vnoise(c.u, c.v, 3, 5, seed + 1) - 0.5) * 6 + (isTop(c) ? 4 : 0));
}
