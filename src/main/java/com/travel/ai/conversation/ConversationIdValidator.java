package com.travel.ai.conversation;

/**
 * 路径变量 {@code conversationId} 的保守校验：防空白、过长与明显路径注入片段。
 */
public final class ConversationIdValidator {

    public static final int MAX_LEN = 128;

    private ConversationIdValidator() {
    }

    public static boolean isValid(String conversationId) {
        if (conversationId == null) {
            return false;
        }
        String t = conversationId.trim();
        if (t.isEmpty() || t.length() > MAX_LEN) {
            return false;
        }
        if (t.contains("..") || t.contains("/") || t.contains("\\")) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
