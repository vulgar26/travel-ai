package com.travel.ai.eval;

import com.travel.ai.eval.dto.EvalChatRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * P0 线性阶段执行骨架（Day2）：<strong>单一入口</strong> {@link #runStubStages(EvalChatRequest)}，内部按固定数组顺序
 * {@code PLAN→RETRIEVE→TOOL→WRITE→GUARD} 串行调用各阶段占位逻辑。
 * <p>
 * <b>如何保证固定顺序（给组长看的概念层说明）</b>：
 * <ul>
 *   <li>顺序写死在 {@link #FIXED_ORDER}，不使用「阶段名 → 处理器」的 Map/注册表，避免动态跳转。</li>
 *   <li>仅一个 {@code for} 循环按数组下标递增执行，不根据模型输出选择下一跳，因此天然无 DAG、无回环。</li>
 *   <li>工具调用若放在 TOOL 阶段，也只允许在该阶段内顺序执行（Day2 仅占位；P0 禁止同一轮并行多工具）。</li>
 * </ul>
 */
public final class EvalLinearAgentPipeline {

    /**
     * 固定顺序；任何改动须与 {@code plans/p0-execution-map.md} A3-2 一致。
     */
    private static final EvalAgentStage[] FIXED_ORDER = {
            EvalAgentStage.PLAN,
            EvalAgentStage.RETRIEVE,
            EvalAgentStage.TOOL,
            EvalAgentStage.WRITE,
            EvalAgentStage.GUARD
    };

    private EvalLinearAgentPipeline() {
    }

    /**
     * Day2：依次执行五阶段占位逻辑，并返回实际经过的阶段名列表（大写枚举名，与契约一致）。
     */
    public static List<String> runStubStages(EvalChatRequest request) {
        return runStubStages(request, () -> {});
    }

    /**
     * Day6：在 {@link EvalAgentStage#TOOL} 节点插入<strong>串行</strong>回调（仅调用一次，不并行多工具），其余阶段仍为占位。
     */
    public static List<String> runStubStages(EvalChatRequest request, Runnable onToolStage) {
        List<String> stageOrder = new ArrayList<>(FIXED_ORDER.length);
        Runnable toolHook = onToolStage != null ? onToolStage : () -> {};
        for (EvalAgentStage stage : FIXED_ORDER) {
            if (stage == EvalAgentStage.TOOL) {
                toolHook.run();
            } else {
                runStubStage(stage, request);
            }
            stageOrder.add(stage.name());
        }
        return Collections.unmodifiableList(stageOrder);
    }

    /**
     * Day2 占位：每阶段仅占位；Day3+ 在此接入计划生成、检索、工具、写作、门控等实现。
     */
    private static void runStubStage(EvalAgentStage stage, EvalChatRequest request) {
        switch (stage) {
            case PLAN -> { /* 后续：产出 Plan JSON + PlanParser */ }
            case RETRIEVE -> { /* 后续：向量检索 / hits */ }
            case TOOL -> { /* Day6：实际工具逻辑由 {@link #runStubStages(EvalChatRequest, Runnable)} 的回调注入 */ }
            case WRITE -> { /* 后续：基于证据生成 answer 草稿 */ }
            case GUARD -> { /* 后续：低置信 / 引用门控 */ }
        }
    }
}
