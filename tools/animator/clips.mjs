import { win, bump, arc, sn, cs, ss, clamp } from './lib.mjs';

// ---------- rigs ----------
const RIG = {
  Cow:    { neck: 'Neck', head: 'Head', jaw: 'Jaw', tail: ['Tail', 'Tail2', 'Tail3', 'Tail4'], eye: 'Eye', calf: 'Calf', fore: 'Forearm', hop: 16, ear: 'z' },
  Calf:   { neck: 'Neck', head: 'Head', jaw: 'Jaw', tail: ['Tail', 'Tail2'], eye: 'Eye', calf: 'Calf', fore: 'Forearm', hop: 14, ear: 'z' },
  Pig:    { neck: 'Neck', head: 'Head', jaw: 'Jaw', tail: ['Tail', 'Tail2', 'Tail3'], eye: 'Eye', calf: 'Calf', fore: 'Forearm', hop: 13, ear: 'x', tailLift: 0, lid: 6 },
  Piglet: { neck: null, head: 'Head', jaw: null, tail: ['Tail', 'Tail2'], eye: 'Eye', calf: null, fore: null, hop: 10, ear: 'z', tailLift: 0, lid: 4 },
  Sheep:  { neck: 'Neck', head: 'Head', jaw: 'Jaw', tail: ['Tail'], eye: 'Eye', calf: 'Calf', fore: 'Forearm', hop: 14, ear: 'z' },
  Lamb:   { neck: 'Neck', head: 'Head', jaw: 'Jaw', tail: ['Tail', 'Tail2'], eye: 'Eye', calf: null, fore: null, hop: 12, ear: 'z', lid: 4 },
};

// The bind poses are not the standing poses: vanilla idles droop tails, fold wings and set the frog's arms.
// Every clip starts from these so nodes it does not otherwise move still sit as the idle has them.
const BASE = {
  Cow: p => p.r('Tail', -40).r('Tail2', -30).r('Tail3', -35).r('Tail4', -25),
  Calf: p => p.r('Tail', -15).r('Tail2', 15).r('Head', -5),
  Chicken: p => p.lr('Arm', 105, 0, 0).lrp('Arm', 0, -12, 0).r('Wattle', 10).lrs('Eyelid', 1, 0, 1),
  Chick: p => p.lr('Arm', 90, 0, 0).lrp('Arm', 1, -6, 5),
  Pig: p => p,
  Piglet: p => p,
  Sheep: p => p.p('Pelvis', 0, -1.3, 0).lr('Thigh', 0, 0, -5).lr('Calf', 0, 0, 5).lr('Arm', 0, 0, -5).lr('Forearm', 0, 0, 5).r('Head', -5),
  Lamb: p => p.r('Head', -5),
  Frog: p => p.lr('Arm', 20, 2, 5).lr('Forearm', -50, 0, 0).lr('Hand', 50, 15, -10).r('Head', 5).r('Jaw', -5),
};

// Pigs and lambs close their eyes with eyelids, as their vanilla blinks do; the rest squash the eye.
const squint = (p, R, amount) => R.lid ? p.lrp('Eyelid', 0, -R.lid * amount, 0).lrs('Eyelid', 1, 1 + 10 * amount, 1) : p.lrs(R.eye, 1, 1 - amount, 1);
const ears = (p, R, v) => { if (R.ear === 'z') p.lr('Ear', 0, 0, v); else p.lr('Ear', -v, 0, 0); };
const wag = (p, R, t, period, amp, envelope = 1, axis = 'y') => R.tail.forEach((n, i) => {
  const v = amp * envelope * sn(t, period, -0.12 * i) * (1 - 0.15 * i);
  axis === 'y' ? p.r(n, 0, v, 0) : p.r(n, v, 0, 0);
});
const tailRaise = (p, R, x) => { if (R.tail.length) p.r(R.tail[0], x, 0, 0); };

