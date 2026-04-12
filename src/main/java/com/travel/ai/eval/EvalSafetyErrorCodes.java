package com.travel.ai.eval;

/**
 * Day9：用户输入 / 工具相关对抗面的稳定 {@code error_code}（与 {@code PARSE_ERROR} / 工具超时错误区分）。
 */
public final class EvalSafetyErrorCodes {

    /** 检测到典型提示注入、越权索取、敏感索取等，拒绝执行。 */
    public static final String PROMPT_INJECTION_BLOCKED = "PROMPT_INJECTION_BLOCKED";

    /** 用户问题明确落在「工具输出注入」演练句式（数据集 attack/tool_output_injection）。 */
    public static final String TOOL_OUTPUT_INJECTION_QUERY_BLOCKED = "TOOL_OUTPUT_INJECTION_QUERY_BLOCKED";

    private EvalSafetyErrorCodes() {
    }
}
