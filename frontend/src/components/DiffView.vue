<script setup>
/*
 * 左右并排的行级 diff。
 *
 * diff 由服务端计算（见 DiffEngine），这里只负责渲染：这样"改了什么"是工具的契约，
 * 而不是各前端各比各的。
 *
 * 渲染细节：相等行两侧都显示同一段文本，而不是一侧留空——这样两栏能保持垂直对齐；
 * 只有一侧缺失时才用空占位。
 */
const props = defineProps({
  result: { type: Object, required: true },
});
</script>

<template>
  <div class="diffwrap">
    <div class="diffhead">
      <span class="add">+{{ result.diffSummary?.added ?? 0 }}</span>
      <span class="del">-{{ result.diffSummary?.removed ?? 0 }}</span>
      <span class="muted">左：原代码　右：AI 优化版</span>
    </div>
    <div class="diffbody">
      <div class="diffgrid">
        <template v-for="(line, i) in result.diff" :key="i">
          <div class="diffcell" :class="line.type === 'ADDED' ? 'EMPTY' : line.type">
            <span class="ln">{{ line.originalNumber ?? '' }}</span>
            <span class="code">{{ line.original ?? '' }}</span>
          </div>
          <div class="diffcell" :class="line.type === 'REMOVED' ? 'EMPTY' : line.type">
            <span class="ln">{{ line.optimizedNumber ?? '' }}</span>
            <span class="code">{{ line.optimized ?? '' }}</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.diffwrap { border: 1px solid var(--border); border-radius: 7px; overflow: hidden; margin-top: 6px; }
.diffhead { display: flex; gap: 10px; padding: 5px 10px; background: var(--panel-2); font-size: 11px; }
.diffhead .add { color: var(--ok); }
.diffhead .del { color: var(--danger); }
.diffbody { max-height: 320px; overflow: auto; font-family: ui-monospace, Consolas, monospace; font-size: 11px; }
.diffgrid { display: grid; grid-template-columns: 1fr 1fr; }
.diffcell { display: flex; min-height: 1.5em; }
.diffcell .ln {
  flex: 0 0 32px;
  text-align: right;
  padding-right: 6px;
  color: #5a6272;
  user-select: none;
  border-right: 1px solid var(--border);
}
.diffcell .code { padding-left: 8px; flex: 1; min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.diffcell.ADDED { background: rgba(62, 207, 142, .12); }
.diffcell.REMOVED { background: rgba(255, 107, 107, .12); }
.diffcell.ADDED .code { color: #8ff0c0; }
.diffcell.REMOVED .code { color: #ffb3b3; }
.diffcell.EMPTY { background: #12141a; }
</style>
