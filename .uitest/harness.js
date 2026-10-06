/*
 * Frontend smoke test: runs the real app.js against a minimal DOM and asserts that the
 * interactions a user actually performs are wired up.
 *
 * Why this exists: a single bad id reference (a bare id passed where the helper wanted
 * "#id") threw at module top level. Everything after it never executed, so the "+ new"
 * button and the drag handle were both dead while the page still looked fine. That is
 * invisible to backend tests and to `node --check`, so it needs its own guard.
 *
 * Run:  node .uitest/harness.js       (exit code 1 on failure)
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(ROOT, 'src/main/resources/static/legacy.html'), 'utf8');
let src = fs.readFileSync(path.join(ROOT, 'src/main/resources/static/legacy-app.js'), 'utf8')
  .replace(/^'use strict';/m, '');

const failures = [];
function check(name, ok, extra = '') {
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? '  ' + extra : ''}`);
  if (!ok) failures.push(name);
}

// ------------------------------------------------------------------ minimal DOM

class El {
  constructor(tag) {
    this.tagName = String(tag).toUpperCase();
    this.children = [];
    this.parentNode = null;
    this.style = { cssText: '' };
    this.dataset = {};
    this._text = '';
    this._listeners = {};
    this._attrs = {};
    this.classList = {
      _s: new Set(),
      add: (...c) => c.forEach((x) => this.classList._s.add(x)),
      remove: (...c) => c.forEach((x) => this.classList._s.delete(x)),
      toggle: (c, on) => (on ? this.classList._s.add(c) : this.classList._s.delete(c)),
      contains: (c) => this.classList._s.has(c),
    };
    this.className = '';
  }
  get textContent() { return this._text + this.children.map((c) => c.textContent || '').join(''); }
  set textContent(v) { this._text = String(v); this.children = []; }
  set innerHTML(v) { this._text = String(v); this.children = []; }
  appendChild(c) { if (c) { c.parentNode = this; this.children.push(c); } return c; }
  append(...cs) { cs.forEach((c) => this.appendChild(c)); }
  remove() {
    if (this.parentNode) {
      const i = this.parentNode.children.indexOf(this);
      if (i >= 0) this.parentNode.children.splice(i, 1);
      this.parentNode = null;
    }
  }
  replaceChildren(...cs) { this.children = []; this._text = ''; cs.forEach((c) => this.appendChild(c)); }
  addEventListener(type, fn) { (this._listeners[type] = this._listeners[type] || []).push(fn); }
  removeEventListener(type, fn) {
    const l = this._listeners[type] || [];
    const i = l.indexOf(fn); if (i >= 0) l.splice(i, 1);
  }
  dispatch(type, ev = {}) {
    (this._listeners[type] || []).slice().forEach((fn) => fn({ preventDefault() {}, ...ev }));
  }
  setAttribute(k, v) { this._attrs[k] = v; }
  getAttribute(k) { return this._attrs[k]; }
  querySelector(sel) { return this.querySelectorAll(sel)[0] || null; }
  querySelectorAll(sel) {
    const cls = sel.replace(/^\./, '');
    const out = [];
    const walk = (n) => (n.children || []).forEach((c) => {
      if (c.classList && (c.classList.contains(cls) || c.className.split(/\s+/).includes(cls))) out.push(c);
      walk(c);
    });
    walk(this);
    return out;
  }
  focus() {}
  click() { if (typeof this.onclick === 'function') this.onclick(); }
}

const byId = new Map();
const IDS = [...html.matchAll(/id="([^"]+)"/g)].map((m) => m[1]);
IDS.forEach((id) => { const e = new El('div'); e.id = id; byId.set(id, e); });

global.document = {
  getElementById: (id) => byId.get(id) || null,
  createElement: (t) => new El(t),
  createTextNode: (t) => { const n = new El('#text'); n._text = String(t); Object.defineProperty(n, 'textContent', { get: () => n._text }); return n; },
  querySelectorAll: (sel) => {
    const cls = sel.replace(/^\./, '');
    return [...byId.values()].filter((e) => e.classList.contains(cls));
  },
  addEventListener() {},
  body: new El('body'),
};
global.__winListeners = {};
global.window = {
  innerWidth: 1600,
  location: { href: '' },
  addEventListener: (t, fn) => { (global.__winListeners[t] = global.__winListeners[t] || []).push(fn); },
  removeEventListener: (t, fn) => {
    const l = global.__winListeners[t] || []; const i = l.indexOf(fn); if (i >= 0) l.splice(i, 1);
  },
};
global.navigator = { clipboard: { writeText: async () => {} } };
global.localStorage = { _d: {}, getItem(k) { return this._d[k] ?? null; }, setItem(k, v) { this._d[k] = String(v); } };
global.Option = function (t, v) { const o = new El('option'); o.text = t; o.value = v; return o; };
global.confirm = () => true;
global.setTimeout = () => 0;
global.clearTimeout = () => {};
global.fetch = async () => ({ ok: true, status: 200, text: async () => '{}' });

// ------------------------------------------------------------------ run

console.log('=== 1. app.js 初始化不抛异常 ===');
let bootError = null;
try {
  // eslint-disable-next-line no-eval
  eval(src);
} catch (e) {
  bootError = e;
}
check('模块顶层无异常（有异常会让后续所有绑定失效）', !bootError, bootError ? bootError.message : '');

console.log('\n=== 2. 所有 id 引用都能在 html 中找到 ===');
// Strip comments first: the helper's own doc block shows a sample lookup that is not
// a real reference, and a false positive here would make this guard untrustworthy.
const codeOnly = src
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/(^|[^:])\/\/.*$/gm, '$1');
const referenced = new Set([...codeOnly.matchAll(/[$]\('#([A-Za-z0-9_-]+)'\)/g)].map((m) => m[1]));
const missing = [...referenced].filter((id) => !byId.has(id));
check('无缺失 id', missing.length === 0, missing.join(', '));

console.log('\n=== 3.「+ 新增」按钮能打开弹窗 ===');
const overlay = byId.get('editorOverlay');
check('newBtn.onclick 已绑定', typeof byId.get('newBtn').onclick === 'function');
try {
  byId.get('newBtn').click();
  check('点击后弹窗显示', overlay.classList.contains('show'));
  check('标题已更新为新增', byId.get('editorTitle').textContent.includes('新增'));
  check('语言下拉已填充', byId.get('sLanguage').children.length > 5,
    `选项=${byId.get('sLanguage').children.length}`);
} catch (e) {
  check('点击「+ 新增」不抛异常', false, e.message);
}

console.log('\n=== 4. 取消按钮能关闭弹窗 ===');
try {
  byId.get('cancelBtn').onclick();
  check('点击取消后弹窗隐藏', !overlay.classList.contains('show'));
} catch (e) {
  check('点击取消不抛异常', false, e.message);
}

console.log('\n=== 5. 分隔条可拖动且比例正确 ===');
const resizer = byId.get('resizer');
const aside = byId.get('workAside');
check('resizer 已绑定 mousedown', (resizer._listeners.mousedown || []).length > 0);
try {
  // drag to x=1000 on a 1600px viewport => 600px from the right => 37.5%
  resizer.dispatch('mousedown', { clientX: 1200 });
  const moves = global.__winListeners.mousemove || [];
  check('mousedown 后进入拖动状态', moves.length > 0);
  moves.slice().forEach((fn) => fn({ clientX: 1000 }));
  check('拖动后写入 flexBasis', aside.style.flexBasis === '37.50%', `实际=${aside.style.flexBasis}`);
  (global.__winListeners.mouseup || []).slice().forEach((fn) => fn({}));
  check('mouseup 后清理监听（无泄漏）', (global.__winListeners.mousemove || []).length === 0);

  // clamping: drag far right must not collapse the panel below the minimum
  resizer.dispatch('mousedown', { clientX: 1200 });
  (global.__winListeners.mousemove || []).slice().forEach((fn) => fn({ clientX: 1595 }));
  const minPct = parseFloat(aside.style.flexBasis);
  check('拖到最右时受最小宽度限制', minPct >= 17 && minPct <= 18, `实际=${aside.style.flexBasis}`);
  (global.__winListeners.mouseup || []).slice().forEach((fn) => fn({}));

  resizer.dispatch('mousedown', { clientX: 1200 });
  (global.__winListeners.mousemove || []).slice().forEach((fn) => fn({ clientX: 100 }));
  const maxPct = parseFloat(aside.style.flexBasis);
  check('拖到最左时受最大宽度限制', maxPct <= 62.01, `实际=${aside.style.flexBasis}`);
  (global.__winListeners.mouseup || []).slice().forEach((fn) => fn({}));
} catch (e) {
  check('拖动不抛异常', false, e.message + '\n' + e.stack);
}

console.log('\n=== 6. 清空对话可用 ===');
try {
  byId.get('clearThreadBtn').onclick();
  check('清空后线程仅剩提示', byId.get('thread').children.length === 1);
} catch (e) {
  check('清空不抛异常', false, e.message);
}

console.log('');
if (failures.length) {
  console.log(`FAILED: ${failures.length} 项 -> ${failures.join(' | ')}`);
  process.exitCode = 1;
} else {
  console.log('ALL PASS');
}
