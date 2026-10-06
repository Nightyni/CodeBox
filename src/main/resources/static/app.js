/*
 * CodeBox frontend.
 *
 * XSS rule enforced throughout: user-controlled data (titles, code, tags, LLM
 * output) is only ever written with textContent / createTextNode — never with
 * innerHTML. The previous JSP version rendered snippet content unescaped, which
 * was a stored-XSS hole; this file is the fix.
 */
'use strict';

const LANGUAGES = ['Java', 'JavaScript', 'Python', 'SQL', 'HTML/CSS', 'XML', 'Go',
  'TypeScript', 'Docker', 'Git', 'Other'];

const state = {
  pageNum: 1,
  pageSize: 6,
  keyword: '',
  language: '',
  tags: '',
  totalPages: 1,
};

const $ = (id) => document.getElementById(id);

// ---------------------------------------------------------------- utilities

function toast(message, isError = false) {
  const el = $('toast');
  el.textContent = message;
  el.classList.toggle('error', isError);
  el.classList.add('show');
  clearTimeout(toast._t);
  toast._t = setTimeout(() => el.classList.remove('show'), 3200);
}

/** Thin fetch wrapper that surfaces backend validation messages. */
async function api(path, options = {}) {
  const res = await fetch(path, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (res.status === 204) return null;

  let body = null;
  const text = await res.text();
  if (text) {
    try { body = JSON.parse(text); } catch { body = { message: text }; }
  }
  if (!res.ok) {
    const err = new Error((body && body.message) || `请求失败 (${res.status})`);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  return body;
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}

function formatTime(value) {
  if (!value) return '';
  // Backend sends ISO-8601 (write-dates-as-timestamps: false).
  return String(value).replace('T', ' ').slice(0, 16);
}

// ---------------------------------------------------------------- auth

function showApp(user) {
  $('authView').style.display = 'none';
  $('appView').style.display = '';
  $('logoutBtn').style.display = '';
  $('whoami').textContent = `已登录：${user.username}`;
  loadSnippets();
}

function showAuth() {
  $('authView').style.display = '';
  $('appView').style.display = 'none';
  $('logoutBtn').style.display = 'none';
  $('whoami').textContent = '';
}

$('tabLogin').onclick = () => switchTab(true);
$('tabRegister').onclick = () => switchTab(false);

function switchTab(isLogin) {
  $('tabLogin').classList.toggle('active', isLogin);
  $('tabRegister').classList.toggle('active', !isLogin);
  $('loginForm').style.display = isLogin ? '' : 'none';
  $('registerForm').style.display = isLogin ? 'none' : '';
}

$('loginForm').onsubmit = async (e) => {
  e.preventDefault();
  $('loginError').textContent = '';
  const form = new FormData(e.target);
  try {
    const user = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        username: form.get('username'),
        password: form.get('password'),
      }),
    });
    toast(`欢迎回来，${user.username}`);
    showApp(user);
  } catch (err) {
    $('loginError').textContent = err.message;
  }
};

$('registerForm').onsubmit = async (e) => {
  e.preventDefault();
  $('registerError').textContent = '';
  const form = new FormData(e.target);
  try {
    await api('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: form.get('username'),
        password: form.get('password'),
        email: form.get('email'),
      }),
    });
    toast('注册成功，请登录');
    switchTab(true);
  } catch (err) {
    const fields = err.body && err.body.fields;
    $('registerError').textContent =
      fields ? Object.values(fields).join('；') : err.message;
  }
};

$('logoutBtn').onclick = async () => {
  await api('/api/auth/logout', { method: 'POST' });
  showAuth();
  toast('已退出登录');
};

// ---------------------------------------------------------------- list

