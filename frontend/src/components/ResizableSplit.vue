<script setup>
import { ref, onMounted } from 'vue';
import { computeAsideRatio } from '../utils.js';

/*
 * 左右分栏，右侧面板宽度可拖动。
 *
 * 用 inline flex-basis 控制比例（而不是 grid-template-columns），这样主区保持流式，
 * 并且比例能直接存下来复用。边界计算放在 utils.computeAsideRatio 里，可单测。
 */
const props = defineProps({
  storageKey: { type: String, default: 'codebox.asideRatio' },
  defaultRatio: { type: Number, default: 0.3 },
});

const asideEl = ref(null);
const ratio = ref(props.defaultRatio);
const dragging = ref(false);

function applyRatio(value) {
  ratio.value = value;
  if (asideEl.value) asideEl.value.style.flexBasis = `${(value * 100).toFixed(2)}%`;
}

function onMouseDown(event) {
  event.preventDefault();
  dragging.value = true;
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';

  const onMove = (e) => applyRatio(computeAsideRatio(e.clientX, window.innerWidth));
  const onUp = () => {
    dragging.value = false;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    window.removeEventListener('mousemove', onMove);
    window.removeEventListener('mouseup', onUp);
    try {
      localStorage.setItem(props.storageKey, asideEl.value.style.flexBasis);
    } catch { /* 隐私模式下 localStorage 可能不可用，忽略即可 */ }
  };

  window.addEventListener('mousemove', onMove);
  window.addEventListener('mouseup', onUp);
}

/** 双击分隔条恢复默认比例。 */
function resetRatio() {
  applyRatio(props.defaultRatio);
  try {
    localStorage.setItem(props.storageKey, asideEl.value.style.flexBasis);
  } catch { /* ignore */ }
}

onMounted(() => {
  let saved = null;
  try {
    saved = localStorage.getItem(props.storageKey);
  } catch { /* ignore */ }
  if (saved && saved.endsWith('%')) {
    const pct = parseFloat(saved);
    if (!Number.isNaN(pct)) {
      asideEl.value.style.flexBasis = saved;
      ratio.value = pct / 100;
      return;
    }
  }
  applyRatio(props.defaultRatio);
});
</script>

<template>
  <div class="workspace">
    <section class="pane-main"><slot name="main" /></section>

    <div
      class="resizer"
      :class="{ dragging }"
      title="拖动调整宽度，双击恢复默认"
      @mousedown="onMouseDown"
      @dblclick="resetRatio"
    ></div>

    <aside ref="asideEl" class="pane-aside">
      <slot name="aside" />
    </aside>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  align-items: flex-start;
  gap: 0;
  padding: 14px 18px;
  min-height: calc(100vh - 60px);
}

.pane-main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pane-aside {
  flex: 0 0 auto;
  min-width: 280px;
  max-width: 62%;
  position: sticky;
  top: 74px;
  height: calc(100vh - 92px);
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
}

/* 拖动条本身有 10px 的命中区域，但视觉上只有 2px，避免阻断点击 */
.resizer {
  flex: 0 0 10px;
  align-self: stretch;
  cursor: col-resize;
  position: relative;
}
.resizer::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--border);
  border-radius: 2px;
  transition: background .15s, width .15s, left .15s;
}
.resizer:hover::before,
.resizer.dragging::before { background: var(--accent); width: 3px; left: 3px; }
</style>
