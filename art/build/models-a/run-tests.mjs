// Run tests/hytale/models-a.test.json in one MCP process and wait for the job (test_run is a background job).
//   node run-tests.mjs [--update]
import { pathToFileURL } from 'node:url';
const mcp = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleMCP';
const imp = (p) => import(pathToFileURL(`${mcp}/${p}`).href);
const { Client } = await imp('node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js');
const { InMemoryTransport } = await imp('node_modules/@modelcontextprotocol/sdk/dist/esm/inMemory.js');
const { loadConfig } = await imp('dist/config.js');
const { createServer } = await imp('dist/server.js');

process.chdir(mcp);
const app = createServer(await loadConfig()), client = new Client({ name: 'models-a-tests', version: '1.0.0' });
const [l, r] = InMemoryTransport.createLinkedPair();
await app.server.connect(r); await client.connect(l);
const text = (res) => res.content.filter((p) => p.type === 'text').map((p) => p.text).join('\n');
try {
  const started = JSON.parse(text(await client.callTool({ name: 'test_run', arguments: { project: '_worktrees/CozyTales-Ranching-models-a', filter: 'models-a', updateGoldens: process.argv.includes('--update') } })));
  let info;
  for (;;) {
    info = JSON.parse(text(await client.callTool({ name: 'job_status', arguments: { jobId: started.id, waitMs: 5000 } })));
    if (info.state !== 'running') break;
  }
  console.log(JSON.stringify(info, null, 1).slice(0, 6000));
  const logs = text(await client.callTool({ name: 'job_logs', arguments: { jobId: started.id, maxChars: 6000 } }));
  console.log(logs);
} finally { await client.close(); await app.close(); }
