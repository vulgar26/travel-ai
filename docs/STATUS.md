# 项目现状清单（Day 1）

更新时间：2026-04-01  
仓库：`travel-ai-planner`

## 一句话定位（当前版）

**面向开发者演示的「智能出行规划」后端原型**：基于 Spring Boot 3 + Spring AI Alibaba（通义千问）实现 **SSE 流式对话**，支持 **上传 txt 文档入库 → RAG 检索增强 → 输出行程建议**，并集成工具调用（天气）。

## 目标用户 & 不做什么（能力边界）

- **目标用户**：想要“可演示、可讲清楚工程链路”的后端/RAG 项目作品集（面试/简历展示）。
- **不做什么（当前明确不覆盖）**：
  - 不承诺生产级安全/鉴权（当前无 JWT / 无用户隔离）。
  - 不提供前端 UI（当前以接口 + SSE 为主）。
  - 不做复杂产品功能（支付、订单、地图等）。

## 已实现能力（基线）

- **SSE 流式输出**：`GET /travel/chat/{conversationId}` 返回 `text/event-stream`。
- **知识上传入库**：`POST /knowledge/upload` 上传文件后由服务端进行处理并写入知识库（向量化 / 向量检索相关逻辑在服务端）。
- **技术栈（以代码/配置为准）**：
  - Spring Boot 3
  - Spring AI Alibaba DashScope（通义千问 `qwen3.5-plus`）
  - PostgreSQL + pgvector（向量持久化/检索）
  - Redis（对话记忆/缓存相关能力）

## 当前对外接口（可演示路径）

1) **上传知识**（建议先准备一个 `.txt`）

- `POST /knowledge/upload`
- 表单字段：`file`

2) **发起对话（SSE）**

- `GET /travel/chat/{conversationId}?query=...`
- `conversationId` 当前由客户端传入（后续会改成服务端生成并做所有权校验）

## 关键现状风险（必须尽快修复）

- **密钥泄露风险（P0）**：`src/main/resources/application.yml` 当前包含明文 `spring.ai.dashscope.api-key` 与 `weather.api-key`。  
  - 影响：密钥进入版本库；一旦推送到远端风险更高；也会影响“可上线最小集”叙事。
  - 处理：Day 2 执行“密钥外置 + `.gitignore` + README 环境变量表”，并**轮换已提交过的 key**。
- **接口错误体不规范（P1）**：上传接口异常直接返回字符串 `"上传失败：..."`，不利于前后端/脚本稳定解析（后续会做统一错误体）。
- **多租户/越权风险（P0~P1）**：`conversationId` 可被猜测/枚举，且缺乏用户身份绑定（Week 2 处理）。

## 下一步（对应计划 Day 2~Day 3）

- **Day 2**：密钥与配置外置；仓库中不含任何真实 key；README 增加环境变量表与启动方式。
- **Day 3**：RAG 只保留“一条链路”（避免重复检索）；补充一次请求的检索统计与最简链路图。

## 验收记录（工程习惯）

### Day 4 — 无副作用验证（幂等/不污染）

目的：验证“默认不开初始化”时启动不灌示例数据；上传行为可预期；重启不重复写入。

- **启动参数**：`--app.knowledge.init.enabled=false`（覆盖本地 profile）
- **vector_store 行数**
  - N0（启动后）：46（无“知识库初始化完成”日志）
  - N1（上传 1 次 `test.txt` 后）：47
  - N2（上传同一文件第 2 次后）：48
  - 重启后再查询：48（无额外写入）

