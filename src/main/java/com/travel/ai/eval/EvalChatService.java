package com.travel.ai.eval;

import com.travel.ai.eval.dto.*;
import com.travel.ai.eval.planrepair.EvalPlanParseCoordinator;
import org.springframework.beans.factory.ObjectProvider;
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
 * Day7 起支持 {@link EvalRagGateScenarios}：空命中/低置信门控（P0 不启用 score 阈值），{@code meta.low_confidence} + {@code reasons[]}。
 * Day9 起在 plan 解析成功后执行 {@link EvalQuerySafetyPolicy}：对抗/敏感句式稳定 {@code deny} 或 {@code clarify}，归因见 {@link EvalSafetyErrorCodes}。
 */
@Service
public class EvalChatService {

    private final EvalPlanParseCoordinator planParseCoordinator;
    private final EvalToolStageRunner evalToolStageRunner;
    private final ObjectProvider<com.travel.ai.agent.QueryRewriter> queryRewriter;
    private final ObjectProvider<VectorStore> vectorStore;

    public EvalChatService(
            EvalPlanParseCoordinator planParseCoordinator,
            EvalToolStageRunner evalToolStageRunner,
            ObjectProvider<com.travel.ai.agent.QueryRewriter> queryRewriter,
            ObjectProvider<VectorStore> vectorStore
    ) {
        this.planParseCoordinator = planParseCoordinator;
        this.evalToolStageRunner = evalToolStageRunner;
        this.queryRewriter = queryRewriter;
        this.vectorStore = vectorStore;
    }

    /**
     * 构造评测响应（不含 {@code latency_ms}，由 Controller 统一填入）。
     * <p>
     * 空 {@code query} 仍返回完整 {@code capabilities}，但<strong>不执行</strong>线性阶段（{@code stage_order=[]}，
     * {@code step_count=0}），且不执行 plan 解析；由 Controller 将 {@code behavior} 改为 {@code clarify}。
     */
    public EvalChatResponse buildStubResponse(EvalChatRequest request) {
        return buildStubResponse(request, null);
    }

    /**
     * @param xEvalMembershipTopN eval 下发的候选截断上限（见 P0+ §16）；为空则用服务端默认值。
     */
    public EvalChatResponse buildStubResponse(EvalChatRequest request, Integer xEvalMembershipTopN) {
        String mode = request.getMode();
        if (mode == null || mode.isBlank()) {
            mode = "EVAL";
        }
        String requestId = UUID.randomUUID().toString();

        boolean skipPipeline = request.getQuery() == null || request.getQuery().isBlank();

        EvalChatMeta meta = new EvalChatMeta(mode, requestId);
        meta.setReplanCount(EvalChatMeta.P0_REPLAN_COUNT);

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
            return response;
        }

        Optional<EvalQuerySafetyPolicy.Decision> safety = EvalQuerySafetyPolicy.evaluate(request.getQuery());
        if (safety.isPresent()) {
            EvalQuerySafetyPolicy.Decision d = safety.get();
            meta.setStageOrder(List.of("PLAN", "GUARD"));
            meta.setStepCount(2);
            meta.setReasons(d.reasons());
            response.setBehavior(d.behavior());
            if (d.errorCode() != null) {
                response.setErrorCode(d.errorCode());
            }
            response.setAnswer(d.answer());
            return response;
        }

        Kind ragKind = EvalRagGateScenarios.resolve(request);
        if (ragKind != null) {
            meta.setStageOrder(List.of("PLAN", "RETRIEVE"));
            meta.setStepCount(2);
            meta.setLowConfidence(true);
            if (ragKind == Kind.EMPTY_HITS) {
                meta.setRetrieveHitCount(0);
                meta.setReasons(EvalRagGateScenarios.REASONS_EMPTY_HITS);
                response.setErrorCode(EvalRagGateScenarios.ERROR_CODE_RETRIEVE_EMPTY);
                response.setAnswer("检索零命中，已门控为澄清（评测 stub，P0 未启用 score 阈值）。");
            } else {
                meta.setRetrieveHitCount(1);
                meta.setReasons(EvalRagGateScenarios.REASONS_LOW_CONFIDENCE);
                response.setAnswer("证据置信不足，已门控为澄清（评测 stub，P0 未启用 score 阈值）。");
            }
            response.setBehavior("clarify");
            return response;
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
            toolDto.setUsed(inv.used());
            toolDto.setName(inv.name());
            toolDto.setOutcome(inv.outcome());
            response.setTool(toolDto);
            meta.setToolCallsCount(inv.used() ? 1 : 0);
            meta.setToolOutcome(inv.outcome());
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
            }
            return response;
        }

        response.setAnswer("Day3：meta 可观测稳定（stage_order / step_count / replan_count=0）；阶段仍为占位执行。");
        response.setBehavior("answer");
        return response;
    }

    private record Evidence(List<EvalChatRetrievalHit> retrievalHits, List<EvalChatSource> sources) {
    }

    /**
     * 复用 TravelAgent 的检索策略（rewrite → 多路向量召回 → 去重），并映射为对外 {@code retrieval_hits} 与 {@code sources}。
     */
    private Evidence retrieveEvidence(String query, String mode, Integer xEvalMembershipTopN) {
        com.travel.ai.agent.QueryRewriter rewriter = queryRewriter.getIfAvailable();
        VectorStore vs = vectorStore.getIfAvailable();
        if (rewriter == null || vs == null) {
            return new Evidence(List.of(), List.of());
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

        List<Document> docs = mergeAndDedupeDocuments(flat, topN);

        List<EvalChatRetrievalHit> hits = new ArrayList<>(docs.size());
        List<EvalChatSource> sources = new ArrayList<>(docs.size());
        for (Document d : docs) {
            String id = d.getId();
            if (id == null || id.isBlank()) {
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
        return new Evidence(Collections.unmodifiableList(hits), Collections.unmodifiableList(sources));
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

    private static List<Document> mergeAndDedupeDocuments(List<Document> documents, int maxDocs) {
        var seen = new LinkedHashMap<String, Document>();
        if (documents == null) {
            return List.of();
        }
        for (Document d : documents) {
            if (d == null) {
                continue;
            }
            String key;
            if (d.getId() != null && !d.getId().isBlank()) {
                key = "id:" + d.getId();
            } else {
                String text = d.getText() != null ? d.getText() : "";
                key = "text:" + text.hashCode();
            }
            seen.putIfAbsent(key, d);
        }
        return new ArrayList<>(seen.values()).subList(0, Math.min(maxDocs, seen.size()));
    }
}
