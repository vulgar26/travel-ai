# 升级方案（本仓执行 backlog）

## 准则与真源

- **规划 SSOT（单一上游）**：`D:\Projects\Vagent\plans\travel-ai-upgrade.md` —— 研发助手 Agent 的完整路线图（Harness 两层、P0 数据合规与验收门槛、线性控制流与 `GUARD` 在 `WRITE` 前、plan 解析治理、与 eval 的 HTTP/契约对接、P1+ 能力如 rerank / multi-hop / evidence map / 长期记忆写入治理等）。
- **本文件角色**：把 SSOT 落到本仓库的项整理为**可勾选 backlog**，并保留早期**代码评审**衍生的 P0–P5 条目（实现细节、文件、验收）；**不重贴** SSOT 全文，避免双源漂移。
- **实现对照真源**：[`docs/IMPLEMENTATION_MATRIX.md`](IMPLEMENTATION_MATRIX.md)（本仓 `src/**` + `application*.yml`）。

下文将此前评审中提出的缺口整理为**可分批执行**的升级路线：每项含背景、具体改法、涉及文件、验收标准与优先级。实施时建议按 **P0 → P1 → P2** 顺序推进，避免并行改动导致难以回归。

### 与 `travel-ai-upgrade.md` 的映射（执行视角）

| SSOT 区块 | 在本仓的跟踪入口 |
| --- | --- |
| 现状与一句话定位 | `README.md`、`docs/ARCHITECTURE.md`、矩阵 §1 |
| Evaluation / Execution Harness、`meta` 可观测字段 | 矩阵 §1–2；`TravelAgent`、`EvalChatService`、`EvalLinearAgentPipeline` |
| P0 合规（PII、最小化、保留期、删除权、日志脱敏） | 矩阵 §1、§4；`UserProfileController`、`application.yml`（画像/确认/注入） |
| P0 验收门槛（`CONTRACT_VIOLATION`、`UNKNOWN`、`TIMEOUT`、`stage_order`、`plan_parse_*`、`replan_count` 等） | 以外部文档阈值为标准；**操作步骤**见 [`docs/eval/P0_THRESHOLD_RUNBOOK.md`](eval/P0_THRESHOLD_RUNBOOK.md)；eval **`run.report`** 聚合 + SSE **`event: plan_parse`** 与日志对账 |
| `sources[]` 与 SSE 引用 | 评测 JSON 与 SSE 文本首包 **同源、不同载体** | [`docs/eval/SOURCES_EVAL_VS_SSE.md`](eval/SOURCES_EVAL_VS_SSE.md) |
| 与 eval 的对接（`X-Eval-Gateway-Key`、`X-Eval-*`、超时墙钟） | `EvalGatewayAuthFilter`、`AppEvalProperties`、`EvalChatController`；矩阵 §2 |
| P1+（反馈表、ReAct、多跳、rerank、DAG…） | 按 SSOT 分期立项；本文件 P2–P3 与部分 P1 为增量工程，不与 SSOT 逐字重复 |
| P1-0 harness（`meta` 扩展、`config_snapshot`、上下文截断等） | **第 1 步**：缺口盘点 [`docs/eval/P1_HARNESS_GAP.md`](eval/P1_HARNESS_GAP.md)；后续按该文 §3 分 PR 实现 |

### 工程债（REST / 鉴权错误体）— 收口说明

- **目标**：受保护业务接口在 **401 / 403** 及常见 **4xx** 上返回 **`application/json`**，形体与评测网关、知识上传校验一致：`{"error","message"}`。（**429** 仍沿用 `RateLimitingFilter` 的 `code` + `message`，后续可选与 `error` 字段对齐。）
- **实现**：`SecurityConfig` 注册 JSON `AuthenticationEntryPoint` / `AccessDeniedHandler`；`JsonApiErrorSupport` 供 `EvalGatewayAuthFilter` 复用；`RestApiExceptionHandler` 处理 `ResponseStatusException`、`HttpMessageNotReadableException`、`MissingServletRequestParameterException`。集成测试断言未带 JWT 访问 `/travel/chat/...` 时 **Content-Type** 为 JSON 且含 `UNAUTHORIZED`。

---

## 0. 目标与原则

| 原则 | 说明 |
| --- | --- |
| 文档即契约 | README / `docs/ARCHITECTURE.md` 中的数字、行为须与代码或配置一致。 |
| 安全边界显式化 | 演示级能力在文档中写明；加固项用配置 + 测试锁住。 |
| 小步可验证 | 每阶段完成后能独立跑 `mvn test` 或手动验收。 |

