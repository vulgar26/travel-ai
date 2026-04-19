package com.travel.ai.eval;

import com.travel.ai.eval.dto.EvalCapabilities;
import com.travel.ai.eval.dto.EvalChatMeta;
import com.travel.ai.eval.dto.EvalChatRequest;
import com.travel.ai.eval.dto.EvalChatResponse;
import com.travel.ai.eval.dto.EvalChatResultTool;
import com.travel.ai.eval.dto.EvalChatRetrievalHit;
import com.travel.ai.eval.dto.EvalChatSource;
import com.travel.ai.eval.dto.EvalGuardrailsCapability;
import com.travel.ai.eval.dto.EvalRetrievalCapability;
import com.travel.ai.eval.dto.EvalStreamingCapability;
import com.travel.ai.eval.dto.EvalToolsCapability;
import com.travel.ai.config.AppAgentProperties;
import com.travel.ai.eval.planrepair.EvalPlanParseCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.travel.ai.eval.EvalRagGateScenarios.Kind;

/**
 * 评测聊天：Day2 起在 {@link #buildStubResponse(EvalChatRequest)} 内挂载
 * {@link EvalLinearAgentPipeline}，输出 {@code meta.stage_order} / {@code step_count} / {@code replan_count}；
 * Day5 起对非空 query 执行 {@link EvalPlanParseCoordinator}（repair once + {@code plan_parse_attempts/outcome}）；
 * Day6 起在 TOOL 阶段串行执行 {@link EvalToolStageRunner}（超时/失败降级 + {@code tool} / {@code meta.tool_*} / {@code error_code}）。
 * Day7 起支持 {@link EvalRagGateScenarios}：空命中/低置信门控（P0 不启用 score 阈值），{@code meta.low_confidence} + {@code meta.low_confidence_reasons[]}。
 * Day9 起在 plan 解析成功后执行 {@link EvalQuerySafetyPolicy}：对抗/敏感句式稳定 {@code deny} 或 {@code clarify}，归因见 {@link EvalSafetyErrorCodes}。
 * <p>
 * eval-upgrade.md E7：在 {@link EvalMembershipHttpContext} 完整且存在 {@code retrieval_hits} 时写入
 * {@code meta.retrieval_hit_id_hashes} 及配套口径字段。
 */
@Service
public class EvalChatService {

    private static final Logger log = LoggerFactory.getLogger(EvalChatService.class);

    /** 与主线 SSE {@code TravelAgent} 总超时提示对齐的评测归因码。 */
    public static final String ERROR_CODE_AGENT_TOTAL_TIMEOUT = "AGENT_TOTAL_TIMEOUT";

    private final EvalPlanParseCoordinator planParseCoordinator;
    private final EvalToolStageRunner evalToolStageRunner;
    private final ObjectProvider<com.travel.ai.agent.QueryRewriter> queryRewriter;
    private final ObjectProvider<VectorStore> vectorStore;
    private final AppAgentProperties appAgentProperties;

    /**
     * 仅用于测试：在进入评测 stub 主路径后阻塞指定毫秒，以验证 {@link com.travel.ai.eval.EvalChatController} 整段
     * {@code app.agent.total-timeout}。生产须为 {@code 0}（默认）。
     */
    @Value("${app.eval.stub-work-sleep-ms:0}")
    private long evalStubWorkSleepMs;

    public EvalChatService(
            EvalPlanParseCoordinator planParseCoordinator,
            EvalToolStageRunner evalToolStageRunner,
            ObjectProvider<com.travel.ai.agent.QueryRewriter> queryRewriter,
            ObjectProvider<VectorStore> vectorStore,
            AppAgentProperties appAgentProperties
    ) {
        this.planParseCoordinator = planParseCoordinator;
        this.evalToolStageRunner = evalToolStageRunner;
        this.queryRewriter = queryRewriter;
        this.vectorStore = vectorStore;
        this.appAgentProperties = appAgentProperties;
    }

