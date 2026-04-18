package com.travel.ai.tools;

import org.slf4j.Logger;

public final class ToolObservability {

    private ToolObservability() {
    }

    public static void log(Logger log, ToolResult r, String requestId) {
        if (log == null || r == null) {
            return;
        }
        log.info("[tool] name={} required={} used={} succeeded={} outcome={} errorCode={} latencyMs={} truncated={} requestId={}",
                r.name(), r.required(), r.used(), r.succeeded(), r.outcome(), r.errorCode(), r.latencyMs(), r.observationTruncated(), requestId);
    }
}