function renderSnippets(page) {
  const grid = $('grid');
  grid.replaceChildren();

  const items = page.items || [];
  $('emptyState').style.display = items.length ? 'none' : '';
  state.totalPages = page.totalPages || 1;

  for (const snippet of items) {
    const card = el('div', 'snippet');

    card.appendChild(el('h3', null, snippet.title));

    const meta = el('div', 'meta');
    meta.appendChild(el('span', 'pill lang', snippet.language));
    if (snippet.tags) {
      for (const tag of snippet.tags.split(',')) {
        const t = tag.trim();
        if (t) meta.appendChild(el('span', 'pill', t));
      }
    }
    meta.appendChild(el('span', null, `${snippet.useCount || 0} 次使用`));
    meta.appendChild(el('span', null, formatTime(snippet.createTime)));
    card.appendChild(meta);

    if (snippet.summary) {
      card.appendChild(el('div', 'summary', `AI 摘要：${snippet.summary}`));
    }

    // textContent only -> literal code display, no HTML execution
    const pre = el('pre');
    pre.appendChild(document.createTextNode(
      snippet.content.length > 260 ? `${snippet.content.slice(0, 260)}…` : snippet.content));
    card.appendChild(pre);

    const actions = el('div', 'actions');
    const viewBtn = el('button', null, '查看');
    viewBtn.onclick = () => openEditor(snippet.id, true);
    const editBtn = el('button', null, '编辑');
    editBtn.onclick = () => openEditor(snippet.id, false);
    const copyBtn = el('button', null, '复制');
    copyBtn.onclick = async () => {
      try {
        await navigator.clipboard.writeText(snippet.content);
        toast('已复制到剪贴板');
      } catch {
        toast('复制失败，请手动选择', true);
      }
    };
    const delBtn = el('button', 'danger', '删除');
    delBtn.onclick = () => removeSnippet(snippet.id, snippet.title);
    actions.append(viewBtn, editBtn, copyBtn, delBtn);
    card.appendChild(actions);

    grid.appendChild(card);
  }

  renderPager(page);
}

function renderPager(page) {
  const pager = $('pager');
  pager.replaceChildren();
  if (state.totalPages <= 1) return;

  const prev = el('button', null, '上一页');
  prev.disabled = state.pageNum <= 1;
  prev.onclick = () => { state.pageNum -= 1; loadSnippets(); };

  const label = el('span', 'current',
    `第 ${state.pageNum} / ${state.totalPages} 页 · 共 ${page.totalCount} 条`);

  const next = el('button', null, '下一页');
  next.disabled = state.pageNum >= state.totalPages;
  next.onclick = () => { state.pageNum += 1; loadSnippets(); };

  pager.append(prev, label, next);
}

async function loadSnippets() {
  const params = new URLSearchParams({
    pageNum: state.pageNum,
    pageSize: state.pageSize,
  });
  if (state.keyword) params.set('keyword', state.keyword);
  if (state.language) params.set('language', state.language);
  if (state.tags) params.set('tags', state.tags);

  try {
    const page = await api(`/api/snippets?${params}`);
    renderSnippets(page);
  } catch (err) {
    if (err.status === 401) { showAuth(); return; }
    toast(err.message, true);
  }
}

$('searchBtn').onclick = () => {
  state.keyword = $('fKeyword').value.trim();
  state.language = $('fLanguage').value;
  state.tags = $('fTags').value.trim();
  state.pageNum = 1;
  loadSnippets();
};

$('resetBtn').onclick = () => {
  $('fKeyword').value = '';
  $('fLanguage').value = '';
  $('fTags').value = '';
  state.keyword = '';
  state.language = '';
  state.tags = '';
  state.pageNum = 1;
  loadSnippets();
};

for (const id of ['fKeyword', 'fTags']) {
  $(id).addEventListener('keydown', (e) => {
    if (e.key === 'Enter') $('searchBtn').click();
  });
}

// ---------------------------------------------------------------- editor

function fillLanguageOptions() {
  const filter = $('fLanguage');
  const editor = $('sLanguage');
  for (const lang of LANGUAGES) {
    filter.appendChild(new Option(lang, lang));
    editor.appendChild(new Option(lang, lang));
  }
}

async function openEditor(id, readOnly) {
  clearFormErrors();
  try {
    const snippet = await api(`/api/snippets/${id}`);
    $('editorTitle').textContent = readOnly ? '查看代码片段' : '编辑代码片段';
    $('snippetId').value = snippet.id;
    $('sTitle').value = snippet.title;
    $('sLanguage').value = snippet.language;
    $('sTags').value = snippet.tags || '';
    $('sContent').value = snippet.content;
    setEditorDisabled(readOnly);
    $('editorOverlay').classList.add('show');
  } catch (err) {
    toast(err.message, true);
  }
}

function setEditorDisabled(readOnly) {
  for (const id of ['sTitle', 'sLanguage', 'sTags', 'sContent']) {
    $(id).disabled = readOnly;
  }
  $('saveBtn').style.display = readOnly ? 'none' : '';
}

