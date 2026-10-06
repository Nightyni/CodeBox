<script setup>
import { onMounted } from 'vue';
import { store } from './store.js';
import LoginView from './views/LoginView.vue';
import LibraryView from './views/LibraryView.vue';

onMounted(() => store.boot());
</script>

<template>
  <div v-if="!store.booted" class="booting">加载中…</div>

  <LoginView v-else-if="!store.user" />

  <div v-else class="shell">
    <header class="topbar">
      <div class="brand">Code<span>Box</span></div>
      <div class="pill">Vue 3 · Spring Boot 3 · DeepSeek · Tool Calling</div>
      <div class="grow"></div>
      <a class="legacy-link" href="/legacy.html" title="旧版原生 JS 界面">旧版界面</a>
      <div class="who">已登录：{{ store.user.username }}</div>
      <button class="ghost" @click="store.logout()">退出</button>
    </header>

    <LibraryView />
  </div>

  <Transition name="fade">
    <div v-if="store.toast" class="toast">{{ store.toast }}</div>
  </Transition>
</template>

<style scoped>
.booting { padding: 60px; text-align: center; color: var(--muted); }

.shell { display: flex; flex-direction: column; min-height: 100vh; }

.topbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 18px;
  background: var(--panel);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 20;
}
.brand { font-weight: 700; font-size: 17px; letter-spacing: .4px; }
.brand span { color: var(--accent); }
.who { color: var(--muted); font-size: 13px; }
.legacy-link {
  color: var(--muted);
  font-size: 12px;
  text-decoration: none;
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 3px 10px;
}
.legacy-link:hover { color: var(--fg); border-color: var(--accent); }
</style>
