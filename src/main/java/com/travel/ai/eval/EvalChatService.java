package com.travel.ai.eval;

import com.travel.ai.eval.dto.*;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Day1：评测聊天骨架——不调用 LLM / 检索 / 工具，只拼装符合契约的 JSON 形态。
 * <p>
 * 后续 Day2+：在此类中接入线性阶段编排、PlanParser、超时与降级等逻辑。
 */
@Service
public class EvalChatService {

    /**
     * 构造评测响应（不含 {@code latency_ms}，由 Controller 统一填入，避免重复计时口径）。
     *
     * @param request 原始请求（可为 null 时由 Controller 校验）
     */
    public EvalChatResponse buildStubResponse(EvalChatRequest request) {
        String mode = request.getMode();
        if (mode == null || mode.isBlank()) {
            mode = "EVAL";
        }

        EvalCapabilities capabilities = new EvalCapabilities(
                new EvalRetrievalCapability(true, false),
                new EvalToolsCapability(true, true),
                new EvalStreamingCapability(false),
                new EvalGuardrailsCapability(false, false, false)
        );

        EvalChatResponse response = new EvalChatResponse();
        response.setAnswer("评测接口骨架已就绪（Day1）。后续将在此接入 Agent 线性流水线（PLAN→RETRIEVE→TOOL→WRITE→GUARD）。");
        response.setBehavior("answer");
        response.setCapabilities(capabilities);
        response.setMeta(new EvalChatMeta(mode, UUID.randomUUID().toString()));
        return response;
    }
}
