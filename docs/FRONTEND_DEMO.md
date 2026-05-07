# 前端展示 Demo

本文描述当前 `frontend` 已完成的演示能力。该前端用于展示现有后端能力，不表示 P1+ 能力已经完成。

## 页面结构

前端位于 `frontend`，技术栈为 Vite + React 18 + Fetch API。开发时通过 Vite 将 `/api` 代理到 `http://127.0.0.1:8081`。

页面区域：

- 顶部状态区：展示产品名、当前操作状态和全局错误。
- 登录区：使用 `demo / demo123` 登录，保存 Bearer Token，支持退出。
- 知识上传区：上传 `.txt` 文件到知识库，展示上传文件名、chunk 数和后端消息。
- 会话聊天区：展示 `conversationId`、用户/助手消息、loading / streaming 状态，支持新建会话、发送和停止输出。
- Agent 阶段区：展示 `plan_parse` 元数据，以及 `PLAN -> RETRIEVE -> TOOL -> GUARD -> WRITE` 阶段事件。
- 引用来源区：展示 SSE 中的结构化来源，或从文本“引用片段”中拆出的来源内容。
- 用户画像区：展示当前画像、待确认画像，支持抽取建议、确认、忽略和重置。
- 反馈区：在回答完成后提交 thumb、rating、comment，并展示最近反馈。

## 接口清单

前端 API 集中在 `frontend/src/api.js`。

| 能力 | 接口 |
| --- | --- |
| 登录 | `POST /auth/login` |
| 创建会话 | `POST /travel/conversations` |
| 上传知识 | `POST /knowledge/upload` |
| SSE 聊天 | `POST /travel/chat/{conversationId}` |
| 当前画像 | `GET /travel/profile` |
| 抽取画像建议 | `POST /travel/profile/extract-suggestion` |
| 读取待确认画像 | `GET /travel/profile/pending-extraction?conversationId=...` |
| 确认待确认画像 | `POST /travel/profile/confirm-extraction` |
| 忽略待确认画像 | `DELETE /travel/profile/pending-extraction?conversationId=...` |
| 重置画像 | `DELETE /travel/profile` |
| 提交反馈 | `POST /travel/feedback` |
| 最近反馈 | `GET /travel/feedback?limit=5&offset=0` |

SSE 聊天请求：

```http
POST /travel/chat/{conversationId}
Accept: text/event-stream
Content-Type: application/json
Authorization: Bearer <token>

{"query":"请基于我上传的资料规划成都两天一晚行程"}
```

前端会解析：

- `message`：追加到助手回答；如果包含“引用片段”，拆分为来源。
- `event: plan_parse`：展示计划解析元数据，并提取 `request_id`。
- `event: stage`：展示 Agent 阶段开始、结束、跳过和耗时。
- `event: policy`：展示工具、RAG 门控等策略事件。
- `event: source` / `sources` / `citation` / `citations`：展示结构化来源。
- `event: error`：展示结构化错误。
- `event: done`：标记本轮回答完成。

## 手动验收步骤

1. 启动依赖：

```powershell
docker compose up -d postgres redis
```

2. 在 IDEA 中运行 `com.travel.ai.TravelAiApplication`，确认后端健康检查可访问：

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

3. 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

4. 打开 `http://localhost:5173`，使用 `demo / demo123` 登录。
5. 确认登录成功后自动创建并展示 `conversationId`。
6. 上传一个 `.txt` 知识文件，确认页面展示 `fileName` 和 `chunkCount`。
7. 发送旅行规划问题，确认助手气泡以流式方式追加内容。
8. 查看 Agent 阶段区，确认能看到 `plan_parse`、`stage`、`policy` 等事件。
9. 查看引用来源区，确认命中知识库时能展示引用；未命中时显示空态。
10. 在用户画像区点击“抽取建议”。若 `app.memory.auto-extract.enabled=true`，确认出现待确认画像并能确认或忽略；若未开启，确认页面展示后端返回的限制说明。
11. 在回答完成后提交反馈，确认提示成功，并能在最近反馈中看到记录。

## 已知限制

- 仍使用后端现有 demo 用户，不包含数据库用户注册、密码治理或角色体系。
- 知识上传 UI 只覆盖现有 `.txt` 上传接口，没有文件列表、删除、更新或重建索引能力。
- 用户画像抽取依赖后端配置 `app.memory.auto-extract.enabled`，关闭时前端只能展示接口限制说明。
- 引用来源优先兼容当前 SSE 文本块，同时保留结构化 sources / citation 入口。
- 前端没有新增 DAG、ReAct、rerank、多跳推理、知识库管理闭环或数据库结构。
