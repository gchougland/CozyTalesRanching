// Poop pile block models: small (1 dropping), medium (2-3), large (4-8), at 32 units per block, origin at the placed
// cell's bottom centre. Soft-serve dollops of stacked octagon layers, each layer turned a little for a swirl, painted
// warm brown with swirl grooves, a moist sheen on the tops and a few straw bits in the bigger piles.
// Uses the art3 generator library from the main checkout (art/build/art3, git-ignored; models-b and art3 made it).
// Run: node tools/poop/build.mjs [--clay]
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const RES = path.resolve(HERE, '../../src/main/resources');
const ART3 = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/CozyTales-Ranching/art/build/art3';
const { box, oct, rot, build, audit } = await import(pathToFileURL(`${ART3}/lib.mjs`).href);
const P = await import(pathToFileURL(`${ART3}/paints.mjs`).href);

const OUT = `${RES}/Common/Blocks/CozyRanch/Poop`;
const clay = process.argv.includes('--clay');

// ---------- paint ----------
const BASE = P.hexLab('#6F4A2D'), DEEP = P.hexLab('#58372A'), WARM = P.hexLab('#86603A');
/** Manure: soft mottle between a deep red-brown and a warm tan, swirl grooves round the sides, a wet sheen on top. */
function manure(seed) {
  return (c) => {
    const s = c.u + 512, t = c.v + 512;
    const m = P.fbm(s, t, 7, seed);
    let col = m < 0.45 ? P.mixLab(BASE, DEEP, (0.45 - m) * 1.6) : P.mixLab(BASE, WARM, (m - 0.45) * 1.2);
    let dL = (P.vnoise(s, t, 2.5, 2.5, seed + 3) - 0.5) * 4;
    if (Math.abs(c.wn[1]) <= 0.7) {
      // each layer rolls over at its top (a lit lip) and tucks under at its bottom (a cool crease)
      if (c.b === 0) dL += 4;
      else if (c.b === c.H - 1) dL -= 7;
      else if (c.b === c.H - 2 && c.H > 3) dL -= 2;
      // a soft swirl highlight drifting round the layer
      const sw = P.vnoise(c.u * 0.5 + c.p[1] * 2 + 512, 7, 5, 5, seed + 9);
      if (sw > 0.62) dL += (sw - 0.62) * 14;
    } else if (c.wn[1] > 0.7) {
      const g = P.fbm(s + 31, t + 17, 4, seed + 5);
      if (g > 0.58) { col = P.mixLab(col, WARM, 0.45); dL += (g - 0.58) * 20; }
      dL += 2;
    }
    return P.shade(col, dL);
  };
}
const materials = clay
  ? { poop: () => P.hexLab('#9A9A9A'), straw: () => P.hexLab('#C8C8C8') }
  : { poop: manure(91), straw: P.straw({ base: '#D8AE4E', tip: '#EED08A', shadow: '#94703A', seed: 92 }) };

// ---------- shapes ----------
/**
 * One soft-serve dollop: layers [y0, y1, width, 'sq'?] from the bottom, octagons turned 11.25 degrees more each
 * layer, small layers as square boxes, and a curled tip. Every layer's top and bottom sit on their own planes.
 */
function dollop(name, [cx, cz], y, layers, tip, turn0 = 0) {
  const out = [];
  layers.forEach(([y0, y1, L, sq], i) => {
    const cy = y + (y0 + y1) / 2, h = y1 - y0, turn = turn0 + 11.25 * i;
    if (sq) out.push(box(`${name}L${i}`, [cx, cy, cz], [L, h, L], 'poop', { q: rot('y', turn + 22.5) }));
    else out.push(...oct(`${name}L${i}_`, [cx, cy, cz], L, h, 'poop', { turn }));
  });
  if (tip) {
    const [w, h, lean] = tip;
    const top = y + layers[layers.length - 1][1];
    out.push(box(`${name}Tip`, [cx + lean * 0.35, top + h / 2 - 0.6, cz], [w, h, w], 'poop', { q: rot('z', -lean * 9, 'y', turn0 + 40) }));
  }
  return out;
}
const straw = (name, centre, len, q) => box(name, centre, [1, 1, len], 'straw', { q, data: { grain: 2, tips: true } });

const MODELS = {
  // count 1: one little swirl, 10 x 10 units, 0.33 block tall
  Small: () => dollop('Swirl', [0, 0], 0, [[0, 2, 10], [2, 4.5, 9], [4.5, 6.5, 7], [6.5, 8.25, 5, 'sq'], [8.25, 9.5, 3, 'sq']], [2, 2, 1]),
  // count 2-3: a bigger swirl with a second little one leaning on it
  Medium: () => [
    ...dollop('Big', [-1.5, 1], 0, [[0, 2.25, 13], [2.25, 5, 12], [5, 7.5, 10], [7.5, 9.75, 8], [9.75, 11.5, 5, 'sq'], [11.5, 12.75, 3, 'sq']], [2, 2, 1]),
    ...dollop('Small', [5, -4], 0.15, [[0, 2.6, 8], [2.6, 4.4, 6], [4.4, 5.9, 4, 'sq']], [2, 2, -1], 30),
  ],
  // count 4-8: a broad low base with a tall swirl, two smaller ones and some straw caught in it
  Large: () => [
    ...oct('Base', [0, 1, 0], 22, 2, 'poop', { turn: 11.25 }),
    ...dollop('Big', [-1, 0], 1.6, [[0, 2.5, 15], [2.5, 5.5, 13], [5.5, 8.25, 11], [8.25, 10.75, 9], [10.75, 12.75, 6, 'sq'], [12.75, 14.25, 4, 'sq']], [2, 3, 1]),
    ...dollop('Left', [-7, 6], 1.3, [[0, 2.7, 8], [2.7, 4.6, 6], [4.6, 6.1, 4, 'sq']], [2, 2, -1], 20),
    ...dollop('Right', [7, 4.5], 1.45, [[0, 3.2, 9], [3.2, 5.3, 7], [5.3, 6.9, 5, 'sq']], [2, 2, 1], 35),
    straw('StrawA', [7.5, 2.6, -6.5], 7, rot('x', -10, 'y', 55)),
    straw('StrawB', [-9, 2.4, -3], 6, rot('x', 8, 'y', -70)),
    straw('StrawC', [2.5, 9.5, 5], 5, rot('x', -35, 'y', 15)),
  ],
};

// darker where the pile meets the ground and in the creases between layers
const ao = (p, n) => (Math.abs(n[1]) < 0.7 && p[1] < 2 ? -(2 - p[1]) * 2.5 : 0);
for (const [name, make] of Object.entries(MODELS)) {
  const suffix = clay ? '_Clay' : '';
  const modelPath = `${OUT}/Poop_${name}${suffix}.blockymodel`;
  const r = build({ roots: make(), materials, ao, modelPath, texturePath: `${OUT}/Poop_${name}${suffix}_Texture.png`, minW: 32 });
  console.log(name, `${r.W}x${r.H}`, `${r.nodeCount} nodes`, JSON.stringify(audit(modelPath)));
}
