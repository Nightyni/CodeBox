/*
 * 纯逻辑工具函数。
 *
 * 刻意与 Vue 组件分离：组件本身难以在 Node 里测试，但这些函数是真正的"易错点"
 * （对话历史的合法性、引用去重、diff 统计），放在这里就能被单测覆盖。
 * 见 frontend/test/utils.test.js。
 */

/**
 * 只保留纯对话轮次。
 *
 * 为什么必须过滤 assistant 的 toolCalls 消息：带 tool_calls 的消息只有和它的 tool
 * 结果成对出现才合法。前端不保存工具结果，只回传 assistant 那一半会让模型服务直接
 * 返回 400（"insufficient tool messages following tool_calls message"），表现为
 * 第二轮对话必然失败。所以只发 user/assistant 文本，工具交互由服务端每轮重建。
 */
export function capHistory(history, limit = 20) {
  const clean = (history || []).filter((m) => {
    if (!m || (m.role !== 'user' && m.role !== 'assistant')) return false;
    if (m.role === 'assistant' && Array.isArray(m.toolCalls) && m.toolCalls.length) return false;
    if (m.role === 'assistant' && !m.content) return false;
    return true;
  });
  return clean.slice(Math.max(0, clean.length - limit));
}

/** 从工具结果里抽出可点击的片段引用，按 id 去重并保持出现顺序。 */
export function extractRefs(toolResults) {
  const seen = new Map();
  (toolResults || []).forEach((block) => {
    const rows = block && block.result && block.result.results;
    if (!Array.isArray(rows)) return;
    rows.forEach((r) => {
      if (r && r.id != null && !seen.has(r.id)) {
        seen.set(r.id, { id: r.id, title: r.title, language: r.language });
      }
    });
  });
  return Array.from(seen.values());
}

/** 从工具结果里找出 optimize_snippet 的结构化输出（用于渲染 diff）。 */
export function findOptimization(toolResults) {
  const block = (toolResults || []).find(
    (b) => b && b.tool === 'optimize_snippet' && b.result && b.result.diff,
  );
  return block ? block.result : null;
}

/**
 * 把助手回复拆成「普通文本」和「代码块」两种段落。
 *
 * 组件据此分别渲染，避免把模型输出当成 HTML 注入——所有文本都走 textContent。
 */
export function splitCodeBlocks(text) {
  const raw = text == null ? '' : String(text);
  return raw.split('```').map((part, i) => {
    if (i % 2 === 1) {
      // 奇数段落在围栏内：去掉可能的语言标记行
      return { type: 'code', text: part.replace(/^[A-Za-z0-9+#._-]*\n/, '').replace(/\n$/, '') };
    }
    return { type: 'text', text: part };
  }).filter((p) => p.type === 'code' || p.text.trim());
}

/** 日期展示：ISO 字符串 -> "YYYY-MM-DD HH:mm"。 */
export function formatTime(value) {
  if (!value) return '';
  return String(value).replace('T', ' ').slice(0, 16);
}

/** 判断响应是否属于"还没配 Key / 模型不支持工具"这类需要引导用户的提示。 */
export function isSetupNotice(answer) {
  return typeof answer === 'string'
    && (answer.includes('API Key') || answer.includes('不支持工具调用'));
}

/**
 * 拖动分隔条时的比例计算。
 *
 * 单独抽出来是因为这里有真实的边界逻辑：面板在右侧，所以比例要从右边缘量，
 * 并且要同时受最小像素宽和最大比例约束，否则会被拖成 0 宽或占满整屏。
 */
export function computeAsideRatio(clientX, viewportWidth, minPx = 280, maxRatio = 0.62) {
  if (!viewportWidth || viewportWidth <= 0) return maxRatio;
  const ratio = (viewportWidth - clientX) / viewportWidth;
  const minRatio = minPx / viewportWidth;
  return Math.min(maxRatio, Math.max(minRatio, ratio));
}
