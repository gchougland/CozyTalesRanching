// Run several Hytale MCP tool calls in one MCP process, so servers and bots started by one step
// survive into the next. Steps come from a JSON file:
//   [{"tool":"server_start","args":{...},"save":"job"},
//    {"tool":"job_status","args":{"jobId":"$job","waitMs":120000}},
//    {"tool":"server_command","args":{"jobId":"$job","command":"ranch ping"}},
//    {"tool":"job_stop","args":{"jobId":"$job"},"always":true}]
// "save": name stores the result's id (or jobId / botId) for later "$name" references.
// "out": a png path saves the first picture. "always": true runs the step even after a failure.
// {"sleepMs": 5000} just waits (job_status returns as soon as the server logs anything).
//   node tools/mcp-session.mjs steps.json
import { readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const mcp = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleMCP';
const imp = p => import(pathToFileURL(`${mcp}/${p}`).href);
const { Client } = await imp('node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js');
const { InMemoryTransport } = await imp('node_modules/@modelcontextprotocol/sdk/dist/esm/inMemory.js');
const { loadConfig } = await imp('dist/config.js');
const { createServer } = await imp('dist/server.js');

const steps = JSON.parse((await readFile(process.argv[2], 'utf8')).replace(/^\uFEFF/, ''));
const cwd = process.cwd();
process.chdir(mcp);
const app = createServer(await loadConfig(undefined));
const client = new Client({ name: 'mcp-session', version: '1.0.0' });
const [left, right] = InMemoryTransport.createLinkedPair();
await app.server.connect(right); await client.connect(left);

const vars = {};
const subst = v => typeof v === 'string' ? v.replace(/\$(\w+)/g, (m, k) => (k in vars ? vars[k] : m))
  : Array.isArray(v) ? v.map(subst) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, subst(x)])) : v;
let failed = false;
try {
  for (const step of steps) {
    if (failed && !step.always) continue;
    if (step.sleepMs) { console.log(`\n=== sleep ${step.sleepMs} ms`); await new Promise(r => setTimeout(r, step.sleepMs)); continue; }
    console.log(`\n=== ${step.tool} ${step.note ?? ''}`);
    let result;
    const deadline = Date.now() + (step.timeoutMs ?? 300000);
    try {
      for (;;) {
        result = await client.callTool({ name: step.tool, arguments: subst(step.args ?? {}) }, undefined, { timeout: 30 * 60 * 1000 });
        if (!step.until || result.isError) break;
        let j = {}; try { j = JSON.parse(result.content.find(p => p.type === 'text')?.text ?? '{}'); } catch { /* keep polling */ }
        if (j[step.until] || (j.state && j.state !== 'running')) break;
        if (Date.now() > deadline) { console.log(`timed out waiting for ${step.until}`); failed = true; break; }
        await new Promise(r => setTimeout(r, 2000));
      }
    }
    catch (e) { console.log('ERROR ' + e.message); failed = true; continue; }
    if (result.isError) failed = true;
    for (const part of result.content) {
      if (part.type === 'text') {
        const text = part.text;
        console.log(text.length > (step.maxChars ?? 6000) ? text.slice(0, step.maxChars ?? 6000) + '\n...[truncated]' : text);
        if (step.save) { try { const j = JSON.parse(text); vars[step.save] = j.id ?? j.jobId ?? j.botId; } catch { /* not json */ } }
      } else if (part.type === 'image' && step.out) {
        const file = step.out.match(/^[A-Za-z]:/) ? step.out : `${cwd}/${step.out}`;
        await writeFile(file, Buffer.from(part.data, 'base64')); console.log('picture written: ' + file);
      }
    }
  }
} finally { await client.close(); await app.close(); }
process.exit(failed ? 1 : 0);
