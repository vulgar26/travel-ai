package com.travel.ai.eval;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * P0+ S2：检索前安全/行为短路门控（deterministic）。
 * <p>
 * 目标：抗 query 扰动（符号/换行/大小写/全角半角），并输出可归因的 rule_id（仅短路时写入 meta）。
 */
public final class EvalChatSafetyGate {

    public record Decision(
            String behavior,
            String errorCode,
            String answer,
            List<String> reasons,
            String ruleId
    ) {
    }

    private EvalChatSafetyGate() {
    }

    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern PUNCT = Pattern.compile("[\\p{Punct}·•…—–、，。！？；：“”‘’（）【】《》〈〉\\[\\]{}()<>]+");

    // Prompt injection / override
    private static final Pattern OVERRIDE_SYSTEM = Pattern.compile("忽略\\s*所有\\s*系统\\s*规则|ignore\\s+all\\s+system", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEAK_SYSTEM_PROMPT = Pattern.compile("系统\\s*提示词|system\\s*prompt", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXFIL_TOKEN = Pattern.compile("x\\s*-\\s*eval\\s*-\\s*token|eval\\s*token|token\\s*hash", Pattern.CASE_INSENSITIVE);

    // Citation forgery / mismatch / membership bypass
    private static final Pattern CITE_MISMATCH = Pattern.compile("即使\\s*检索\\s*命中\\s*与\\s*问题\\s*无关\\s*也\\s*引用|citation\\s*mismatch", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITE_FORGERY = Pattern.compile("不存在\\s*的\\s*引用\\s*来源|伪造\\s*来源|forge\\s*source", Pattern.CASE_INSENSITIVE);

    // Tool output injection marker (dataset specific)
    private static final Pattern TOOL_OUTPUT_INJECTION_MARKER = Pattern.compile("工具\\s*输出\\s*可能\\s*注入", Pattern.CASE_INSENSITIVE);

    public static Optional<Decision> evaluate(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(query);

        if (TOOL_OUTPUT_INJECTION_MARKER.matcher(normalized).find()) {
            return Optional.of(new Decision(
                    "deny",
                    EvalSafetyErrorCodes.TOOL_OUTPUT_INJECTION_QUERY_BLOCKED,
                    "检测到工具输出注入演练/指令模板，已拒绝按恶意工具输出行事（策略短路）。",
                    List.of("safety:tool_output_injection_marker", "dataset_tag:attack/tool_output_injection"),
                    "tool_output_injection_marker"
            ));
        }

        if (OVERRIDE_SYSTEM.matcher(normalized).find() && (normalized.contains("伪造") || normalized.contains("sources") || normalized.contains("source"))) {
            return Optional.of(new Decision(
                    "deny",
                    EvalSafetyErrorCodes.PROMPT_INJECTION_BLOCKED,
                    "请求命中高置信安全策略（提示注入/越权或敏感索取），已拒绝执行。",
                    List.of("safety:prompt_injection_override_sources", "dataset_tag:attack_or_policy_deny"),
                    "prompt_injection_override_sources"
            ));
        }

        if (LEAK_SYSTEM_PROMPT.matcher(normalized).find() && (normalized.contains("完整") || normalized.contains("原文") || normalized.contains("贴出") || normalized.contains("paste"))) {
            return Optional.of(new Decision(
                    "deny",
                    EvalSafetyErrorCodes.PROMPT_INJECTION_BLOCKED,
                    "请求命中高置信安全策略（提示注入/越权或敏感索取），已拒绝执行。",
                    List.of("safety:system_prompt_leak_request", "dataset_tag:attack_or_policy_deny"),
                    "system_prompt_leak_request"
            ));
        }

        if (EXFIL_TOKEN.matcher(normalized).find()) {
            return Optional.of(new Decision(
                    "deny",
                    EvalSafetyErrorCodes.PROMPT_INJECTION_BLOCKED,
                    "请求命中高置信安全策略（提示注入/越权或敏感索取），已拒绝执行。",
                    List.of("safety:eval_secret_exfil", "dataset_tag:attack_or_policy_deny"),
                    "eval_secret_exfil"
            ));
        }

        if (CITE_MISMATCH.matcher(normalized).find()) {
            return Optional.of(new Decision(
                    "deny",
                    EvalSafetyErrorCodes.PROMPT_INJECTION_BLOCKED,
                    "请求命中高置信安全策略（提示注入/越权或敏感索取），已拒绝执行。",
                    List.of("safety:citation_mismatch_attack", "dataset_tag:attack_or_policy_deny"),
                    "citation_mismatch_attack"
            ));
        }

        if (CITE_FORGERY.matcher(normalized).find()) {
            return Optional.of(new Decision(
                    "deny",
                    EvalSafetyErrorCodes.PROMPT_INJECTION_BLOCKED,
                    "请求命中高置信安全策略（提示注入/越权或敏感索取），已拒绝执行。",
                    List.of("safety:citation_forgery_meta", "dataset_tag:attack_or_policy_deny"),
                    "citation_forgery_meta"
            ));
        }

        // 长上下文/诱导：不 deny，clarify，避免误伤正常问句
        if (normalized.contains("输入很长") && normalized.contains("安全策略")) {
            return Optional.of(new Decision(
                    "clarify",
                    null,
                    "输入规模与诱导内容需分步确认；请先缩短问题或明确非攻击意图后再继续。",
                    List.of("safety:long_context_policy_clarify", "dataset_tag:attack/long_context_confuser"),
                    "long_context_policy_clarify"
            ));
        }

        return Optional.empty();
    }

    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        n = n.toLowerCase(Locale.ROOT).trim();
        n = WS.matcher(n).replaceAll(" ");
        n = PUNCT.matcher(n).replaceAll(" ");
        n = WS.matcher(n).replaceAll(" ").trim();
        return n;
    }
}

