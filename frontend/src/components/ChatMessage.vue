<script setup>
import { computed } from 'vue';
import { splitCodeBlocks } from '../utils.js';

/*
 * 一条消息的气泡。
 *
 * 所有模型输出都通过文本插值渲染（等价于 textContent），代码块单独放进 <pre>，
 * 因此模型回复里的任何 HTML 都不会被当成标记执行。
 */
const props = defineProps({
  role: { type: String, required: true },
  text: { type: String, default: '' },
  notice: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  badge: { type: String, default: '' },
});

const blocks = computed(() => (props.role === 'user'
  ? [{ type: 'text', text: props.text }]
  : splitCodeBlocks(props.text)));
</script>

<template>
  <div class="msg" :class="role === 'user' ? 'msg-user' : 'msg-agent'">
    <div class="bubble" :class="{ notice, error }">
      <template v-for="(block, i) in blocks" :key="i">
        <pre v-if="block.type === 'code'"><code>{{ block.text }}</code></pre>
        <span v-else>{{ block.text }}</span>
      </template>
      <span v-if="badge" class="badge">{{ badge }}</span>
    </div>
  </div>
</template>

<style scoped>
.msg { display: flex; }
.msg-user { justify-content: flex-end; }
.bubble {
  max-width: 94%;
  padding: 9px 12px;
  font-size: 13px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.msg-user .bubble {
  background: var(--accent);
  color: #fff;
  border-radius: 10px 10px 2px 10px;
}
.msg-agent .bubble {
  background: var(--panel-2);
  border: 1px solid var(--border);
  border-radius: 10px 10px 10px 2px;
}
.msg-agent .bubble.notice { border-color: #4a3520; background: #241d12; color: var(--warn); }
.msg-agent .bubble.error { border-color: #4a2020; background: #241414; color: var(--danger); }
pre {
  margin: 6px 0;
  padding: 8px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: auto;
  font-size: 11px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
