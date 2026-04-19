package com.travel.ai.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationIdValidatorTest {

    @Test
    void acceptsAlphanumericDashUnderscore() {
        assertThat(ConversationIdValidator.isValid("demo-conv")).isTrue();
        assertThat(ConversationIdValidator.isValid("c1")).isTrue();
        assertThat(ConversationIdValidator.isValid("a_b-1")).isTrue();
    }

    @Test
    void rejectsBlankTooLongAndUnsafe() {
        assertThat(ConversationIdValidator.isValid(null)).isFalse();
        assertThat(ConversationIdValidator.isValid("")).isFalse();
        assertThat(ConversationIdValidator.isValid("   ")).isFalse();
        assertThat(ConversationIdValidator.isValid("a/b")).isFalse();
        assertThat(ConversationIdValidator.isValid("a..b")).isFalse();
        assertThat(ConversationIdValidator.isValid("x@y")).isFalse();
        assertThat(ConversationIdValidator.isValid("a b")).isFalse();
        assertThat(ConversationIdValidator.isValid("a\\b")).isFalse();
        assertThat(ConversationIdValidator.isValid("a".repeat(ConversationIdValidator.MAX_LEN + 1))).isFalse();
    }
}
