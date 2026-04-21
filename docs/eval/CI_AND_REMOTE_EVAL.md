# CI 与「远程全量 eval」边界说明

## 本仓库 CI 当前覆盖什么

GitHub Actions（`.github/workflows/ci.yml`）在 **`mvn test`** 下已包含：

- **Testcontainers**：Postgres（pgvector）+ Redis，与业务/集成测试一致。  
- **评测口契约与确定性路径**：`EvalChatControllerTest`、`EvalChatLlmRealTagGateMvcTest`、`EvalLlmRealTagPolicyTest` 等（**不**依赖公网 DashScope；**`llm_mode=real`** 在默认配置下不会触发外网）。  
- **主线相关**：`TravelConversationIntegrationTest` 等（含会话、聊天 POST、长度校验等）。

因此：**「离线/容器内可回归」的 eval 与主线子集**已在每次 push/PR 上跑。

## 刻意不在默认 CI 里做什么

以下依赖 **公网可达的已部署 target**、**长期密钥**、**大体量 dataset** 或 **计费**，默认 **不**放进本仓 CI，以免：

- Fork PR 泄露 secrets 或误跑账单；  
- Runner 网络/供应商抖动导致 flaky；  
- 与「每次 `mvn test` 须稳定绿」目标冲突。

对应叙述见 **`docs/travel-ai-upgrade.md`**「批量导入」「CI 对公网真实 target 的全量回归」及 **`docs/IMPLEMENTATION_MATRIX.md`** §4。

## 若要在 staging 做「远程全量 eval」

1. **Target**：部署本应用（或镜像），配置 **`APP_EVAL_GATEWAY_KEY`**、DashScope Key 等。  
2. **Client**：在 **Vagent**（或等价 eval runner）注册 `baseUrl`、`X-Eval-Gateway-Key`、membership 头（E7 时 `X-Eval-*`）。  
3. **Dataset**：将 **`docs/eval.md`** 中对抗/RAG/tool 等行导入为正式 case；对需要 **provider usage** 的抽样行打 **`cost/*`** tag，并仅在 staging 打开 **`app.eval.llm-real-enabled`**（见 [`LLM_REAL_USAGE_RUNBOOK.md`](LLM_REAL_USAGE_RUNBOOK.md)）。  
4. **门槛**：聚合 **`run.report`**，与 [`P0_THRESHOLD_RUNBOOK.md`](P0_THRESHOLD_RUNBOOK.md) 对齐。

## 可选后续（本仓工作流）

若将来要在 **受控仓库**（如私有 fork + self-hosted runner + OIDC 发密钥）上跑「对固定 staging URL 的烟测」，可新增 **`workflow_dispatch`**  job：仅在手动触发时 `curl`/`httpie` 若干 `POST /api/v1/eval/chat` 用例，并 **不把密钥写入 YAML 明文**。当前 **未实现**，以本文为决策留痕。
