import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';

test('sync persists tasks, handles deletion, retries and stale edits', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'remindergray-test-'));
  const port = 19000 + Math.floor(Math.random() * 20000);
  const child = spawn(process.execPath, ['server.js'], {
    cwd: path.dirname(new URL(import.meta.url).pathname.replace(/^\/(?=[A-Za-z]:)/, '')),
    env: { ...process.env, REMINDERGRAY_TOKEN: 'test-secret-123456',
      REMINDERGRAY_PORT: String(port), REMINDERGRAY_DATA: path.join(directory, 'data.json') },
    stdio: 'pipe'
  });
  const base = `http://127.0.0.1:${port}`;
  async function sync(operations, secret = 'test-secret-123456') {
    const response = await fetch(`${base}/api/sync`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ operations })
    });
    return { status: response.status, body: await response.json() };
  }
  try {
    for (let attempt = 0; attempt < 50; attempt++) {
      try { if ((await fetch(`${base}/api/health`)).ok) break; }
      catch { await new Promise(resolve => setTimeout(resolve, 50)); }
    }
    assert.equal((await sync([], 'wrong-key')).status, 401);
    const task = { id: '1234567890123', text: '测试同步', quadrant: 0,
      done: false, createdAt: 1234567890123, dueAt: 0, order: -1024 };
    const add = { opId: 'device-a.1', kind: 'upsert', id: task.id,
      baseVersion: 0, task };
    const first = await sync([add]);
    assert.equal(first.status, 200);
    assert.equal(first.body.tasks[0].text, '测试同步');
    assert.equal(first.body.tasks[0].order, -1024);
    assert.equal((await sync([add])).body.tasks.length, 1);
    const stale = await sync([{ ...add, opId: 'device-b.1',
      task: { ...task, text: '离线修改' } }]);
    assert.equal(stale.body.conflicts.length, 1);
    assert.equal(stale.body.tasks.length, 2);
    assert.ok(stale.body.tasks.some(item => item.text === '离线修改（冲突副本）'));
    const invalid = await sync([{ ...add, opId: 'device-a.2', baseVersion: 1,
      task: { ...task, text: '暂存' } }, { opId: 'invalid', kind: 'oops', id: task.id }]);
    assert.equal(invalid.status, 400);
    assert.equal((await sync([])).body.tasks[0].text, '测试同步');
    const deleted = await sync([{ opId: 'device-a.3', kind: 'delete', id: task.id,
      baseVersion: 1 }]);
    assert.equal(deleted.body.tasks.find(item => item.id === task.id).deleted, true);
    assert.ok(fs.existsSync(path.join(directory, 'data.json')));
  } finally {
    child.kill();
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
