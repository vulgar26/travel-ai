# Travel AI Planner

Spring Boot 3 + Spring AI Alibaba 的旅行规划 AI Agent / RAG 应用，支持 JWT 登录、知识上传、pgvector 检索、Redis 会话记忆、SSE 流式对话、Agent 阶段事件、用户画像和反馈闭环。默认后端端口为 `8081`，前端开发端口为 `5173`。

[![CI](https://github.com/vulgar26/travel-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/vulgar26/travel-ai/actions/workflows/ci.yml)

## 本地开发运行

推荐在 Windows + Docker Desktop + IDEA 下只用 Docker 启动依赖服务，再用 IDEA 启动后端主类。

1. 启动依赖：

```powershell
docker compose up -d postgres redis
```

2. 在 IDEA 中运行后端：

运行 `com.travel.ai.TravelAiApplication`。

IDEA 本地运行时连接的是宿主机端口：

- Postgres: `localhost:5433`
- Redis: `localhost:16379`

需要配置的常用环境变量：

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ragent
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=16379
APP_JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
SPRING_AI_DASHSCOPE_API_KEY=<your-key>
```

3. 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址为 `http://localhost:5173`，Vite 会把 `/api` 代理到 `http://127.0.0.1:8081`。

更详细的 Windows / Docker Desktop / IDEA 启动和排错说明见 [docs/LOCAL_DEV.md](docs/LOCAL_DEV.md)。

## 前端展示能力

当前 `frontend` 已具备面向演示的最小展示界面，覆盖后端已完成能力，不包含 P1+ 新业务能力的完成声明。

已接入能力：

- 登录：`POST /auth/login`，默认演示账号 `demo / demo123`。
- 知识上传：`POST /knowledge/upload`，上传 `.txt` 文件并展示文件名、chunk 数等结果。
- SSE 聊天：`POST /travel/chat/{conversationId}`，请求体 `{"query":"..."}`，使用 `Accept: text/event-stream`。
- Agent 阶段展示：解析并展示 `plan_parse`、`stage`、`policy`、`done`、`error` 等 SSE 事件，以及 `PLAN -> RETRIEVE -> TOOL -> GUARD -> WRITE` 阶段。
- 引用来源：支持结构化 sources / citation 事件，也兼容从后端纯文本“引用片段”块中拆出来源。
- 用户画像区域：展示当前画像、抽取建议、pending extraction、确认、忽略和重置。
- 反馈提交：支持 thumb、rating、comment，并在有 `request_id` 时随反馈提交，展示最近反馈记录。

前端验收细节见 [docs/FRONTEND_DEMO.md](docs/FRONTEND_DEMO.md)。

## 文档入口

| 文档 | 内容 |
| --- | --- |
| [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | 项目总览、当前已实现能力、已知不足和后续路线 |
| [docs/LOCAL_DEV.md](docs/LOCAL_DEV.md) | Windows + Docker Desktop + IDEA 本地启动和常见错误 |
| [docs/FRONTEND_DEMO.md](docs/FRONTEND_DEMO.md) | 前端页面结构、接口清单、手动验收步骤、已知限制 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 请求链路、Agent 主线、SSE、Compose |
| [docs/STATUS.md](docs/STATUS.md) | 当前能力摘要 |
| [docs/IMPLEMENTATION_MATRIX.md](docs/IMPLEMENTATION_MATRIX.md) | 实现与计划对照 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |

## 核心接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/auth/login` | 登录并返回 JWT |
| `POST` | `/travel/conversations` | 创建并登记 `conversationId` |
| `POST` | `/knowledge/upload` | 上传 `.txt` 知识文件 |
| `GET` | `/travel/knowledge` | 获取当前用户知识文件列表 |
| `DELETE` | `/travel/knowledge/{fileId}` | 删除当前用户知识文件的向量 chunks |
| `POST` | `/travel/chat/{conversationId}` | SSE 流式聊天，推荐使用 POST JSON |
| `GET` | `/travel/profile` | 获取当前用户画像 |
| `POST` | `/travel/profile/extract-suggestion` | 从会话中抽取画像建议 |
| `GET` | `/travel/profile/pending-extraction?conversationId=...` | 获取待确认画像 |
| `POST` | `/travel/profile/confirm-extraction` | 确认并写入画像 |
| `DELETE` | `/travel/profile/pending-extraction?conversationId=...` | 忽略待确认画像 |
| `DELETE` | `/travel/profile` | 重置画像，可选清理聊天记忆 |
| `POST` | `/travel/feedback` | 提交用户反馈 |
| `GET` | `/travel/feedback?limit=20&offset=0` | 获取当前用户反馈列表 |
| `GET` | `/actuator/health` | 健康检查 |

业务接口默认需要 `Authorization: Bearer <token>`。未登录返回 `401`，无权访问会话返回 `403`，限流返回 `429`。

## 技术栈

- 后端：Java 21、Spring Boot 3、Spring Security、Spring AI Alibaba、DashScope、Spring JDBC、Flyway、Actuator
- 数据：PostgreSQL + pgvector、Redis
- 前端：Vite、React 18、Fetch API、手写 text/event-stream 解析
- 测试与部署：JUnit、Spring Boot Test、Testcontainers、Docker Compose、GitHub Actions
