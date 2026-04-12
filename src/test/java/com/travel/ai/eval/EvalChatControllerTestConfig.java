package com.travel.ai.eval;

import com.travel.ai.security.JwtService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 仅为 {@link EvalChatControllerTest} 提供 {@link JwtService} Bean。
 * <p>
 * {@code @WebMvcTest} 会拉起 {@link com.travel.ai.security.SecurityConfig} 与 {@link com.travel.ai.security.JwtAuthFilter}，
 * 但不会加载被切片排除的真实 {@code @Service JwtService}。使用无 Mockito 的占位实现，避免 JDK 25+ 下 inline mock 初始化失败。
 */
@TestConfiguration
public class EvalChatControllerTestConfig {

    @Bean
    JwtService jwtService() {
        return new NoopJwtServiceForEvalTest();
    }

    @Bean
    MutablePlanRepairModelPort planRepairModelPort() {
        return new MutablePlanRepairModelPort();
    }
}
