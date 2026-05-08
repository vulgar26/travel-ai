# 前端展示 Demo

本文描述当前 `frontend` 已完成的演示能力。该前端用于展示现有后端能力，不表示 P1+ 能力已经完成。

## 页面结构

前端位于 `frontend`，技术栈为 Vite + React 18 + Fetch API。开发时通过 Vite 将 `/api` 代理到 `http://127.0.0.1:8081`。

页面区域：

- 顶部状态区：展示产品名、当前操作状态和全局错误。
- 登录区：使用 `demo / demo123` 登录，保存 Bearer Token，支持退出。
- 知识上传区：上传 `.txt` 文件到知识库，展示上传文件名、chunk 数和后端消息。
- 知识库列表区：登录后自动加载当前用户知识文件，展示 filename、chunk_count、created_at、legacy、deletable 和 content_hash 短值，支持删除可删除文件。
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
| 知识列表 | `GET /travel/knowledge` |
| 删除知识 | `DELETE /travel/knowledge/{fileId}` |
| SSE 聊天 | `POST /travel/chat/{conversationId}` |
| 当前画像 | `GET /travel/profile` |
| 抽取画像建议 | `POST /travel/profile/extract-suggestion` |
| 读取待确认画像 | `GET /travel/profile/pending-extraction?conversationId=...` |
| 确认待确认画像 | `POST /travel/profile/confirm-extraction` |
| 忽略待确认画像 | `DELETE /travel/profile/pending-extraction?conversationId=...` |
| 重置画像 | `DELETE /travel/profile` |
| 提交反馈 | `POST /travel/feedback` |
| 最近反馈 | `GET /travel/feedback?limit=5&offset=0` |

删除知识只影响后续 RAG 检索，不删除历史对话和已生成回答。验证删除后不再命中时，请新建 conversation。

## PowerShell 接口验收

登录使用 `Invoke-RestMethod`：

```powershell
$loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
$auth = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody
$headers = @{ Authorization = "Bearer $($auth.token)" }
```

上传知识：

```powershell
$form = @{ file = Get-Item ".\test.txt" }
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/knowledge/upload" `
  -Headers $headers `
  -Form $form
```

查看知识列表：

```powershell
$knowledge = Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8081/travel/knowledge" `
  -Headers $headers
$knowledge.items
```

删除第一个可删除文件：

```powershell
$fileId = ($knowledge.items | Where-Object { $_.deletable -eq $true } | Select-Object -First 1).file_id
Invoke-RestMethod -Method Delete `
  -Uri "http://localhost:8081/travel/knowledge/$fileId" `
  -Headers $headers
```

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
5. 确认登录成功后自动创建并展示 `conversationId`，并自动加载知识库列表。
6. 上传一个 `.txt` 知识文件，确认页面展示 `fileName`、`chunkCount`，知识库列表自动刷新。
7. 再次上传相同文件，确认页面友好提示 `DUPLICATE_KNOWLEDGE` 对应的重复内容信息。
8. 点击可删除文件的删除按钮，确认弹窗提示“删除只影响后续 RAG 检索，不删除历史对话和已生成回答”。
9. 确认删除后列表自动刷新；若文件已被删除，确认 `KNOWLEDGE_FILE_NOT_FOUND` 以友好状态展示。
10. 对 legacy 旧数据，确认展示为只读，不显示删除按钮；如后端返回 `LEGACY_KNOWLEDGE_NOT_DELETABLE`，页面展示友好说明。
11. 新建 conversation，发送旅行规划问题，确认助手气泡以流式方式追加内容。
12. 查看 Agent 阶段区，确认能看到 `plan_parse`、`stage`、`policy` 等事件。
13. 查看引用来源区，确认命中知识库时能展示引用；未命中时显示空态。
14. 在回答完成后提交反馈，确认提示成功，并能在最近反馈中看到记录。

## 已知限制

- 仍使用后端现有 demo 用户，不包含数据库用户注册、密码治理或角色体系。
- 知识库管理第一版只支持 `.txt` 上传、列表和删除新上传文件；旧数据没有 `file_id` 时仅展示为 legacy 只读项。
- 删除知识只影响后续 RAG 检索，不删除 Redis 历史对话和已生成回答。
- 用户画像抽取依赖后端配置 `app.memory.auto-extract.enabled`，关闭时前端只能展示接口限制说明。
- 引用来源优先兼容当前 SSE 文本块，同时保留结构化 sources / citation 入口。
- 前端没有新增 DAG、ReAct、rerank、多跳推理或数据库结构。
