# Travel AI Planner

Travel AI Planner 是一个面向出行规划场景的 RAG + SSE 后端项目。它不是简单包装大模型接口，而是围绕 Spring Boot 3、Spring AI Alibaba、PostgreSQL/pgvector、Redis 和 JWT，把知识上传、检索增强、线性 Agent 编排、流式输出、用户隔离、工具治理和评测接口串成一条可演示、可测试的后端链路。

[![CI](https://github.com/vulgar26/travel-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/vulgar26/travel-ai/actions/workflows/ci.yml)

## 核心链路

```text
PLAN -> RETRIEVE -> TOOL -> GUARD -> WRITE
```

- `PLAN`：生成或兜底解析结构化计划，约束后续阶段顺序。
- `RETRIEVE`：对用户问题做 query rewrite，执行 pgvector 检索并按当前用户过滤知识片段。
- `TOOL`：按计划调用受控工具，包含超时、限流、熔断和降级结果。
- `GUARD`：对检索零命中等低依据场景做门控，避免直接编造答案。
- `WRITE`：通过 SSE 返回阶段事件、引用片段、正文 token、心跳、完成或错误事件。

核心实现入口：

- `src/main/java/com/travel/ai/agent/TravelAgent.java`
- `src/main/java/com/travel/ai/agent/QueryRewriter.java`
- `src/main/java/com/travel/ai/config/PgVectorStore.java`
- `src/main/java/com/travel/ai/eval/EvalChatController.java`
- `src/main/java/com/travel/ai/security/SecurityConfig.java`

## 已实现功能

- JWT 登录与 Spring Security 保护业务接口。
- 服务端签发并登记 `conversationId`，支持按用户隔离会话。
- `.txt` 知识上传、分块、向量化，并写入 PostgreSQL + pgvector。
- 检索 metadata 写入 `user_id`，查询时按当前登录用户过滤。
- Redis ChatMemory 保存短期对话上下文。
- SSE 流式聊天，包含阶段事件、心跳、引用片段、完成和错误事件。
- query rewrite、多路向量检索、合并去重和引用返回。
- 检索零命中门控，可返回澄清而不是无依据生成。
- 天气工具调用，并配套工具超时、限流、熔断和观测日志。
- 用户画像读取、抽取建议、确认写入、忽略和重置。
- 用户反馈提交与分页查询，支持关联 `request_id` / eval 字段。
- 评测接口 `POST /api/v1/eval/chat`，用于非流式 JSON 回归验证。
- Docker Compose 启动 Postgres/Redis/App，Flyway 管理数据库迁移。
- JUnit、Spring Boot Test、Testcontainers 和 GitHub Actions CI。
- Vite + React 最小演示前端，覆盖登录、上传、聊天、阶段事件、引用、画像和反馈。

## 快速启动

推荐本地开发方式：Docker Compose 启动依赖，IDEA 启动后端主类，Vite 启动前端。

1. 复制环境变量模板并填写本地密钥：

```powershell
Copy-Item .env.example .env
```

2. 启动依赖服务：

```powershell
docker compose up -d postgres redis
```

3. 在 IDEA 中运行后端主类：

```text
com.travel.ai.TravelAiApplication
```

本地 IDEA 连接宿主机端口：

- Postgres: `localhost:5433`
- Redis: `localhost:16379`
- Backend: `http://localhost:8081`

4. 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址为 `http://localhost:5173`，Vite 将 `/api` 代理到 `http://127.0.0.1:8081`。

更完整的 Windows / Docker Desktop / IDEA 启动说明见 [docs/LOCAL_DEV.md](docs/LOCAL_DEV.md)。

## 环境变量

| 变量 | 是否必需 | 示例 / 默认值 | 说明 |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | 本地 IDEA 必需 | `jdbc:postgresql://localhost:5433/ragent` | 后端直连宿主机 Postgres 时使用 |
| `SPRING_DATASOURCE_USERNAME` | 本地 IDEA 必需 | `postgres` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 本地 IDEA 必需 | `postgres` | 数据库密码，本地演示值 |
| `SPRING_DATA_REDIS_HOST` | 本地 IDEA 必需 | `localhost` | Redis 主机 |
| `SPRING_DATA_REDIS_PORT` | 本地 IDEA 必需 | `16379` | Redis 宿主机端口 |
| `POSTGRES_PASSWORD` | Compose 必需 | `postgres` | Compose 中 Postgres 密码 |
| `APP_JWT_SECRET` | 必需 | 64 位以上随机字符串 | JWT HS256 密钥，生产环境必须替换 |
| `SPRING_AI_DASHSCOPE_API_KEY` | 真实 LLM 必需 | `<your-key>` | DashScope / Spring AI Alibaba API Key |
| `APP_EVAL_GATEWAY_KEY` | 使用 eval 接口时必需 | `<your-eval-key>` | `/api/v1/eval/**` 网关密钥 |
| `WEATHER_API_KEY` | 可选 | 空 | 天气工具真实 API Key；为空时走降级或本地演示逻辑 |

不要提交 `.env`。仓库只保留 [.env.example](.env.example) 作为模板。

## 核心接口

业务接口默认需要 `Authorization: Bearer <token>`。未登录返回 `401`，无权访问会话返回 `403`，限流返回 `429`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/auth/login` | 登录并返回 JWT |
| `POST` | `/travel/conversations` | 创建并登记 `conversationId` |
| `POST` | `/knowledge/upload` | 上传 `.txt` 知识文件 |
| `GET` | `/travel/knowledge` | 获取当前用户知识文件列表 |
| `DELETE` | `/travel/knowledge/{fileId}` | 删除当前用户知识文件的向量 chunks |
| `POST` | `/travel/chat/{conversationId}` | SSE 流式聊天，推荐 POST JSON `{"query":"..."}` |
| `GET` | `/travel/profile` | 获取当前用户画像 |
| `POST` | `/travel/profile/extract-suggestion` | 从会话中抽取画像建议 |
| `GET` | `/travel/profile/pending-extraction?conversationId=...` | 获取待确认画像 |
| `POST` | `/travel/profile/confirm-extraction` | 确认并写入画像 |
| `DELETE` | `/travel/profile/pending-extraction?conversationId=...` | 忽略待确认画像 |
| `DELETE` | `/travel/profile` | 重置画像，可选清理聊天记忆 |
| `POST` | `/travel/feedback` | 提交用户反馈 |
| `GET` | `/travel/feedback?limit=20&offset=0` | 获取当前用户反馈列表 |
| `POST` | `/api/v1/eval/chat` | 非流式评测接口，需要 `X-Eval-Gateway-Key` |
| `GET` | `/actuator/health` | 健康检查 |

## 技术栈

- 后端：Java 21、Spring Boot 3、Spring Security、Spring AI Alibaba、Spring JDBC、Flyway、Actuator
- 数据：PostgreSQL + pgvector、Redis
- 前端：Vite、React 18、Fetch API、手写 text/event-stream 解析
- 测试与部署：JUnit 5、Spring Boot Test、Testcontainers、Docker Compose、GitHub Actions

## 文档入口

| 文档 | 内容 |
| --- | --- |
| [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | 项目总览、当前能力、已知不足和路线 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 请求链路、Agent 主线、SSE、Compose |
| [docs/LOCAL_DEV.md](docs/LOCAL_DEV.md) | Windows + Docker Desktop + IDEA 本地启动和排错 |
| [docs/FRONTEND_DEMO.md](docs/FRONTEND_DEMO.md) | 前端页面结构、接口清单和手动验收 |
| [docs/IMPLEMENTATION_MATRIX.md](docs/IMPLEMENTATION_MATRIX.md) | 实现与计划对照 |
| [docs/eval.md](docs/eval.md) | 评测接口与回归样例 |

## 已知限制

- 当前前端是最小演示界面，不是完整产品级工作台。
- 账号体系使用演示账号，未实现完整注册、密码找回、角色权限管理。
- 知识上传主要支持 `.txt`，PDF、网页、Markdown 等多格式解析仍未完成。
- RAG 质量增强仍以向量检索为主，BM25/RRF、rerank、严格引用覆盖率校验属于后续方向。
- 评测接口已具备结构化输出，但没有内置可视化 dashboard。
- 生产级密钥托管、反向代理、监控告警和部署治理仍需按真实环境补齐。

## 后续计划

- 完善知识库管理闭环：文件列表、删除、重复上传处理、重建索引和来源筛选。
- 引入混合检索与质量提升：关键词检索、RRF、rerank、score threshold 和引用一致性检查。
- 增强文档解析：Markdown、PDF、网页抓取和结构化 metadata。
- 补齐 eval 报告化：固定题集导入、run/result 落库、report/compare、失败归因统计。
- 升级可观测能力：将当前分段日志沉淀为 Micrometer 指标和更稳定的 trace 字段。
- 完善生产化说明：密钥管理、反向代理、健康探针、日志脱敏和部署 runbook。
