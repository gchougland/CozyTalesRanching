// Pose helpers for animation_build: clips are functions of the frame, sampled densely with linear keys.
export const TAU = Math.PI * 2;
export const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
export const ss = x => { x = clamp(x); return x * x * (3 - 2 * x); };
/** 0 before a, ramps to 1 by a+r, holds, ramps to 0 from b-r to b. */
export const win = (t, a, b, r) => ss((t - a) / r) * (1 - ss((t - (b - r)) / r));
/** a single smooth bump between a and b, 1 at the middle. */
export const bump = (t, a, b) => (t <= a || t >= b) ? 0 : Math.sin(Math.PI * (t - a) / (b - a)) ** 2;
/** hop arc: 0 at a and b, 1 at the middle (parabola). */
export const arc = (t, a, b) => { const u = (t - a) / (b - a); return u <= 0 || u >= 1 ? 0 : 4 * u * (1 - u); };
export const sn = (t, period, phase = 0) => Math.sin(TAU * (t / period + phase));
export const cs = (t, period, phase = 0) => Math.cos(TAU * (t / period + phase));

export class Pose {
  constructor() { this.m = {}; }
  e(n) { return this.m[n] ??= {}; }
  r(n, x = 0, y = 0, z = 0) { const e = this.e(n); const v = e.rotation ?? [0, 0, 0]; e.rotation = [v[0] + x, v[1] + y, v[2] + z]; return this; }
  p(n, x = 0, y = 0, z = 0) { const e = this.e(n); const v = e.position ?? [0, 0, 0]; e.position = [v[0] + x, v[1] + y, v[2] + z]; return this; }
  s(n, x = 1, y = 1, z = 1) { const e = this.e(n); const v = e.stretch ?? [1, 1, 1]; e.stretch = [v[0] * x, v[1] * y, v[2] * z]; return this; }
  /** left/right pair: y and z mirrored for the right side. */
  lr(base, x = 0, y = 0, z = 0) { this.r('L-' + base, x, y, z); this.r('R-' + base, x, -y, -z); return this; }
  lrp(base, x = 0, y = 0, z = 0) { this.p('L-' + base, x, y, z); this.p('R-' + base, -x, y, z); return this; }
  lrs(base, x = 1, y = 1, z = 1) { this.s('L-' + base, x, y, z); this.s('R-' + base, x, y, z); return this; }
}

const rnd = (v, d) => Math.round(v * d) / d;
/** Sample f(t) -> Pose into keys. Every node gets every channel it uses at every key. */
export function sample(f, dur, loop, step = 3) {
  const times = [];
  for (let t = 0; t < dur; t += step) times.push(t);
  if (!loop) times.push(dur);
  const poses = times.map(t => f(t, new Pose()).m);
  const used = {};
  for (const p of poses) for (const [n, e] of Object.entries(p)) { used[n] ??= new Set(); for (const k of Object.keys(e)) used[n].add(k); }
  return times.map((t, i) => {
    const pose = {};
    for (const [n, ks] of Object.entries(used)) {
      const e = poses[i][n] ?? {}, o = {};
      if (ks.has('rotation')) o.rotation = (e.rotation ?? [0, 0, 0]).map(v => rnd(v, 10));
      if (ks.has('position')) o.position = (e.position ?? [0, 0, 0]).map(v => rnd(v, 100));
      if (ks.has('stretch')) o.stretch = (e.stretch ?? [1, 1, 1]).map(v => rnd(v, 1000));
      pose[n] = o;
    }
    return { time: t, interpolation: 'linear', pose };
  });
}
