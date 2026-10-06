<script setup>
import { ref, watch } from 'vue';
import { http } from '../api.js';
import { store } from '../store.js';

const props = defineProps({
  snippetId: { type: Number, default: null },
  readOnly: { type: Boolean, default: false },
});
const emit = defineEmits(['close', 'saved']);

const LANGUAGES = ['Java', 'JavaScript', 'Python', 'SQL', 'HTML/CSS', 'XML', 'Go',
  'TypeScript', 'Docker', 'Git', 'Other'];

const form = ref({ title: '', language: 'Java', tags: '', content: '' });
const fieldErrors = ref({});
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const isNew = () => props.snippetId == null;

async function load() {
  if (isNew()) {
    form.value = { title: '', language: 'Java', tags: '', content: '' };
    return;
  }
  loading.value = true;
  try {
    const s = await http.get(`/api/snippets/${props.snippetId}`);
    form.value = {
      title: s.title, language: s.language, tags: s.tags || '', content: s.content,
    };
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

watch(() => props.snippetId, load, { immediate: true });

async function save() {
  fieldErrors.value = {};
  error.value = '';
  if (!form.value.title.trim()) { fieldErrors.value.title = '标题不能为空'; return; }
  if (!form.value.content.trim()) { fieldErrors.value.content = '代码内容不能为空'; return; }

  saving.value = true;
  try {
    const payload = {
      title: form.value.title.trim(),
      language: form.value.language,
      tags: form.value.tags.trim(),
      content: form.value.content,
    };
    if (isNew()) {
      await http.post('/api/snippets', payload);
      store.notify('已保存，AI 已补全标签与摘要');
    } else {
      await http.put(`/api/snippets/${props.snippetId}`, payload);
      store.notify('已更新');
    }
    emit('saved');
    emit('close');
  } catch (e) {
    fieldErrors.value = (e.body && e.body.fields) || {};
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="modal">
      <h2>{{ isNew() ? '新增代码片段' : (readOnly ? '查看代码片段' : '编辑代码片段') }}</h2>
      <p class="muted small">
        保存时 DeepSeek 会自动补全标签和一句话摘要；你手动填写的标签不会被覆盖。
      </p>

      <div v-if="loading" class="muted">加载中…</div>

      <form v-else @submit.prevent="save">
        <label>标题</label>
        <input v-model="form.title" maxlength="100" :disabled="readOnly" required>
        <div class="field-error">{{ fieldErrors.title }}</div>

        <label>语言</label>
        <select v-model="form.language" :disabled="readOnly">
          <option v-for="lang in LANGUAGES" :key="lang" :value="lang">{{ lang }}</option>
        </select>

        <label>标签（留空则由 AI 生成，多个用英文逗号分隔）</label>
        <input v-model="form.tags" maxlength="200" :disabled="readOnly" placeholder="如：MyBatis,分页">

        <label>代码</label>
        <textarea v-model="form.content" rows="12" :disabled="readOnly" required></textarea>
        <div class="field-error">{{ fieldErrors.content }}</div>

        <div class="field-error">{{ error }}</div>

        <div class="row actions">
          <button v-if="!readOnly" type="submit" class="primary" :disabled="saving">
            {{ saving ? '保存中…' : '保存' }}
          </button>
          <button type="button" @click="emit('close')">{{ readOnly ? '关闭' : '取消' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.actions { margin-top: 16px; }
</style>
