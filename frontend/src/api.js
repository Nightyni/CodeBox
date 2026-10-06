/**
 * 统一的 fetch 封装。
 *
 * 关键点：始终带 credentials（后端用 Session 鉴权），并且把后端的结构化错误
 * （{status, message, fields}）转成带 status/body 的 Error，这样调用方既能展示
 * 字段级校验错误，也能识别 401 去跳登录页。
 */
export async function api(path, options = {}) {
  const res = await fetch(path, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (res.status === 204) return null;

  const text = await res.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { message: text };
    }
  }

  if (!res.ok) {
    const err = new Error((body && body.message) || `请求失败 (${res.status})`);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  return body;
}

export const http = {
  get: (path) => api(path),
  post: (path, payload) => api(path, { method: 'POST', body: JSON.stringify(payload ?? {}) }),
  put: (path, payload) => api(path, { method: 'PUT', body: JSON.stringify(payload ?? {}) }),
  del: (path) => api(path, { method: 'DELETE' }),
};