**建议里程碑命名**（可选）：`v0.2-doc-security`、`v0.3-rag-ops`、`v0.4-tests-api`。

---

## P0 — 契约一致与配置外置

### P0-0 数值门槛验收（runbook）

**背景**：`travel-ai-upgrade.md` 中 P0 **比例型门槛**（`UNKNOWN`/`TIMEOUT`/`plan_parse_failed` 占比等）必须由 eval 全量 **`run.report`** 证明；单靠本仓单元测试不足以声称「长期达标」。

**已定稿**：[`docs/eval/P0_THRESHOLD_RUNBOOK.md`](eval/P0_THRESHOLD_RUNBOOK.md) —— 固定 dataset 规模约定、逐条门槛的聚合公式、`meta`/`latency_ms` 核对顺序、离线 `EvalChatControllerTest` 烟测范围、与 [`docs/DAY10_P0_CLOSURE.md`](DAY10_P0_CLOSURE.md) 归档的衔接。

---

### P0-1 聊天限流：代码与文档一致，并外置到配置

**问题**：`RateLimitingFilter` 硬编码为每分钟 2 次，与 README / 架构文档中「约 5 次/分钟」等表述不一致。

**改法**：

1. 在 `application.yml` 增加可配置项，例如：
   - `app.rate-limit.chat.requests-per-minute`（整数）
   - 可选：`app.rate-limit.chat.burst`（若使用 Bucket4j 多带宽）
2. `RateLimitingFilter` 通过 `@Value` 或 `@ConfigurationProperties` 读取，构造 `Bandwidth` 时使用该值。
3. 同步修改 **README**、**`docs/ARCHITECTURE.md`**、**`docs/STATUS.md`** 中所有提及限流次数的句子，与默认值一致。

**涉及文件**：`RateLimitingFilter.java`、`application.yml`、`.env.example`（若有说明）、`README.md`、`docs/ARCHITECTURE.md`、`docs/STATUS.md`

**验收**：改配置后无需改代码即可调整限流；文档与运行行为一致。

---

### P0-2 默认 JWT 密钥：弱密钥拒绝启动或强告警

**问题**：`APP_JWT_SECRET` 默认 `change-me-in-local`，误用于非本地环境时风险极高。

**改法**（二选一或组合）：

- **A（推荐）**：非 `local`/`dev` profile 时，若 secret 为空或长度小于 32 字符（或匹配弱口令列表），**启动失败**并打印明确错误。
- **B**：至少在生产 profile 下 `fail-fast`；本地保留短 secret 但日志 `WARN`。

**涉及文件**：新建 `JwtProperties` + `ApplicationRunner`/`EnvironmentPostProcessor` 校验，或 `TravelAiApplication` 内条件校验；`application.yml` 注释说明。

**验收**：用错误 secret 启动非本地 profile 时进程退出且信息可读；正确配置可正常启动。

---

## P1 — 安全加固（在「演示边界」之上加分）

### P1-1 登录接口专项限流

**问题**：`/auth/login` 当前无 Bucket4j 限流，易被撞库/爆破；聊天限流无法覆盖登录。

**改法**：

1. 扩展 `RateLimitingFilter`（或新增 `LoginRateLimitingFilter`）对 `POST /auth/login` 按 **IP**（及可选按 username hash）限流，策略应 **严于** 聊天（例如每分钟 10～20 次/ IP，可按产品调整）。
2. 限流参数同样 **配置化**（`app.rate-limit.login.*`）。
3. 返回体与现有 429 JSON 风格统一（`code` + `message`）。

**涉及文件**：`RateLimitingFilter.java` 或新 Filter、`SecurityConfig.java`（Filter 顺序）、`application.yml`、文档

**验收**：同一 IP 快速刷登录超过阈值得到 429；正常登录不受影响。

---

### P1-2 收紧 `SecurityFilterChain` 的 `anyRequest()`

**问题**：`anyRequest().permitAll()` 在新增端点时容易误放行。

**改法**：

1. 将默认策略改为 **`authenticated()`**，仅对明确白名单 `permitAll()`：`/auth/login`、`/actuator/health/**`、`/actuator/info`、静态错误页等（按实际暴露需求列出）。
2. 若存在 Springdoc/OpenAPI 等，单独加入白名单或仅 dev profile 启用。

