(() => {
  'use strict';
  const KEY = 'remindergray-web-v1';
  const TITLES = ['立即做', '安排做', '委托做', '尽量不做'];
  const $ = id => document.getElementById(id);
  const home = $('homeView'), detail = $('detailView');
  const input = $('taskInput'), dueInput = $('dueInput');
  const editor = $('editor');
  let tasks = readTasks();
  let editingId = null;
  let activeQuadrant = null;
  let toastTimer;

  function readTasks() {
    try {
      const saved = localStorage.getItem(KEY);
      if (saved !== null) {
        const parsed = JSON.parse(saved);
        return Array.isArray(parsed) ? parsed.filter(validTask) : [];
      }
      const legacy = JSON.parse(localStorage.getItem('eisenhower-todos') || '[]');
      if (!Array.isArray(legacy)) return [];
      const migrated = legacy.filter(item => item && typeof item.text === 'string').map(item => ({
        id: String(item.id || makeId()), text: item.text, quadrant: Math.max(0, Math.min(3, Number(String(item.q).slice(1)) - 1 || 0)),
        done: !!item.done, due: ''
      }));
      if (migrated.length) localStorage.setItem(KEY, JSON.stringify(migrated));
      return migrated;
    } catch { return []; }
  }
  function validTask(task) {
    return task && typeof task.id === 'string' && typeof task.text === 'string' &&
      Number.isInteger(task.quadrant) && task.quadrant >= 0 && task.quadrant < 4;
  }
  function makeId() {
    return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(tasks)); }
    catch { showToast('保存失败：请检查浏览器存储空间'); }
  }
  function showToast(message) {
    const toast = $('toast');
    toast.textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.hidden = true; }, 3000);
  }
  function pressed(button) { return button.getAttribute('aria-pressed') === 'true'; }
  function setPressed(button, value) { button.setAttribute('aria-pressed', String(!!value)); }
  function quadrantFor(important, urgent) { return important ? (urgent ? 0 : 1) : (urgent ? 2 : 3); }
  function prettyDue(value) {
    if (!value) return '';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
    }).format(date);
  }
  function render() {
    document.querySelectorAll('.quadrant').forEach(card => {
      const quadrant = Number(card.dataset.quadrant);
      const list = tasks.filter(task => task.quadrant === quadrant);
      card.querySelector('.count').textContent = String(list.length);
      const preview = card.querySelector('.preview');
      preview.replaceChildren(...list.slice(0, 4).map(task => taskCard(task, false)));
    });
    if (activeQuadrant !== null) renderDetail();
  }
  function taskCard(task, full) {
    const card = document.createElement('div');
    card.className = `task-card${task.done ? ' done' : ''}`;
    card.dataset.id = task.id;
    const check = document.createElement('button');
    check.type = 'button'; check.className = 'check';
    check.textContent = task.done ? '☑' : '□';
    check.setAttribute('aria-label', task.done ? '标记为未完成' : '标记为已完成');
    const label = document.createElement('span');
    label.className = 'task-label';
    label.textContent = task.text;
    if (full && task.due) {
      const due = document.createElement('span');
      due.className = 'due'; due.textContent = `◷ ${prettyDue(task.due)}`;
      label.appendChild(due);
    }
    const more = document.createElement('button');
    more.type = 'button'; more.className = 'more';
    more.textContent = full ? '编辑 ›' : '⋯';
    more.setAttribute('aria-label', '编辑待办');
    card.append(check, label, more);
    return card;
  }
  function renderDetail() {
    $('detailTitle').textContent = TITLES[activeQuadrant];
    const list = tasks.filter(task => task.quadrant === activeQuadrant);
    $('detailCount').textContent = String(list.length);
    const holder = $('detailList');
    holder.replaceChildren(...list.map(task => taskCard(task, true)));
    if (!list.length) {
      const empty = document.createElement('p');
      empty.className = 'empty'; empty.textContent = '点击下方按钮添加待办';
      holder.appendChild(empty);
    }
  }
  function route() {
    const match = location.hash.match(/^#q([0-3])$/);
    activeQuadrant = match ? Number(match[1]) : null;
    document.querySelector('.app-shell').classList.toggle('detail-mode', activeQuadrant !== null);
    home.hidden = activeQuadrant !== null;
    detail.hidden = activeQuadrant === null;
    if (activeQuadrant !== null) renderDetail();
    window.scrollTo(0, 0);
  }
  function addTask(text, quadrant, due = '') {
    const clean = text.trim();
    if (!clean) { showToast('请输入待办事项'); return false; }
    tasks.push({ id: makeId(), text: clean, quadrant, done: false, due });
    save(); render(); return true;
  }
  function addDraft(quadrant = quadrantFor(pressed($('importantToggle')), pressed($('urgentToggle')))) {
    if (!addTask(input.value, quadrant, dueInput.value)) return;
    input.value = ''; dueInput.value = ''; $('timePanel').hidden = true;
    updateTimeButton(); input.focus();
  }
  function updateTimeButton() {
    $('timeButton').textContent = dueInput.value ? `◷ ${prettyDue(dueInput.value)}` : '◷ 时间（可选）';
  }
  function findTask(id) { return tasks.find(task => task.id === id); }
  function openEditor(task = null, quadrant = activeQuadrant) {
    editingId = task?.id || null;
    $('editorHeading').textContent = task ? '编辑待办' : '新增待办';
    $('editText').value = task?.text || '';
    const q = task?.quadrant ?? quadrant ?? 3;
    setPressed($('editImportant'), q === 0 || q === 1);
    setPressed($('editUrgent'), q === 0 || q === 2);
    $('editDue').value = task?.due || '';
    $('deleteButton').hidden = !task;
    editor.showModal(); $('editText').focus();
  }
  function updateTask(id, patch) {
    const task = findTask(id);
    if (!task) return;
    Object.assign(task, patch); save(); render();
  }
  function reorder(id, anchorId, after) {
    if (id === anchorId) return;
    const from = tasks.findIndex(task => task.id === id);
    if (from < 0) return;
    const [task] = tasks.splice(from, 1);
    const anchor = tasks.findIndex(item => item.id === anchorId);
    if (anchor < 0) { tasks.splice(from, 0, task); return; }
    tasks.splice(anchor + (after ? 1 : 0), 0, task);
    save(); render();
  }

  $('addButton').addEventListener('click', () => addDraft());
  input.addEventListener('keydown', event => { if (event.key === 'Enter') addDraft(); });
  [$('importantToggle'), $('urgentToggle'), $('editImportant'), $('editUrgent')].forEach(button =>
    button.addEventListener('click', () => setPressed(button, !pressed(button))));
  $('timeButton').addEventListener('click', () => { $('timePanel').hidden = !$('timePanel').hidden; });
  dueInput.addEventListener('change', updateTimeButton);
  $('clearTime').addEventListener('click', () => { dueInput.value = ''; updateTimeButton(); $('timePanel').hidden = true; });
  document.querySelectorAll('[data-open]').forEach(button => button.addEventListener('click', () => {
    location.hash = `q${button.dataset.open}`;
  }));
  $('backButton').addEventListener('click', () => { location.hash = ''; });
  $('detailAdd').addEventListener('click', () => openEditor());
  $('cancelButton').addEventListener('click', () => editor.close());
  $('editorForm').addEventListener('submit', event => {
    event.preventDefault();
    const text = $('editText').value.trim();
    if (!text) return;
    const quadrant = quadrantFor(pressed($('editImportant')), pressed($('editUrgent')));
    const due = $('editDue').value;
    if (editingId) updateTask(editingId, { text, quadrant, due });
    else addTask(text, quadrant, due);
    editor.close();
  });
  $('deleteButton').addEventListener('click', () => {
    if (!editingId || !confirm('确定删除这条待办吗？')) return;
    tasks = tasks.filter(task => task.id !== editingId);
    save(); render(); editor.close();
  });
  document.addEventListener('click', event => {
    const card = event.target.closest('.task-card');
    if (!card || card.dataset.suppressClick) return;
    const task = findTask(card.dataset.id);
    if (!task) return;
    if (event.target.closest('.check')) updateTask(task.id, { done: !task.done });
    else if (event.target.closest('.more') || activeQuadrant !== null) openEditor(task);
  });
  window.addEventListener('hashchange', route);

  // One pointer path works for mouse and touch: drag a draft into a quadrant,
  // drag an existing card to another quadrant, or reorder in detail.
  function beginDrag(event, source, id = null) {
    if (event.button !== 0 || event.target.closest('button')) return;
    const origin = { x: event.clientX, y: event.clientY };
    let ghost = null, target = null, after = false;
    const text = source === 'draft' ? input.value.trim() : findTask(id)?.text;
    if (!text) { if (source === 'draft') showToast('请先输入待办事项'); return; }
    const move = e => {
      if (!ghost && Math.hypot(e.clientX - origin.x, e.clientY - origin.y) < 8) return;
      if (!ghost) {
        ghost = document.createElement('div'); ghost.className = 'drag-ghost';
        ghost.textContent = text; document.body.appendChild(ghost);
      }
      ghost.style.left = `${e.clientX + 10}px`; ghost.style.top = `${e.clientY + 10}px`;
      document.querySelectorAll('.drag-over').forEach(node => node.classList.remove('drag-over'));
      const beneath = document.elementFromPoint(e.clientX, e.clientY);
      target = activeQuadrant === null ? beneath?.closest('.quadrant') : beneath?.closest('#detailList .task-card');
      if (target) {
        target.classList.add('drag-over');
        after = e.clientY > target.getBoundingClientRect().top + target.getBoundingClientRect().height / 2;
      }
    };
    const end = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', end);
      window.removeEventListener('pointercancel', cancel);
      document.querySelectorAll('.drag-over').forEach(node => node.classList.remove('drag-over'));
      if (ghost && target) {
        if (activeQuadrant !== null) reorder(id, target.dataset.id, after);
        else if (source === 'draft') addDraft(Number(target.dataset.quadrant));
        else updateTask(id, { quadrant: Number(target.dataset.quadrant) });
      }
      if (ghost && id) {
        const card = document.querySelector(`.task-card[data-id="${CSS.escape(id)}"]`);
        if (card) { card.dataset.suppressClick = 'true'; setTimeout(() => delete card.dataset.suppressClick, 150); }
      }
      ghost?.remove();
    };
    const cancel = () => { target = null; end(); };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', end, { once: true });
    window.addEventListener('pointercancel', cancel, { once: true });
  }
  $('draftHandle').addEventListener('pointerdown', event => beginDrag(event, 'draft'));
  document.addEventListener('pointerdown', event => {
    const card = event.target.closest('.task-card');
    if (card) beginDrag(event, 'task', card.dataset.id);
  });
  route(); render();
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
})();