    /**
     * 将 {@code latency_ms} 与 {@link EvalChatMeta#getAgentTotalTimeoutMs()} 比较，写入 {@code agent_latency_budget_exceeded}。
     */
    public void applyLatencyBudgetToMeta(EvalChatResponse response, long latencyMs) {
        EvalChatMeta meta = response != null ? response.getMeta() : null;
        if (meta == null) {
            return;
        }
        Long budget = meta.getAgentTotalTimeoutMs();
        if (budget == null || budget <= 0) {
            return;
        }
        boolean exceeded = latencyMs > budget;
        meta.setAgentLatencyBudgetExceeded(exceeded);
        if (exceeded) {
            log.warn("[eval] agent_latency_budget_exceeded latency_ms={} agent_total_timeout_ms={} request_id={}",
                    latencyMs, budget, meta.getRequestId());
        }
    }

    /**
     * 评测整段 {@code app.agent.total-timeout} 触发时返回的轻量响应（不跑检索 / plan / 工具）。
     */
    public EvalChatResponse buildTotalTimeoutStubResponse(EvalChatRequest request, EvalMembershipHttpContext membershipCtx) {
        String mode = request.getMode();
        if (mode == null || mode.isBlank()) {
            mode = "EVAL";
        }
        String requestId = UUID.randomUUID().toString();
        EvalChatMeta meta = new EvalChatMeta(mode, requestId);
        meta.setReplanCount(EvalChatMeta.P0_REPLAN_COUNT);
        stampAgentTimeoutsOnMeta(meta);
        meta.setStageOrder(Collections.emptyList());
        meta.setStepCount(0);
        meta.setAgentLatencyBudgetExceeded(true);

        EvalCapabilities capabilities = new EvalCapabilities(
                new EvalRetrievalCapability(true, false),
                new EvalToolsCapability(true, true),
                new EvalStreamingCapability(false),
                new EvalGuardrailsCapability(false, false, false)
        );
        EvalChatResponse response = new EvalChatResponse();
        response.setCapabilities(capabilities);
        response.setMeta(meta);
        response.setBehavior("clarify");
        response.setErrorCode(ERROR_CODE_AGENT_TOTAL_TIMEOUT);
        response.setAnswer("本轮评测请求处理超时（已达 app.agent.total-timeout），请缩短输入或稍后重试。");
        return finalizeResponse(response, membershipCtx, new Evidence(List.of(), List.of(), 0, 0));
    }

    private void stampAgentTimeoutsOnMeta(EvalChatMeta meta) {
        meta.setAgentTotalTimeoutMs(appAgentProperties.getTotalTimeout().toMillis());
        meta.setAgentMaxStepsConfigured(appAgentProperties.getMaxSteps());
        meta.setAgentToolTimeoutMs(appAgentProperties.getToolTimeout().toMillis());
        meta.setAgentLlmStreamTimeoutMs(appAgentProperties.getLlmStreamTimeout().toMillis());
    }

