package com.travel.ai.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalMembershipHasherTest {

    @Test
    void kCaseAndHitHashAreDeterministicLowerHex64() throws Exception {
        String token = "x-eval-token-unit-test-32bytes!!!";
        byte[] kCase = RetrievalMembershipHasher.deriveKCase(token, "travel-ai", "ds_1", "case_1");
        String h = RetrievalMembershipHasher.hitIdHashHex(kCase, "doc-chunk-a");
        assertEquals(64, h.length());
        assertTrue(RetrievalMembershipHasher.isValidMembershipHashHex(h));
        assertEquals(h, RetrievalMembershipHasher.hitIdHashHex(kCase, "doc-chunk-a"));
    }

    @Test
    void sortedHashesOrderStable() {
        String token = "x-eval-token-unit-test-32bytes!!!";
        List<String> a = RetrievalMembershipHasher.sortedHitIdHashes(
                token, "travel-ai", "ds_1", "case_1", List.of("z-id", "a-id"));
        List<String> b = RetrievalMembershipHasher.sortedHitIdHashes(
                token, "travel-ai", "ds_1", "case_1", List.of("a-id", "z-id"));
        assertEquals(a, b);
    }
}
