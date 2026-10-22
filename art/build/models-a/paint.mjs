// Procedural "painted" materials. Each returns paint(ctx) -> (u, v) => [lab, alpha].
// u runs right, v runs down on the face; on top faces v runs toward +Z.
import { hexToLab, shade, mix, fbm, vnoise, hash, smoothstep } from './lib.mjs';

// Soft colour field: base colour with low-frequency tone and a little hue drift (clusters, never per-texel noise).
function field(base, seed, amp = 3, scale = 6) {
  const lab = hexToLab(base);
  return (u, v) => {
    const n = fbm(u / scale, v / scale, seed) * amp + fbm(u / (scale * 3), v / (scale * 3), seed + 9) * amp * 0.8;
    const h = fbm(u / (scale * 1.5) + 40, v / (scale * 1.5), seed + 3) * 0.008;
    const c = shade(lab, n);
    return [c[0], c[1] + h, c[2] - h * 0.5];
  };
}

// Plain painted surface.
export function paint(base, { amp = 3, scale = 6, light } = {}) {
  return { light, paint: ({ seed }) => { const f = field(base, seed, amp, scale); return (u, v) => [f(u, v)]; } };
}

// Boards. dir: 'h' boards run horizontally on side faces, 'v' vertically. topDir: 'x' or 'z' on top/bottom faces.
export function planks(base, { bw = 6, dir = 'h', topDir = 'x', groove = 12, tone = 4, joints = 34, light, ao, ragged = 0, splash = null } = {}) {
  const spl = splash && hexToLab(splash);
  return {
    light, ao,
    paint: ({ W, H, kind, seed, part }) => {
      const f = field(base, seed, 2.2, 5);
      const inner = planksInner({ W, H, kind, seed, part, f, bw, dir, topDir, groove, tone, joints, ragged });
      if (!spl || kind !== 'side') return inner;
      // mud splashes on the lower part of side faces: soft blobs, a few drips
      const blobs = [];
      const n = Math.max(1, Math.round((W * H) / 140));
      for (let i = 0; i < n; i++) blobs.push([hash(i, 11, seed) * W, H * (0.45 + 0.55 * hash(i, 12, seed)), 1.6 + hash(i, 13, seed) * 2.4]);
      return (u, v) => {
        let [c] = inner(u, v);
        let t = smoothstep(0.55, 1, v / Math.max(1, H - 1)) * 0.45 * (0.6 + 0.4 * (fbm(u / 4, 3, seed) * 0.5 + 0.5));
        for (const [x, y, r] of blobs) t = Math.max(t, smoothstep(1.1, 0.6, Math.hypot(u + 0.5 - x, (v + 0.5 - y) * 0.8) / r) * 0.8);
        return [mix(c, shade(spl, fbm(u / 2.5, v / 2.5, seed) * 2), t)];
      };
    },
  };
}

function planksInner({ W, H, kind, seed, part, f, bw, dir, topDir, groove, tone, joints, ragged }) {
  {
    {
      const horiz = kind === 'side' ? dir === 'h' : topDir === 'x';
      // boards line up across separate parts when they share world heights
      const world = part.opt.worldBoards && kind === 'side' && horiz;
      const off = world ? 1000 * bw - Math.round(part.c[1] + part.s[1] / 2) : Math.floor(hash(seed, 1) * bw);
      return (u, v) => {
        const a = horiz ? u : v, b = horiz ? v : u;
        const span = horiz ? H : W;
        const bi = Math.floor((b + off) / bw), within = (b + off) % bw;
        const bt = hash(bi, seed, 5);
        let dL = (bt - 0.5) * 2 * tone;
        // grain streaks along the board, 2-4 texels wide across, long along
        dL += fbm(a / 9 + bi * 13, within / 2.2, seed + bi) * 2.5;
        // board end joints
        const jl = joints * (0.7 + hash(bi, seed, 8) * 0.6);
        const jp = (a + hash(bi, seed, 9) * jl) % jl;
        let c = f(u, v);
        if (within === 0 && span > bw * 0.8) dL -= groove;
        else if (within === 1) dL += 3;
        else if (within === bw - 1) dL -= 2.5;
        if (jp < 1 && within !== 0) dL -= groove * 0.85;
        else if (jp < 2 && within !== 0) dL += 2;
        // weathered: board edges slightly uneven tone
        if (ragged) dL += fbm(a / 3, bi, seed + 21) * ragged;
        return [shade(c, dL)];
      };
    }
  }
}

