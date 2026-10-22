export const RES = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/_worktrees/CozyTales-Ranching-models-a/src/main/resources';

export function report(name, r) {
  console.log(`${name}: ${r.nodes} nodes, texture ${r.width}x${r.height}, ${r.rects} face rects`);
  console.log(`  fractional sizes: ${r.report.fractional.length ? r.report.fractional.join(', ') : 'none'}`);
  console.log(`  coplanar overlaps: ${r.report.zfight.length ? '\n    ' + r.report.zfight.join('\n    ') : 'none'}`);
}
