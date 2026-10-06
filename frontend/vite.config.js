import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

/*
 * 前端构建产物直接写进 Spring Boot 的 static 目录。
 *
 * 结果：mvn package 出来的 jar 里就带着前端，部署只有一个 jar、一个端口，不需要
 * CORS，也不需要额外的 nginx。开发时用 `npm run dev`，/api 由 Vite 代理到 8080。
 *
 * emptyOutDir 必须为 false —— 该目录里还有 legacy.html / legacy-app.js（旧版界面），
 * 默认的清空行为会把它们删掉。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false,
    // 打开后能在构建日志里看清每个 chunk 的实际大小
    reportCompressedSize: true,
    rollupOptions: {
      output: {
        // 带 hash 的独立文件：升级后浏览器不会用到旧缓存
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
