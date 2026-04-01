# Travel AI Planner — 二次升级计划（每日 5h+，6 周）

适用前提：你已完成 `PLAN_5H_PER_DAY.md` 的 4 周 MVP（至少具备：RAG 单链路闭环、鉴权/会话隔离、Compose/CI、关键路径测试、可演示）。  
目标：把项目从「能上线演示的 MVP」升级到更接近“企业级 RAG 系统”的三个核心特征：**效果可控（评测）**、**可解释（引用/溯源）**、**可扩展（多知识库/混合检索/可观测）**。

> 二次升级的重点不是“功能更多”，而是：你能用数据和证据证明“变强了、变稳了、变省了”。

---

## 0. 二次升级交付物（强证据包）

完成后你应至少具备以下证据（建议都放到 `docs/`，并在 README 顶部链接）：

- **评测体系**：`docs/eval/`（数据集、指标定义、运行方式、结果报告）
- **引用与溯源**：回答中显示引用（docId/chunkId/source），并可回查
- **混合检索**：向量 + 关键词（或 PostgreSQL FTS）并行，合并去重，能解释策略
- **多知识库/权限**：按 user/knowledgeBase 隔离检索，支持删除/重建索引
- **可观测**：至少有 metrics（或日志统计）覆盖 rewrite/retrieve/generate/tool 的耗时与失败率
- **性能/成本结果**：哪怕是本机基准（首包时间/平均耗时/缓存命中率）
- **演示素材**：60～120 秒录屏 + 2～3 张关键截图（引用、看板、评测结果）

---

## 1. 参考资料（Phase 2 专用）

### 1.1 Spring AI / RAG
- Spring AI RAG：`https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html`

### 1.2 观测与指标（建议 Micrometer + Actuator）
-（如果你已接 actuator，继续用即可；Phase2 重点是“做出指标与看板证据”）