/** Quadruped joyful hop + wiggle, 60 frames. */
function quadHappy(R) {
  return (t, p) => {
    const H = R.hop;
    const antic = bump(t, 0, 14), land = bump(t, 34, 48), air = arc(t, 12, 36), tuck = win(t, 12, 36, 6);
    const wig = win(t, 36, 60, 8), all = win(t, 0, 60, 8);
    p.p('Origin', 0, H * air, 0).r('Origin', 0, 12 * sn(t, 12) * wig, 0);
    p.p('Pelvis', 0, -2.5 * antic - 2.5 * land, 0).r('Pelvis', -10 * bump(t, 8, 32) + 5 * bump(t, 30, 44), 0, 4 * sn(t, 12, 0.25) * wig);
    const sq = 0.08 * antic + 0.1 * land - 0.06 * bump(t, 14, 32);
    p.s('Pelvis', 1 + sq * 0.6, 1 - sq, 1 + sq * 0.3).s('Chest', 1 + sq * 0.6, 1 - sq, 1 + sq * 0.3);
    p.lr('Thigh', 30 * tuck, 0, 6 * tuck); if (R.calf) p.lr(R.calf, -25 * tuck, 0, 0);
    p.lr('Arm', -30 * tuck, 0, 6 * tuck); if (R.fore) p.lr(R.fore, 45 * tuck, 0, 0);
    const look = win(t, 6, 52, 10);
    if (R.neck) p.r(R.neck, -18 * look + 8 * antic, 0, 0);
    p.r(R.head, (R.neck ? -6 : -16) * look, 0, 12 * sn(t, 24) * wig);
    if (R.jaw) p.r(R.jaw, 16 * win(t, 12, 44, 6), 0, 0);
    ears(p, R, 28 * win(t, 8, 44, 6) + 14 * sn(t, 8) * all);
    squint(p, R, 0.8 * win(t, 4, 56, 6));
    tailRaise(p, R, (R.tailLift ?? 25) * look);
    wag(p, R, t, 10, 35, all);
    return p;
  };
}

/** Head down into a trough, chomping. Loop. */
function quadTrough(R, o) {
  return (t, p) => {
    const chomp = 0.5 + 0.5 * sn(t, o.chomp ?? 20);
    p.r('Chest', o.chest, 0, 0).lr('Arm', -o.chest, 0, 0);
    if (R.neck) p.r(R.neck, o.neck + 3 * sn(t, o.chomp ?? 20, 0.25), 2 * sn(t, 80), 0);
    p.r(R.head, o.head + (R.neck ? 0 : 3 * sn(t, o.chomp ?? 20, 0.25)), 6 * sn(t, 40), 4 * sn(t, 80, 0.3));
    if (R.jaw) p.r(R.jaw, (o.jawAmp ?? 16) * chomp, 0, 0);
    p.p('Pelvis', 0, -1 * chomp, 0);
    ears(p, R, 6 * sn(t, 40, 0.2) + (o.earDown ?? -8));
    wag(p, R, t, 80, 14);
    return p;
  };
}

/** Affectionate nuzzle and two little bounces, 90 frames, starts and ends at rest.
 *  o.big scales the sway, the head roll and the bounces for big animals that must read from ten blocks. */
function quadLove(R, o = {}) {
  const B = o.big ?? 1;
  return (t, p) => {
    const w = win(t, 0, 90, 14), H = R.hop;
    p.p('Origin', 0, (0.45 + 0.35 * (B - 1)) * H * (arc(t, 10, 32) + arc(t, 46, 68)), 0);
    if (B > 1) p.r('Origin', 0, 14 * (B - 1) * sn(t, 45) * w, 0).r('Pelvis', -8 * (B - 1) * (arc(t, 10, 32) + arc(t, 46, 68)), 0, 0);
    p.r('Pelvis', 0, 0, 5 * sn(t, 45) * w).r('Chest', (B > 1 ? 0 : 6) * w, 8 * B * sn(t, 45, 0.1) * w, 0);
    const sq = 0.07 * B * (bump(t, 30, 42) + bump(t, 66, 78));
    p.s('Pelvis', 1 + sq * 0.6, 1 - sq, 1).s('Chest', 1 + sq * 0.6, 1 - sq, 1);
    if (R.neck) p.r(R.neck, 10 * w - 12 * (B - 1) * w, 14 * B * sn(t, 45) * w, 0);
    p.r(R.head, 8 * w, R.neck ? 0 : 16 * sn(t, 45) * w, 20 * B * sn(t, 45, 0.25) * w);
    ears(p, R, -12 * w + 10 * sn(t, 15) * w);
    squint(p, R, 0.9 * win(t, 8, 82, 8));
    wag(p, R, t, 12, 30 * Math.min(B, 1.4), w);
    p.lr('Arm', -6 * w, 0, 0);
    return p;
  };
}

/** Head right down to the grass, tugging and chewing. Loop of 120. */
function graze(R, o) {
  return (t, p) => {
    const tug = bump(t, 20, 34) + bump(t, 74, 88);
    const chew = 0.5 + 0.5 * sn(t, 15);
    p.r('Chest', o.chest, 0, 0).lr('Arm', -o.chest + (o.armBend ?? 0), 0, 0);
    if (o.fore && R.fore) p.lr(R.fore, o.fore, 0, 0);
    p.p('Pelvis', 0, o.drop ?? 0, 0);
    p.r(R.neck, o.neck - 8 * tug + 2 * sn(t, 60), 6 * sn(t, 120), 0);
    p.r(R.head, o.head + 10 * tug, 14 * (bump(t, 20, 34) - bump(t, 74, 88)), 4 * sn(t, 60, 0.2));
    if (R.jaw) p.r(R.jaw, 14 * chew * (1 - 0.7 * tug), 0, 0);
    ears(p, R, 8 * sn(t, 30) - 6);
    wag(p, R, t, 60, 18);
    return p;
  };
}

