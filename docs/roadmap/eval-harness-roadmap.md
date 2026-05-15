# Eval Harness Roadmap

## 项目定位

当前项目已经提供 `POST /api/v1/eval/chat`，用于非流式 JSON 回归验证，并且主线 SSE 与 eval 共享 `StageEvent`、`PolicyEvent`、`PlanParseEvent` 等语义。长期目标是把 eval 从单项目测试入口升级为统一 Evaluation Harness：可以评测不同 target、不同模型、不同配置和不同运行模式。

Eval Target Adapter 的核心价值是隔离“评测平台”和“被测系统”。Harness 不直接绑定某个 Controller 或某套 DTO，而是通过统一 target adapter 调用主线、异步任务、mock pipeline 或远程服务。

## 当前状态

已经具备的能力：

- `EvalChatController` 提供结构化 JSON eval 响应。
- `EvalChatService` 汇总 response、meta、sources、tool、policy events。
- `EvalLinearAgentPipeline` 与主线共享 `PLAN -> RETRIEVE -> TOOL -> GUARD -> WRITE` 阶段语义。
- `EvalChatSafetyGate`、`EvalQuerySafetyPolicy`、`EvalBehaviorPolicy` 支持 deterministic 测试。
- `EvalToolStageRunner` 支持工具成功、超时、错误等场景。
- `RetrievalMembershipHasher`、checkpoint 和 gateway key 提供安全与归因基础。

当前限制：

- eval target 主要绑定当前应用内的 eval controller。
- case schema、target capability、run result、report/compare 尚未统一产品化。
- 主线 SSE、异步 job、远程 target 的评测入口还没有统一 adapter。
- 金融合规、引用一致性、行情时效等领域指标尚未系统化。

## 长期目标

统一架构：

```text
Eval Dataset
  -> Harness Runner
  -> Target Adapter
     -> Local Eval Chat
     -> Main SSE Chat
     -> Async Job Runtime
     -> Remote HTTP Target
     -> Mock Deterministic Target
  -> Normalized Result
  -> Scorers / Policies
  -> Report / Compare / Regression Gate
```

核心接口：

- `EvalTargetAdapter`：封装 target 调用方式。
- `EvalCase`：输入 query、mode、tags、expected behavior、expected sources、policy expectation。
- `EvalTargetCapabilities`：声明 target 是否支持 streaming、tools、retrieval、guardrails、async jobs。
- `EvalRunResult`：统一 answer、sources、tool result、stage order、policy events、latency、error_code。
- `EvalScorer`：事实一致性、引用覆盖、合规拒答、工具归因、时效性、格式契约。
- `EvalReporter`：生成 run summary、case diff、regression buckets。

## V1

目标：统一 eval 契约和 target adapter 概念。

- 定义 `EvalTargetAdapter` 文档接口：`name`、`capabilities`、`invoke(case)`、`normalize(response)`。
- 将当前 `POST /api/v1/eval/chat` 视为 `LocalEvalChatAdapter`。
- 将主线 SSE 设计为 `MainSseChatAdapter`，通过收集 SSE event 归一化为 `EvalRunResult`。
- 固化 normalized result 字段：`answer`、`sources`、`tool`、`meta.stage_order`、`meta.policy_events`、`error_code`、`latency_ms`。
- 扩展 case tags：`rag/*`、`tool/*`、`attack/*`、`finance/compliance/*`、`market_data/stale`。
- 增加基础 report/compare 输出规范。

## V2

目标：支持多 target、多运行模式和金融领域指标。

- 支持 `AsyncJobAdapter`：提交 job、轮询状态、收集 job events、归一化结果。
- 支持 `RemoteHttpAdapter`：评测部署环境或候选分支服务。
- 增加 scorer：
  - 引用覆盖率：回答中的关键结论是否有来源支持。
  - RAG membership：sources 是否来自允许集合。
  - Tool correctness：工具是否在应调用时调用，失败时是否正确降级。
  - Compliance refusal：自动交易、收益承诺、荐股请求是否被拒绝或改写为风险提示。
  - Freshness：行情、新闻、宏观数据是否标注时间且未过期。
- 建立 eval run/result 存储模型，为趋势对比和回归门禁做准备。
- 支持按 tags、target、model、config hash 分桶统计。

## V3

目标：形成可持续运行的 Evaluation Platform。

- 接入 CI/CD，关键 tags regression 必须通过。
- 支持 nightly eval、成本受控的 real LLM probes、失败样例自动归因。
- 建立 dashboard：通过率、失败类别、latency、tool outcome、source coverage。
- 支持跨版本 compare：baseline vs candidate，输出新增失败、修复失败和指标变化。
- 支持人工标注闭环，将用户反馈、失败 case、线上异常转化为 eval dataset。
- 支持金融专项评测集：财报问答、新闻事件、行情时效、合规安全、图表解读。

## 风险与边界

- Eval 结果必须与线上用户数据隔离，避免 eval token、gateway key 或 membership hash 泄漏。
- Harness 不应依赖单一模型的自由文本判断，关键门禁优先使用 deterministic scorer。
- Streaming target 的归一化必须明确事件顺序和结束语义，避免误判未完成响应。
- Async target 必须设置 max wait、poll interval 和 timeout，避免评测任务挂死。
- 金融评测不能只看回答流畅度，必须覆盖证据、时效、工具归因和合规拒答。
- Eval adapter 是测试接口抽象，不应改变主产品接口行为。

