package com.travel.ai.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.ai.config.AppMemoryProperties;

import java.util.regex.Pattern;

/**
 * 校验用户画像 JSON：仅顶层标量、键名白名单、槽位与字符串长度上限。
 */
public final class UserProfilePayloadValidator {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");

    private UserProfilePayloadValidator() {
    }

    /**
     * @throws IllegalArgumentException 校验失败
     */
    public static ObjectNode validateCopy(ObjectNode input, AppMemoryProperties memoryProperties, ObjectMapper objectMapper) {
        if (input == null) {
            throw new IllegalArgumentException("profile must be a JSON object");
        }
        int maxSlots = memoryProperties.getProfile().getMaxSlots();
        int maxVal = memoryProperties.getProfile().getMaxValueChars();
        if (input.size() > maxSlots) {
            throw new IllegalArgumentException("profile exceeds max slots: " + maxSlots);
        }
        ObjectNode out = objectMapper.createObjectNode();
        var it = input.fields();
        while (it.hasNext()) {
            var e = it.next();
            String key = e.getKey();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException("invalid profile key (use [a-zA-Z][a-zA-Z0-9_]{0,63}): " + key);
            }
            JsonNode v = e.getValue();
            if (v == null || v.isNull()) {
                throw new IllegalArgumentException("null profile values are not allowed here; use PATCH with null to remove a key");
            } else if (v.isBoolean()) {
                out.put(key, v.booleanValue());
            } else if (v.isIntegralNumber()) {
                out.put(key, v.longValue());
            } else if (v.isFloatingPointNumber()) {
                out.put(key, v.doubleValue());
            } else if (v.isTextual()) {
                String s = v.asText();
                if (s.length() > maxVal) {
                    throw new IllegalArgumentException("profile value too long for key '" + key + "' (max " + maxVal + " chars)");
                }
                out.put(key, s);
            } else {
                throw new IllegalArgumentException("unsupported profile value type for key '" + key + "' (only string, number, boolean)");
            }
        }
        return out;
    }

    /**
     * PATCH 片段：仅校验出现的键名；允许 {@code null} 表示删除该键（不入参校验 value）。
     */
    public static void validatePatchKeys(ObjectNode patch) {
        if (patch == null) {
            throw new IllegalArgumentException("profile must be a JSON object");
        }
        var it = patch.fields();
        while (it.hasNext()) {
            var e = it.next();
            String key = e.getKey();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException("invalid profile key (use [a-zA-Z][a-zA-Z0-9_]{0,63}): " + key);
            }
            JsonNode v = e.getValue();
            if (v != null && !v.isNull() && !isAllowedScalar(v)) {
                throw new IllegalArgumentException("unsupported profile value type for key '" + key + "'");
            }
        }
    }

    private static boolean isAllowedScalar(JsonNode v) {
        return v.isBoolean() || v.isIntegralNumber() || v.isFloatingPointNumber() || v.isTextual();
    }
}