// ---------- cow ----------
function cowTailSwish(t, p) {
  const R = RIG.Cow;
  const sw = win(t, 0, 80, 14);
  // tail swats across the rump in two wide arcs while the cow looks round at its flank and stamps a hind foot,
  // then a calm stretch with breathing and an ear flick
  // the tail flicks up at the end of each swat
  const swat = sn(t, 40);
  R.tail.forEach((n, i) => p.r(n, (i === 0 ? 20 + 30 * swat * swat : 12) * sw, 80 * sw * sn(t, 40, -0.1 * i) * (1 - 0.1 * i), 0));
  const look = win(t, 4, 76, 16);
  p.r('Neck', -6 * look, -50 * look, 0).r('Head', 0, -30 * look, 16 * look);
  p.r('Chest', 0, -8 * look, 0).r('Pelvis', 0, 6 * look, 2.5 * sw * sn(t, 40, 0.25));
  const stamp = bump(t, 28, 44);
  p.r('R-Thigh', -35 * stamp, 0, 0).r('R-Calf', 45 * stamp, 0, 0);
  const breath = 0.5 + 0.5 * sn(t, 150, -0.25);
  p.s('Chest', 1 + 0.025 * breath, 1 + 0.02 * breath, 1);
  const flick = bump(t, 100, 110) + bump(t, 112, 122);
  p.r('L-Ear', 0, 0, 28 * flick).r('R-Ear', 0, 0, -10 * bump(t, 104, 116));
  p.lrs('Eye', 1, 1 - 0.95 * bump(t, 128, 138), 1);
  return p;
}

