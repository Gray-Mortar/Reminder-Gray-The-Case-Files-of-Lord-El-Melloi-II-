const names = ['立即做', '安排做', '委托做', '尽量不做'];
const stateKey = 'remindergray-web-v1';
const keyKey = 'remindergray-sync-key';
const saved = JSON.parse(localStorage.getItem(stateKey) || '{}');
function uniqueId() {
  return globalThis.crypto?.randomUUID?.() ||
    `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}
const state = {
  clientId: saved.clientId || uniqueId(),
  tasks: Array.isArray(saved.tasks) ? saved.tasks : [],
  pending: Array.isArray(saved.pending) ? saved.pending : [],
  revision: saved.revision || 0
};
let syncing = false;
const matrix = document.querySelector('#matrix');
const syncState = document.querySelector('#sync-state');

function persist() { localStorage.setItem(stateKey, JSON.stringify(state)); }
function taskOrder(a, b) { return a.quadrant - b.quadrant || a.order - b.order || a.createdAt - b.createdAt; }
function quadrantFor() {
  const important = document.querySelector('#important').checked;
  const urgent = document.querySelector('#urgent').checked;
  return important ? (urgent ? 0 : 1) : (urgent ? 2 : 3);
}
function pendingFor(id) { return state.pending.find(op => op.id === String(id)); }
function queue(kind, task) {
  const id = String(task.id);
  const old = pendingFor(id);
  state.pending = state.pending.filter(op => op.id !== id);
  state.pending.push({
    opId: `${state.clientId}.${uniqueId()}`,
    kind, id,
    baseVersion: old?.baseVersion ?? (task.version || 0),
    ...(kind === 'upsert' ? { task } : {})
  });
  persist();
  render();
  sync();
}
function update(task) {
  state.tasks = state.tasks.filter(item => item.id !== task.id);
  state.tasks.push(task);
  queue('upsert', task);
}
function remove(task) {
  state.tasks = state.tasks.filter(item => item.id !== task.id);
  queue('delete', task);
}
function render() {
  matrix.replaceChildren();
  for (let quadrant = 0; quadrant < 4; quadrant++) {
    const section = document.createElement('section');
    section.className = `quadrant q${quadrant}`;
    const heading = document.createElement('div');
    heading.className = 'quadrant-head';
    const count = document.createElement('span');
    count.className = 'count';
    const tasks = state.tasks.filter(item => !item.deleted && item.quadrant === quadrant).sort(taskOrder);
    count.textContent = String(tasks.length);
    heading.append(count);
    const list = document.createElement('div');
    list.className = 'tasks';
    list.addEventListener('dragover', event => event.preventDefault());
    list.addEventListener('drop', event => {
      event.preventDefault();
      const id = event.dataTransfer.getData('text/plain');
      const dragged = state.tasks.find(item => item.id === id);
      if (!dragged) return;
      const target = event.target.closest('.task');
      dragged.quadrant = quadrant;
      dragged.order = target ? Number(target.dataset.order) - 1 :
        Math.min(0, ...tasks.map(item => item.order)) - 1024;
      update(dragged);
    });
    for (const task of tasks) {
      const row = document.createElement('div');
      row.className = `task${task.done ? ' done' : ''}`;
      row.draggable = true;
      row.dataset.order = task.order;
      row.addEventListener('dragstart', event => {
        event.dataTransfer.setData('text/plain', task.id);
        row.classList.add('dragging');
      });
      row.addEventListener('dragend', () => row.classList.remove('dragging'));
      const check = document.createElement('input');
      check.type = 'checkbox'; check.checked = task.done;
      check.setAttribute('aria-label', `完成 ${task.text}`);
      check.addEventListener('change', () => { task.done = check.checked; update(task); });
      const main = document.createElement('div');
      main.className = 'task-main';
      main.textContent = task.text;
      if (task.dueAt) {
        const due = document.createElement('small');
        due.textContent = new Date(task.dueAt).toLocaleString('zh-CN');
        main.append(due);
      }
      const edit = document.createElement('button');
      edit.type = 'button'; edit.textContent = '编辑';
      edit.addEventListener('click', () => {
        const text = prompt('待办内容', task.text);
        if (text === null || !text.trim()) return;
        task.text = text.trim();
        update(task);
      });
      const del = document.createElement('button');
      del.type = 'button'; del.textContent = '删除';
      del.addEventListener('click', () => { if (confirm(`删除“${task.text}”？`)) remove(task); });
      row.append(check, main, edit, del);
      list.append(row);
    }
    const watermark = document.createElement('div');
    watermark.className = 'watermark'; watermark.textContent = names[quadrant];
    section.append(heading, list, watermark);
    matrix.append(section);
  }
}

async function sync() {
  if (syncing) return;
  const key = localStorage.getItem(keyKey);
  if (!key) { syncState.textContent = '需要连接设置'; return; }
  syncing = true;
  const sent = state.pending.map(op => structuredClone(op));
  syncState.textContent = '同步中…';
  try {
    const response = await fetch('/api/sync', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      body: JSON.stringify({ clientId: state.clientId, operations: sent })
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);
    const sentIds = new Set(sent.map(op => op.opId));
    state.pending = state.pending.filter(op => !sentIds.has(op.opId));
    const fresh = body.tasks.filter(task => !task.deleted).map(task => ({ ...task, id: String(task.id) }));
    for (const op of state.pending) {
      const remote = body.tasks.find(task => String(task.id) === op.id);
      if (remote) op.baseVersion = remote.version;
    }
    const localPending = new Set(state.pending.map(op => op.id));
    state.tasks = [...fresh.filter(task => !localPending.has(task.id)),
      ...state.tasks.filter(task => localPending.has(task.id))];
    state.revision = body.revision;
    persist(); render();
    syncState.textContent = body.conflicts.length ?
      `已同步，保留 ${body.conflicts.length} 个冲突副本` : '已同步';
  } catch (error) {
    syncState.textContent = `离线：${error.message}`;
  } finally { syncing = false; }
}

document.querySelector('#composer').addEventListener('submit', event => {
  event.preventDefault();
  const input = document.querySelector('#task-input');
  const dueValue = document.querySelector('#due-at').value;
  const quadrant = quadrantFor();
  const order = Math.min(0, ...state.tasks.filter(task => task.quadrant === quadrant).map(task => task.order)) - 1024;
  const id = String(Date.now() * 1000 + Math.floor(Math.random() * 1000));
  update({ id, text: input.value.trim(), quadrant, done: false,
    createdAt: Date.now(), dueAt: dueValue ? new Date(dueValue).getTime() : 0,
    order, version: 0 });
  input.value = ''; document.querySelector('#due-at').value = '';
});
const dialog = document.querySelector('#settings-dialog');
document.querySelector('#settings-button').addEventListener('click', () => {
  document.querySelector('#sync-key').value = localStorage.getItem(keyKey) || '';
  dialog.showModal();
});
document.querySelector('#cancel-settings').addEventListener('click', () => dialog.close());
document.querySelector('#settings-form').addEventListener('submit', event => {
  event.preventDefault();
  localStorage.setItem(keyKey, document.querySelector('#sync-key').value);
  dialog.close(); sync();
});
window.addEventListener('online', sync);
document.addEventListener('visibilitychange', () => { if (!document.hidden) sync(); });
setInterval(sync, 10000);
persist(); render(); sync();
