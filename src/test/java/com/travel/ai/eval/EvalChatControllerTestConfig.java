package com.travel.ai.eval;

import com.travel.ai.security.JwtService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 仅为 {@link EvalChatControllerTest} 提供 {@link JwtService} Bean。
 * <p>
 * {@code @WebMvcTest} 会拉起 {@link com.travel.ai.security.SecurityConfig} 与 {@link com.travel.ai.security.JwtAuthFilter}，
 * 但不会加载被切片排除的真实 {@code @Service JwtService}。{@code @MockBean} 在部分环境下注册顺序偏晚会导致仍报
 * {@code NoSuchBeanDefinitionException}；用 {@code @TestConfiguration + @Bean} 显式注册可稳定通过上下文启动。
 */
@TestConfiguration
public class EvalChatControllerTestConfig {

    @Bean
    JwtService jwtService() {
        return Mockito.mock(JwtService.class);
    }
}