// ---------- chicken ----------
// The wing folds back by 105 degrees at rest; to spread it, unfold the pitch and lift it outward.
const chickWingsOut = (p, a) => { const f = a / 90; p.lr('Arm', -85 * f, 0, 80 * f).lrp('Arm', 0, 8 * f, 0); };
function chickenHappy(t, p) {
  const air = arc(t, 10, 34), antic = bump(t, 0, 12), land = bump(t, 32, 44), all = win(t, 0, 60, 6);
  p.p('Origin', 0, 12 * air, 0).r('Origin', 0, 14 * sn(t, 14) * win(t, 36, 60, 8), 0);
  p.p('Pelvis', 0, -2 * antic - 2 * land, 0).r('Pelvis', -12 * win(t, 8, 40, 8), 0, 0);
  const sq = 0.1 * antic + 0.12 * land - 0.08 * bump(t, 12, 30);
  p.s('Pelvis', 1 + sq * 0.5, 1 - sq, 1 + sq * 0.5);
  chickWingsOut(p, (55 + 25 * sn(t, 8)) * win(t, 6, 46, 6));
  p.lr('Thigh', 25 * win(t, 10, 34, 6), 0, 0);
  p.r('Neck', -14 * win(t, 6, 50, 8), 0, 0).r('Head', 0, 0, 14 * sn(t, 20) * all);
  p.r('Tail', -25 * win(t, 4, 52, 8), 10 * sn(t, 8) * all, 0);
  p.lrs('Eye', 1, 1 - 0.7 * win(t, 4, 56, 6), 1);
  p.r('Comb', 0, 0, 12 * sn(t, 10) * all).r('Wattle', 18 * sn(t, 10, 0.3) * all, 0, 0);
  return p;
}
function chickenFlapHappy(t, p) {
  // 1.25 s: four big flaps on tiptoe with two small hops, neck stretched up
  const w = win(t, 0, 75, 10), flap = 0.5 + 0.5 * sn(t, 12, -0.25);
  chickWingsOut(p, (20 + 75 * flap) * w);
  p.lr('Arm', -10 * flap * w, 0, 0);
  p.p('Origin', 0, 7 * (arc(t, 14, 32) + arc(t, 38, 56)), 0);
  p.r('Pelvis', -18 * w, 0, 0).s('Pelvis', 1 + 0.06 * w, 1 + 0.06 * w, 1 + 0.06 * w);
  p.lr('Thigh', 10 * w, 0, 0);
  p.r('Neck', -16 * w + 6 * flap * w, 0, 0).r('Head', 6 * flap * w, 0, 8 * sn(t, 24) * w);
  p.r('Tail', -30 * w, 0, 0).r('Comb', 0, 0, 14 * sn(t, 12, 0.2) * w);
  p.r('Wattle', 20 * sn(t, 12, 0.4) * w, 0, 0);
  p.lrs('Eye', 1, 1 - 0.7 * win(t, 6, 70, 6), 1);
  return p;
}
function chickenPeck(t, p) {
  // loop 60: two quick pecks, a look up between with a head tilt
  const pk = bump(t, 6, 18) + bump(t, 22, 32), up = win(t, 36, 60, 8);
  const down = win(t, 0, 36, 6);
  p.r('Pelvis', 14 * down + 6 * pk, 0, 0).p('Pelvis', 0, -1.5 * down, 1.5 * pk);
  p.lr('Thigh', -14 * down - 6 * pk, 0, 0);
  p.r('Neck', 22 * down + 34 * pk, 0, 0).r('Head', 12 * down + 22 * pk, 0, 18 * up * sn(t, 48, 0.0));
  p.lr('Arm', 0, 0, 6 * pk);
  p.r('Tail', 10 * down - 8 * pk, 0, 0);
  p.r('Wattle', 20 * pk, 0, 0);
  p.lrs('Eye', 1, 1 - 0.9 * bump(t, 44, 52), 1);
  return p;
}
// the belly sits this far down, nestled into the dust; deeper and the leaning body passes into the ground
const WALLOW = 5;
function chickenDustBathe(t, p) {
  // loop 120: settled low in the dust, one slow lean to each side with the high wing lifting, one fluff shake per loop
  const rock = sn(t, 120), shake = bump(t, 52, 70), sh = sn(t, 6) * shake;
  p.p('Pelvis', 0, -WALLOW, 0).r('Pelvis', 8, 6 * sn(t, 120, 0.25), 11 * rock + 4 * sh);
  const puff = 0.1 + 0.06 * shake;
  p.s('Pelvis', 1 + puff, 1 + puff * 0.6, 1 + puff);
  chickWingsOut(p, 40 + 6 * shake);
  // wings spread wide and flat over the dust; the low side lifts too, so neither tip goes into the ground
  const LOW = 20;
  p.lr('Arm', -30, 0, 30).r('L-Arm', 0, 0, 18 * Math.max(0, rock) + LOW * Math.max(0, -rock) + 6 * sh).r('R-Arm', 0, 0, -18 * Math.max(0, -rock) - LOW * Math.max(0, rock) - 6 * sh);
  p.lr('Thigh', -80, 0, 12).lr('Foot', 60, 0, 0);
  p.r('Neck', 16 + 4 * sn(t, 120, 0.25), 0, -14 * rock).r('Head', 6, 10 * sn(t, 120, 0.1), -6 * rock + 8 * sh);
  p.r('Tail', 4 * sh, 10 * rock, 0);
  p.lrs('Eye', 1, 0.35 - 0.3 * shake, 1);
  return p;
}
function chickenTrough(t, p) {
  // loop 60: three pecks at trough height
  const pk = bump(t, 4, 16) + bump(t, 22, 34) + bump(t, 40, 52);
  p.r('Pelvis', 10 + 4 * pk, 0, 0).p('Pelvis', 0, -1, 1 * pk);
  p.lr('Thigh', -10 - 4 * pk, 0, 0);
  p.r('Neck', 18 + 22 * pk, 0, 0).r('Head', 8 + 16 * pk, 0, 6 * sn(t, 60));
  p.r('Tail', 8 + 4 * sn(t, 20), 0, 0).r('Wattle', 15 * pk, 0, 0);
  return p;
}
function chickenLove(t, p) {
  // 1.5 s: puffs up, sways side to side, wings drooped like courtship, head bobs
  const w = win(t, 0, 90, 14), sway = sn(t, 45);
  p.s('Pelvis', 1 + 0.14 * w, 1 + 0.1 * w, 1 + 0.12 * w);
  p.r('Origin', 0, 16 * sway * w, 0).r('Pelvis', -6 * w, 0, 10 * sway * w);
  p.p('Origin', 0, 4 * (arc(t, 12, 30) + arc(t, 34, 52) + arc(t, 56, 74)), 0);
  chickWingsOut(p, (22 + 10 * sn(t, 15)) * w);
  p.lr('Arm', 20 * w, 0, 0);
  p.r('Neck', (-8 + 10 * (0.5 + 0.5 * sn(t, 15))) * w, 0, -12 * sway * w).r('Head', 0, 0, 22 * sn(t, 45, 0.2) * w);
  p.r('Tail', -30 * w, 14 * sway * w, 0);
  p.lrs('Eye', 1, 1 - 0.8 * win(t, 8, 82, 8), 1);
  return p;
}