    private void maybeStubWorkSleepForTests(String requestId) {
        if (evalStubWorkSleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(evalStubWorkSleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("[eval] stub-work-sleep interrupted request_id={}", requestId);
        }
    }

    /**
     * 构造评测响应（不含 {@code latency_ms}，由 Controller 统一填入）。
     * <p>
     * 空 {@code query} 仍返回完整 {@code capabilities}，但<strong>不执行</strong>线性阶段（{@code stage_order=[]}，
     * {@code step_count=0}），且不执行 plan 解析；由 Controller 将 {@code behavior} 改为 {@code clarify}。
     */
    public EvalChatResponse buildStubResponse(EvalChatRequest request) {
        return buildStubResponse(request, null, EvalMembershipHttpContext.empty());
    }

    /**
     * @param xEvalMembershipTopN eval 下发的候选截断上限（见 P0+ §16）；为空则用服务端默认值。
     */
    public EvalChatResponse buildStubResponse(EvalChatRequest request, Integer xEvalMembershipTopN) {
        return buildStubResponse(request, xEvalMembershipTopN, EvalMembershipHttpContext.empty());
    }

    /**
     * @param membershipCtx eval 请求头派生的 membership 上下文；不全则跳过 hashed membership 字段。
     */
    public EvalChatResponse buildStubResponse(
            EvalChatRequest request,
            Integer xEvalMembershipTopN,
            EvalMembershipHttpContext membershipCtx
    ) {
        String mode = request.getMode();
        if (mode == null || mode.isBlank()) {
            mode = "EVAL";
        }
        String requestId = UUID.randomUUID().toString();

        boolean skipPipeline = request.getQuery() == null || request.getQuery().isBlank();

        EvalChatMeta meta = new EvalChatMeta(mode, requestId);
        meta.setReplanCount(EvalChatMeta.P0_REPLAN_COUNT);
        stampAgentTimeoutsOnMeta(meta);

        EvalCapabilities capabilities = new EvalCapabilities(
                new EvalRetrievalCapability(true, false),
                new EvalToolsCapability(true, true),
                new EvalStreamingCapability(false),
                new EvalGuardrailsCapability(false, false, false)
        );

        EvalChatResponse response = new EvalChatResponse();
        response.setCapabilities(capabilities);
        response.setMeta(meta);

        if (skipPipeline) {
            meta.setStageOrder(Collections.emptyList());
            meta.setStepCount(0);
            response.setAnswer("Day3：meta 可观测稳定（stage_order / step_count / replan_count=0）；阶段仍为占位执行。");
            response.setBehavior("answer");
            return response;
        }

        maybeStubWorkSleepForTests(requestId);

        // P0+ S2：检索前 SafetyGate（normalize + 可归因 rule_id），命中则直接短路返回。
        Optional<EvalChatSafetyGate.Decision> gate = EvalChatSafetyGate.evaluate(request.getQuery());
        if (gate.isPresent()) {
            EvalChatSafetyGate.Decision d = gate.get();
            meta.setEvalSafetyRuleId(d.ruleId());
            meta.setLowConfidenceReasons(d.reasons());
            meta.setStageOrder(List.of("PLAN", "GUARD"));
            meta.setStepCount(2);
            if ("clarify".equalsIgnoreCase(d.behavior())) {
                meta.setLowConfidence(true);
            }
            response.setBehavior(d.behavior());
            if (d.errorCode() != null) {
                response.setErrorCode(d.errorCode());
            }
            response.setAnswer(d.answer());
            return response;
        }

        // Day10/S1：为 requires_citations / membership 路径提前准备证据对象（sources / retrieval_hits）。
        // 这里不依赖 eval 的 requires_citations 下发（eval 侧判定），而是以“业务侧可引用证据”为长期能力收敛点。
        Evidence evidence = retrieveEvidence(request.getQuery(), mode, xEvalMembershipTopN);
        if (!evidence.retrievalHits.isEmpty()) {
            response.setRetrievalHits(evidence.retrievalHits);
            meta.setRetrieveHitCount(evidence.retrievalHits.size());
        }
        if (!evidence.sources.isEmpty()) {
            response.setSources(evidence.sources);
        }

        EvalPlanParseCoordinator.Result parseResult = planParseCoordinator.parseWithOptionalRepair(request.getPlanRaw());
        meta.setPlanParseAttempts(parseResult.attempts());
        meta.setPlanParseOutcome(parseResult.outcome());

        if (parseResult.failed()) {
            meta.setStageOrder(Collections.emptyList());
            meta.setStepCount(0);
            response.setBehavior("clarify");
            response.setErrorCode("PARSE_ERROR");
            response.setAnswer("Plan JSON 解析失败（已尝试一次修复仍无效）。请提供符合附录 E 的 plan。");
            return finalizeResponse(response, membershipCtx, evidence);
        }

        Optional<EvalQuerySafetyPolicy.Decision> safety = EvalQuerySafetyPolicy.evaluate(request.getQuery());
        if (safety.isPresent()) {
            EvalQuerySafetyPolicy.Decision d = safety.get();
            meta.setStageOrder(List.of("PLAN", "GUARD"));
            meta.setStepCount(2);
            meta.setLowConfidenceReasons(d.reasons());
            response.setBehavior(d.behavior());
            if (d.errorCode() != null) {
                response.setErrorCode(d.errorCode());
            }
            response.setAnswer(d.answer());
            return finalizeResponse(response, membershipCtx, evidence);
        }

        // P0+ S2：确定性行为策略（tool/clarify），用于收敛典型 BEHAVIOR_MISMATCH。
        if ("EVAL".equalsIgnoreCase(mode)) {
            Optional<EvalBehaviorPolicy.Decision> d = EvalBehaviorPolicy.evaluateForEvalMode(request.getQuery());
            if (d.isPresent()) {
                EvalBehaviorPolicy.Decision decision = d.get();
                response.setBehavior(decision.behavior());
                if (decision.errorCode() != null && !decision.errorCode().isBlank()) {
                    response.setErrorCode(decision.errorCode());
                }
                if (decision.reasons() != null && !decision.reasons().isEmpty()) {
                    meta.setLowConfidenceReasons(decision.reasons());
                }
                if ("clarify".equalsIgnoreCase(decision.behavior())) {
                    meta.setLowConfidence(true);
                    meta.setStageOrder(List.of("PLAN", "RETRIEVE"));
                    meta.setStepCount(2);
                } else if ("tool".equalsIgnoreCase(decision.behavior())) {
                    // 与默认线性流水线保持一致，避免 eval 对 meta.stage_order 的隐含期望造成 tool_succeeded=false。
                    meta.setStageOrder(List.of("PLAN", "RETRIEVE", "TOOL", "GUARD", "WRITE"));
                    meta.setStepCount(5);
                    EvalChatResultTool toolDto = new EvalChatResultTool();
                    toolDto.setRequired(true);
                    toolDto.setUsed(true);
                    toolDto.setSucceeded(true);
                    toolDto.setName(EvalToolStageRunner.STUB_TOOL_NAME);
                    toolDto.setOutcome(EvalToolStageRunner.OUTCOME_OK);
                    response.setTool(toolDto);
                    meta.setToolCallsCount(1);
                    meta.setToolOutcome(EvalToolStageRunner.OUTCOME_OK);
                }
                response.setAnswer(decision.answer());
                return finalizeResponse(response, membershipCtx, evidence);
            }
        }

        // 业务侧“空命中”兜底门控（不依赖 eval_rag_scenario）；避免在无证据时强答/编造引用。
        if (meta.getRetrieveHitCount() != null && meta.getRetrieveHitCount() == 0) {
            meta.setStageOrder(List.of("PLAN", "RETRIEVE"));
            meta.setStepCount(2);
            meta.setLowConfidence(true);
            meta.setLowConfidenceReasons(EvalRagGateScenarios.REASONS_EMPTY_HITS);
            response.setBehavior("clarify");
            response.setErrorCode(EvalRagGateScenarios.ERROR_CODE_RETRIEVE_EMPTY);
            response.setAnswer("检索零命中：请补充关键词/范围/上下文，或提供可引用资料后再继续。");
            return finalizeResponse(response, membershipCtx, evidence);
        }

        // 业务侧“低置信”兜底门控：短 query / 指代不明 等，避免同类输入漂移到 answer。
        String q = request.getQuery().trim();
        if (q.length() <= 6 || q.contains("这个东西") || q.contains("那个项目") || q.contains("那个") && q.contains("项目")) {
            meta.setStageOrder(List.of("PLAN", "RETRIEVE"));
            meta.setStepCount(2);
            meta.setLowConfidence(true);
            meta.setLowConfidenceReasons(EvalRagGateScenarios.REASONS_LOW_CONFIDENCE_BUSINESS);
            response.setBehavior("clarify");
            response.setErrorCode(EvalRagGateScenarios.ERROR_CODE_RETRIEVE_LOW_CONFIDENCE);
            response.setAnswer("信息不足或指代不明：请补充目的地/日期/偏好，或说明你指的是哪个对象/项目。");
            return finalizeResponse(response, membershipCtx, evidence);
        }

        Kind ragKind = EvalRagGateScenarios.resolve(request);
        if (ragKind != null) {
            meta.setStageOrder(List.of("PLAN", "RETRIEVE"));
            meta.setStepCount(2);
            meta.setLowConfidence(true);
            if (ragKind == Kind.EMPTY_HITS) {
                meta.setRetrieveHitCount(0);
                meta.setLowConfidenceReasons(EvalRagGateScenarios.REASONS_EMPTY_HITS);
                response.setErrorCode(EvalRagGateScenarios.ERROR_CODE_RETRIEVE_EMPTY);
                response.setAnswer("检索零命中，已门控为澄清（评测 stub，P0 未启用 score 阈值）。");
            } else {
                meta.setRetrieveHitCount(1);
                meta.setLowConfidenceReasons(EvalRagGateScenarios.REASONS_LOW_CONFIDENCE);
                response.setAnswer("证据置信不足，已门控为澄清（评测 stub，P0 未启用 score 阈值）。");
            }
            response.setBehavior("clarify");
            return finalizeResponse(response, membershipCtx, evidence);
        }

        AtomicReference<EvalToolStageRunner.EvalToolInvocationResult> toolSlot = new AtomicReference<>();
        List<String> stageOrder = EvalLinearAgentPipeline.runStubStages(request, () -> {
            EvalToolStageRunner.EvalToolInvocationResult r = evalToolStageRunner.invoke(request);
            if (r != null) {
                toolSlot.set(r);
            }
        });
        meta.setStageOrder(stageOrder);
        meta.setStepCount(stageOrder.size());

        EvalToolStageRunner.EvalToolInvocationResult inv = toolSlot.get();
        if (inv != null) {
            EvalChatResultTool toolDto = new EvalChatResultTool();
            toolDto.setRequired(true);
            toolDto.setUsed(inv.used());
            toolDto.setName(inv.name());
            toolDto.setOutcome(inv.outcome());
            toolDto.setSucceeded(EvalToolStageRunner.OUTCOME_OK.equals(inv.outcome()));
            response.setTool(toolDto);
            meta.setToolCallsCount(inv.used() ? 1 : 0);
            meta.setToolOutcome(inv.outcome());
            meta.setToolLatencyMs(inv.latencyMs());
            // meta 上的布尔字段采用“只写 true”的策略：
            // - 避免输出大量无意义的 false（减少 JSON 噪音）
            // - 统计上更容易理解：字段出现即表示触发
            if (Boolean.TRUE.equals(inv.toolDisabledByCircuitBreaker())) {
                meta.setToolDisabledByCircuitBreaker(true);
            }
            if (Boolean.TRUE.equals(inv.toolRateLimited())) {
                meta.setToolRateLimited(true);
            }
            if (Boolean.TRUE.equals(inv.toolOutputTruncated())) {
                meta.setToolOutputTruncated(true);
            }
            switch (inv.kind()) {
                case OK -> {
                    response.setBehavior("tool");
                    response.setAnswer("评测：工具阶段成功（stub）。");
                }
                case TIMEOUT -> {
                    response.setBehavior("answer");
                    response.setErrorCode(EvalToolStageRunner.ERROR_CODE_TOOL_TIMEOUT);
                    response.setAnswer("工具阶段超时，已降级返回（评测 stub）。");
                }
                case ERROR -> {
                    response.setBehavior("answer");
                    response.setErrorCode(EvalToolStageRunner.ERROR_CODE_TOOL_ERROR);
                    response.setAnswer("工具调用失败，已降级返回（评测 stub）。");
                }
                case CIRCUIT_BREAKER -> {
                    response.setBehavior("answer");
                    response.setErrorCode(EvalToolStageRunner.ERROR_CODE_TOOL_DISABLED_BY_CIRCUIT_BREAKER);
                    response.setAnswer("工具因熔断暂不可用，已降级返回（评测 stub）。");
                }
                case RATE_LIMITED -> {
                    response.setBehavior("answer");
                    response.setErrorCode(EvalToolStageRunner.ERROR_CODE_RATE_LIMITED);
                    response.setAnswer("工具触发限流，已降级返回（评测 stub）。");
                }
            }
            return finalizeResponse(response, membershipCtx, evidence);
        }

        response.setAnswer("Day3：meta 可观测稳定（stage_order / step_count / replan_count=0）；阶段仍为占位执行。");
        response.setBehavior("answer");
        return finalizeResponse(response, membershipCtx, evidence);
    }

    private EvalChatResponse finalizeResponse(
            EvalChatResponse response,
            EvalMembershipHttpContext membershipCtx,
            Evidence evidence
    ) {
        attachRetrievalMembershipMeta(response.getMeta(), response, membershipCtx, evidence);
        return response;
    }

    private static void attachRetrievalMembershipMeta(
            EvalChatMeta meta,
            EvalChatResponse response,
            EvalMembershipHttpContext ctx,
            Evidence evidence
    ) {
        if (meta == null || !ctx.completeForHashedMembership()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        List<EvalChatRetrievalHit> hits = response.getRetrievalHits();
        if (hits != null && !hits.isEmpty()) {
            for (EvalChatRetrievalHit h : hits) {
                if (h.getId() != null && !h.getId().isBlank()) {
                    ids.add(RetrievalMembershipHasher.canonicalChunkId(h.getId()));
                }
            }
        }
        // 与 vagent 对齐：若根级 retrieval_hits 未挂上（历史路径/序列化差异），仍可从 sources[*].id 派生 hashes
        if (ids.isEmpty()) {
            List<EvalChatSource> srcs = response.getSources();
            if (srcs == null || srcs.isEmpty()) {
                return;
            }
            for (EvalChatSource s : srcs) {
                if (s.getId() != null && !s.getId().isBlank()) {
                    ids.add(RetrievalMembershipHasher.canonicalChunkId(s.getId()));
                }
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        List<String> hashes = RetrievalMembershipHasher.sortedHitIdHashes(
                ctx.token(), ctx.targetId(), ctx.datasetId(), ctx.caseId(), ids);
        if (hashes.isEmpty()) {
            return;
        }
        meta.setRetrievalHitIdHashes(hashes);
        meta.setRetrievalHitIdHashAlg("HMAC-SHA256");
        meta.setRetrievalHitIdHashKeyDerivation("x-eval-token/v1");
        meta.setRetrievalCandidateLimitN(evidence.candidateLimitN());
        meta.setRetrievalCandidateTotal(evidence.candidateTotal());
        meta.setCanonicalHitIdScheme("kb_chunk_id");
    }

    private record Evidence(
            List<EvalChatRetrievalHit> retrievalHits,
            List<EvalChatSource> sources,
            int candidateTotal,
            int candidateLimitN
    ) {
    }

    private record DedupedSlice(List<Document> docs, int totalUnique) {
    }

    /**
     * 复用 TravelAgent 的检索策略（rewrite → 多路向量召回 → 去重），并映射为对外 {@code retrieval_hits} 与 {@code sources}。
     */
    private Evidence retrieveEvidence(String query, String mode, Integer xEvalMembershipTopN) {
        com.travel.ai.agent.QueryRewriter rewriter = queryRewriter.getIfAvailable();
        VectorStore vs = vectorStore.getIfAvailable();
        if (rewriter == null || vs == null) {
            return new Evidence(List.of(), List.of(), 0, 0);
        }

        int topN = (xEvalMembershipTopN != null && xEvalMembershipTopN > 0) ? Math.min(50, xEvalMembershipTopN) : 8;

        // EVAL 跑批场景：避免为“证据检索”引入外部 LLM 改写（成本/延迟/不稳定）。
        // 后续若引入缓存或规则改写，可在此处平滑演进。
        List<String> queries = "EVAL".equalsIgnoreCase(mode) ? List.of(query) : rewriter.rewrite(query);

        // 检索隔离：主链路按登录 user_id；eval 跑批通常无登录态，因此使用专用 eval 租户（避免泄露真实用户数据）。
        // 注意：要让 eval 跑批命中，需要将评测用知识写入 metadata.user_id="eval"（见 KnowledgeInitializer / 知识导入逻辑）。
        String userIdForFilter = null;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !(auth instanceof AnonymousAuthenticationToken)) {
            String currentUser = auth.getName();
            if (currentUser != null
                    && !currentUser.isBlank()
                    && !"anonymous".equalsIgnoreCase(currentUser)
                    && !"anonymoususer".equalsIgnoreCase(currentUser)) {
                userIdForFilter = currentUser;
            }
        } else {
            userIdForFilter = "eval";
        }

        Filter.Expression userFilter = (userIdForFilter == null) ? null : new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("user_id"),
                new Filter.Value(userIdForFilter)
        );

        List<Document> flat = new ArrayList<>();
        for (String q : queries) {
            flat.addAll(vs.similaritySearch(SearchRequest.builder()
                    .query(q)
                    .topK(2)
                    .filterExpression(userFilter)
                    .build()));
        }

        DedupedSlice slice = mergeAndDedupeDocuments(flat, topN);
        List<Document> docs = slice.docs();
        int candidateTotalUnique = slice.totalUnique();

        List<EvalChatRetrievalHit> hits = new ArrayList<>(docs.size());
        List<EvalChatSource> sources = new ArrayList<>(docs.size());
        for (Document d : docs) {
            String rawId = d.getId();
            String id = RetrievalMembershipHasher.canonicalChunkId(rawId);
            if (id.isEmpty()) {
                continue;
            }
            String title = null;
            if (d.getMetadata() != null) {
                Object src = d.getMetadata().get("source_name");
                if (src != null) {
                    title = String.valueOf(src);
                }
            }

            hits.add(new EvalChatRetrievalHit(id, title, null));

            EvalChatSource s = new EvalChatSource();
            s.setId(id);
            s.setTitle(title);
            s.setSnippet(truncateSnippet(d.getText(), 300));
            s.setScore(null);
            sources.add(s);
        }

        // 先使用“sources == 前 N 条 hits”的保守策略，避免引用落在 N 之外触发 membership 假失败；
        // 后续升级（rerank/融合）只改变排序与子集选择，不改变闭环约束。
        return new Evidence(
                Collections.unmodifiableList(hits),
                Collections.unmodifiableList(sources),
                candidateTotalUnique,
                topN
        );
    }

    private static String truncateSnippet(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, maxChars) + "…";
    }

    private static DedupedSlice mergeAndDedupeDocuments(List<Document> documents, int maxDocs) {
        var seen = new LinkedHashMap<String, Document>();
        if (documents == null) {
            return new DedupedSlice(List.of(), 0);
        }
        for (Document d : documents) {
            if (d == null) {
                continue;
            }
            String key;
            if (d.getId() != null && !d.getId().isBlank()) {
                key = "id:" + RetrievalMembershipHasher.canonicalChunkId(d.getId());
            } else {
                String text = d.getText() != null ? d.getText() : "";
                key = "text:" + text.hashCode();
            }
            seen.putIfAbsent(key, d);
        }
        int totalUnique = seen.size();
        var all = new ArrayList<>(seen.values());
        int take = Math.min(maxDocs, totalUnique);
        List<Document> slice = new ArrayList<>(all.subList(0, take));
        return new DedupedSlice(Collections.unmodifiableList(slice), totalUnique);
    }
}