**涉及文件**：`SecurityConfig.java`、`application*.yml`

**验收**：新增任意 `@RestController` 默认需认证；白名单路径仍匿名可访问。

---

### P1-3（可选）用户体系演进路线

**问题**：`InMemoryUserDetailsManager` 仅适合演示。

**改法**（择一做深即可写进简历）：

- 最小：**JdbcUserDetailsManager** + Flyway 用户表 + BCrypt；
- 或：独立 `User` 实体 + `UserDetailsService` 实现 + 注册接口（需邮箱/验证码则另起任务）。

**涉及文件**：新 migration、实体/Repository、`SecurityConfig`、`AuthController`

**验收**：用户数据持久化；密码不明文存储；集成测试覆盖注册或登录。

---

## P2 — RAG 链路鲁棒性与成本

### P2-1 `QueryRewriter` 失败与格式兜底

**问题**：完全信任模型按行输出 3 条 query；异常或胡言时检索质量骤降。

**改法**：

1. 解析后若有效行数小于 1，回退为 `List.of(userQuestion)` 或 `List.of(userQuestion, userQuestion 精简版)`。
2. 若行数 **1～2**，可 **补齐** 为原问题 + 同义词/短句（规则或再调一次小模型，择一）。
3. 对每行做 **最大长度截断**，避免异常长行拖垮 embed。
4. 记录 `warn` 日志（含 requestId，勿打全量隐私）。

**涉及文件**：`QueryRewriter.java`、`TravelAgent.java`（可选传入 requestId）

**验收**：单测构造畸形 LLM 输出仍返回非空 query 列表；集成测试可 Mock `ChatClient`。

---

### P2-2 检索结果按文档维度去重

**问题**：`Stream.distinct()` 依赖 `Document` 的 `equals`/`hashCode`，版本升级后行为可能不符合「按业务 id 去重」的预期。

**改法**：

1. 多路 `similaritySearch` 合并后，按 **`Document.getId()`**（非空）或 **`metadata` + 内容 hash** 显式去重，再 `limit(N)`。
2. 保持进入 prompt 的顺序（可按相似度分数排序，若当前 SQL 无分数，可先按首次出现顺序）。

**涉及文件**：`TravelAgent.java`（抽取 `mergeAndDedupeDocuments` 私有方法）

**验收**：单测给定重复 id 的列表，最终条数符合预期。

---

### P2-3（进阶）改写 / 向量缓存与问句分类

**问题**：每轮对话均调用改写 LLM + 多次 embed，成本与延迟随流量线性上升。

**改法**（按需实施）：

1. **改写缓存**：`userQuestion` 规范化（trim、小写）为 key，Redis TTL 5～30 分钟，命中则跳过改写 LLM。
2. **简单问句跳过改写**：规则或轻量分类器（关键词长度、是否含「规划」「几天」等），短问直接用原句检索。
3. **Embedding 缓存**：对 query 文本做 hash，缓存 `float[]` 或向量序列化结果（注意维度与模型版本一致，版本变更时清缓存）。

**涉及文件**：新建 `RewriteCache` / `EmbeddingCache` 或内聚在 `QueryRewriter`、`PgVectorStore`；`application.yml`

**验收**：压测或日志对比改写/ embed 调用次数下降；缓存失效策略有说明。

---

### P2-4（进阶）召回质量：重排或混合检索

**问题**：纯向量 TopK 在关键词精确匹配场景易漏召。

**改法**（选一条）：

1. **PostgreSQL 全文检索 + 向量**：`tsvector` 与 `embedding` 各取 TopK，合并去重；
2. **Cross-encoder / API 重排**：对合并候选集做小模型重排（成本更高）。

**涉及文件**：Flyway 新 migration、`PgVectorStore` 或新 DAO、配置开关

**验收**：`docs/eval.md` 中问题集通过率或可解释案例数提升（需基线对比）。

---

## P3 — 数据层与运维

### P3-1 `PgVectorStore.add` 批量写入

**问题**：逐条 `INSERT` 在大文件分块场景下延迟高、事务多。

**改法**：

1. 使用 **批处理** `addBatch` + 单事务 `commit`，或 JDBC `rewriteBatchedStatements`（按 PG 驱动文档验证）。
2. 控制单批大小（如 50～100）避免单事务过大。

**涉及文件**：`PgVectorStore.java`

