<script setup>
import { ref, computed, nextTick } from 'vue';
import { http } from '../api.js';
import { store } from '../store.js';
import { capHistory, extractRefs, findOptimization, isSetupNotice } from '../utils.js';
import ChatMessage from './ChatMessage.vue';
import DiffView from './DiffView.vue';

const emit = defineEmits(['open-snippet', 'library-changed']);

/*
 * 对话线程。
 *
 * 每一轮都保留（用户气泡 + 助手回复 + 工具轨迹 + 引用 + diff + 确认框），而不是
 * 只显示最后一轮——这是产品要求，也让工具调用过程可见。
 *
 * 注意：发送给服务端的 history 用的是 capHistory 过滤后的纯对话轮次；界面上的
 * turns 是展示用的完整记录，两者不是同一个东西。
 */
const turns = ref([]);
const input = ref('');
const threadEl = ref(null);

const busy = computed(() => store.busy);

async function scrollToEnd() {
  await nextTick();
  if (threadEl.value) threadEl.value.scrollTop = threadEl.value.scrollHeight;
}

function addTurn(turn) {
  turns.value.push(turn);
  scrollToEnd();
}

async function send() {
  const message = input.value.trim();
  if (!message || busy.value) return;

  addTurn({ kind: 'user', text: message });
  input.value = '';
  const placeholder = { kind: 'agent', text: '正在处理…', notice: true };
  addTurn(placeholder);
  store.busy = true;

  try {
    const res = await http.post('/api/agent/chat', {
      message,
      history: capHistory(store.history),
    });
    applyResponse(placeholder, res);
    if (res.history) store.history = res.history;
    emit('library-changed');
  } catch (e) {
    placeholder.text = `请求失败：${e.message}`;
    placeholder.notice = false;
    placeholder.error = true;
  } finally {
    store.busy = false;
    scrollToEnd();
  }
}

/** 把一次后端响应套用到占位消息上，并追加轨迹/引用/diff/确认框。 */
function applyResponse(target, res) {
  target.text = res.answer || '（没有返回内容）';
  target.notice = isSetupNotice(res.answer);
  target.error = Boolean(res.insufficientEvidence && !res.answer);
  target.badge = res.pendingAction ? '等待确认' : '';

  if (res.trace && res.trace.length) {
    target.trace = res.trace.filter((s) => s.type === 'TOOL_CALL');
  }
  const refs = extractRefs(res.toolResults);
  if (refs.length) target.refs = refs;

  const opt = findOptimization(res.toolResults);
  if (opt) {
    target.changes = opt.changes || [];
    target.optimization = opt;
  }
  if (res.pendingAction) {
    target.pending = res.pendingAction;
  }
}

async function resolvePending(turn, approved) {
  if (busy.value) return;
  store.busy = true;
  try {
    const res = await http.post('/api/agent/confirm', {
      pendingActionId: turn.pending.id,
      approved,
    });
    turn.pending = null;
    const reply = { kind: 'agent', text: res.answer || (approved ? '已执行' : '已取消'), notice: !approved };
    if (res.trace && res.trace.length) reply.trace = res.trace.filter((s) => s.type === 'TOOL_CALL');
    addTurn(reply);
    if (res.history) store.history = res.history;
    if (approved) emit('library-changed');
  } catch (e) {
    addTurn({ kind: 'agent', text: `确认失败：${e.message}`, error: true });
  } finally {
    store.busy = false;
    scrollToEnd();
  }
}

async function reindex() {
  try {
    const res = await http.post('/api/ask/reindex');
    store.notify(`索引已重建：${res.indexed} 条片段（${res.embeddingModel}）`);
  } catch (e) {
    store.notify(e.message);
  }
}

function clearThread() {
  turns.value = [];
  store.history = [];
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    send();
  }
}
</script>

