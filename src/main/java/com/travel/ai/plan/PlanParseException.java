package com.travel.ai.plan;

/**
 * {@link PlanParser} 在输入无法满足 {@code plans/p0-execution-map.md} 附录 E 的骨架要求时抛出。
 */
public class PlanParseException extends Exception {

    public PlanParseException(String message) {
        super(message);
    }

    public PlanParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