**验收**：上传大 txt 时分块入库耗时明显下降；集成测试或单测验证条数正确。

---

### P3-2 向量检索与索引运维说明

**问题**：数据量增长后需明确索引与维护策略。

**改法**：

1. 在 `docs/ARCHITECTURE.md` 或新建 `docs/OPS_VECTOR.md` 写明：`vector_store` 上 **ivfflat/hnsw**（与当前 Flyway 一致）、`ANALYZE`、近似参数、重建索引条件。
2. （可选）提供简单 **数据量阶梯压测** 脚本或 JMH/手工步骤：1k / 10k / 100k 行下 P95 检索延迟。

**涉及文件**：文档、可选 `scripts/` 或 `src/test` 性能类

**验收**：新成员能按文档完成索引与健康检查。

---

### P3-3 Flyway `baseline-version: 0` 说明

**问题**：非常规 baseline，新人易误解为何 V1 仍会执行。

**改法**：在 `docs/ARCHITECTURE.md` 或 `README`「本地运行」节增加 **「已有库 / 绿场库」** 两种操作说明；绿场是否可改为默认 `baseline-version: 1` 单独评估。

**验收**：文档能回答「为什么这样配」。

---

## P4 — 测试与 CI

### P4-1 `KnowledgeServiceImplTest` 绑定安全上下文

**问题（历史）**：测试未设置 `SecurityContext` 时，`user_id` 实际为 `anonymous`，未覆盖真实业务断言。

**状态**：已在 `@BeforeEach` / `@AfterEach` 中挂载与清理 `SecurityContext`（等价于已登录 `demo`）；亦可选改用 `@WithMockUser("demo")`。

**改法**：使用 `@WithMockUser("demo")`（Spring Security Test）或在 `@BeforeEach` 中 `SecurityContextHolder` 注入认证。

**涉及文件**：`KnowledgeServiceImplTest.java`、`pom.xml`（若缺 `spring-security-test`）

**验收**：断言 `vectorStore.add` 捕获的 `Document` metadata 中 `user_id` 为期望用户。

---

### P4-2 JWT 集成测试路径

**改法**：

1. `TestRestTemplate`：`POST /auth/login` 取 token → `GET`/`POST` 带 `Authorization` 访问 `/knowledge/upload` 或聊天接口。
2. 无 API Key 时聊天测试可 **Mock** LLM 层或仅测 **401/403/429** 与 header 契约，避免 CI 依赖 DashScope。

**涉及文件**：`TravelAiApplicationIntegrationTest.java` 或新建 `*SecurityIntegrationTest.java`

**验收**：CI 中不依赖真实 LLM 也能验证鉴权链路。

---

### P4-3 `TravelController` / SSE 契约测试（可选）

**改法**：`WebMvcTest` + `MockBean` 注入 `TravelAgent`，验证响应 `Content-Type`、`Authorization` 缺失时 401；或仅测 Filter 链。

**涉及文件**：新测试类

**验收**：重构 Controller 时 SSE 与 security 行为有回归网。

---

## P5 — API 与代码整洁

### P5-1 删除无用依赖注入

**问题（历史）**：`TravelController` 曾注入未使用的 `ChatClient.Builder`；`TravelAgent` 曾存在未使用依赖风险。

**状态**：`TravelController` 已仅保留在用 Bean；`TravelAgent` 构造器中的 `RedisChatMemory`、`ChatClient.Builder` 等均为在用依赖（以当前 `src` 为准）。

**改法**：删除无用字段与 import；构造器注入已足够处保持单一数据源。

**涉及文件**：`TravelAgent.java`、`TravelController.java`

**验收**：编译无告警；行为不变。

---

### P5-2 聊天从 GET 演进为 POST（推荐用于「上线叙事」）

**问题**：`GET /travel/chat/...?query=` 易导致 URL 长度限制、访问日志泄露、缓存语义模糊。

**改法**：

1. 新增 `POST /travel/chat/{conversationId}`，Body JSON：`{ "query": "..." }`，保留 **GET 为 deprecated** 一段时间或仅文档标注 breaking change。
2. 服务端校验 query 最大长度（如 4K～8K 字符）。
3. 同步更新 **`frontend/`** 代理与调用示例。

**涉及文件**：`TravelController.java`、`frontend/*`、`README.md`、`docs/demo.md`

**验收**：前端可走 POST；原 GET 行为有迁移说明或兼容期。

