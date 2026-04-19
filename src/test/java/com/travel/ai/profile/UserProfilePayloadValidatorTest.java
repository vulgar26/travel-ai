package com.travel.ai.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.ai.config.AppMemoryProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfilePayloadValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validateCopy_acceptsScalars() {
        AppMemoryProperties p = new AppMemoryProperties();
        ObjectNode in = objectMapper.createObjectNode();
        in.put("budget", "5000");
        in.put("likesTrain", true);
        in.put("score", 3.5);
        ObjectNode out = UserProfilePayloadValidator.validateCopy(in, p, objectMapper);
        assertThat(out.path("budget").asText()).isEqualTo("5000");
        assertThat(out.path("likesTrain").asBoolean()).isTrue();
        assertThat(out.path("score").asDouble()).isEqualTo(3.5);
    }

    @Test
    void validateCopy_rejectsTooManyKeys() {
        AppMemoryProperties p = new AppMemoryProperties();
        ObjectNode in = objectMapper.createObjectNode();
        for (int i = 0; i < 11; i++) {
            in.put("k" + i, "v");
        }
        assertThatThrownBy(() -> UserProfilePayloadValidator.validateCopy(in, p, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max slots");
    }

    @Test
    void validateCopy_rejectsNestedObject() {
        AppMemoryProperties p = new AppMemoryProperties();
        ObjectNode in = objectMapper.createObjectNode();
        in.set("nested", objectMapper.createObjectNode().put("x", 1));
        assertThatThrownBy(() -> UserProfilePayloadValidator.validateCopy(in, p, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void validatePatchKeys_allowsNullValue() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.putNull("dropMe");
        UserProfilePayloadValidator.validatePatchKeys(patch);
    }
}
