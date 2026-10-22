import { writeFileSync, readFileSync, existsSync, unlinkSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { sample } from './lib.mjs';
import { CLIPS } from './clips.mjs';

const WT = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/CozyTales-Ranching';
const PROJ = 'CozyTales-Ranching/src/main/resources/';
const RES = WT + '/src/main/resources/';
const VAN = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleSourceCode/hytale-shared-source/HytaleAssets/';
const [mode = 'build', filter = ''] = process.argv.slice(2);
const pick = CLIPS.filter(c => !filter || filter.split(',').some(f => (c.rig + '_' + c.name).toLowerCase().includes(f.toLowerCase())));
const YAW = { Cow: 60, Calf: 60, Pig: 55, Piglet: 55, Sheep: 60, Lamb: 55, Chicken: 40, Chick: 40, Frog: 35 };

const ASSETS = [
  ['CozyRanch_Cow', 'Cow', 'Server/Models/Livestock/Cow.json'],
  ['CozyRanch_Cow_Calf', 'Calf', 'Server/Models/Livestock/Calf.json'],
  ['CozyRanch_Chicken', 'Chicken', 'Server/Models/Livestock/Chicken.json'],
  ['CozyRanch_Chicken_Chick', 'Chick', 'Server/Models/Livestock/Chick.json'],
  ['CozyRanch_Pig', 'Pig', 'Server/Models/Livestock/Pig.json'],
  ['CozyRanch_Pig_Piglet', 'Piglet', 'Server/Models/Livestock/Piglet.json'],
  ['CozyRanch_Sheep', 'Sheep', 'Server/Models/Livestock/Sheep.json'],
  ['CozyRanch_Sheep_Lamb', 'Lamb', 'Server/Models/Livestock/Lamb.json'],
  ['CozyRanch_Frog', 'Frog', 'Server/Models/Critter/Frog_Green.json'],
  // the colour frogs are the green frog's asset with another texture, written from the same source so they cannot drift
  ['CozyRanch_Frog_Blue', 'Frog', 'Server/Models/Critter/Frog_Green.json', { Texture: 'NPC/Critter/Frog/Models/Model_Textures/Blue.png' }],
  ['CozyRanch_Frog_Orange', 'Frog', 'Server/Models/Critter/Frog_Green.json', { Texture: 'NPC/Critter/Frog/Models/Model_Textures/Orange.png' }],
];
const assetPath = id => `Server/Models/CozyRanch/${id}.json`;

function runSteps(steps, tag) {
  const file = `C:/temp/anim/steps_${tag}.json`;
  writeFileSync(file, JSON.stringify(steps));
  try { return execFileSync('node', [WT + '/tools/mcp-session.mjs', file], { cwd: WT, encoding: 'utf8', maxBuffer: 64 << 20 }); }
  catch (e) { return (e.stdout || '') + '\nFAILED ' + (e.stderr || e.message); }
}

if (mode === 'build') {
  mkdirSync('C:/temp/anim/build', { recursive: true });
  const steps = [];
  for (const c of pick) {
    const target = RES + 'Common/' + c.file;
    if (existsSync(target)) unlinkSync(target);
    const keys = sample(c.f, c.dur, c.loop, c.step ?? 3);
    steps.push({ tool: 'animation_build', note: c.rig + ' ' + c.name, always: true, maxChars: 1200,
      args: { model: { root: 'assets', path: c.model }, output: PROJ + 'Common/' + c.file, duration: c.dur, loop: c.loop, keys, frames: 6, yaw: YAW[c.rig], pitch: 15, size: 300 },
      out: `C:/temp/anim/build/${c.rig}_${c.name}.png` });
  }
  console.log(runSteps(steps, 'build'));
}

if (mode === 'assets') {
  for (const [id, rig, van, override = {}] of ASSETS) {
    const j = { ...JSON.parse(readFileSync(VAN + van, 'utf8').replace(/^\uFEFF/, '')), ...override };
    for (const c of CLIPS.filter(c => c.rig === rig)) {
      const entry = { Animation: c.file, BlendingDuration: c.loop ? 0.3 : 0.15 };
      if (!c.loop) entry.Looping = false;
      j.AnimationSets[c.name] = { Animations: [entry] };
    }
    const out = RES + assetPath(id);
    mkdirSync(out.slice(0, out.lastIndexOf('/')), { recursive: true });
    writeFileSync(out, JSON.stringify(j, null, 2) + '\n');
    console.log('wrote ' + out + ' sets: ' + CLIPS.filter(c => c.rig === rig).map(c => c.name).join(', '));
  }
}

if (mode === 'review') {
  // close look: four frames, bigger, from the side (yaw from env YAW or 90)
  const yaw = +(process.env.YAW ?? 90);
  const steps = pick.map(c => {
    const at = (process.env.FRAMES ? process.env.FRAMES.split(' ').map(Number) : [0.2, 0.4, 0.6, 0.8]).map(u => Math.round(u * c.dur));
    const id = ASSETS.find(a => a[1] === c.rig)[0];
    return { tool: 'animation_render', note: c.rig + ' ' + c.name, always: true, maxChars: 300,
      args: { root: 'projects', path: PROJ + assetPath(id), animation: c.name, frameList: at, yaw, pitch: +(process.env.PITCH ?? 10), size: 400 },
      out: `C:/temp/anim/build/R_${c.rig}_${c.name}.png` };
  });
  console.log(runSteps(steps, 'review'));
}

if (mode === 'strip') {
  // comparison strips: DIR (folder), PREFIX (file prefix), N (frames), SIZE, YAW, PITCH, MODEL (asset id override)
  const dir = process.env.DIR ?? 'C:/temp/anim/strip';
  mkdirSync(dir, { recursive: true });
  const steps = pick.map(c => {
    const id = process.env.MODEL ?? ASSETS.find(a => a[1] === c.rig)[0];
    return { tool: 'animation_render', note: id + ' ' + c.name, always: true, maxChars: 400,
      args: { root: 'projects', path: PROJ + assetPath(id), animation: c.name, frames: +(process.env.N ?? 6),
        yaw: +(process.env.YAW ?? c.yaw ?? YAW[c.rig]), pitch: +(process.env.PITCH ?? 15), size: +(process.env.SIZE ?? 360),
        hitbox: process.env.HITBOX === '1', ...(process.env.FRAMES ? { frameList: process.env.FRAMES.split(' ').map(Number) } : {}) },
      out: `${dir}/${process.env.PREFIX ?? ''}${c.rig}_${c.name}.png` };
  });
  console.log(runSteps(steps, 'strip'));
}

if (mode === 'render') {
  // final pictures through our model asset, saved in notes/pictures/animator
  const dir = WT + '/notes/pictures/animator';
  mkdirSync(dir, { recursive: true });
  const steps = pick.map(c => {
    const id = ASSETS.find(a => a[1] === c.rig)[0];
    return { tool: 'animation_render', note: id + ' ' + c.name, always: true, maxChars: 400,
      args: { root: 'projects', path: PROJ + assetPath(id), animation: c.name, frames: 6, yaw: c.yaw ?? YAW[c.rig], pitch: 15, size: 300 },
      out: `${dir}/${id}_${c.name}.png` };
  });
  console.log(runSteps(steps, 'render'));
}

if (mode === 'tests') {
  // every set resolves through its asset with all nodes found; one golden strip per species, plus a baby
  // the clips the animpolish pass reworked have goldens too
  const GOLDEN = ['Cow_Graze', 'Chicken_FlapHappy', 'Pig_MudRoll', 'Sheep_Pronk', 'Frog_LilyHop', 'Piglet_Happy',
    'Chicken_DustBathe', 'Chick_Happy', 'Cow_Love', 'Sheep_Love', 'Cow_TailSwish'];
  // every asset of a rig binds every set of that rig, so the colour frogs are checked set by set too
  const steps = CLIPS.flatMap(c => ASSETS.filter(a => a[1] === c.rig).map(([id], k) => {
    const step = { tool: 'animation_render', name: `${id} ${c.name}`,
      arguments: { root: 'projects', path: PROJ + assetPath(id), animation: c.name, frames: 4, yaw: c.yaw ?? YAW[c.rig], pitch: 15, size: 240 },
      expect: { animation: { nodesNotInModel: { $length: 0 } } } };
    if (k === 0 && GOLDEN.includes(c.rig + '_' + c.name)) step.golden = `tests/hytale/golden/animator/${id}_${c.name}.png`;
    return step;
  }));
  const cases = [
    { name: 'animator gate', steps: [
      { tool: 'asset_validate', arguments: { project: PROJ.replace('/src/main/resources/', '') }, expect: { valid: true, schemaErrors: 0 } }] },
    { name: 'animator clips', steps },
  ];
  const out = WT + '/tests/hytale/animator.test.json';
  mkdirSync(out.slice(0, out.lastIndexOf('/')), { recursive: true });
  writeFileSync(out, JSON.stringify({ cases }, null, 2) + '\n');
  console.log('wrote ' + out + ' with ' + steps.length + ' clip steps');
}
