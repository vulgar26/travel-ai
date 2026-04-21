# 评测口：真实 LLM usage 探针（`llm_mode=real`）跑法与注意事项

**适用对象**：在 **Vagent / 自建 eval** 中需要 **供应商 token 真值**（`meta.prompt_tokens` / `completion_tokens` / `total_tokens`）时，对 `POST /api/v1/eval/chat` 做**受控**打开。  
**代码真源**：`AppEvalProperties`、`EvalChatService#maybeAttachProviderTokenUsage`、`EvalLlmRealTagPolicy`、`EvalUsageChatClientConfig`（`evalUsageChatClient`）。

---

## 1. 默认策略（与 CI 对齐）

| 场景 | 建议 |
|------|------|
| **CI / 本地日常** | 保持 **`app.eval.llm-real-enabled=false`**（默认）。响应里 **`token_source=estimate`**，依赖 `meta.context_*` 做趋势与异常检测，**不**对外网计费。 |
| **staging / 专项回归** | 仅在短窗口、小样本上打开 `llm-real-enabled`，并配合下文 **`eval_tags` 抽样** 与超时配置。 |

---

## 2. 何时需要打开「real」探针

- 需要把 **`run.report` / compare** 里的 token 与 **供应商账单或控制台 usage** 对齐（验收、对账、排查异常消耗）。
- 接受：**额外一次**与 stub 主路径并行的 **best-effort** LLM 调用（回答内容**不作为**评测 `answer` 主判据；仅取 usage）。

若只做「上下文是否异常变长」，**不必**开 real：默认 **`token_source=estimate`** 已够用。

---

## 3. 服务端配置（`app.eval.*`）

| 配置项 | 含义 |
|--------|------|
| **`app.eval.llm-real-enabled`** | **`true`** 才允许在请求带 **`llm_mode=real`** 时发起 usage 探针。 |
| **`app.eval.llm-real-timeout-ms`** | 探针 `Future#get` 上限（毫秒）；超时则 **`provider_usage_available=false`**，`provider_usage_failure_reason=timeout`。 |
| **`app.eval.llm-real-require-tag-match`** | 默认 **`true`**：必须命中前缀门禁（见下），否则**不调用**外网，并写 `tag_gate_*`。 |
| **`app.eval.llm-real-required-tag-prefixes`** | YAML 字符串列表；未配置时逻辑默认 **`cost/`**。任一 **`eval_tags[]`** 元素 **以列表中任一前缀开头** 即通过。 |
| **`app.eval.gateway-key`** | 与 **`X-Eval-Gateway-Key`** 一致；评测路径仍须带网关密钥（与 real 无关但必配）。 |

环境变量覆盖键名见根目录 **`README.md`**（`APP_EVAL_GATEWAY_KEY` 等）。

---

## 4. 请求体约定（评测 JSON）

- **`llm_mode`**：设为 **`real`** 才请求探针；省略或其它值 → 不探针，**`token_source=estimate`**（且通常不出现 `provider_usage_*`）。
- **`eval_tags`**：字符串数组（**snake_case** `eval_tags`）。在 **`llm-real-require-tag-match=true`** 时，至少一条应类似 **`cost/smoke`**、**`cost/billing-check`**（即带默认前缀 **`cost/`**），否则见 §5 门禁归因。

**抽样建议**：跑批全量保持默认 **estimate**；仅对 dataset 中打上 **`cost/*`**（或你们自定义前缀）的 case 传 **`llm_mode=real`**，避免每一行都计费。

---

## 5. 响应 `meta` 如何读

| 字段 | 含义 |
|------|------|
| **`token_source`** | **`estimate`**：仅用字符近似；**`provider`**：探针成功且从 SDK 取到 usage。 |
| **`prompt_tokens` / `completion_tokens` / `total_tokens`** | 仅在 **`token_source=provider`** 时写入（具体以 `JsonInclude` 为准，失败时可能省略）。 |
| **`provider_usage_available`** | **`true`**：usage 已解析；**`false`**：未拿到可用 usage（含门禁跳过、超时、无 client 等）。 |
| **`provider_usage_failure_reason`** | 归因码（不含敏感原文）；常见见下表。 |

### `provider_usage_failure_reason` 速查

| 取值 | 含义 |
|------|------|
| **`tag_gate_no_tags`** | 开了 real 且服务端允许，但 **`eval_tags` 缺失或空数组** → **故意不调用**外网。 |
| **`tag_gate_no_match`** | 有 `eval_tags`，但**没有任何一条**以配置前缀开头。 |
| **`no_client`** | 未注入 **`evalUsageChatClient`**（ Bean 不可用）。 |
| **`timeout`** | 探针超过 **`llm-real-timeout-ms`**。 |
| **`no_usage`** | 调用返回但 **SDK 未暴露**可解析的 usage。 |
| **`error`** | 其它异常（实现细节可能演进，以日志为准）。 |

当 **`llm_mode=real`** 但服务端 **`llm-real-enabled=false`** 时：行为与未开 real 一致（**不**写 `provider_usage_*`，**不**调用探针）。

---

## 6. 成本、波动与合规

- **成本**：每命中一次门禁的 **`real`** 请求会多 **一次** DashScope（或当前 Spring AI 绑定模型）调用；大批量跑务必 **抽样** + **`eval_tags`**。
- **波动**：供应商返回的 token 计数可能随模型/SDK 版本变化；**compare** 时建议对 token 字段设合理容差或仅作人工对账字段。
- **合规 / 出境**：探针会把 **`query`** 再发给模型一次；敏感数据环境请在数据分级与 DPA 允许范围内使用，或仅在脱敏题库上开启。

---

## 7. 手工 curl 示例（本地）

先登录或走 eval 网关（与你们环境一致）。以下为 **eval 路径** 示意（网关密钥、membership 头按你们 `eval-upgrade` 补齐）：

```http
POST /api/v1/eval/chat
Content-Type: application/json
X-Eval-Gateway-Key: <与 app.eval.gateway-key 一致>
Authorization: Bearer <JWT 或 eval 注入主体>
```

```json
{
  "query": "上海多日游行程规划需求说明加长到超过六字以走全管线",
  "mode": "AGENT",
  "llm_mode": "real",
  "eval_tags": ["cost/smoke"]
}
```

若 **`llm-real-enabled=true`** 且 DashScope 等配置有效，响应 `meta` 中应出现 **`token_source=provider`** 与整数 token 字段；否则查看 **`provider_usage_failure_reason`**。

---

## 8. 进阶：关闭标签门禁（慎用）

将 **`app.eval.llm-real-require-tag-match=false`** 后，在 **`llm-real-enabled=true`** 时，**凡**带 **`llm_mode=real`** 的请求都可能触发探针 → **极易在跑批时打满外网与账单**。仅建议隔离环境短期排障使用。

---

## 9. 相关文档

- **`README.md`**：`app.eval.*` 表格（含 **`config-snapshot-meta-enabled`**：可选 **`meta.config_snapshot`** 明文键值）。  
- **`docs/eval/P1_HARNESS_GAP.md`**：与其它 `meta` 字段的对照。  
- **`docs/travel-ai-upgrade.md`**：SSOT 摘要中的「评测口 token / usage」条。
