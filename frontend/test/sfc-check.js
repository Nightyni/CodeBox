/*
 * SFC sanity checker — 在没有 npm 依赖的环境里尽可能验证 .vue 文件。
 *
 * 检查并报告：
 *   1. <script setup> 的 JS 语法（把 import/export 换成等价占位后用 new Function 解析）
 *   2. <template> 标签是否严格配对
 *   3. 模板插值/指令里引用的标识符是否在 script 或 v-for 作用域内声明
 *
 * 两个曾经踩到的坑，这里都处理了：
 *   - 属性值里含 '>'（如 v-if="page.totalPages > 1"）不能被当成标签结束
 *   - v-for 引入的循环变量（item / (val, key)）是局部作用域，不是未声明
 *
 * 它替代不了 `npm run build`（需要 npm install），但能挡住语法错、标签漏闭、
 * 变量名写错这三类最常见的问题。
 *
 * 运行：node frontend/test/sfc-check.js
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.join(import.meta.dirname, '..');
const SRC = path.join(ROOT, 'src');

const VOID_TAGS = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr']);

const JS_GLOBALS = new Set(['true', 'false', 'null', 'undefined', 'typeof', 'in', 'of',
  'new', 'return', 'if', 'else', 'Number', 'String', 'Array', 'Object', 'Boolean', 'Math',
  'JSON', 'parseFloat', 'parseInt', 'isNaN', 'NaN', '$event', '$props', '$emit', 'confirm',
  'console', 'window', 'document']);

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    return e.isDirectory() ? walk(p) : [p];
  });
}

/**
 * 提取根级 <template> / <script> 块。
 *
 * 必须锚定行首：组件里会出现嵌套的 <template v-for>，用非贪婪的
 * `<template...>([\s\S]*?)</template>` 会在第一个嵌套的 </template> 就截断，
 * 于是检查器看到的是一段被切短的模板，报出一堆假的"未闭合标签"。
 */
function block(source, tag) {
  const re = new RegExp(`^<${tag}(?:\\s[^>]*)?>\\r?\\n([\\s\\S]*?)^</${tag}>\\s*$`, 'm');
  const m = source.match(re);
  return m ? m[1] : null;
}

/**
 * 标签配对检查。
 *
 * 属性部分用 ["'][^"']*["'] 优先匹配引号整体，避免把引号里的 '>' 当成标签结束——
 * 这正是 v-if="page.totalPages > 1" 会触发的错误。
 */
function checkTags(template) {
  const errors = [];
  const stack = [];
  const re = /<(\/?)([A-Za-z][\w.-]*)((?:"[^"]*"|'[^']*'|[^>])*)>/g;
  let m;
  while ((m = re.exec(template)) !== null) {
    const closing = m[1] === '/';
    const name = m[2];
    const selfClosing = /\/\s*$/.test(m[3]);
    if (VOID_TAGS.has(name.toLowerCase())) continue;

    if (closing) {
      const open = stack.pop();
      if (open !== name) errors.push(`标签不匹配：</${name}> 对应 <${open ?? '(无)'}>`);
    } else if (!selfClosing) {
      stack.push(name);
    }
  }
  if (stack.length) errors.push(`未闭合标签：${stack.join(' > ')}`);
  return errors;
}

function collectDeclarations(script) {
  const names = new Set();
  const add = (n) => { const t = (n || '').trim(); if (t && /^[A-Za-z_$][\w$]*$/.test(t)) names.add(t); };

  for (const m of script.matchAll(/\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)/g)) add(m[1]);
  for (const m of script.matchAll(/\bfunction\s+([A-Za-z_$][\w$]*)/g)) add(m[1]);
  for (const m of script.matchAll(/(?:const|let|var)\s*\{([^}]*)\}/g)) {
    m[1].split(',').forEach((p) => add(p.split(':').pop().split('=')[0]));
  }
  for (const m of script.matchAll(/(?:const|let|var)\s*\[([^\]]*)\]/g)) {
    m[1].split(',').forEach(add);
  }
  for (const m of script.matchAll(/import\s+([A-Za-z_$][\w$]*)\s+from/g)) add(m[1]);
  for (const m of script.matchAll(/import\s*\{([^}]*)\}\s*from/g)) {
    m[1].split(',').forEach((p) => add(p.split(/\s+as\s+/).pop()));
  }
  // defineProps 解构出的名字也算已声明
  for (const m of script.matchAll(/defineProps\s*\(\s*\{([\s\S]*?)\}\s*\)/g)) {
    for (const p of m[1].matchAll(/([A-Za-z_$][\w$]*)\s*:/g)) add(p[1]);
  }
  return names;
}