// ---------- pig ----------
function pigHappy(t, p) {
  const R = RIG.Pig;
  quadHappy(R)(t, p);
  const all = win(t, 0, 60, 6);
  p.r('Tail', 0, 0, 0); // tail corkscrew spin on top of wag
  p.r('Tail2', 0, 0, 60 * sn(t, 10) * all);
  p.lr('Ear2', -20 * win(t, 10, 44, 6), 0, 0);
  return p;
}
function pigSnuffle(t, p) {
  // loop 90: nose down, sweeping slowly side to side, snout twitching fast
  const sweep = sn(t, 90), tw = 0.5 + 0.5 * sn(t, 7.5);
  p.r('Chest', 12, 0, 0).lr('Arm', -12, 0, 0);
  p.r('Neck', 28, 10 * sweep, 0).r('Head', 22 + 3 * tw, 14 * sweep, -6 * sweep);
  p.p('Nose', 0, 0.8 * tw, 0.6 * tw).s('Nose', 1 + 0.12 * tw, 1 - 0.08 * tw, 1);
  p.r('Jaw', 6 * tw, 0, 0);
  p.p('Pelvis', 0, -1, 0).r('Pelvis', 0, -4 * sweep, 0);
  p.lr('Ear', 14 + 6 * sn(t, 15), 0, 0);
  p.r('Tail2', 0, 0, 30 * sn(t, 30));
  return p;
}
// The pig's chest box is 40 wide and 30 tall about the Pelvis pivot, 26 above Origin; the feet stand 5.8 up.
// Rolled by `ang` degrees, the box's lowest corner is this far below the pivot.
const pigReach = ang => { const a = ang * Math.PI / 180; return 20 * Math.abs(Math.sin(a)) + 15 * Math.abs(Math.cos(a)); };
function pigMudRoll(t, p) {
  // loop 150: flop down, roll over onto the back, wiggle all four legs in the air while rocking, roll on over, stand up
  const R = RIG.Pig;
  const lie = win(t, 0, 150, 16);                       // lying on the ground
  const back = win(t, 40, 112, 10);                     // settled on the back
  const ang = 180 * ss((t - 14) / 28) + 180 * ss((t - 108) / 28) + 22 * sn(t, 36) * back;
  const roll = ang * Math.PI / 180, up = Math.cos(roll); // up: 1 upright, -1 on the back
  // keep the lowest corner of the rolling body on the ground, a hair above it
  const drop = (5.8 + 0.6 + 1.6 * back + 3.2 * Math.abs(Math.sin(2 * roll)) * (1 - back) - 26 + pigReach(ang)) * win(t, 6, 144, 16);
  p.r('Pelvis', 0, 0, ang).p('Pelvis', 0, drop, 0);
  // legs: tucked under while lying the right way up, kicking in the air on the back
  const tuck = win(t, 0, 150, 10) * clamp(0.5 + 0.5 * up);
  const air = lie * clamp(0.5 - 0.5 * up) ** 0.7;
  const k = sn(t, 10), k2 = sn(t, 10, 0.5), kb = back;
  p.r('L-Thigh', -55 * tuck + (-10 + 40 * k) * air * (0.4 + 0.6 * kb), 0, 25 * air).r('R-Thigh', -55 * tuck + (-10 + 40 * k2) * air * (0.4 + 0.6 * kb), 0, -25 * air);
  p.lr('Calf', -60 * tuck, 0, 0).r('L-Calf', 30 * air * (0.5 + 0.5 * k2), 0, 0).r('R-Calf', 30 * air * (0.5 + 0.5 * k), 0, 0);
  p.r('L-Arm', 55 * tuck + (10 + 40 * k2) * air * (0.4 + 0.6 * kb), 0, 25 * air).r('R-Arm', 55 * tuck + (10 + 40 * k) * air * (0.4 + 0.6 * kb), 0, -25 * air);
  p.lr('Forearm', 60 * tuck, 0, 0).r('L-Forearm', -35 * air * (0.5 + 0.5 * k), 0, 0).r('R-Forearm', -35 * air * (0.5 + 0.5 * k2), 0, 0);
  // head: tipped back toward the sky on the back, wobbling with glee; ears flop forward so they stay off the ground
  p.r('Neck', 18 * air, 0, -0.3 * Math.sin(roll) * 40 * lie + (28 + 10 * sn(t, 36, 0.1)) * back).r('Head', 12 * air, 0, 12 * sn(t, 18) * kb);
  p.r('Jaw', 20 * lie + 8 * sn(t, 12) * kb, 0, 0);
  p.lr('Ear', 75 * air - 10 * tuck + 10 * sn(t, 10) * kb, 0, 0).lr('Ear2', 20 * air, 0, 0);
  squint(p, R, 0.85 * lie);
  p.r('Tail2', 0, 0, 60 * sn(t, 10) * lie);
  // a little shake standing back up
  p.r('Chest', 0, 0, 0).r('Origin', 0, 8 * sn(t, 8) * bump(t, 134, 150), 0);
  return p;
}

