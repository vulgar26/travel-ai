package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * {@code capabilities.guardrails}：门控/引用/evidence 等能力声明（Day1 骨架先占位为 false）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalGuardrailsCapability {

    private boolean quoteOnly;
    private boolean evidenceMap;
    private boolean reflection;

    public EvalGuardrailsCapability() {
    }

    public EvalGuardrailsCapability(boolean quoteOnly, boolean evidenceMap, boolean reflection) {
        this.quoteOnly = quoteOnly;
        this.evidenceMap = evidenceMap;
        this.reflection = reflection;
    }

    public boolean isQuoteOnly() {
        return quoteOnly;
    }

    public void setQuoteOnly(boolean quoteOnly) {
        this.quoteOnly = quoteOnly;
    }

    public boolean isEvidenceMap() {
        return evidenceMap;
    }

    public void setEvidenceMap(boolean evidenceMap) {
        this.evidenceMap = evidenceMap;
    }

    public boolean isReflection() {
        return reflection;
    }

    public void setReflection(boolean reflection) {
        this.reflection = reflection;
    }
}