// Shingle courses on top faces (rows stack along the slope axis). Sides get plain painted tile edge.
export function shingles(base, { line = '#682C1D', rowAxis = 'u', rowH = 10, tileW = 9, light } = {}) {
  const lineLab = hexToLab(line);
  return {
    light,
    paint: ({ W, H, kind, seed }) => {
      const f = field(base, seed, 2.5, 5);
      if (kind !== 'top') return (u, v) => {
        const c = f(u, v);
        const t = H > 1 ? v / (H - 1) : 0;
        return [kind === 'bottom' ? shade(c, -8) : mix(shade(c, 2 - 6 * t), lineLab, t > 0.7 ? 0.35 : 0)];
      };
      return (u, v) => {
        const s = rowAxis === 'u' ? u : v, a = rowAxis === 'u' ? v : u;
        const span = rowAxis === 'u' ? W : H;
        const t = s / Math.max(1, span - 1); // 0 = up-slope, 1 = butt edge
        const row = Math.floor(s / rowH);
        const shift = (row % 2) * Math.floor(tileW / 2) + Math.floor(hash(seed, row) * 3);
        const ti = Math.floor((a + shift) / tileW), wi = (a + shift) % tileW;
        let dL = (hash(ti, row, seed) - 0.5) * 8 + 3 - 7 * t;
        dL += fbm(a / 2.5, s / 6, seed + ti) * 2;
        let c = shade(f(u, v), dL);
        if (wi === 0) c = shade(c, -11);
        else if (wi === 1) c = shade(c, 2);
        if (t > 0.86) c = mix(c, lineLab, 0.55);
        return [c];
      };
    },
  };
}

// Irregular stone courses with mortar, bevels and soft moss patches.
export function cobble(warm, mossy, { moss = '#6F7A35', deepMoss = '#4C6A2A', mortar = '#6E6248', rowH = 8, stoneW = 11, mossAmt = 0.45, light } = {}) {
  const wL = hexToLab(warm), mL = hexToLab(mossy), moL = hexToLab(moss), dmL = hexToLab(deepMoss), mtL = hexToLab(mortar);
  return {
    light,
    paint: ({ W, H, kind, seed }) => {
      return (u, v) => {
        const row = Math.floor(v / rowH), wv = v % rowH;
        const shift = Math.floor(hash(row, seed, 2) * stoneW);
        const si = Math.floor((u + shift) / stoneW), wu = (u + shift) % stoneW;
        const sw = stoneW;
        const st = hash(si, row, seed + 4);
        let c = mix(wL, mL, smoothstep(0.35, 0.8, st));
        c = shade(c, (hash(si, row, seed + 6) - 0.5) * 9 + fbm(u / 3, v / 3, seed) * 2);
        // bevel: top/left lit, bottom/right dark
        if (wv === 1) c = shade(c, 4);
        if (wv === rowH - 2) c = shade(c, -4);
        if (wu === 1) c = shade(c, 2);
        if (wu === sw - 2) c = shade(c, -3);
        const isMortar = wv === 0 || wu === 0;
        if (isMortar) c = mix(shade(c, -10), mtL, 0.6);
        // moss: more on tops and on the upper rows
        const bias = kind === 'top' ? 0.35 : kind === 'bottom' ? -0.6 : (1 - v / Math.max(1, H)) * 0.3 - 0.05;
        const m = fbm(u / 6, v / 5, seed + 33) * 0.5 + 0.5 + bias - (1 - mossAmt);
        const mt = smoothstep(0.35, 0.6, m);
        if (mt > 0) {
          const mc = shade(mix(moL, dmL, smoothstep(0.55, 0.9, m) * 0.6), fbm(u / 2.5, v / 2.5, seed + 7) * 4);
          c = mix(c, mc, mt * (isMortar ? 1 : 0.92));
        }
        return [c];
      };
    },
  };
}

// Straw / hay: streaks along an axis.
export function straw(base, { along = 'v', light } = {}) {
  const lab = hexToLab(base);
  return {
    light,
    paint: ({ seed }) => (u, v) => {
      const a = along === 'v' ? v : u, b = along === 'v' ? u : v;
      const n = fbm(b / 1.6, a / 9, seed) * 7 + fbm(b / 4, a / 3, seed + 2) * 3;
      return [shade(lab, n)];
    },
  };
}

// Mud with glossy highlight blobs (on tops).
export function mud(base, { gloss = '#B8806A', light } = {}) {
  const lab = hexToLab(base), gl = hexToLab(gloss);
  return {
    light,
    paint: ({ W, H, kind, seed }) => {
      const blobs = [];
      const n = Math.round((W * H) / 110);
      for (let i = 0; i < n; i++) blobs.push([hash(i, 1, seed) * W, hash(i, 2, seed) * H, 2 + hash(i, 3, seed) * 3.5, 1 + hash(i, 4, seed) * 1.2]);
      return (u, v) => {
        let c = shade(lab, fbm(u / 5, v / 5, seed) * 4 + fbm(u / 2.5, v / 2.5, seed + 1) * 1.5);
        if (kind !== 'top') return [c];
        for (const [bx, by, r, ex] of blobs) {
          const d = Math.hypot((u - bx) / ex, v - by) / r;
          if (d < 1.25) c = shade(c, -5 * smoothstep(1.25, 0.95, d)); // rim of a puddle bump
          if (d < 0.9) c = mix(c, gl, 0.75 * smoothstep(0.9, 0.35, d));
        }
        return [c];
      };
    },
  };
}