// ---------- sheep ----------
function sheepPronk(t, p) {
  // 1 s: crouch, stiff-legged spring straight up, legs splayed straight, bounce on landing
  const antic = bump(t, 0, 14), air = arc(t, 12, 38), stiff = win(t, 12, 40, 5), land = bump(t, 36, 48), all = win(t, 0, 60, 6);
  p.p('Origin', 0, 24 * air + 5 * arc(t, 46, 58), 0);
  p.p('Pelvis', 0, -4 * antic - 3 * land, 0);
  p.r('Pelvis', -6 * bump(t, 10, 26) + 6 * bump(t, 24, 40), 0, 0);
  const sq = 0.09 * antic + 0.1 * land - 0.07 * bump(t, 14, 34);
  p.s('Pelvis', 1 + 0.5 * sq, 1 - sq, 1).s('Chest', 1 + 0.5 * sq, 1 - sq, 1);
  // stiff legs: straighten the knees and splay out a little, plus anticipation bend
  p.lr('Thigh', -12 * stiff + 14 * antic, 0, 10 * stiff).lr('Calf', 8 * stiff - 18 * antic, 0, 0);
  p.lr('Arm', 10 * stiff - 10 * antic, 0, 10 * stiff).lr('Forearm', 8 * stiff + 16 * antic, 0, 0);
  p.r('Neck', -20 * win(t, 8, 48, 8), 0, 0).r('Head', -8 * win(t, 8, 48, 8), 0, 10 * sn(t, 20) * all);
  p.lr('Ear', 0, 0, 30 * stiff + 10 * sn(t, 10) * all);
  p.r('Tail', -25 * stiff, 30 * sn(t, 8) * all, 0);
  p.r('Jaw', 18 * stiff, 0, 0);
  p.lrs('Eye', 1, 1 - 0.7 * win(t, 6, 54, 6), 1);
  return p;
}

