package com.travel.ai.eval;

import com.travel.ai.eval.dto.*;
import com.travel.ai.eval.planrepair.EvalPlanParseCoordinator;
import org.springframework.stereotype.Service;

import java.util.Collections;
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

    public EvalChatService(EvalPlanParseCoordinator planParseCoordinator, EvalToolStageRunner evalToolStageRunner) {
        this.planParseCoordinator = planParseCoordinator;
        this.evalToolStageRunner = evalToolStageRunner;
    }

    /**
     * 构造评测响应（不含 {@code latency_ms}，由 Controller 统一填入）。
     * <p>
     * 空 {@code query} 仍返回完整 {@code capabilities}，但<strong>不执行</strong>线性阶段（{@code stage_order=[]}，
     * {@code step_count=0}），且不执行 plan 解析；由 Controller 将 {@code behavior} 改为 {@code clarify}。
     */
    public EvalChatResponse buildStubResponse(EvalChatRequest request) {
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
}
