import { reactive } from 'vue';
import { http } from './api.js';

/**
 * 全局状态。
 *
 * 用一个简单的 reactive 对象而不是 Pinia：这个应用的共享状态只有"当前用户"和
 * "AI 对话记录"两块，引入状态管理库的收益抵不上多一个依赖和一层概念。
 */
export const store = reactive({
  user: null,
  booted: false,
  /** 对话记录（前端持有；持久化是后续独立的一轮工作） */
  history: [],
  busy: false,
  toast: '',

  async boot() {
    try {
      this.user = await http.get('/api/auth/me');
    } catch {
      this.user = null;
    } finally {
      this.booted = true;
    }
  },

  async login(username, password) {
    const user = await http.post('/api/auth/login', { username, password });
    this.user = user;
    this.history = [];
    return user;
  },

  async register(payload) {
    return http.post('/api/auth/register', payload);
  },

  async logout() {
    try {
      await http.post('/api/auth/logout');
    } finally {
      this.user = null;
      this.history = [];
    }
  },

  notify(message) {
    this.toast = message;
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => { this.toast = ''; }, 3200);
  },
});