// ---------- frog ----------
function frogHappy(t, p) {
  // 1 s: two bouncy hops in place with a wiggle and a tongue blep
  const h1 = arc(t, 6, 22), h2 = arc(t, 26, 42), land = bump(t, 20, 28) + bump(t, 40, 50), all = win(t, 0, 60, 6);
  p.p('Origin', 0, 9 * h1 + 9 * h2, 0).r('Origin', 0, 18 * sn(t, 20) * all, 0);
  const sq = 0.12 * land + 0.08 * bump(t, 0, 8) - 0.08 * (h1 + h2);
  p.s('Pelvis', 1 + 0.6 * sq, 1 - sq, 1 + 0.4 * sq).s('Head', 1 + 0.4 * sq, 1 - 0.6 * sq, 1);
  p.r('Pelvis', -10 * (h1 + h2), 0, 0);
  const ext = h1 + h2;
  p.lr('Thigh', 40 * ext, -12 * ext, 10 * ext).lr('Calf', -20 * ext, 0, -8 * ext);
  p.lr('Arm', -20 * ext, 0, 35 * ext);
  p.r('Head', -10 * win(t, 4, 54, 8), 0, 10 * sn(t, 30) * all);
  const blep = win(t, 40, 58, 4);
  p.r('Jaw', 14 * blep, 0, 0).p('Tongue', 0, -1 * blep, 3 * blep);
  p.lrs('Eye', 1, 1 - 0.75 * win(t, 4, 56, 6), 1);
  return p;
}
function frogLilyHop(t, p) {
  // 1.2 s: crouch, hop straight up with a full spin, splat landing
  const antic = bump(t, 0, 14), air = arc(t, 12, 46), land = bump(t, 44, 60), ext = win(t, 12, 44, 6);
  const spin = 360 * ss((t - 14) / 30);
  p.p('Origin', 0, 30 * air, 0).r('Origin', 0, spin >= 360 ? 0 : spin, 0);
  p.p('Pelvis', 0, -2 * antic - 2 * land, 0).r('Pelvis', 10 * antic - 18 * ext, 0, 0);
  const sq = 0.12 * antic + 0.15 * land - 0.1 * ext;
  p.s('Pelvis', 1 + 0.6 * sq, 1 - sq, 1 + 0.4 * sq).s('Head', 1 + 0.4 * sq, 1 - 0.6 * sq, 1);
  p.lr('Thigh', 70 * ext, -20 * ext, 20 * ext).lr('Calf', -30 * ext, 0, -10 * ext).lr('Foot', 40 * ext, 0, 0);
  p.lr('Arm', -25 * ext + 25 * land, 0, 45 * ext);
  p.r('Head', 14 * antic - 16 * ext, 0, 0);
  p.r('Jaw', 10 * ext, 0, 0);
  p.lrs('Eye', 1, 1 - 0.8 * land, 1);
  return p;
}
function frogCroakSit(t, p) {
  // loop 120: sitting up proud, throat balloons twice
  const puff = bump(t, 8, 40), puff2 = bump(t, 58, 90);
  const pf = puff + puff2;
  p.r('Pelvis', -8, 0, 0).p('Pelvis', 0, 1, 0).s('Pelvis', 1 + 0.05 * pf, 1 + 0.04 * pf, 1 + 0.05 * pf);
  p.lr('Arm', -6, 0, 0);
  p.r('Head', -8 - 6 * pf, 0, 4 * sn(t, 120)).s('Head', 1, 1 + 0.03 * pf, 1);
  // the throat grows down and forward from the jaw; growing it upward would hide the eyes
  p.p('Jaw', 0, -3.5 * pf, 2.2 * pf).s('Jaw', 1 + 0.25 * pf, 1 + 0.9 * pf, 1 + 0.6 * pf);
  p.lrs('Eye', 1, 1 - 0.7 * pf, 1).lr('Eye', 0, 0, 6 * pf);
  return p;
}
function frogTrough(t, p) {
  // loop 80: leaning in, two tongue flicks and a gulp
  const f1 = bump(t, 6, 18), f2 = bump(t, 30, 42), gulp = bump(t, 50, 66);
  const f = f1 + f2;
  p.r('Pelvis', 12, 0, 0).p('Pelvis', 0, 0, 2);
  p.r('Head', 10 + 8 * f - 6 * gulp, 6 * (f1 - f2), 0);
  p.r('Jaw', 24 * f, 0, 0).p('Tongue', 0, -2 * f, 8 * f);
  p.s('Jaw', 1 + 0.08 * gulp, 1 + 0.45 * gulp, 1 + 0.15 * gulp).p('Jaw', 0, -1.2 * gulp, 0);
  p.lrs('Eye', 1, 1 - 0.9 * gulp, 1);
  p.lr('Arm', -10, 0, 4);
  return p;
}
function frogLove(t, p) {
  // 1.5 s: swaying side to side, little bounces, a soft throat puff, dreamy eyes
  const w = win(t, 0, 90, 14), sway = sn(t, 45);
  p.r('Pelvis', -6 * w, 0, 10 * sway * w).r('Origin', 0, 12 * sway * w, 0);
  p.p('Origin', 0, 5 * (arc(t, 10, 28) + arc(t, 32, 50) + arc(t, 54, 72)), 0);
  p.r('Head', -6 * w, 0, 16 * sn(t, 45, 0.15) * w);
  const pf = bump(t, 30, 70);
  p.p('Jaw', 0, -2.5 * pf, 0.5 * pf).s('Jaw', 1 + 0.2 * pf, 1 + 0.9 * pf, 1 + 0.3 * pf);
  p.lr('Arm', -20 * w, 0, 12 * w);
  p.lrs('Eye', 1, 1 - 0.85 * win(t, 8, 82, 8), 1);
  return p;
}

// ---------- chick (baby chicken) ----------
function chickHappy(t, p) {
  const air = arc(t, 10, 32), antic = bump(t, 0, 12), land = bump(t, 30, 42), all = win(t, 0, 60, 6);
  p.p('Origin', 0, 9 * air + 4 * arc(t, 42, 54), 0).r('Origin', 0, 14 * sn(t, 14) * win(t, 34, 60, 8), 0);
  const sq = 0.12 * antic + 0.14 * land - 0.1 * bump(t, 12, 30);
  p.s('Pelvis', 1 + sq * 0.5, 1 - sq, 1 + sq * 0.5).s('Head', 1 + sq * 0.4, 1 - sq * 0.6, 1 + sq * 0.4);
  // the wings are tiny quads folded back along the body: unfold them to the sides, grow them a little and flap them high
  const open = win(t, 4, 50, 6), flap = 0.5 + 0.5 * sn(t, 10, -0.25);
  p.lr('Arm', -80 * open, 0, (35 + 85 * flap) * open).lrp('Arm', 0, 3 * open, -3 * open).lrs('Arm', 1, 1 + 0.5 * open, 1 + 0.3 * open);
  p.r('Head', -12 * win(t, 6, 50, 8), 0, 14 * sn(t, 20) * all);
  p.r('Rear', -20 * win(t, 4, 52, 8), 0, 0).r('Tail', 0, 20 * sn(t, 8) * all, 0);
  p.lr('Thigh', 25 * win(t, 10, 32, 6), 0, 0);
  p.lrs('Eye', 1, 1 - 0.7 * win(t, 4, 56, 6), 1);
  return p;
}

