package com.travel.ai.eval;

import com.travel.ai.eval.dto.EvalChatRequest;
import com.travel.ai.eval.dto.EvalChatResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测专用入口（P0）：{@code POST /api/v1/eval/chat}，<strong>非流式</strong>、一次性 JSON 返回。
 * <p>
 * <b>Day1 交付</b>：契约与序列化（snake_case）正确；业务为占位说明文案。安全门控（{@code X-Eval-Token}、CIDR 等）按
 * {@code plans/eval-upgrade.md} 在后续迭代接入。
 *
 * <h2>最小请求示例（curl，Windows PowerShell 可把单引号改为双引号并转义内部引号）</h2>
 * <pre>{@code
 * curl -s -X POST "http://localhost:8080/api/v1/eval/chat" \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "query": "帮我规划上海三日游",
 *     "mode": "AGENT",
 *     "conversation_id": "day1-demo-conv"
 *   }'
 * }</pre>
 *
 * <h2>Day3 证据 ①：正常 query（走满线性占位流水线）</h2>
 * <p>{@code step_count == stage_order.length}（此处为 5）；{@code replan_count} 恒为 0。</p>
 * <pre>{@code
 * {
 *   "answer": "Day3：meta 可观测稳定…",
 *   "behavior": "answer",
 *   "latency_ms": 3,
 *   "capabilities": { ... },
 *   "meta": {
 *     "mode": "AGENT",
 *     "request_id": "550e8400-e29b-41d4-a716-446655440000",
 *     "stage_order": ["PLAN","RETRIEVE","TOOL","WRITE","GUARD"],
 *     "step_count": 5,
 *     "replan_count": 0
 *   }
 * }
 * }</pre>
 *
 * <h2>Day3 证据 ②：空/空白 query（clarify，不跑流水线）</h2>
 * <p>{@code stage_order} 为空、{@code step_count=0}；{@code replan_count} 仍为 0。</p>
 * <pre>{@code
 * {
 *   "behavior": "clarify",
 *   "meta": {
 *     "mode": "EVAL",
 *     "request_id": "...",
 *     "stage_order": [],
 *     "step_count": 0,
 *     "replan_count": 0
 *   }
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/eval")
public class EvalChatController {

    private final EvalChatService evalChatService;

    public EvalChatController(EvalChatService evalChatService) {
        this.evalChatService = evalChatService;
    }

    /**
     * 评测聊天：非流式；测量从进入本方法到返回前的耗时，写入 {@code latency_ms}。
     */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public EvalChatResponse chat(@RequestBody EvalChatRequest request) {
        long startMs = System.currentTimeMillis();
        // 先走同一套 meta/capabilities 拼装，保证契约字段始终齐全；空 query 仅覆盖 answer/behavior。
        EvalChatResponse body = evalChatService.buildStubResponse(request);
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            body.setAnswer("请求体缺少非空字段 query。");
            body.setBehavior("clarify");
        }
        body.setLatencyMs(System.currentTimeMillis() - startMs);
        return body;
    }
}
