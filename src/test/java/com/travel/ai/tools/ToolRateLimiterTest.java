package com.travel.ai.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRateLimiterTest {

    @Test
    void enforcesPerMinuteLimitWithinSameBucket() {
        ToolRateLimiter rl = new ToolRateLimiter(2);
        String tool = "weather";
        long ms = 123_456_789L;

        assertTrue(rl.tryAcquireAt(tool, ms));
        assertTrue(rl.tryAcquireAt(tool, ms + 1000L));
        assertFalse(rl.tryAcquireAt(tool, ms + 2000L));
    }

    @Test
    void newMinuteBucketResetsCount() {
        ToolRateLimiter rl = new ToolRateLimiter(1);
        String tool = "weather";
        long minute0 = 60_000L * 1000L;
        long minute1 = minute0 + 60_000L;

        assertTrue(rl.tryAcquireAt(tool, minute0));
        assertFalse(rl.tryAcquireAt(tool, minute0 + 1L));

        assertTrue(rl.tryAcquireAt(tool, minute1));
    }
}
