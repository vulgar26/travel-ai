package com.travel.ai.plan;

import java.util.EnumSet;

/**
 * 根据附录 E {@link PlanV1#steps()} 推导<strong>物理阶段</strong>是否执行（与「计划文本仅注入 prompt」分离）。
 * <p>
 * 规则：
 * <ul>
 *   <li>{@code RETRIEVE}：{@code steps} 中至少一步 {@code stage=RETRIEVE} 时执行向量检索等。</li>
 *   <li>{@code TOOL}：至少一步 {@code stage=TOOL} 时执行工具阶段。</li>
 *   <li>{@code GUARD}：显式含 {@code GUARD}，或<strong>已计划执行 RETRIEVE</strong>时仍跑门控（保留零命中澄清；与历史「RETRIEVE+WRITE」降级 plan 对齐）。</li>
 *   <li>{@code PLAN} / {@code WRITE}：由编排器固定处理，不读取 steps 中的 {@code PLAN}（PLAN 始终在首轮解析前执行一次）。</li>
 * </ul>
 */
public final class PlanPhysicalStagePolicy {

    /**
     * 物理阶段开关（不含 PLAN/WRITE；WRITE 由编排固定收尾）。
     *
     * @param runRetrieve 是否跑检索
     * @param runTool     是否跑工具阶段
     * @param runGuard    是否跑门控
     */
    public record PhysicalStageFlags(boolean runRetrieve, boolean runTool, boolean runGuard) {

        /** 与「五步全跑」等价：用于仅缺 {@link PlanV1} 时的兜底。 */
        public static PhysicalStageFlags allStagesAfterPlan() {
            return new PhysicalStageFlags(true, true, true);
        }
    }

    private PlanPhysicalStagePolicy() {
    }

    public static PhysicalStageFlags resolve(PlanV1 plan) {
        EnumSet<PlanStage> present = EnumSet.noneOf(PlanStage.class);
        for (PlanStepV1 s : plan.steps()) {
            present.add(s.stage());
        }
        boolean retrieve = present.contains(PlanStage.RETRIEVE);
        boolean tool = present.contains(PlanStage.TOOL);
        boolean guard = present.contains(PlanStage.GUARD) || retrieve;
        return new PhysicalStageFlags(retrieve, tool, guard);
    }
}
