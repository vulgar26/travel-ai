package com.travel.ai.web;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class JsonApiErrorSupportTest {

    @Test
    void write_rateLimitedUsesErrorKeyNotCode() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        JsonApiErrorSupport.write(
                resp,
                HttpServletResponse.SC_TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                "请求过于频繁，请稍后再试");
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getContentAsString()).contains("\"error\":\"RATE_LIMITED\"");
        assertThat(resp.getContentAsString()).contains("\"message\":\"请求过于频繁，请稍后再试\"");
        assertThat(resp.getContentAsString()).doesNotContain("\"code\"");
    }
}
