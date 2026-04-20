# `sources[]`（评测 JSON）与 SSE「引用片段」对齐说明

**目的**：落实「**部分落地**」里 **`sources[]` 系统构造** 一项——说明 **评测口** 与 **SSE 主产品** 如何**同源**从向量库 `Document` 取证据、**载体**为何不同、以及人工对账时看哪些字段。

**实现真源**：`EvalChatService#retrieveEvidence`（`POST /api/v1/eval/chat`）、`TravelAgent#buildCitationBlock`（SSE 首段 `data` 正文）。

---

## 1. 共同点（系统生成，禁止 LLM 编造证据对象）

| 能力 | 评测 JSON | SSE 主产品 |
| --- | --- | --- |
| 证据来源 | 同一 **`VectorStore`** 的 `Document`（`similaritySearch`）；评测在 **无登录态** 时按 **`user_id=eval`** 过滤（与灌库约定一致） | 按 **JWT 用户** `user_id` 过滤 |
| id 口径（评测 + E7） | `RetrievalMembershipHasher.canonicalChunkId(documentId)` 写入 **`sources[*].id`** 与 **`retrieval_hits[*].id`** | SSE 块内打印 **原始** `Document#getId()`（便于人读；**未**在 SSE 中重复输出 canonical 串，除非 id 本身已规范） |
| 标题/来源名 | `metadata.source_name` → `sources[].title` / `retrieval_hits[].title` | 文本行 `来源=` 同源字段 |
| 片段正文 | `Document#getText()` **规则截断** → `sources[].snippet` | 同字段 **规则截断** 后写入引用块 |
| LLM 是否生成 `sources[]` | **否**（`EvalChatSource` javadoc） | **否**（纯 `StringBuilder` 拼块） |

---

## 2. 差异（载体与字段粒度——预期内，不视为实现缺陷）

| 维度 | 评测 `EvalChatResponse` | SSE `TravelAgent` |
| --- | --- | --- |
| 载体 | JSON 根级 **`sources[]`** + **`retrieval_hits[]`** + `meta.retrieval_hit_id_hashes[]`（E7） | **首段 SSE `data`** 为**纯文本**「【引用片段】…」，**无**与 eval 同形的 JSON 数组 |
| snippet / 预览长度上限 | **≤300** 字符（`EvalChatService#truncateSnippet`） | **≤200** 字符（`buildCitationBlock`） |
| `score` | `sources[].score` / `retrieval_hits[].score` 当前多为 **`null`**（`capabilities.retrieval.score` 可为 false） | 文本块**不展示** score |
| 编排入口 | `retrieveEvidence` 内 **`EVAL`** 模式 **不调** `QueryRewriter`（`List.of(query)`），其它 `mode` 走改写 | 主线默认 **走** `QueryRewriter` 多 query 检索 |

**对账建议**：同一 query、同一库、在评测侧把 `user_id` 对齐为 `eval`、主线侧用能命中同一批 chunk 的用户上传数据时，比对 **条数、id、来源名、片段前几行**；**不要求** SSE 字符串与 `sources[].snippet` 逐字符一致（截断长度不同）。

---

## 3. 与 SSOT / 矩阵的表述

- `travel-ai-upgrade.md`：**`sources[]` 由系统构造**——评测路径已满足；SSE 为**等价信息的另一呈现**，不破坏「不把引用生成交给 LLM」的原则。  
- `docs/IMPLEMENTATION_MATRIX.md`：评测行「`sources[]` 系统生成」与本文一致；若将来 SSE 也暴露结构化 `sources[]`（例如新增字段或事件），须同步 E7 与前端契约。

---

## 4. 可选后续（未在本迭代实现）

- 将 SSE 截断长度与评测 **统一为 300**（产品决策）。  
- 在 SSE 增加 **`event: sources`**（JSON）与 eval **逐字段**对齐（工作量大，见 `UPGRADE_PLAN` P5-2 等演进项）。  
- `snippet_hash`（SSOT 可选字段）：当前 **未**输出。
