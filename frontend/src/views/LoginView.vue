<script setup>
import { ref } from 'vue';
import { store } from '../store.js';

const mode = ref('login');
const loginForm = ref({ username: 'demo', password: 'codebox123' });
const registerForm = ref({ username: '', password: '', email: '' });
const error = ref('');
const fieldErrors = ref({});
const busy = ref(false);

async function submitLogin() {
  error.value = '';
  busy.value = true;
  try {
    await store.login(loginForm.value.username.trim(), loginForm.value.password);
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}

async function submitRegister() {
  error.value = '';
  fieldErrors.value = {};
  busy.value = true;
  try {
    await store.register({
      username: registerForm.value.username.trim(),
      password: registerForm.value.password,
      email: registerForm.value.email.trim(),
    });
    store.notify('注册成功，请登录');
    mode.value = 'login';
    loginForm.value.username = registerForm.value.username;
    loginForm.value.password = '';
  } catch (e) {
    fieldErrors.value = (e.body && e.body.fields) || {};
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="card auth-card">
      <div class="brand">Code<span>Box</span></div>
      <p class="muted small">AI 驱动的代码知识库 · 让 AI 帮你查、分析、优化、保存代码</p>

      <div class="tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'; error = ''">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'; error = ''">注册</button>
      </div>

      <form v-if="mode === 'login'" @submit.prevent="submitLogin">
        <label class="inline">用户名</label>
        <input v-model="loginForm.username" autocomplete="username" required>
        <label>密码</label>
        <input v-model="loginForm.password" type="password" autocomplete="current-password" required>
        <div class="field-error">{{ error }}</div>
        <button class="primary wide" :disabled="busy">{{ busy ? '登录中…' : '登录' }}</button>
      </form>

      <form v-else @submit.prevent="submitRegister">
        <label class="inline">用户名（3-50 字符）</label>
        <input v-model="registerForm.username" autocomplete="username" required>
        <div class="field-error">{{ fieldErrors.username }}</div>

        <label>密码（至少 8 位）</label>
        <input v-model="registerForm.password" type="password" autocomplete="new-password" required>
        <div class="field-error">{{ fieldErrors.password }}</div>

        <label>邮箱</label>
        <input v-model="registerForm.email" type="email" autocomplete="email" required>
        <div class="field-error">{{ fieldErrors.email }}</div>

        <div class="field-error">{{ error }}</div>
        <button class="primary wide" :disabled="busy">{{ busy ? '注册中…' : '注册' }}</button>
      </form>

      <p class="muted small demo">演示账号 <b>demo</b> / <b>codebox123</b></p>
    </div>
  </div>
</template>

<style scoped>
.auth-wrap { display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
.auth-card { width: 100%; max-width: 400px; padding: 24px; }
.brand { font-weight: 700; font-size: 20px; margin-bottom: 4px; }
.brand span { color: var(--accent); }
.tabs { display: flex; gap: 6px; margin: 16px 0; }
.tabs button { flex: 1; }
.tabs button.active { background: var(--accent); border-color: var(--accent); color: #fff; }
.wide { width: 100%; }
.demo { margin: 14px 0 0; }
</style>
