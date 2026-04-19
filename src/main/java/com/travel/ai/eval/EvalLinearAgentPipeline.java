package com.travel.ai.eval;

import com.travel.ai.eval.dto.EvalChatRequest;
import com.travel.ai.plan.PlanPhysicalStagePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * P0 线性阶段执行骨架（Day2）：<strong>单一入口</strong> {@link #runStubStages(EvalChatRequest, PlanPhysicalStagePolicy.PhysicalStageFlags, Runnable)}，
 * 在固定顺序 {@code PLAN→RETRIEVE→TOOL→GUARD→WRITE} 下按 {@link PlanPhysicalStagePolicy} <strong>物理跳过</strong>未出现在 plan steps 中的阶段
 * （与主线 {@link com.travel.ai.agent.TravelAgent} 一致）。
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
     * 固定顺序；须与主线 {@link com.travel.ai.agent.TravelAgent} 线性阶段一致。
     */
    private static final EvalAgentStage[] FIXED_ORDER = {
            EvalAgentStage.PLAN,
            EvalAgentStage.RETRIEVE,
            EvalAgentStage.TOOL,
            EvalAgentStage.GUARD,
            EvalAgentStage.WRITE
    };

    /** {@code PLAN} 之后、按物理跳过规则迭代的子序列。 */
    private static final EvalAgentStage[] POST_PLAN_STAGES = {
            EvalAgentStage.RETRIEVE,
            EvalAgentStage.TOOL,
            EvalAgentStage.GUARD,
            EvalAgentStage.WRITE
    };

    private EvalLinearAgentPipeline() {
    }

    /**
     * Day2：依次执行五阶段占位逻辑，并返回实际经过的阶段名列表（大写枚举名，与契约一致）。
     *
     * @deprecated 使用 {@link #runStubStages(EvalChatRequest, PlanPhysicalStagePolicy.PhysicalStageFlags, Runnable)} 以对齐 plan steps。
     */
    @Deprecated
    public static List<String> runStubStages(EvalChatRequest request) {
        return runStubStages(request, PlanPhysicalStagePolicy.PhysicalStageFlags.allStagesAfterPlan(), () -> {});
    }

    /**
     * Day6：在 {@link EvalAgentStage#TOOL} 节点插入<strong>串行</strong>回调（仅调用一次，不并行多工具），其余阶段仍为占位。
     *
     * @deprecated 使用 {@link #runStubStages(EvalChatRequest, PlanPhysicalStagePolicy.PhysicalStageFlags, Runnable)}。
     */
    @Deprecated
    public static List<String> runStubStages(EvalChatRequest request, Runnable onToolStage) {
        return runStubStages(request, PlanPhysicalStagePolicy.PhysicalStageFlags.allStagesAfterPlan(), onToolStage);
    }

    /**
     * 按 {@link PlanPhysicalStagePolicy} 物理跳过 RETRIEVE/TOOL/GUARD；{@code PLAN} 与 {@code WRITE} 始终计入 {@code stage_order}。
     */
    public static List<String> runStubStages(
            EvalChatRequest request,
            PlanPhysicalStagePolicy.PhysicalStageFlags flags,
            Runnable onToolStage
    ) {
        List<String> stageOrder = new ArrayList<>(FIXED_ORDER.length);
        Runnable toolHook = onToolStage != null ? onToolStage : () -> {};
        stageOrder.add(EvalAgentStage.PLAN.name());
        runStubStage(EvalAgentStage.PLAN, request);
        for (EvalAgentStage stage : POST_PLAN_STAGES) {
            if (stage == EvalAgentStage.RETRIEVE && !flags.runRetrieve()) {
                continue;
            }
            if (stage == EvalAgentStage.TOOL && !flags.runTool()) {
                continue;
            }
            if (stage == EvalAgentStage.GUARD && !flags.runGuard()) {
                continue;
            }
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
