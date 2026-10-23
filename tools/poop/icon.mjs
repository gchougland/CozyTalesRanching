// The poop pile item icon: the medium pile, three-quarter orthographic on a clear background, cropped, padded square,
// box-averaged to 64 x 64 (as art/build/artpolish/icons.mjs does for the other icons). Needs python with PIL.
// Run: node tools/poop/icon.mjs
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const WORKTREE = path.resolve(HERE, '../..');
const MODDING = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding';
const PROJECT = path.relative(MODDING, WORKTREE).replaceAll('\\', '/');
const RES = `${WORKTREE}/src/main/resources`;

const mcp = `${MODDING}/HytaleMCP`;
const imp = (p) => import(pathToFileURL(`${mcp}/${p}`).href);
const { Client } = await imp('node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js');
const { InMemoryTransport } = await imp('node_modules/@modelcontextprotocol/sdk/dist/esm/inMemory.js');
const { loadConfig } = await imp('dist/config.js');
const { createServer } = await imp('dist/server.js');

process.chdir(mcp);
const app = createServer(await loadConfig(undefined));
const client = new Client({ name: 'poop-icon', version: '1.0.0' });
const [l, r] = InMemoryTransport.createLinkedPair();
await app.server.connect(r); await client.connect(l);
try {
  const item = 'CozyRanch_Poop_Pile';
  const args = { root: 'projects', path: `${PROJECT}/src/main/resources/Server/Item/Items/CozyRanch/Poop/${item}.json`, blockState: 'Count3',
    yaw: 35, pitch: 26, size: 512, grid: false, hitbox: false, projection: 'orthographic', background: '#00000000' };
  const res = await client.callTool({ name: 'model_render', arguments: args }, undefined, { timeout: 600000 });
  const img = res.content.find((p) => p.type === 'image');
  if (!img) throw new Error('no picture: ' + res.content.map((p) => p.text).join('\n').slice(0, 400));
  const raw = `${os.tmpdir()}/${item}.icon_raw.png`, out = `${RES}/Common/Icons/ItemsGenerated/${item}.png`;
  fs.writeFileSync(raw, Buffer.from(img.data, 'base64'));
  // a pile fills less of the slot than furniture: about 46 of 64 px wide
  execFileSync('python', ['-c', `from PIL import Image
im = Image.open(r'${raw}').convert('RGBA')
im = im.crop(im.getchannel('A').getbbox())
s = int(max(im.size) * 1.36)
sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
sq.paste(im, ((s - im.width) // 2, (s - im.height) // 2 + int(s * 0.04)))
sq.resize((64, 64), Image.BOX).save(r'${out}')`]);
  console.log('icon written:', out);
} finally { await client.close(); await app.close(); }
