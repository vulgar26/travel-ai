package com.travel.ai.eval;

import com.travel.ai.eval.dto.*;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 评测聊天：Day2 起在 {@link #buildStubResponse(EvalChatRequest)} 内挂载
 * {@link EvalLinearAgentPipeline}，输出 {@code meta.stage_order} / {@code step_count} / {@code replan_count}。
 */
@Service
public class EvalChatService {

    /**
     * 构造评测响应（不含 {@code latency_ms}，由 Controller 统一填入）。
     * <p>
     * 空 {@code query} 仍返回完整 {@code capabilities}，但<strong>不执行</strong>线性阶段（{@code stage_order=[]}，
     * {@code step_count=0}），由 Controller 将 {@code behavior} 改为 {@code clarify}。
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
        if (skipPipeline) {
            meta.setStageOrder(Collections.emptyList());
            meta.setStepCount(0);
        } else {
            List<String> stageOrder = EvalLinearAgentPipeline.runStubStages(request);
            meta.setStageOrder(stageOrder);
            meta.setStepCount(stageOrder.size());
        }

        EvalCapabilities capabilities = new EvalCapabilities(
                new EvalRetrievalCapability(true, false),
                new EvalToolsCapability(true, true),
                new EvalStreamingCapability(false),
                new EvalGuardrailsCapability(false, false, false)
        );

        EvalChatResponse response = new EvalChatResponse();
        response.setAnswer("Day3：meta 可观测稳定（stage_order / step_count / replan_count=0）；阶段仍为占位执行。");
        response.setBehavior("answer");
        response.setCapabilities(capabilities);
        response.setMeta(meta);
        return response;
    }
}
