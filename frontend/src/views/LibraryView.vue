<script setup>
import { ref, onMounted } from 'vue';
import { http } from '../api.js';
import { store } from '../store.js';
import { formatTime } from '../utils.js';
import ResizableSplit from '../components/ResizableSplit.vue';
import SnippetEditor from '../components/SnippetEditor.vue';
import AiPanel from '../components/AiPanel.vue';

const LANGUAGES = ['Java', 'JavaScript', 'Python', 'SQL', 'HTML/CSS', 'XML', 'Go',
  'TypeScript', 'Docker', 'Git', 'Other'];

const filters = ref({ keyword: '', language: '', tags: '' });
const page = ref({ items: [], pageNum: 1, pageSize: 6, totalCount: 0, totalPages: 0 });
const loading = ref(false);

const editor = ref({ open: false, id: null, readOnly: false });

async function load() {
  loading.value = true;
  try {
    const params = new URLSearchParams({
      pageNum: String(page.value.pageNum),
      pageSize: String(page.value.pageSize),
    });
    if (filters.value.keyword) params.set('keyword', filters.value.keyword);
    if (filters.value.language) params.set('language', filters.value.language);
    if (filters.value.tags) params.set('tags', filters.value.tags);

    page.value = await http.get(`/api/snippets?${params}`);
  } catch (e) {
    if (e.status === 401) { store.user = null; return; }
    store.notify(e.message);
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value.pageNum = 1;
  load();
}

function reset() {
  filters.value = { keyword: '', language: '', tags: '' };
  page.value.pageNum = 1;
  load();
}

function go(delta) {
  const next = page.value.pageNum + delta;
  if (next < 1 || (page.value.totalPages && next > page.value.totalPages)) return;
  page.value.pageNum = next;
  load();
}

function openNew() {
  editor.value = { open: true, id: null, readOnly: false };
}

function openView(id) {
  editor.value = { open: true, id, readOnly: true };
}

function openEdit(id) {
  editor.value = { open: true, id, readOnly: false };
}

async function remove(snippet) {
  if (!confirm(`确定删除「${snippet.title}」吗？`)) return;
  try {
    await http.del(`/api/snippets/${snippet.id}`);
    store.notify('已删除');
    load();
  } catch (e) {
    store.notify(e.message);
  }
}

async function copy(snippet) {
  try {
    await navigator.clipboard.writeText(snippet.content);
    store.notify('已复制到剪贴板');
  } catch {
    store.notify('复制失败，请手动选择');
  }
}

onMounted(load);
</script>

<template>
  <ResizableSplit>
    <template #main>
      <div class="card">
        <div class="row">
          <div style="flex:2">
            <label class="inline">关键词</label>
            <input v-model="filters.keyword" placeholder="标题 / 内容 / 摘要 / 标签" @keyup.enter="search">
          </div>
          <div>
            <label class="inline">语言</label>
            <select v-model="filters.language">
              <option value="">全部</option>
              <option v-for="l in LANGUAGES" :key="l" :value="l">{{ l }}</option>
            </select>
          </div>
          <div>
            <label class="inline">标签</label>
            <input v-model="filters.tags" placeholder="如：分页" @keyup.enter="search">
          </div>
          <div class="fixed-actions">
            <button class="primary" @click="search">搜索</button>
            <button @click="reset">重置</button>
          </div>
          <div class="fixed-actions">
            <button class="primary" @click="openNew">+ 新增</button>
          </div>
        </div>
      </div>

      <div v-if="loading" class="muted center">加载中…</div>

      <template v-else>
        <div v-if="!page.items.length" class="center muted empty">
          还没有代码片段，点「+ 新增」开始沉淀
        </div>

        <div v-else class="grid">
          <article v-for="s in page.items" :key="s.id" class="snippet">
            <h3 :title="s.title">{{ s.title }}</h3>

            <div class="meta">
              <span class="pill lang">{{ s.language }}</span>
              <span v-for="tag in (s.tags || '').split(',').map(t => t.trim()).filter(t => t)" :key="tag" class="pill">
                {{ tag.trim() }}
              </span>
              <span class="muted">{{ s.useCount || 0 }} 次使用</span>
              <span class="muted">{{ formatTime(s.createTime) }}</span>
            </div>

            <div v-if="s.summary" class="summary">AI 摘要：{{ s.summary }}</div>

            <pre class="code">{{ s.content.length > 260 ? s.content.slice(0, 260) + '…' : s.content }}</pre>

            <div class="actions">
              <button class="small" @click="openView(s.id)">查看</button>
              <button class="small" @click="openEdit(s.id)">编辑</button>
              <button class="small" @click="copy(s)">复制</button>
              <button class="small danger" @click="remove(s)">删除</button>
            </div>
          </article>
        </div>

        <div v-if="page.totalPages > 1" class="pager">
          <button :disabled="page.pageNum <= 1" @click="go(-1)">上一页</button>
          <span class="muted">第 {{ page.pageNum }} / {{ page.totalPages }} 页 · 共 {{ page.totalCount }} 条</span>
          <button :disabled="page.pageNum >= page.totalPages" @click="go(1)">下一页</button>
        </div>
      </template>
    </template>

    <template #aside>
      <AiPanel @open-snippet="openView" @library-changed="load" />
    </template>
  </ResizableSplit>

  <SnippetEditor
    v-if="editor.open"
    :snippet-id="editor.id"
    :read-only="editor.readOnly"
    @close="editor.open = false"
    @saved="load"
  />
</template>

<style scoped>
.fixed-actions { flex: 0 0 auto; display: flex; gap: 8px; }
.center { text-align: center; }
.empty { padding: 46px 0; }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.snippet {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.snippet h3 { margin: 0; font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }

.meta { display: flex; gap: 7px; flex-wrap: wrap; align-items: center; font-size: 11px; }

.summary { font-size: 12px; color: var(--muted); font-style: italic; }

.code {
  margin: 0;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 9px;
  max-height: 130px;
  overflow: auto;
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: #c9d1d9;
}

.actions {
  display: flex;
  gap: 7px;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}

.pager { display: flex; gap: 10px; justify-content: center; align-items: center; margin-top: 20px; }
</style>