### 1.3 对标仓库（只学“形状”，不追求体量）
- 企业级 RAG 参考（链路、后台、观测）：[nageoffer/ragent](https://github.com/nageoffer/ragent)

---

## 2. 6 周总览（每日 5h+）

每周 6 天执行，1 天机动/复盘。每天结构固定：
- 学习 1～1.5h（只学当天要用的）
- 实现 2.5～3h（小步可运行）
- 验收 0.5～1h（测试/截图/报告/脚本）

---

## Week 1 — 引用与溯源（Citations）+ Metadata 体系

目标：让回答“有据可依”，让知识库“可治理可隔离”。这是企业 RAG 的最强加分项之一。

### Day 1：梳理数据模型（5h）
- 学习（1h）：理解“chunk 溯源”和 metadata 过滤的价值
- 实现（3h）：设计 metadata 字段（建议最小集）
  - `doc_id`、`chunk_id`、`source_name`、`uploaded_by`、`uploaded_at`、（可选）`knowledge_base_id`
- 验收（1h）：在 `docs/ARCHITECTURE.md` 增加“数据模型小节”（表字段草图即可）

### Day 2：向量库存 metadata（5h）
- 实现（4h）：
  - 修改 `vector_store` 表结构：增加 `doc_id/chunk_id/source/...`（迁移脚本）
  - `VectorStore.add()` 写入 metadata
- 验收（1h）：写一个最小测试：插入后能查到 metadata

### Day 3：检索返回引用（5h）
- 实现（3h）：
  - 检索结果在拼 prompt 时保留 `chunk_id/doc_id` 列表
  - 在最终响应里附带引用（最小版：在回答末尾追加“引用：...”）
- 验收（2h）：演示截图 1 张（回答末尾带引用）

### Day 4：引用可回查 API（5h）
- 实现（3h）：
  - 增加接口：`GET /knowledge/chunks/{chunkId}`（返回 chunk 内容 + source）
- 验收（2h）：写 1 个集成测试 + 更新 `docs/demo.md` 演示步骤

### Day 5：多知识库隔离（最小版）（5h）
- 实现（3h）：
  - 引入 `knowledgeBaseId` 概念（最小可以先用字符串）
  - 上传时写入 `knowledgeBaseId`；检索时过滤只搜当前库
- 验收（2h）：两个库同名问题检索结果不同（脚本/测试二选一）

### Day 6：周总结（5h）
- 实现（2h）：`docs/RESUME_BULLETS.md` 增加 2 条（引用与溯源、知识库隔离）
- 验收（3h）：录 60 秒演示（上传→问答→引用→回查 chunk）

---

## Week 2 — 评测体系（Eval）+ 回归标准

目标：让你能回答“你怎么证明 RAG 更好”，并把改动变成可持续迭代。

### Day 1：定义指标与数据集格式（5h）
- 实现（3h）：
  - 建 `docs/eval/` 目录
  - 定义数据格式（建议 JSONL）：`question`, `knowledgeBaseId`, `expected_keywords`, `notes`
- 验收（2h）：写 `docs/eval/README.md`（如何运行/如何扩展）

### Day 2：离线评测脚本（5h）
- 实现（4h）：
  - `scripts/eval.*`：批量请求接口，保存输出
  - 记录：检索命中数、是否返回引用、耗时
- 验收（1h）：生成一份 `docs/eval/report-<date>.md`

### Day 3：质量检查（最小自动化）（5h）
- 实现（3h）：
  - 写一个“规则评测”：是否包含 expected keywords / 是否包含引用段
- 验收（2h）：在 CI 增加一个可选 job（或手动 profile）跑 eval

### Day 4：Prompt/Context 长度控制（5h）
- 实现（3h）：
  - 控制检索片段总长度（截断/合并）
  - 记录被截断的比例（日志/指标）
- 验收（2h）：评测报告对比（前后差异）

### Day 5：防幻觉最小策略（5h）
- 实现（3h）：
  - system prompt 增加“无依据就说不知道/给澄清问题”
  - 当检索为空时走“通用回答模式”并标注
- 验收（2h）：挑 5 个“库里没有”的问题做回归记录

### Day 6：周总结（5h）
- 产出：`docs/eval/` 下形成可持续扩充的评测框架 + 2 份报告

---

## Week 3 — 混合检索（Hybrid Search）+ 合并与去重

目标：从“只靠向量”升级为“可控召回”，并能解释为什么效果更稳。

### Day 1：选择关键词检索方案（5h）
二选一（建议先选 A）：
- A：PostgreSQL Full-Text Search（轻量、依赖少）
- B：Elasticsearch（更强，但部署复杂度上升）

产出：在 `docs/ARCHITECTURE.md` 写清你选了哪种及原因。

### Day 2：实现关键词检索通道（5h）
- 实现（4h）：新增 `KeywordSearchService`（或等价），返回 topN chunks（同样携带 chunkId）
- 验收（1h）：写 1 个测试：关键词命中能返回预期 chunk

### Day 3：混合合并策略（5h）
- 实现（3h）：
  - 向量 + 关键词并行
  - 合并去重（chunkId 去重）
  - 简单重排（例如：关键词命中优先 / 置信阈值）
- 验收（2h）：eval 报告对比（至少提升 1～2 个问题的命中）

### Day 4：检索后处理（5h）
- 实现（3h）：实现 1～2 个后处理器
  - 去噪（过短 chunk 过滤）
  - 合并相邻 chunk（同 doc 相邻 chunk 合并）
- 验收（2h）：报告对比 + 解释“为什么这样做”

### Day 5：检索 trace（5h）
- 实现（3h）：记录每个通道的 top 结果与耗时（日志/DB 任选其一，MVP 可用日志）
- 验收（2h）：输出一份 `docs/retrieval-trace-example.md`

### Day 6：周总结（5h）
- 产出：混合检索上线 + 评测报告证明改进

---

## Week 4 — 记忆摘要压缩 + 成本优化（Caching）

目标：让系统“越聊越稳、越聊越省”，具备长对话能力。

### Day 1：记忆策略设计（5h）
- 实现（3h）：定义策略：最近 N 轮 + summary
- 验收（2h）：更新 `docs/ARCHITECTURE.md` 的 memory 部分

### Day 2：摘要实现（5h）
- 实现（4h）：超过阈值自动摘要（调用 LLM），写入 Redis（或 DB）
- 验收（1h）：写测试或脚本验证摘要触发

### Day 3：摘要与对话拼装（5h）
- 实现（3h）：prompt 组装：summary + recent messages + current question
- 验收（2h）：长对话 demo（至少 20 轮）仍稳定

### Day 4：缓存（rewrite / embedding / retrieval）（5h）
- 实现（4h）：选一项先做“最值钱”的缓存（建议 rewrite 或 retrieval）
- 验收（1h）：输出缓存命中率（日志/指标）

### Day 5：成本与性能报告（5h）
- 实现（3h）：`docs/perf-cost.md`：首包时间/平均耗时/缓存命中率（本机基准也可）
- 验收（2h）：录屏或截图（性能对比）

### Day 6：周总结（5h）
- 产出：记忆压缩 + 缓存落地 + 量化报告

---

## Week 5 — 工具平台化（Tool Registry）+ 可靠性（超时/熔断）

目标：从“写了一个天气工具”升级为“可扩展的工具调用框架”。

### Day 1：工具抽象（5h）
- 实现：工具注册表（name、schema、timeout、retry）
- 验收：新增一个简单工具（例如汇率/地图占位）证明可扩展

### Day 2：工具调用可观测（5h）
- 实现：记录每次 tool call 的耗时、成功/失败、参数（脱敏）
- 验收：`docs/tool-trace.md` 示例

### Day 3：超时与熔断（最小版）（5h）
- 实现：对外部 HTTP 工具加超时/重试；对失败率高的工具临时熔断（可简化）
- 验收：故障注入脚本（模拟超时）与降级结果

### Day 4：模型调用降级（最小版）（5h）
- 实现：多模型候选（同供应商不同模型也可以），失败切换
- 验收：模拟主模型不可用时仍能返回

### Day 5：补齐文档与演示（5h）
- 更新：README + 架构图 + 演示脚本
- 录：工具调用的演示视频

### Day 6：周总结（5h）
- 产出：工具平台化 + 可靠性证据

---

## Week 6 — 打磨发布：管理面板/看板（可选）+ 最终简历包

目标：把“工程证据”包装成“可一眼看懂的作品”。

### Day 1～2：最小管理能力（10h，二选一）
- A：后端提供 `admin` API + Swagger/OpenAPI 文档
- B：做一个极简管理前端（查看知识库、文档、chunks、trace）

### Day 3：Dashboard（5h）
- 接入 metrics + Grafana（截图即可）
- 输出：`docs/observability.md` + 2 张截图

### Day 4：最终评测报告（5h）
- 输出：`docs/eval/final-report.md`（包含改进前后对比、案例解释）

### Day 5：简历最终稿（5h）
- 输出：`docs/RESUME_BULLETS.md`（升级到 8～10 条备选 bullet）
- 输出：`docs/PROJECT_INTRO.md`（项目介绍：功能、架构、亮点、演示）

### Day 6：发布（5h）
- 打 tag：`v0.2-phase2`
- README 顶部放：演示视频、架构图、eval 结果、dashboard 截图

---

## 3. Phase2 完成后你应该能怎么说（简历/面试）

你会从“做过 RAG”升级为能讲下面这些：

- **可解释 RAG**：回答带引用与溯源，支持 chunk 回查
- **可控效果**：有 eval 数据集与报告，知道如何做回归与调优
- **可控召回**：混合检索 + 后处理策略，解释适用场景与权衡
- **可控成本**：记忆摘要压缩 + 缓存命中率与性能报告
- **可扩展平台**：工具注册表 + tool trace + 超时/熔断/降级

---

## 4. 最小化建议（如果你不想做满 6 周）

如果你只想再做 2～3 周，但要“含金量最大”，建议优先做：

1. **引用与溯源（Week 1）**
2. **评测体系（Week 2）**
3. **混合检索（Week 3）**

这三件事做完，你的项目在 RAG 面试里就非常能打，而且每一条都可展示、可量化、可解释。