// Translucent water surface: deeper teal in the middle, lighter at the edges, soft ripples.
export function water(deep, edge, { alpha = 0.72, light } = {}) {
  const dl = hexToLab(deep), el = hexToLab(edge);
  return {
    light, ao: 0,
    paint: ({ W, H, kind, seed }) => (u, v) => {
      const ex = Math.min(u, W - 1 - u, v, H - 1 - v);
      let c = mix(el, dl, smoothstep(0, 9, ex));
      const r = Math.sin((u * 0.35 + v * 0.22) + fbm(u / 7, v / 7, seed) * 3);
      c = shade(c, smoothstep(0.75, 1, r) * 5 + fbm(u / 5, v / 5, seed + 4) * 2);
      return [c, kind === 'top' ? alpha : alpha];
    },
  };
}

// Toadstool cap: orange-red with soft cream spots.
export function spotted(base, spot, { r0 = 3, r1 = 5, density = 70, light } = {}) {
  const lab = hexToLab(base), sl = hexToLab(spot);
  return {
    light,
    paint: ({ W, H, kind, seed }) => {
      const f = field(base, seed, 2.5, 6);
      const spots = [];
      const n = Math.max(1, Math.round((W * H) / density));
      for (let i = 0; i < n * 4 && spots.length < n; i++) {
        const s = [1 + hash(i, 1, seed) * (W - 2), 1 + hash(i, 2, seed) * (H - 2), r0 + hash(i, 3, seed) * (r1 - r0)];
        if (H < 8) s[1] = H / 2;
        if (spots.every((o) => Math.hypot(o[0] - s[0], o[1] - s[1]) > o[2] + s[2] + 2)) spots.push(s);
      }
      return (u, v) => {
        let c = f(u, v);
        if (kind === 'bottom') return [shade(c, -6)];
        for (const [x, y, r] of spots) {
          const d = Math.hypot(u + 0.5 - x, v + 0.5 - y) / Math.min(r, kind === 'side' ? Math.max(2, H / 2.2) : r);
          if (d < 1) c = mix(c, shade(sl, -2 * d + fbm(u / 2, v / 2, seed) * 1.5), smoothstep(1, 0.7, d));
        }
        return [c];
      };
    },
  };
}

// Square end grain rings (beam ends).
export function endgrain(base, { ring = '#8A5530', light } = {}) {
  const lab = hexToLab(base), rl = hexToLab(ring);
  return {
    light,
    paint: ({ W, H, seed }) => (u, v) => {
      const d = Math.max(Math.abs(u + 0.5 - W / 2), Math.abs(v + 0.5 - H / 2)) + fbm(u / 4, v / 4, seed) * 0.8;
      const r = d % 3;
      let c = shade(lab, fbm(u / 4, v / 4, seed + 2) * 2.5);
      if (r < 1) c = mix(c, rl, 0.55);
      if (d > Math.min(W, H) / 2 - 1) c = shade(c, -6);
      return [c];
    },
  };
}

// Pink pig-face sign board: eyes, snout with nostrils, blush, on the front face.
export function pigface(base, { snout = '#E29A93', eye = '#2A2130', light } = {}) {
  const lab = hexToLab(base), sn = hexToLab(snout), ey = hexToLab(eye), blush = hexToLab('#D9686C');
  return {
    light,
    paint: ({ W, H, face, seed }) => (u, v) => {
      let c = shade(lab, fbm(u / 3, v / 3, seed) * 2);
      if (face !== 'front') return [c];
      const x = u + 0.5, y = v + 0.5;
      const sx = W / 2, sy = H * 0.66;
      const ds = Math.hypot((x - sx) / (W * 0.26), (y - sy) / (H * 0.2));
      if (ds < 1) c = mix(c, shade(sn, (1 - ds) * 3), smoothstep(1, 0.8, ds));
      for (const nx of [sx - W * 0.1, sx + W * 0.1]) if (Math.hypot(x - nx, y - sy) < 0.9) c = mix(c, shade(lab, -22), 0.9);
      for (const ex of [W * 0.3, W * 0.7]) if (Math.hypot(x - ex, y - H * 0.36) < 0.95) c = ey;
      for (const bx of [W * 0.16, W * 0.84]) { const d = Math.hypot(x - bx, y - H * 0.58); if (d < 1.6) c = mix(c, blush, 0.4 * smoothstep(1.6, 0.5, d)); }
      return [c];
    },
  };
}

// Dark doorway inset: near black-blue, a touch lighter at the bottom where light spills in.
export function inset(base = '#1A1F2C') {
  const lab = hexToLab(base);
  return {
    light: 0.2, ao: 0,
    paint: ({ H, seed }) => (u, v) => [shade(lab, (v / Math.max(1, H - 1)) * 4 - 1 + fbm(u / 4, v / 4, seed) * 1.2)],
  };
}
