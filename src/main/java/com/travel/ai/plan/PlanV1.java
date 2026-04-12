package com.travel.ai.plan;

import java.util.List;
import java.util.Map;

/**
 * travel-ai Plan（P0 v1），字段语义与 {@code plans/p0-execution-map.md} <b>附录 E</b> 对齐。
 * <p>
 * SSOT：附录 E JSON Schema；本类型为其在 Java 中的投影，冲突以附录 E 为准。
 */
public record PlanV1(
        String planVersion,
        String goal,
        List<PlanStepV1> steps,
        PlanConstraintsV1 constraints,
        String notes
) {
}