/** v-for 引入的局部变量：v-for="x in xs" / v-for="(a, b) in xs"。 */
function collectLoopAliases(template) {
  const aliases = new Set();
  for (const m of template.matchAll(/v-for="\s*\(?([^)"]*?)\)?\s+(?:in|of)\s+[^"]*"/g)) {
    m[1].split(',').forEach((p) => aliases.add(p.trim()));
  }
  return aliases;
}

function collectTemplateExpressions(template) {
  const exprs = [];
  for (const m of template.matchAll(/\{\{([\s\S]*?)\}\}/g)) exprs.push(m[1]);
  const attrRe = /(?:v-[a-zA-Z-]+|:[a-zA-Z-]+|@[a-zA-Z.-]+)="([^"]*)"/g;
  let m;
  while ((m = attrRe.exec(template)) !== null) {
    // v-for 的右侧含别名声明，这里只关心被遍历的集合
    const value = m[0].startsWith('v-for') ? m[1].split(/\s+(?:in|of)\s+/).pop() : m[1];
    exprs.push(value);
  }
  return exprs;
}

/** 去掉对象字面量的键名（{ active: mode === 'x' } 里的 active 不是变量引用）。 */
function stripObjectKeys(expr) {
  return expr.replace(/([A-Za-z_$][\w$]*)\s*:/g, ' ');
}

let failed = 0;
const files = walk(SRC).filter((f) => f.endsWith('.vue'));
console.log(`检查 ${files.length} 个 .vue 文件\n`);

for (const file of files) {
  const rel = path.relative(ROOT, file);
  const source = fs.readFileSync(file, 'utf8');
  const errors = [];

  const scriptRaw = block(source, 'script');
  const template = block(source, 'template');

  if (!scriptRaw && !template) errors.push('既没有 script 也没有 template');

  if (scriptRaw) {
    const js = scriptRaw
      .replace(/^\s*import\s+[^;]*?from\s*['"][^'"]+['"];?\s*$/gm, '')
      .replace(/^\s*import\s+['"][^'"]+['"];?\s*$/gm, '')
      .replace(/^\s*export\s+default\s+/gm, 'const __default = ')
      .replace(/^\s*export\s+/gm, '');
    try {
      // eslint-disable-next-line no-new-func
      new Function(js);
    } catch (e) {
      errors.push(`script 语法错误: ${e.message}`);
    }
  }

  if (template) {
    errors.push(...checkTags(template));

    if (scriptRaw) {
      const declared = collectDeclarations(scriptRaw);
      for (const alias of collectLoopAliases(template)) declared.add(alias);

      // 模板表达式里可以写箭头函数（如 .map(t => t.trim())），其中的参数是局部作用域
      const declaredFromLambdas = new Set();
      for (const expr of collectTemplateExpressions(template)) {
        for (const m of expr.matchAll(/\(?\s*([A-Za-z_$][\w$]*)\s*\)?\s*=>/g)) {
          declaredFromLambdas.add(m[1]);
        }
      }

      const unknown = new Set();
      for (const expr of collectTemplateExpressions(template)) {
        const cleaned = stripObjectKeys(expr)
          .replace(/'[^']*'/g, ' ')
          .replace(/"[^"]*"/g, ' ')
          .replace(/\.[A-Za-z_$][\w$]*/g, ' ');
        for (const m of cleaned.matchAll(/(?<![\w.$])([A-Za-z_$][\w$]*)/g)) {
          const name = m[1];
          if (!declared.has(name) && !declaredFromLambdas.has(name) && !JS_GLOBALS.has(name)) {
            unknown.add(name);
          }
        }
      }
      if (unknown.size) {
        errors.push(`模板引用了未声明的名字: ${[...unknown].join(', ')}`);
      }
    }
  }

  if (errors.length) {
    failed++;
    console.log(`FAIL  ${rel}`);
    errors.forEach((p) => console.log(`        ${p}`));
  } else {
    console.log(`ok    ${rel}`);
  }
}

console.log('');
if (failed) {
  console.log(`FAILED: ${failed} 个文件`);
  process.exitCode = 1;
} else {
  console.log('ALL PASS（脚本语法 / 标签闭合 / 模板标识符均正常）');
}
