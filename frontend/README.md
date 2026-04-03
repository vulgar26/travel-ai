# Travel AI Planner — 最小前端（Week 4）

## 前置

- 本机已启动后端：`TravelAiApplication`（默认 `http://127.0.0.1:8081`），或使用 `docker compose` 起全套。
- 本机已安装 Node.js（建议 18+）。

## 安装与启动

```powershell
cd frontend
npm install
npm run dev
```

浏览器打开 Vite 提示的地址（一般是 `http://localhost:5173`）。

## 说明

- 开发服务器将 **`/api` 代理到 `http://127.0.0.1:8081`**，因此前端请求写 `/api/auth/login`、`/api/travel/chat/...`，可避免浏览器跨域。
- SSE 使用 **`fetch` + ReadableStream** 解析（`EventSource` 无法携带 `Authorization: Bearer`）。
- **知识上传**：页面内「上传知识」或 `POST /api/knowledge/upload`，表单字段 **`file`**，仅 **`.txt`**。必须先登录，否则检索会显示「未命中知识库」。

## 账号

演示账号与后端 `SecurityConfig` 中一致：`demo` / `demo123`。
