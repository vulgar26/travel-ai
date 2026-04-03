# Travel AI Planner — 每日 5h+ 升级任务清单（学习 + 实做 + 产出）

目标：把当前仓库从「练手 Demo」升级为**可写入简历、可上线演示、可持续迭代**的项目。  
强度：**每天 5h+**（默认一周 6 天学习/开发，1 天休息或机动）。  
周期建议：**4 周可达 MVP（强简历叙事 + 可部署演示）**；想追求更接近企业级，可扩到 6～8 周。

> 你不需要把项目做成 ragent 那个体量；你需要做成「链路闭环 + 安全 + 可测 + 可部署 + 可演示」。

---

## 0. 你要交付的“证据”（写简历/面试用）

最终你应该能拿出这些**可验证产物**（面试官能看、能跑、能点）：

- **可复现**：`README` + 一条命令启动（Docker Compose）能跑通。
- **可演示**：最小 Web 页面或脚本（含 SSE 流式输出）能展示「上传知识 → 对话 → 引用/上下文」。
- **可解释**：一张简化架构图 + 一条请求链路说明（Intent/Rewrite/Retrieve/Augment/Generate）。
- **可测**：至少 2～4 个关键路径测试（RAG 拼上下文确实进模型；鉴权隔离不越权）。
- **可上线最小集**：密钥外置、鉴权、限流（最小版）、健康检查、日志 requestId。
- **可写简历**：3～6 条 bullet（带动词 + 结果/指标/验收）。

---

## 1. 参考资料（建议收藏）

### 1.1 Spring AI / RAG（核心链路）

- Spring AI 官方 RAG 文档：`https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html`
- `VectorStoreDocumentRetriever` API：`https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/rag/retrieval/search/VectorStoreDocumentRetriever.html`
- Spring AI Alibaba（RAG 介绍）：`https://java2ai.com/en/integration/rag/retrieval-augmented-generation`
- RAG + Vector Store 示例文章（理解链路用）：`https://piotrminkowski.com/2025/02/24/using-rag-and-vector-store-with-spring-ai`

### 1.2 SSE（流式输出工程化）

- SSE in Spring Boot（概念 + 代码）：`https://lorenzomiscoli.com/server-sent-events-in-spring-boot/`
- WebFlux 流式与背压（理解 limitRate / buffer）：`https://www.javacodegeeks.com/2025/07/mastering-backpressure-in-spring-webflux-for-streaming-apis.html`

### 1.3 Spring Security 6 + JWT（最小上线鉴权）

（以下是 2026 语境的教程型参考，选 1～2 篇看完即可）

- 2026 JWT（避免过度设计思路）：`https://medium.com/towardsdev/spring-boot-security-in-2026-build-jwt-authentication-the-right-way-without-overengineering-24b373338570`
- JWT + Spring Security（实现清单型）：`https://oneuptime.com/blog/post/2026-01-25-jwt-authentication-spring-security/view`

### 1.4 Testcontainers（把“能跑”变成“可验证”）

- Docker 官方：Spring Boot + Testcontainers：`https://docker.com/blog/spring-boot-application-testing-and-development-with-testcontainers`
- Spring Boot 3.1+ `@ServiceConnection` 思路说明：`https://oneuptime.com/blog/post/2026-01-26-spring-boot-testcontainers/view`

### 1.5 简历写法（把成果写出来）

- 项目经验怎么写（STAR/量化）：`https://www.wondercv.com/blog/qbihkjsq`
- RAG 项目如何写出工程含金量（避免“一行代码 RAG”）：`https://javabetter.cn/sidebar/itwanger/qiuzhi/paismart-rag-citation.html`

### 1.6 可对标学习的仓库（看结构与交付形态）

