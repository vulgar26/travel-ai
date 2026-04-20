package com.travel.ai.llm;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * 从 Spring AI 的响应对象（尤其是 streaming 的元素对象）里用<strong>反射</strong>提取 usage。\n
 * <p>
 * 目的：降低对具体 provider SDK/字段名的编译期耦合，便于未来切换供应商。
 * <p>
 * 注意：若提取失败，返回 empty，不影响主流程。
 */
public final class SpringAiUsageExtractor {

    private SpringAiUsageExtractor() {
    }

    public static Optional<LlmUsage> tryExtract(Object chatResponseLike) {
        if (chatResponseLike == null) {
            return Optional.empty();
        }
        try {
            // 常见路径 1：getMetadata().getUsage() / getUsage()
            Object metadata = invokeNoArg(chatResponseLike, "getMetadata").orElse(null);
            Object usage = null;
            if (metadata != null) {
                usage = invokeNoArg(metadata, "getUsage").orElse(null);
            }
            if (usage == null) {
                usage = invokeNoArg(chatResponseLike, "getUsage").orElse(null);
            }
            if (usage == null) {
                return Optional.empty();
            }

            Integer prompt = readIntAny(usage, "getPromptTokens", "promptTokens", "getPromptTokenCount", "getInputTokens");
            Integer completion = readIntAny(usage, "getGenerationTokens", "generationTokens", "getCompletionTokens", "completionTokens", "getOutputTokens");
            Integer total = readIntAny(usage, "getTotalTokens", "totalTokens", "getTokenCount");

            // 若 total 缺失，尽量由 prompt+completion 推导
            if (total == null && prompt != null && completion != null) {
                total = prompt + completion;
            }
            if (prompt == null && completion == null && total == null) {
                return Optional.empty();
            }
            return Optional.of(new LlmUsage(prompt, completion, total, "provider"));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> invokeNoArg(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return Optional.ofNullable(m.invoke(target));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Integer readIntAny(Object target, String... methodOrFieldNames) {
        for (String name : methodOrFieldNames) {
            Integer v = readInt(target, name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static Integer readInt(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return null;
        }
        String n = name.trim();
        try {
            // method
            if (n.startsWith("get")) {
                Method m = target.getClass().getMethod(n);
                m.setAccessible(true);
                Object v = m.invoke(target);
                return coerceInt(v);
            }
            // fallback: try getter naming
            String getter = "get" + n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
            Method m2 = target.getClass().getMethod(getter);
            m2.setAccessible(true);
            Object v2 = m2.invoke(target);
            return coerceInt(v2);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer coerceInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}

