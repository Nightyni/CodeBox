import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  capHistory,
  extractRefs,
  findOptimization,
  splitCodeBlocks,
  formatTime,
  isSetupNotice,
  computeAsideRatio,
} from '../src/utils.js';

// ---------------------------------------------------------------- capHistory

test('capHistory drops assistant toolCalls messages', () => {
  // 这是真实踩过的坑：只回传 assistant 的 tool_calls 一半，模型服务会返回
  // 400 "insufficient tool messages following tool_calls message"，表现为第二轮必失败。
  const history = [
    { role: 'user', content: '帮我找代码' },
    { role: 'assistant', toolCalls: [{ id: 'c1', name: 'search_snippets' }] },
    { role: 'tool', content: '{"count":2}', toolCallId: 'c1' },
    { role: 'assistant', content: '找到了 2 条' },
  ];

  const out = capHistory(history);

  assert.equal(out.length, 2);
  assert.deepEqual(out.map((m) => m.role), ['user', 'assistant']);
  assert.ok(out.every((m) => !m.toolCalls));
});

test('capHistory drops empty assistant messages', () => {
  const out = capHistory([
    { role: 'user', content: 'a' },
    { role: 'assistant', content: '' },
    { role: 'assistant', content: 'ok' },
  ]);
  assert.equal(out.length, 2);
});

test('capHistory keeps only the most recent turns', () => {
  const history = Array.from({ length: 30 }, (_, i) => ({ role: 'user', content: `m${i}` }));
  const out = capHistory(history, 20);
  assert.equal(out.length, 20);
  assert.equal(out[0].content, 'm10');
  assert.equal(out[19].content, 'm29');
});

test('capHistory tolerates null and malformed input', () => {
  assert.deepEqual(capHistory(null), []);
  assert.deepEqual(capHistory([null, { content: 'no role' }, { role: 'system', content: 'x' }]), []);
});

// ---------------------------------------------------------------- extractRefs

test('extractRefs pulls snippet references and de-duplicates by id', () => {
  const toolResults = [
    { tool: 'search_snippets', result: { results: [
      { id: 4, title: 'MyBatis 动态条件查询', language: 'XML' },
      { id: 3, title: 'Redis 分布式锁', language: 'Java' },
    ] } },
    { tool: 'search_snippets', result: { results: [
      { id: 4, title: 'MyBatis 动态条件查询', language: 'XML' },
      { id: 1, title: '分页查询', language: 'SQL' },
    ] } },
  ];

  const refs = extractRefs(toolResults);

  assert.equal(refs.length, 3);
  assert.deepEqual(refs.map((r) => r.id), [4, 3, 1]);
});

test('extractRefs ignores tools whose output is not a results list', () => {
  assert.deepEqual(extractRefs([
    { tool: 'get_snippet', result: { id: 4, content: 'code' } },
    { tool: 'optimize_snippet', result: null },
    { tool: 'x', result: { results: 'not-an-array' } },
  ]), []);
});

// ---------------------------------------------------------------- findOptimization

test('findOptimization returns the structured optimisation payload', () => {
  const payload = { changes: ['a'], optimizedCode: 'x', diff: [{ type: 'ADDED' }] };
  const found = findOptimization([
    { tool: 'get_snippet', result: { content: 'c' } },
    { tool: 'optimize_snippet', result: payload },
  ]);
  assert.equal(found, payload);
});

test('findOptimization returns null when there is no diff', () => {
  assert.equal(findOptimization([{ tool: 'optimize_snippet', result: { changes: [] } }]), null);
  assert.equal(findOptimization([]), null);
  assert.equal(findOptimization(null), null);
});

// ---------------------------------------------------------------- splitCodeBlocks

test('splitCodeBlocks separates prose from fenced code', () => {
  const blocks = splitCodeBlocks('说明如下：\n```java\nint a = 1;\n```\n结束');

  assert.equal(blocks.length, 3);
  assert.equal(blocks[0].type, 'text');
  assert.equal(blocks[1].type, 'code');
  assert.equal(blocks[1].text, 'int a = 1;');   // 语言标记与首尾换行被去掉
  assert.equal(blocks[2].type, 'text');
});

test('splitCodeBlocks handles text with no code', () => {
  const blocks = splitCodeBlocks('只有文字');
  assert.deepEqual(blocks, [{ type: 'text', text: '只有文字' }]);
});

test('splitCodeBlocks tolerates null and empty', () => {
  assert.deepEqual(splitCodeBlocks(null), []);
  assert.deepEqual(splitCodeBlocks(''), []);
});

// ---------------------------------------------------------------- misc

test('formatTime trims ISO strings to minutes', () => {
  assert.equal(formatTime('2026-10-06T21:03:25.123'), '2026-10-06 21:03');
  assert.equal(formatTime(null), '');
});

test('isSetupNotice recognises the two actionable messages', () => {
  assert.equal(isSetupNotice('还没有配置大模型 API Key，所以无法生成回答。'), true);
  assert.equal(isSetupNotice('当前模型不支持工具调用'), true);
  assert.equal(isSetupNotice('找到了 2 条片段'), false);
  assert.equal(isSetupNotice(null), false);
});

// ---------------------------------------------------------------- computeAsideRatio

test('computeAsideRatio measures from the right edge', () => {
  // 1600 宽，拖到 x=1000 -> 右侧 600px -> 37.5%
  assert.equal(computeAsideRatio(1000, 1600), 0.375);
});

test('computeAsideRatio clamps to the minimum pixel width', () => {
  // 拖到最右：右侧 5px，但最小 280px
  assert.equal(computeAsideRatio(1595, 1600), 280 / 1600);
});

test('computeAsideRatio clamps to the maximum ratio', () => {
  // 拖到最左：会占满整屏，但上限 62%
  assert.equal(computeAsideRatio(0, 1600), 0.62);
});

test('computeAsideRatio survives a zero-width viewport', () => {
  assert.equal(computeAsideRatio(100, 0), 0.62);
});
