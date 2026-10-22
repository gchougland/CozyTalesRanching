import { readFileSync } from 'node:fs';
const A = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleSourceCode/hytale-shared-source/HytaleAssets/Common/';
// quaternion -> XYZ intrinsic euler degrees
const eul = ({ x, y, z, w }) => {
  const d = 180 / Math.PI;
  const sy = Math.max(-1, Math.min(1, 2 * (x * z + w * y)));
  return [Math.atan2(2 * (w * x - y * z), 1 - 2 * (x * x + y * y)) * d, Math.asin(sy) * d, Math.atan2(2 * (w * z - x * y), 1 - 2 * (y * y + z * z)) * d].map(v => Math.round(v));
};
for (const p of process.argv.slice(2)) {
  const a = JSON.parse(readFileSync(A + p, 'utf8'));
  console.log(`### ${p} dur=${a.duration} hold=${a.holdLastKeyframe}`);
  for (const [n, ch] of Object.entries(a.nodeAnimations)) {
    const o = (ch.orientation || []).map(k => `${k.time}:${eul(k.delta).join('/')}`).join(' ');
    const ps = (ch.position || []).map(k => `${k.time}:${[k.delta.x, k.delta.y, k.delta.z].map(v => Math.round(v * 10) / 10).join('/')}`).join(' ');
    const st = (ch.shapeStretch || []).map(k => `${k.time}:${[k.delta.x, k.delta.y, k.delta.z].map(v => Math.round(v * 100) / 100).join('/')}`).join(' ');
    if (!o && !ps && !st) continue;
    console.log(`  ${n}${o ? ' R[' + o + ']' : ''}${ps ? ' P[' + ps + ']' : ''}${st ? ' S[' + st + ']' : ''}`);
  }
}
