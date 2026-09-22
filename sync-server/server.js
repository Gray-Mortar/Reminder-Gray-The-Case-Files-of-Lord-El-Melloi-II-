import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const publicRoot = path.join(root, 'public');
const host = process.env.REMINDERGRAY_HOST || '127.0.0.1';
const port = Number(process.env.REMINDERGRAY_PORT || 8787);
const token = process.env.REMINDERGRAY_TOKEN;
const dataFile = process.env.REMINDERGRAY_DATA || path.join(root, 'data', 'tasks.json');
const maxBody = 1024 * 1024;

if (!token || token.length < 12) {
  console.error('Set REMINDERGRAY_TOKEN to a private key of at least 12 characters.');
  process.exit(1);
}

function readState() {
  try {
    const parsed = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
    return {
      revision: Number(parsed.revision) || 0,
      tasks: Array.isArray(parsed.tasks) ? parsed.tasks : [],
      applied: Array.isArray(parsed.applied) ? parsed.applied : []
    };
  } catch (error) {
    if (error.code === 'ENOENT') return { revision: 0, tasks: [], applied: [] };
    throw error;
  }
}

let state = readState();

function persist() {
  fs.mkdirSync(path.dirname(dataFile), { recursive: true });
  const temporary = `${dataFile}.${process.pid}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify(state), { mode: 0o600 });
  fs.renameSync(temporary, dataFile);
}

function safeEqual(provided) {
  const a = Buffer.from(provided || '');
  const b = Buffer.from(token);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function send(response, status, body, type = 'application/json; charset=utf-8') {
  response.writeHead(status, {
    'Content-Type': type,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  });
  response.end(typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body));
}

function normalizedTask(value) {
  if (!value || typeof value !== 'object') throw new Error('Task is required');
  const id = String(value.id);
  const text = String(value.text || '').trim();
  const quadrant = Number(value.quadrant);
  if (!/^\d{1,16}$/.test(id) || !text || text.length > 1000 ||
      !Number.isInteger(quadrant) || quadrant < 0 || quadrant > 3) {
    throw new Error('Invalid task');
  }
  return {
    id,
    text,
    quadrant,
    done: Boolean(value.done),
    createdAt: Number(value.createdAt) || Date.now(),
    dueAt: Math.max(0, Number(value.dueAt) || 0),
    order: Number.isFinite(Number(value.order)) ? Number(value.order) : 0,
    deleted: false
  };
}

function applyOperations(operations) {
  if (!Array.isArray(operations) || operations.length > 500) {
    throw new Error('Invalid operations');
  }
  const originalState = state;
  state = structuredClone(state);
  const conflicts = [];
  try {
  for (const operation of operations) {
    const opId = String(operation?.opId || '');
    const id = String(operation?.id || operation?.task?.id || '');
    const kind = operation?.kind;
    const baseVersion = Number(operation?.baseVersion) || 0;
    if (!/^[\w.-]{1,100}$/.test(opId) || !/^\d{1,16}$/.test(id) ||
        !['upsert', 'delete'].includes(kind) || baseVersion < 0) {
      throw new Error('Invalid operation');
    }
    if (state.applied.includes(opId)) continue;
    const index = state.tasks.findIndex(task => task.id === id);
    const previous = index >= 0 ? state.tasks[index] : null;
    if (previous && previous.version !== baseVersion) {
      if (kind === 'upsert') {
        const copy = normalizedTask(operation.task);
        do {
          copy.id = String(Date.now() * 1000 + crypto.randomInt(1000));
        } while (state.tasks.some(task => task.id === copy.id));
        copy.text = `${copy.text}（冲突副本）`;
        copy.version = ++state.revision;
        state.tasks.push(copy);
        conflicts.push({ id, copyId: copy.id, reason: 'remote-changed' });
      } else {
        conflicts.push({ id, reason: 'remote-changed-delete-skipped' });
      }
    } else if (kind === 'delete') {
      const tombstone = {
        ...(previous || { id, createdAt: Date.now(), quadrant: 3, order: 0, text: '' }),
        deleted: true,
        version: ++state.revision
      };
      if (index >= 0) state.tasks[index] = tombstone;
      else state.tasks.push(tombstone);
    } else {
      const task = normalizedTask(operation.task);
      if (task.id !== id) throw new Error('Task id mismatch');
      task.version = ++state.revision;
      if (index >= 0) state.tasks[index] = task;
      else state.tasks.push(task);
    }
    state.applied.push(opId);
  }
  state.applied = state.applied.slice(-20000);
  persist();
  return { revision: state.revision, tasks: state.tasks, conflicts };
  } catch (error) {
    state = originalState;
    throw error;
  }
}

async function readBody(request) {
  let text = '';
  for await (const chunk of request) {
    text += chunk;
    if (text.length > maxBody) throw new Error('Request too large');
  }
  return JSON.parse(text || '{}');
}

const server = http.createServer(async (request, response) => {
  const pathname = new URL(request.url, 'http://localhost').pathname;
  if (pathname === '/api/health') {
    send(response, 200, { ok: true, revision: state.revision });
    return;
  }
  if (pathname === '/api/sync' && request.method === 'POST') {
    if (!safeEqual(request.headers.authorization?.replace(/^Bearer /, ''))) {
      send(response, 401, { error: 'Invalid sync key' });
      return;
    }
    try {
      const body = await readBody(request);
      send(response, 200, applyOperations(body.operations || []));
    } catch (error) {
      send(response, 400, { error: error.message });
    }
    return;
  }
  if (request.method !== 'GET') {
    send(response, 405, { error: 'Method not allowed' });
    return;
  }
  const allowed = {
    '/': ['index.html', 'text/html; charset=utf-8'],
    '/app.js': ['app.js', 'text/javascript; charset=utf-8'],
    '/style.css': ['style.css', 'text/css; charset=utf-8'],
    '/background.png': ['../../app/src/main/res/drawable-nodpi/app_background_tall.png', 'image/png'],
    '/wenkai.ttf': ['../../app/src/main/res/font/lxgw_wenkai_lite_regular.ttf', 'font/ttf']
  };
  const asset = allowed[pathname];
  if (!asset) {
    send(response, 404, { error: 'Not found' });
    return;
  }
  send(response, 200, fs.readFileSync(path.join(publicRoot, asset[0])), asset[1]);
});

server.listen(port, host, () => {
  console.log(`ReminderGray sync: http://${host}:${port}`);
  console.log(`Data: ${dataFile}`);
});
