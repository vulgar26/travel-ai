package com.travel.ai.plan;

import java.util.Map;

/**
 * 附录 E 中 {@code steps[*].tool}：必填 {@code name} 与 {@code args}。
 */
public record PlanToolV1(String name, Map<String, Object> args) {
}