// ---------- the list ----------
const M = { Cow: 'Server/Models/Livestock/Cow.json', Calf: 'Server/Models/Livestock/Calf.json', Chicken: 'Server/Models/Livestock/Chicken.json', Chick: 'Server/Models/Livestock/Chick.json',
  Pig: 'Server/Models/Livestock/Pig.json', Piglet: 'Server/Models/Livestock/Piglet.json', Sheep: 'Server/Models/Livestock/Sheep.json', Lamb: 'Server/Models/Livestock/Lamb.json', Frog: 'Server/Models/Critter/Frog_Green.json' };

const c = (species, rig, name, dur, loop, f, extra = {}) => ({ species, rig, name, dur, loop, f: (t, p) => f(t, BASE[rig](p)), model: M[rig],
  file: `NPC/CozyRanch/${species}/Animations/${rig !== species ? rig + '/' : ''}${name}.blockyanim`, ...extra });

export const CLIPS = [
  c('Cow', 'Cow', 'Happy', 60, false, quadHappy(RIG.Cow)),
  c('Cow', 'Cow', 'EatTrough', 80, true, quadTrough(RIG.Cow, { chest: 8, neck: 34, head: 10 })),
  c('Cow', 'Cow', 'Love', 90, false, quadLove(RIG.Cow, { big: 1.8 })),
  c('Cow', 'Cow', 'Graze', 120, true, graze(RIG.Cow, { chest: 14, neck: 54, head: 18, fore: -20, drop: -2 })),
  c('Cow', 'Cow', 'TailSwish', 150, true, cowTailSwish),
  c('Cow', 'Calf', 'Happy', 60, false, quadHappy(RIG.Calf)),

  c('Chicken', 'Chicken', 'Happy', 60, false, chickenHappy),
  c('Chicken', 'Chicken', 'EatTrough', 60, true, chickenTrough),
  c('Chicken', 'Chicken', 'Love', 90, false, chickenLove),
  c('Chicken', 'Chicken', 'DustBathe', 120, true, chickenDustBathe),
  c('Chicken', 'Chicken', 'PeckGround', 60, true, chickenPeck),
  c('Chicken', 'Chicken', 'FlapHappy', 75, false, chickenFlapHappy),
  c('Chicken', 'Chick', 'Happy', 60, false, chickHappy),

  c('Pig', 'Pig', 'Happy', 60, false, pigHappy),
  c('Pig', 'Pig', 'EatTrough', 80, true, quadTrough(RIG.Pig, { chest: 6, neck: 16, head: 12, chomp: 16 })),
  c('Pig', 'Pig', 'Love', 90, false, quadLove(RIG.Pig)),
  c('Pig', 'Pig', 'MudRoll', 150, true, pigMudRoll, { yaw: 25 }),
  c('Pig', 'Pig', 'Snuffle', 90, true, pigSnuffle),
  c('Pig', 'Piglet', 'Happy', 60, false, quadHappy(RIG.Piglet)),

  c('Sheep', 'Sheep', 'Happy', 60, false, quadHappy(RIG.Sheep)),
  c('Sheep', 'Sheep', 'EatTrough', 80, true, quadTrough(RIG.Sheep, { chest: 6, neck: 24, head: 10 })),
  c('Sheep', 'Sheep', 'Love', 90, false, quadLove(RIG.Sheep, { big: 1.5 })),
  c('Sheep', 'Sheep', 'Graze', 120, true, graze(RIG.Sheep, { chest: 10, neck: 55, head: 6, drop: -1 })),
  c('Sheep', 'Sheep', 'Pronk', 60, false, sheepPronk),
  c('Sheep', 'Lamb', 'Happy', 60, false, quadHappy(RIG.Lamb)),

  c('Frog', 'Frog', 'Happy', 60, false, frogHappy),
  c('Frog', 'Frog', 'EatTrough', 80, true, frogTrough),
  c('Frog', 'Frog', 'Love', 90, false, frogLove),
  c('Frog', 'Frog', 'LilyHop', 72, false, frogLilyHop),
  c('Frog', 'Frog', 'CroakSit', 120, true, frogCroakSit, { yaw: 20 }),
];