---

## 附录 A — 实施顺序建议

| 顺序 | 编号 | 说明 |
| --- | --- | --- |
| 1 | P0-1, P0-2 | 低成本、立刻提升可信度 |
| 2 | P5-1 | 纯清理，几分钟完成 |
| 3 | P1-1, P1-2 | 安全加分项 |
| 4 | P4-1, P4-2 | 锁住行为 |
| 5 | P2-1, P2-2 | RAG 质量与稳定性 |
| 6 | P3-1, P3-2, P3-3 | 规模与运维 |
| 7 | P1-3, P2-3, P2-4, P5-2 | 按需选做 |

---

## 附录 B — 完成后自检清单

- [x] README / ARCHITECTURE / STATUS 中与限流、端口、密钥相关的描述已与代码一致（持续以 `IMPLEMENTATION_MATRIX` 为对照）  
- [x] 弱 JWT secret：docker/prod/production profile 下 `JwtSecretStartupValidator` fail-fast（见 P0-2 改法 A）  
- [x] `/auth/login` 具备限流且可配置（`RateLimitingFilter` + `app.rate-limit.login.*`）  
- [x] `SecurityConfig` 默认「非白名单需认证」；评测路径另需 `X-Eval-Gateway-Key`  
- [x] Filter 链 **401/403** 与 `RestApiExceptionHandler` 对常见 4xx 返回统一 JSON（`JsonApiErrorSupport`；与 `EVAL_GATEWAY_*` 同形）  
- [x] P0 **比例型门槛**的验收步骤已文档化（[`docs/eval/P0_THRESHOLD_RUNBOOK.md`](eval/P0_THRESHOLD_RUNBOOK.md)；达标仍依赖每次全量 `run.report`）  
- [x] 评测口 **对抗 / 安全 / rag/tool** 确定性用例与建议 **`tags`** 已写入 [`docs/eval.md`](eval.md)（与 `EvalChatSafetyGate` 等源码对齐；批量导入 CI 仍待办）  
- [x] **`sources[]`（eval）与 SSE「引用片段」** 同源差异与对账方式已文档化（[`docs/eval/SOURCES_EVAL_VS_SSE.md`](eval/SOURCES_EVAL_VS_SSE.md)）  
- [x] P1-0 harness **第 1 小步**：`EvalChatMeta` 与 SSOT 差距已盘点（[`docs/eval/P1_HARNESS_GAP.md`](eval/P1_HARNESS_GAP.md)；尚未新增运行时字段）  
- [x] `QueryRewriter` 畸形输出有兜底；检索结果按 id 显式去重（`TravelAgent#mergeAndDedupeDocuments`）  
- [x] `KnowledgeServiceImplTest` 在 `@BeforeEach` 绑定 `SecurityContext`（等价于已登录 `demo`，见 P4-1）  
- [x] 至少一条 JWT 链路与 eval 网关链路的集成测试（`TravelAiApplicationIntegrationTest`，含未授权访问 JSON 断言）  
- [x] `TravelController` 无未用 `ChatClient.Builder`；`TravelAgent` 构造注入字段均为在用（见 P5-1；若后续引入死字段再开任务清理）  
- [ ] （可选）聊天 POST 与 query 长度校验已上线并文档化  

---

## 附录 C — 相关源码索引（升级起点）

| 领域 | 主要文件 |
| --- | --- |
| 限流 | `com.travel.ai.security.RateLimitingFilter` |
| 安全链 | `com.travel.ai.security.SecurityConfig`、`JwtAuthFilter`、`JwtService` |
| RAG | `com.travel.ai.agent.TravelAgent`、`QueryRewriter` |
| 向量库 | `com.travel.ai.config.PgVectorStore` |
| 知识上传 | `com.travel.ai.service.impl.KnowledgeServiceImpl` |
| 对话入口 | `com.travel.ai.controller.TravelController` |
| 集成测试 | `com.travel.ai.integration.TravelAiApplicationIntegrationTest` |
| 单测 | `com.travel.ai.service.KnowledgeServiceImplTest` |
| 统一 JSON 错误体 | `com.travel.ai.web.JsonApiErrorSupport`、`RestApiExceptionHandler` |

---

*文档版本：以 `D:\Projects\Vagent\plans\travel-ai-upgrade.md` 为规划 SSOT，本文件为迁仓 backlog；可按迭代增删「已完成」勾选或链接到 PR。*
