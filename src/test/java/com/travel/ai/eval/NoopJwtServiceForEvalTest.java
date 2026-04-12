package com.travel.ai.eval;

import com.travel.ai.security.JwtService;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

/**
 * 无 Mockito、无字节码增强：供 {@link EvalChatControllerTest} 等在 JDK 25+ 下替代 {@code Mockito.mock(JwtService.class)}。
 */
public class NoopJwtServiceForEvalTest extends JwtService {

    @Override
    public String extractUsername(String token) {
        return "eval-webmvc-test";
    }

    @Override
    public String generateToken(UserDetails userDetails) {
        return "noop-token";
    }

    @Override
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return "noop-token";
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        return false;
    }
}