function openNew() {
  clearFormErrors();
  $('editorTitle').textContent = '新增代码片段';
  $('snippetId').value = '';
  $('sTitle').value = '';
  $('sLanguage').value = 'Java';
  $('sTags').value = '';
  $('sContent').value = '';
  setEditorDisabled(false);
  $('editorOverlay').classList.add('show');
}

function clearFormErrors() {
  for (const id of ['titleError', 'languageError', 'contentError']) $(id).textContent = '';
}

function showFieldErrors(body) {
  clearFormErrors();
  const fields = body && body.fields;
  if (!fields) return;
  if (fields.title) $('titleError').textContent = fields.title;
  if (fields.language) $('languageError').textContent = fields.language;
  if (fields.content) $('contentError').textContent = fields.content;
}

$('newBtn').onclick = openNew;
$('cancelBtn').onclick = () => $('editorOverlay').classList.remove('show');
$('editorOverlay').onclick = (e) => {
  if (e.target === $('editorOverlay')) $('editorOverlay').classList.remove('show');
};

$('saveBtn').onclick = async () => {
  clearFormErrors();
  const id = $('snippetId').value;
  const payload = {
    title: $('sTitle').value.trim(),
    language: $('sLanguage').value,
    tags: $('sTags').value.trim(),
    content: $('sContent').value,
  };

  if (!payload.title) { $('titleError').textContent = '标题不能为空'; return; }
  if (!payload.content.trim()) { $('contentError').textContent = '代码内容不能为空'; return; }

  $('saveBtn').disabled = true;
  $('saveBtn').textContent = '保存中…';
  try {
    if (id) {
      await api(`/api/snippets/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
      toast('已更新');
    } else {
      await api('/api/snippets', { method: 'POST', body: JSON.stringify(payload) });
      toast('已保存，AI 已补全标签与摘要');
    }
    $('editorOverlay').classList.remove('show');
    state.pageNum = 1;
    loadSnippets();
  } catch (err) {
    showFieldErrors(err.body);
    toast(err.message, true);
  } finally {
    $('saveBtn').disabled = false;
    $('saveBtn').textContent = '保存';
  }
};

async function removeSnippet(id, title) {
  if (!confirm(`确定删除「${title}」吗？`)) return;
  try {
    // DELETE verb, not a GET link: a GET delete is triggerable by prefetch/CSRF.
    await api(`/api/snippets/${id}`, { method: 'DELETE' });
    toast('已删除');
    loadSnippets();
  } catch (err) {
    toast(err.message, true);
  }
}

// ---------------------------------------------------------------- AI Agent

/**
 * Conversation state for the agent.
 *
 * history is sent back each turn so the agent can hold a multi-turn conversation
 * ("find X" -> "analyze the first one") before session persistence exists (P5).
 */
const agent = { history: [], busy: false };

$('askBtn').onclick = sendToAgent;
$('reindexBtn').onclick = reindex;
$('askInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') sendToAgent();
});

/**
 * Renders the answer panel.
 *
 * Writes into #citations, whose visibility is not controlled by any stylesheet rule,
 * rather than #answer which starts as CSS `display:none` and depends on an inline
 * style to appear.
 */
function renderAnswer(text, { badge, isError = false, isNotice = false } = {}) {
  const box = $('citations');
  box.replaceChildren();

  const panel = el('div', isError ? 'answer-panel error'
    : (isNotice ? 'answer-panel notice' : 'answer-panel'));
  panel.appendChild(document.createTextNode(text));
  if (badge) {
    panel.appendChild(document.createTextNode(' '));
    panel.appendChild(el('span', 'badge', badge));
  }
  box.appendChild(panel);
  return panel;
}

/** Shows which tools the agent actually invoked - the run is not a black box. */
function renderTrace(trace) {
  const box = $('agentTrace');
  box.replaceChildren();
  (trace || []).forEach((step) => {
    const isTool = step.type === 'TOOL_CALL';
    const node = el('div', isTool ? 'trace-step tool' : 'trace-step');
    const label = isTool ? `调用工具 ${step.tool}` : `模型第 ${step.step} 步`;
    node.appendChild(el('span', 'toolname', label));
    if (step.detail) node.appendChild(document.createTextNode(` — ${step.detail}`));
    if (step.elapsedMs) node.appendChild(document.createTextNode(` (${step.elapsedMs}ms)`));
    box.appendChild(node);
  });
}

/** Approval box for a write action the agent proposed but did not perform. */
function renderPending(pending) {
  const box = $('agentTrace');
  const wrap = el('div', 'confirm-box');
  wrap.appendChild(el('div', 'what', `需要你确认：${pending.summary}`));
  wrap.appendChild(el('div', 'args', pending.arguments || '{}'));

  const actions = el('div', 'confirm-actions');
  const yes = el('button', 'primary', '确认执行');
  const no = el('button', null, '取消');
  yes.onclick = () => resolvePending(pending.id, true);
  no.onclick = () => resolvePending(pending.id, false);
  actions.append(yes, no);
  wrap.appendChild(actions);
  box.appendChild(wrap);
}

async function sendToAgent() {
  const message = $('askInput').value.trim();
  if (!message) { toast('请先输入内容', true); return; }
  if (agent.busy) return;

  agent.busy = true;
  const btn = $('askBtn');
  btn.disabled = true;
  btn.textContent = '处理中…';
  $('agentTrace').replaceChildren();
  renderAnswer('正在处理…', { isNotice: true });

  try {
    const res = await api('/api/agent/chat', {
      method: 'POST',
      body: JSON.stringify({
        message,
        history: capHistory(agent.history),
      }),
    });
    applyAgentResponse(res);
    $('askInput').value = '';
    loadSnippets();
  } catch (err) {
    if (err.status === 401) { showAuth(); return; }
    renderAnswer(`请求失败：${err.message}`, { isError: true });
  } finally {
    agent.busy = false;
    btn.disabled = false;
    btn.textContent = '发送';
  }
}

function applyAgentResponse(res) {
  const needsSetup = typeof res.answer === 'string'
    && (res.answer.includes('API Key') || res.answer.includes('不支持工具调用'));

  renderAnswer(res.answer || '（没有返回内容）', {
    isNotice: needsSetup,
    badge: res.pendingAction ? '等待确认' : undefined,
  });

  renderTrace(res.trace || []);
  if (res.history) agent.history = res.history;
  if (res.pendingAction) renderPending(res.pendingAction);
}

async function resolvePending(pendingActionId, approved) {
  if (agent.busy) return;
  agent.busy = true;
  const btn = $('askBtn');
  btn.disabled = true;
  btn.textContent = '执行中…';

  try {
    const res = await api('/api/agent/confirm', {
      method: 'POST',
      body: JSON.stringify({ pendingActionId, approved }),
    });
    renderAnswer(res.answer || (approved ? '已执行' : '已取消'), { isNotice: !approved });
    renderTrace(res.trace || []);
    if (res.history) agent.history = res.history;
    // A confirmed write changes the library, so refresh the list below.
    if (approved) loadSnippets();
  } catch (err) {
    renderAnswer(`确认失败：${err.message}`, { isError: true });
  } finally {
    agent.busy = false;
    btn.disabled = false;
    btn.textContent = '发送';
  }
}

/**
 * Keeps only plain conversational turns.
 *
 * Tool-calling turns are deliberately dropped: a tool_calls message is only valid
 * together with its tool result, and the client does not keep the results. Replaying
 * the assistant half alone makes the provider reject the request, so we send just
 * user/assistant text and let the server rebuild tool interaction per turn.
 */
function capHistory(history) {
  const clean = (history || []).filter((m) => {
    if (!m || (m.role !== 'user' && m.role !== 'assistant')) return false;
    if (m.role === 'assistant' && Array.isArray(m.toolCalls) && m.toolCalls.length) return false;
    if (m.role === 'assistant' && !m.content) return false;
    return true;
  });
  const limit = 20;
  return clean.slice(Math.max(0, clean.length - limit));
}

/** Rebuilds the vector index for snippets that never went through the API (bulk imports). */
async function reindex() {
  const btn = $('reindexBtn');
  btn.disabled = true;
  const original = btn.textContent;
  btn.textContent = '重建中…';
  try {
    const res = await api('/api/ask/reindex', { method: 'POST', body: '{}' });
    toast(`索引已重建：${res.indexed} 条片段（${res.embeddingModel}）`);
  } catch (err) {
    toast(err.message, true);
  } finally {
    btn.disabled = false;
    btn.textContent = original;
  }
}
// ---------------------------------------------------------------- boot

(async function boot() {
  fillLanguageOptions();
  try {
    const user = await api('/api/auth/me');
    showApp(user);
  } catch {
    showAuth();
  }
})();
