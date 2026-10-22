// Box-average a square render down to 64 x 64 (premultiplied alpha, so edges carry no halo).
// node icon-downscale.mjs <in.png> <out.png>
import { createRequire } from 'node:module';
import fs from 'node:fs';

const require = createRequire('C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleMCP/package.json');
const { PNG } = require('pngjs');

const [, , src, dst] = process.argv;
const img = PNG.sync.read(fs.readFileSync(src));
const N = 64, k = img.width / N;
const out = new PNG({ width: N, height: N });
for (let y = 0; y < N; y++) {
  for (let x = 0; x < N; x++) {
    let r = 0, g = 0, b = 0, a = 0, n = 0;
    for (let yy = Math.floor(y * k); yy < Math.floor((y + 1) * k); yy++) {
      for (let xx = Math.floor(x * k); xx < Math.floor((x + 1) * k); xx++) {
        const o = (yy * img.width + xx) * 4, al = img.data[o + 3] / 255;
        r += img.data[o] * al; g += img.data[o + 1] * al; b += img.data[o + 2] * al; a += al; n++;
      }
    }
    const o = (y * N + x) * 4;
    out.data[o] = a ? Math.round(r / a) : 0; out.data[o + 1] = a ? Math.round(g / a) : 0;
    out.data[o + 2] = a ? Math.round(b / a) : 0; out.data[o + 3] = Math.round((a / n) * 255);
  }
}
fs.writeFileSync(dst, PNG.sync.write(out));