<template>
  <div class="ai-panel">
    <header class="ai-head">
      <span class="ai-title">AI 助手</span>
      <span class="muted small grow">能查 · 能分析 · 能优化 · 能保存</span>
      <button class="ghost small" @click="clearThread">清空</button>
      <button class="ghost small" title="直接导入数据库的片段没有向量，检索不到时重建" @click="reindex">
        重建索引
      </button>
    </header>

    <div ref="threadEl" class="thread">
      <div v-if="!turns.length" class="hint">
        试试这样说：<br>
        · 帮我找一下之前的 MyBatis 分页代码<br>
        · 打开第一条并分析它<br>
        · 帮我优化一下，然后保存这个版本
      </div>

      <template v-for="(turn, ti) in turns" :key="ti">
        <ChatMessage
          :role="turn.kind === 'user' ? 'user' : 'assistant'"
          :text="turn.text"
          :notice="turn.notice"
          :error="turn.error"
          :badge="turn.badge"
        />

        <!-- 工具轨迹：可折叠，让"AI 真的调用了工具"可见 -->
        <div v-if="turn.trace && turn.trace.length" class="trace">
          <details v-for="(step, si) in turn.trace" :key="si">
            <summary>调用工具 {{ step.tool }}<span v-if="step.elapsedMs" class="muted"> · {{ step.elapsedMs }}ms</span></summary>
            <div class="detail">{{ step.detail }}</div>
          </details>
        </div>

        <!-- 优化结果：改动说明 + diff -->
        <div v-if="turn.changes && turn.changes.length" class="changes-block">
          <div class="muted small">AI 改动说明</div>
          <ul class="changes">
            <li v-for="(c, ci) in turn.changes" :key="ci">{{ c }}</li>
          </ul>
        </div>
        <DiffView v-if="turn.optimization" :result="turn.optimization" />

        <!-- 引用片段：可点击打开 -->
        <div v-if="turn.refs && turn.refs.length" class="refs">
          <button
            v-for="r in turn.refs"
            :key="r.id"
            class="ref"
            @click="emit('open-snippet', r.id)"
          >
            [{{ r.id }}] {{ r.title }}<span v-if="r.language" class="muted"> · {{ r.language }}</span>
          </button>
        </div>

        <!-- 写操作确认 -->
        <div v-if="turn.pending" class="confirm">
          <div class="what">需要你确认：{{ turn.pending.summary }}</div>
          <div class="args">{{ turn.pending.arguments }}</div>
          <div class="confirm-actions">
            <button class="primary" :disabled="busy" @click="resolvePending(turn, true)">确认执行</button>
            <button :disabled="busy" @click="resolvePending(turn, false)">取消</button>
          </div>
        </div>
      </template>
    </div>

    <div class="ai-input">
      <textarea
        v-model="input"
        rows="2"
        placeholder="用一句话描述你要做什么（Enter 发送 / Shift+Enter 换行）"
        @keydown="onKeydown"
      ></textarea>
      <button class="primary" :disabled="busy" @click="send">{{ busy ? '处理中…' : '发送' }}</button>
    </div>
  </div>
</template>

<style scoped>
.ai-panel { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.ai-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--border);
  flex: 0 0 auto;
  flex-wrap: wrap;
}
.ai-title { font-weight: 600; font-size: 14px; }

.thread {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}
.hint {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.9;
  background: var(--panel-2);
  border: 1px dashed var(--border);
  border-radius: 8px;
  padding: 11px 13px;
}

.trace { display: flex; flex-direction: column; gap: 4px; }
.trace details { background: var(--bg); border: 1px solid var(--border); border-radius: 6px; font-size: 11px; }
.trace summary { cursor: pointer; padding: 5px 9px; color: var(--ok); font-weight: 600; list-style: none; }
.trace summary::-webkit-details-marker { display: none; }
.trace summary::before { content: '▸ '; color: var(--muted); }
.trace details[open] summary::before { content: '▾ '; }
.trace .detail {
  padding: 0 10px 8px;
  color: var(--muted);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: ui-monospace, Consolas, monospace;
}

.changes-block { font-size: 12px; }
.changes { margin: 4px 0 0; padding-left: 18px; }
.changes li { margin: 2px 0; }

.refs { display: flex; gap: 6px; flex-wrap: wrap; }
.ref { padding: 4px 9px; font-size: 11px; border-radius: 6px; }

.confirm {
  border: 1px solid #4a3520;
  background: #241d12;
  border-radius: 8px;
  padding: 11px 13px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.confirm .what { color: var(--warn); font-size: 13px; font-weight: 600; }
.confirm .args {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 11px;
  color: var(--muted);
  max-height: 110px;
  overflow: auto;
  white-space: pre-wrap;
}
.confirm-actions { display: flex; gap: 8px; }

.ai-input {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 11px 14px;
  border-top: 1px solid var(--border);
}
.ai-input textarea { flex: 1; resize: none; }
</style>
