package com.travel.ai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import com.travel.ai.web.JsonApiErrorSupport;

/**
 * Spring Security 主配置：JWT 无状态会话 + 白名单放行健康检查与登录。
 * <p>
 * {@code anyRequest().authenticated()}：除 {@code requestMatchers} 中显式 permitAll 的路径外，
 * 一律需要已认证主体，避免后续新增接口默认匿名可访问（升级方案 P1-2）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("demo")
                        .password(passwordEncoder.encode("demo123"))
                        .roles("USER")
                        .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 未认证访问受保护资源时返回 JSON（与评测网关、Knowledge 错误体一致），避免浏览器收到非 JSON 401。
     */
    @Bean
    public AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> JsonApiErrorSupport.write(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "需要有效的 Authorization: Bearer token");
    }

    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> JsonApiErrorSupport.write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "FORBIDDEN",
                "权限不足");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationProvider authenticationProvider,
                                                   EvalGatewayAuthFilter evalGatewayAuthFilter,
                                                   JwtAuthFilter jwtAuthFilter,
                                                   RateLimitingFilter rateLimitingFilter,
                                                   AuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                                   AccessDeniedHandler jsonAccessDeniedHandler) throws Exception {
                                                    
        http.securityMatcher((request) ->
            request.getDispatcherType() == DispatcherType.REQUEST
        );
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 默认策略改为「除白名单外均需认证」，避免新增 Controller 时误被 permitAll 放行（UPGRADE P1-2）
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/auth/login", "/actuator/health/**", "/actuator/info").permitAll()
                        // 评测入口：EvalGatewayAuthFilter 校验 X-Eval-Gateway-Key 后注入主体，须 authenticated()
                        .requestMatchers("/api/v1/eval/**").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                // 须先把 JwtAuthFilter 挂进链，再以它为锚注册 EvalGateway；否则 Boot 6 报 “does not have a registered order”
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(evalGatewayAuthFilter, JwtAuthFilter.class)
                // 限流需要在 JwtAuthFilter 之后，这样才能拿到当前用户信息
                .addFilterAfter(rateLimitingFilter, JwtAuthFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler));

        return http.build();
    }
}