- 企业级 Agentic RAG 参考（结构/文档/后台/链路）：[nageoffer/ragent](https://github.com/nageoffer/ragent)
- 作品集型基础设施实践（学习“可交付”与 CI/CD）：`https://dev.to/dinku143/how-i-built-my-cloud-resume-on-azure-with-terraform-github-actions-54no`

> 学仓库不是为了照抄功能；是为了学它们**怎么把“想法”变成“能交付的工程证据”**。

---

## 2. 4 周任务总览（每天 5h+）

每一天按这个结构执行：

- **学习（1～2h）**：只看足够支撑当日任务的材料。
- **实现（2～3h）**：小步提交（建议至少一次可运行）。
- **验收（0.5～1h）**：写测试/跑一次 demo/补文档/截图。

> 如果你当天只有 3h，也要优先保证“实现 + 验收”，学习可以压缩。

---

## Week 1 — 链路闭环 + 密钥治理 + 最小工程基线

目标：让项目从“看起来像 Demo”变成“有工程底座”，并把**RAG 链路收口**成一条可解释路径。

### Day 1：项目体检 & 目标定标（5h）

- **学习（1h）**
  - 阅读：WonderCV 项目经验写法（抓住 STAR/量化）`https://www.wondercv.com/blog/qbihkjsq`
- **实现（3h）**
  - 输出一份“当前项目现状清单”（可写在 `docs/STATUS.md` 或直接在本文件下方附录）
  - 统一项目命名与描述：一句话定位 + 目标用户 + 不做什么（范围）
- **验收（1h）**
  - README 增加“项目定位 / 能力边界 / 演示路径”（不改太长）

**产出**：README 最顶部 10 行就能让人理解你在做什么。

### Day 2：密钥外置（5h）

- **学习（1h）**
  - 学习“配置分层”的基本套路（环境变量、local 配置、示例文件）
- **实现（3h）**
  - 把 `application.yml` 中所有敏感信息替换为占位符（例如 `${SPRING_AI_DASHSCOPE_API_KEY}`）
  - 增加 `application-local.yml.example`（只写 key 名，不写值）
  - `.gitignore` 增加 `application-local.yml` / `.env` 等
  - 立即轮换已泄露的 Key（这是操作步骤，不是代码）
- **验收（1h）**
  - 仓库中搜不到真实 key（通过搜索确认）
  - README 增加环境变量表

**产出**：安全底线达标（这是“上线项目”最直观的门槛之一）。

### Day 3：RAG 只保留“一条链路”（5h）

- **学习（1.5h）**
  - Spring AI RAG：`https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html`
- **实现（2.5h）**
  - 选一种方案（A 或 B）并落实：
    - A：去掉 `RetrievalAugmentationAdvisor`，保留你现有的“改写 + 多检索 + 拼上下文”，并确保最终 prompt 使用拼好的内容
    - B：保留 Advisor，但把多 query 合并检索做成 retriever，避免手写和 Advisor 同时检索
- **验收（1h）**
  - 打印/记录一次请求的“检索条数、最终 prompt 长度”（不打印隐私内容）
  - `docs/ARCHITECTURE.md` 画一张最简链路图（可以是文本流程图）

**产出**：RAG 行为与文档一致，延迟/成本可预期。

### Day 4：知识入库治理（5h）

- **学习（1h）**
  - 了解向量库 metadata 与“按用户隔离”概念（文章/文档随便一篇即可）
- **实现（3h）**
  - 上传入库：限制文件大小/类型（txt）、拒绝空文件
  - `KnowledgeInitializer` 改为“仅 dev 启动 / 幂等导入 / 可配置开关”，避免每次启动重复写
- **验收（1h）**
  - 写 1 个最小测试（上传后 chunk 数正确、入库被调用）

**产出**：知识库不是“随便灌进去”，而是可控。

### Day 5：日志与可观测最小化（5h）

- **学习（1h）**
  - 了解 SLF4J + MDC 的 requestId 思路
- **实现（3h）**
  - 用 SLF4J 替换 `System.out.println`
  - 在请求入口生成 `requestId` 并贯穿日志（MDC）
  - 对敏感信息（API key、用户输入）做最小脱敏策略（至少不打全量）
- **验收（1h）**
  - 一次完整请求日志能串起来（可读性提升）

### Day 6：打第一个“可写简历”的小结（5h）

- **学习（1h）**
  - 看一篇“RAG 简历怎么写”类文章，抓要点：`https://javabetter.cn/sidebar/itwanger/qiuzhi/paismart-rag-citation.html`
- **实现（2h）**
  - 把你完成的改动整理成 3 条简历 bullet（写在 `docs/RESUME_BULLETS.md`）
- **验收（2h）**
  - 录一段 30 秒演示（上传 → 问答 → SSE 输出），放到 `docs/demo.md` 说明如何复现

---

## Week 2 — 鉴权 + 会话隔离 + 可靠性（上线最小安全包）

目标：从“谁都能调”变成“至少不裸奔”，具备基础上线条件。

### Day 1：引入 Spring Security & JWT 最小闭环（5h）

- **学习（2h）**
  - JWT 简化版实践：`https://oneuptime.com/blog/post/2026-01-25-jwt-authentication-spring-security/view`
- **实现（2h）**
  - `POST /auth/login`（可先硬编码账号或内存用户），返回 JWT
  - 所有业务接口要求 `Authorization: Bearer ...`
- **验收（1h）**
  - 未登录访问返回 401；登录后能正常 SSE

### Day 2：会话与用户绑定（5h）

- **学习（1h）**
  - 了解“资源所有权校验”（conversation 属于 user）
- **实现（3h）**
  - 服务端生成 `conversationId`（不要信任客户端随便传）
  - Redis ChatMemory key 里加入 userId 维度，或在会话表中保存 owner
- **验收（1h）**
  - 用户 A 无法读取用户 B 的 conversation（写成测试或手测脚本）

### Day 3：限流（最小版）与超时策略（5h）

- **学习（1h）**
  - 了解 Bucket4j / Resilience4j 的基本用法（选其一）
- **实现（3h）**
  - 对 `/travel/chat` 做用户级限流（例如 5 req/min）
  - 外部调用（天气/LLM/DB）设置合理超时与失败提示
- **验收（1h）**
  - 触发限流时返回明确状态与错误体（别 500）

### Day 4：健康检查（Actuator）与运行状态（5h）

- **学习（1h）**
  - 了解 Actuator health
- **实现（3h）**
  - 接入 actuator：`/actuator/health`
  - 可选：增加 Redis/DB 自定义 indicator
- **验收（1h）**
  - Compose 启动后 health 能体现依赖是否可用

### Day 5：SSE 工程化（5h）

- **学习（2h）**
  - SSE + 背压：`https://lorenzomiscoli.com/server-sent-events-in-spring-boot/`
  - 背压实践：`https://www.javacodegeeks.com/2025/07/mastering-backpressure-in-spring-webflux-for-streaming-apis.html`
- **实现（2h）**
  - 增加心跳/keepalive（避免代理断开）
  - 增加断线处理（客户端断开时停止上游）
- **验收（1h）**
  - 浏览器/客户端断开后服务端不持续占用资源

### Day 6：周总结（5h）

- **实现（3h）**
  - 更新 `docs/ARCHITECTURE.md`：把鉴权、会话隔离、限流、SSE 心跳写进去
- **验收（2h）**
  - 输出 3 条简历 bullet（安全/上线相关）

---

## Week 3 — 可部署与可复现（Docker Compose + Testcontainers + CI）

目标：让别人 clone 仓库后可以“像产品一样”启动与验证。

### Day 1：Dockerfile（5h）

- **学习（1h）**
  - 多阶段构建（Maven build → JRE runtime）基本写法
- **实现（3h）**
  - Dockerfile：构建 jar、运行 jar
- **验收（1h）**
  - 本地 `docker build` 成功并能跑起来

### Day 2：docker-compose（5h）

- **实现（4h）**
  - `docker-compose.yml`：app + postgres + redis
  - 环境变量注入（API Key 用环境变量，不写死）
- **验收（1h）**
  - 一条命令启动，访问 health 与 chat 成功

### Day 3：数据库迁移（Flyway/Liquibase，选一个）（5h）

- **学习（1h）**
  - Flyway 基本概念（版本化 SQL）
- **实现（3h）**
  - 把 `vector_store` 表结构写成迁移脚本
- **验收（1h）**
  - 新库启动后自动建表，不靠手工 SQL

### Day 4：Testcontainers（Postgres + Redis）（5h）

- **学习（2h）**
  - Docker 官方 Testcontainers：`https://docker.com/blog/spring-boot-application-testing-and-development-with-testcontainers`
- **实现（2h）**
  - 引入 `spring-boot-testcontainers` 与 `@ServiceConnection`
  - 写 1～2 个集成测试：上传入库 / 记忆读写 / 向量检索调用链
- **验收（1h）**
  - 纯 `mvn test` 不需要你手工起 DB/Redis

### Day 5：GitHub Actions（CI）（5h）

- **学习（1h）**
  - GitHub Actions 的 Maven workflow 模板
- **实现（3h）**
  - `ci.yml`：checkout → setup-java → mvn test
- **验收（1h）**
  - PR/Push 触发 CI 绿灯

### Day 6：周总结（5h）

- **实现（2h）**
  - README 增加“本地启动（Compose）/测试（Testcontainers）/CI 状态”说明
- **验收（3h）**
  - 再录一段 60 秒演示：`docker compose up` → 上传 → 登录 → SSE

---

## Week 4 — 产品感：最小前端 + 评测/指标 + 简历最终稿

目标：让项目“看起来像产品”，并把简历叙事写到位。

### Day 1～2：最小前端（10h，建议 React/Vite）

- **学习（2h）**
  - 学 1 个 SSE 前端消费示例（EventSource / fetch streaming）
- **实现（6h）**
  - 页面：登录（拿 token）→ 输入框 → SSE 输出区域（Markdown 渲染可选）
  - 会话列表（最小版：最近 5 个 conversation）
- **验收（2h）**
  - 前后端联调，体验顺畅（断线重连可选）

> 如果你不想做前端：至少提供一套 `scripts/` 下的 curl 脚本 + 截图/录屏，但“产品感”会弱一点。

### Day 3：RAG 质量与可解释（5h）

- **实现（3h）**
  - 给回答加“引用片段/来源”（最小版：返回时附带使用到的 top 文本片段）
- **验收（2h）**
  - 用 10 个固定问题做回归（写成 `docs/eval.md`），观察是否更稳定

### Day 4：指标与性能（5h）

- **实现（3h）**
  - 记录关键耗时：重写、检索、生成（日志或 metrics）
- **验收（2h）**
  - 给出一个很小的量化结果：如平均响应时间/首包时间（哪怕是本机基准）

### Day 5：简历 bullet 最终版（5h）

- **实现（3h）**
  - 输出 6 条简历 bullet（每条都能在仓库里找到证据：CI、Compose、测试、鉴权、链路）
- **验收（2h）**
  - 让一个同学按 README 跑一遍（或你用全新目录/全新机器模拟），补全缺失步骤

### Day 6：整理发布（5h）

- **实现（3h）**
  - 打 tag：`v0.1-mvp`
  - README 顶部放：架构图、演示 GIF/视频链接、启动命令
- **验收（2h）**
  - 对照第 0 节「证据清单」逐项勾掉

---

## 3. 6～8 周扩展路线（如果你想继续追“更像企业级”）

从这里开始，才建议逐步靠近 ragent 那种「平台化」能力（但不要一口吃成胖子）：

- **意图识别与澄清**：低置信度时追问，不硬答。
- **模型路由与降级**：多供应商/多模型候选，失败自动切换（先做非常简化版）。
- **检索后处理**：重排、去噪、chunk 合并策略；混合检索（BM25 + 向量）。
- **会话摘要压缩**：超过 N 轮自动摘要，控制 token 成本。
- **管理后台**：知识库、文档、chunk、检索 trace 的可视化。

可学习对标：仍可参考 [nageoffer/ragent](https://github.com/nageoffer/ragent) 的目录、文档组织与“链路表达方式”，但只挑你能在 1～2 周落地的点。

---

## 4. 你最终“怎么写进简历”（模板）

> **智能出行规划助手（RAG / SSE / Spring AI）**（个人项目）  
> - 设计并实现「问题改写 → 多路检索 → 上下文增强 → 流式生成」RAG 链路，统一检索入口并编写集成测试保障链路一致性  
> - 基于 PostgreSQL + pgvector 实现知识入库与向量检索，增加文档生命周期治理与幂等初始化，避免重复向量污染  
> - 引入 Spring Security + JWT 实现鉴权与会话所有权校验，修复 conversationId 越权风险，并增加用户级限流与失败降级  
> - 完成 Docker Compose 一键启动（App + Postgres + Redis）与 Actuator 健康检查；接入 CI（GitHub Actions）实现提交即自动测试  

你要做的是让每条 bullet 都能在仓库里找到证据：测试文件、Compose、CI、文档、演示。

---

## 5. 每周复盘提问（强烈建议）

每周末用 15 分钟回答这 5 个问题（写到 `docs/WEEKLY_NOTES.md`）：

1. 本周最有含金量的一件事是什么？（能写简历的那种）
2. 哪个设计决策是你做的？为什么？
3. 哪个坑最痛？怎么定位与修复的？
4. 下周要减少什么“无效工作”？
5. 现在别人 clone 下来，能不能在 10 分钟内跑通演示？

---

## 6. 逐天改动映射（对应你当前仓库）

说明：下面是“你在代码层面大概率会动到的点”。具体实现方式你可以按技术选择调整，但方向尽量保持一致：**收口 RAG 链路 + 上线安全底座 + 可复现交付 + 可演示闭环**。

### Week 1（链路收口 + 安全配置 + 可测基线）

Day 1（项目体检 & 目标定标）
- 只改文档：`README.md`、（可选）`docs/STATUS.md`
- 产出：记录你当前“RAG 是否真正进入模型”的观察与待办

Day 2（密钥外置）
- 改配置：`src/main/resources/application.yml`（把 `api-key` / 数据库密码等替换为占位符）
- 新增示例文件：`application-local.yml.example`（或 `docs/ENVIRONMENT.md`）
- 改忽略：`.gitignore`（加入本地配置文件规则）
- 产出：仓库中不含真实 key（验收通过“搜索关键字”）

Day 3（RAG 只保留“一条链路”）
- 核心改动：`src/main/java/com/powernode/springmvc/agent/TravelAgent.java`
  - 选择 A 或 B 后删除/调整其中一条“检索路径”
  - 确保检索到的 `Document` 文本最终进入 `ChatClient` 的 prompt 消息（而不是只在变量里拼接）
- 可能涉及：`src/main/java/com/powernode/springmvc/agent/QueryRewriter.java`（如果你需要调整改写调用/输出格式稳健性）
- 产出：`docs/ARCHITECTURE.md`（最简链路图）+ 一次请求的检索/拼装统计日志（注意脱敏）

Day 4（知识入库治理）
- 改上传接口：`src/main/java/com/powernode/springmvc/service/impl/KnowledgeServiceImpl.java`
  - 限制上传类型/大小/空文件
  - 避免把全文内容 `System.out.println` 打到日志（敏感/噪声）
- 改初始化：`src/main/java/com/powernode/springmvc/agent/KnowledgeInitializer.java`
  - 增加开关（dev 才灌库）或幂等逻辑
- 可选：`src/main/java/com/powernode/springmvc/config/PgVectorStore.java`（为 metadata/去重预留扩展点）
- 产出：至少 1 个测试或最小验证脚本（上传后 chunk 入库可见）

Day 5（日志与可观测最小化）
- 全局替换：把 `System.out.println` 替换为 SLF4J（Spring Boot 默认日志系统）
  - 涉及文件：`TravelAgent.java`、`RedisChatMemory.java`、`KnowledgeServiceImpl.java`、`KnowledgeInitializer.java`、`WeatherTool.java`、等
- 改造 requestId：通常在 `TravelController` 或全局拦截器里生成并写 MDC
  - 起点：`src/main/java/com/powernode/springmvc/controller/TravelController.java`
- 产出：一次请求日志可按 requestId 串起来（并且不泄露 key/隐私）

Day 6（简历 bullet + 演示脚本）
- 新增文档：`docs/RESUME_BULLETS.md`、`docs/demo.md`
- 可选新增目录：`scripts/`（curl 或脚本复现上传与 SSE 输出）

### Week 2（鉴权 + 会话隔离 + 可靠性 + SSE 工程化）

Day 1（Spring Security & JWT 最小闭环）
- 改依赖：`pom.xml`（加入 `spring-boot-starter-security`、JWT 库等；按你选择的实现追加）
- 新增模块/类（示例命名，你可按习惯调整）：
  - `com.powernode.springmvc.security.SecurityConfig`
  - `com.powernode.springmvc.security.JwtAuthFilter`
  - `com.powernode.springmvc.security.JwtService`
  - `com.powernode.springmvc.controller.AuthController`
- 改造接口：
  - `TravelController`：确保 `/travel/chat/**` 需要鉴权
- 产出：未登录 401、有 token 才能建立 SSE

Day 2（会话与用户绑定）
- 改会话存储：`src/main/java/com/powernode/springmvc/config/RedisChatMemory.java`
  - key 维度从 `conversationId` 扩展到 `userId:conversationId`（或至少在逻辑层校验所有权）
- 改入口参数：
  - `TravelController.chat(...)`：不要相信客户端传来的 conversationId 归属，或加入所有权校验
- 可选：新增 `ConversationService`（用于读/写 conversation owner）
- 产出：用户 A 不能读用户 B 的会话（测试或脚本验证）

Day 3（限流 + 超时）
- 选择实现：
  - 方案 1：Bucket4j（实现简单）
  - 方案 2：Resilience4j（熔断/重试更完整）
- 接入点：
  - 全局过滤器对 `/travel/chat/...` 做限流（按用户/IP 维度，每分钟限次，超额返回 429 + 统一 JSON 错误体）
- 外部调用超时：
  - 天气：`WeatherTool.java` 使用 OkHttp 设置 connect/read/call timeout，并对超时/异常返回友好降级提示
  - LLM：`TravelAgent.chat(...)` 使用 Reactor `.timeout + onErrorResume` 做整体超时与兜底提示，避免 SSE 长时间挂起
- 产出：触发限流时返回明确错误码（429）与 JSON 错误体，外部依赖超时时 SSE 端能收到清晰系统提示而不是静默失败

Day 4（Actuator health）
- 改依赖：`pom.xml` 加 `spring-boot-starter-actuator`
- 新增配置：`src/main/resources/application.yml` 打开/配置 actuator 暴露路径
- 可选：自定义 health indicator（Redis、Postgres 可用性）
- 产出：`/actuator/health` 能反映服务依赖状态

Day 5（SSE 工程化）
- 核心入口：`TravelController` + 返回 `Flux<ServerSentEvent<String>>`（`data` 正文 + `comment` 心跳）的资源释放行为
  - `TravelController.java`（响应头、心跳/断线策略）
  - `TravelAgent.chat(...)`（`share` + `merge` + `takeUntilOther`，确保上游在取消订阅时可停止、避免重复调用 LLM）
- 产出：客户端断开后服务端不会持续占用资源（可用日志观察）

Day 6（周总结）
- 写文档：`docs/ARCHITECTURE.md` 补上 Week3 的 Docker Compose / Flyway / Testcontainers / CI 设计（已完成）
- 周总结证据补齐：`docs/STATUS.md` 更新 Week3 状态（Flyway 迁移 + Testcontainers 集成测试 + GitHub Actions 自动跑 `mvn test`）
- 更新 `docs/RESUME_BULLETS.md`：加入“Flyway（pgvector schema）+ Testcontainers（迁移验证）+ CI（可复现）”表述（已完成）

### Week 3（可部署与可复现：Docker Compose + 测试 + CI）

Day 1（Dockerfile）
- 新增文件：`Dockerfile`
- 改依赖/打包方式：检查 `mvn package` 生成的 jar 名（不一定需要改代码）
- 产出：`docker build` + `docker run` 后可访问健康检查

Day 2（docker-compose）
- 新增文件：`docker-compose.yml`
- 服务：
  - app：运行 jar
  - redis：`redis:7`
  - postgres：`postgres:15`
- 改配置：
  - `application.yml` 允许从环境变量读 DB/Redis host/port
- 产出：一条命令起全套并能跑通 `/travel/chat`（带鉴权）

Day 3（数据库迁移）
- 新增迁移工具之一：Flyway 或 Liquibase（选一个）
- 新增目录：`src/main/resources/db/migration/`（如果用 Flyway）
- 需要迁移的表：
  - `vector_store`（以及你后续新增的 conversation/user 等表）
- 产出：新库从空开始启动时自动建表

Day 4（Testcontainers）
- 改依赖：`pom.xml` 增加 `spring-boot-testcontainers` / `testcontainers`
- 新增测试：
  - `src/test/java/.../TravelIntegrationTest.java`（或按模块拆）
  - 覆盖：上传入库 / 记忆读写 / 向量检索链路
- 产出：`mvn test` 在本机无需手工起 DB/Redis

Day 5（GitHub Actions）
- 新增：`.github/workflows/ci.yml`
- 产出：push 后自动跑 `mvn test` 并报告结果

Day 6（演示与复盘）
- 更新 README：补齐 Docker Compose 启动 + `/actuator/health` 验收 + `mvn test` 验证说明（已完成）
- 新增演示材料：`docs/demo.md`（60 秒流程：Compose 起 → 登录拿 token → 上传 → SSE 输出）（已完成）
- 产出：别人克隆后能按 README 在 10 分钟内完成“起服务 + health + 集成测试验证”

### Week 4（产品感：最小前端 + 评测/指标 + 简历最终版）

Day 1～2（最小前端）
- 新增目录：`frontend/`（如果你选择前端）或新增 `scripts/`（如果你只做脚本）
- 若做前端：
  - 实现登录拿 token
  - 使用 EventSource 或 fetch streaming 消费 `/travel/chat` SSE
- 产出：网页里能点、能看到流式输出
- **当前进度**：已落地最小 **`frontend/`（Vite + React）**：`/api` 代理到本机 `8081`、`POST /api/auth/login` 存 token、`fetch` 解析 SSE（因 Bearer 无法走 `EventSource`）。

Day 3（RAG 质量与可解释）
- 回答增强：
  - `TravelAgent.java`：把“检索片段/来源ID”以某种形式返回（最小版即可）
- 新增文件：`docs/eval.md`（10 个固定问题 + 观察结果）
- 产出：你能说明为什么答案更稳（哪怕是定性 + 回归对比）
- **当前进度**：`PgVectorStore` 持久化/回读 `metadata` 并支持 `user_id` SQL 过滤；`TravelAgent` 在 SSE **首段 `data`** 输出 `【引用片段】`（含 id、来源、截断正文）；已新增 `docs/eval.md`。

Day 4（指标与性能）
- 新增/改日志或 metrics：
  - 记录重写耗时、检索耗时、生成耗时
- 可选：加入 Micrometer（如果你愿意）
- 产出：至少一张简单结果（本机平均首包时间等）
- **当前进度**：`TravelAgent` 在 INFO 中输出 `[perf] rewrite_ms / retrieve_ms / doc_count`（改写结束至检索结束分段）以及 `llm_first_token_ms`、`llm_stream_wall_ms`（流式首包与整段 wall，均带 `requestId`）。本机一次样例（通义千问 + 本地 Postgres）：`rewrite_ms≈2000`、`retrieve_ms≈800`、`llm_first_token_ms≈1500`（仅作定性参考，随网络与模型负载变化）。

Day 5（简历 bullet 最终版）
- 更新 `docs/RESUME_BULLETS.md`：6 条 bullet 最终稿
- 产出：每条 bullet 都能对应到仓库里的证据（文件/测试/CI/Compose）
- **当前进度**：`docs/RESUME_BULLETS.md` 已收敛为 **6 条**，每条末尾附「证据」路径（源码/迁移/CI/前端）。

Day 6（整理发布）
- 新增发布标签与变更说明：
  - 不需要改代码逻辑，只做 `git tag` 与 README 补齐
- 产出：`v0.1-mvp` tag 可检索，README 一键跑通
- **当前进度**：根 `README.md` 顶部增加 MVP 速览表 + Mermaid 架构图 + `docs/demo.md` / `CHANGELOG.md` / `RESUME_BULLETS` 索引；新增 `CHANGELOG.md`；`RedisChatMemory` 中 Redis 原始 JSON 改为 **DEBUG**，减少 INFO 噪音。本地执行：`git tag -a v0.1-mvp -m "MVP"` 后 `git push origin v0.1-mvp`。

