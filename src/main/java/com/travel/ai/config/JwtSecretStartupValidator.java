package com.travel.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * JWT 签名密钥在「类生产」环境下的启动期校验。
 * <p>
 * 设计意图（与 {@code docs/UPGRADE_PLAN.md} 中 P0-2 一致）：
 * <ul>
 *   <li><b>宽松环境</b>（profile 含 {@code test} / {@code local} / {@code dev}，或<b>未激活</b>任何 profile）：
 *       允许使用默认占位密钥，仅打 WARN，方便本机 IDE 直接启动。</li>
 *   <li><b>严格环境</b>（profile 含 {@code docker} / {@code prod} / {@code production}）：
 *       密钥不可为空、长度须 ≥32，且不能为默认占位串，否则立即失败退出，避免容器/线上误用弱密钥。</li>
 * </ul>
 * Docker Compose 中为 app 服务设置 {@code SPRING_PROFILES_ACTIVE=docker}，即可在容器运行时走严格校验（无需额外 yml 文件）。
 */
@Component
@Order(Integer.MIN_VALUE)
public class JwtSecretStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretStartupValidator.class);

    /** 与 README 中「建议 ≥32 字节」对齐：按字符长度校验（UTF-8 下绝大多数场景等价于「足够熵」的底线） */
    private static final int MIN_STRICT_LENGTH = 32;

    /** application.yml 中的默认占位值，严禁在 docker/prod 等环境使用 */
    private static final String WEAK_PLACEHOLDER = "change-me-in-local";

    private final Environment environment;

    @Value("${app.security.jwt.secret:}")
    private String jwtSecret;

    public JwtSecretStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        boolean strict = isStrictProfileActive();

        if (strict) {
            validateStrict(secret);
        } else {
            warnIfWeak(secret);
        }
    }

    /**
     * 是否启用严格校验：docker（Compose 默认）/ prod / production 任一激活即严格。
     */
    private boolean isStrictProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> {
                    String x = p.toLowerCase();
                    return "docker".equals(x) || "prod".equals(x) || "production".equals(x);
                });
    }

    /**
     * 严格模式：不满足条件则抛异常，Spring Boot 会中止启动并打印堆栈中的消息。
     */
    private void validateStrict(String secret) {
        if (secret.isEmpty()) {
            throw new IllegalStateException(
                    "[JWT] 当前激活了 docker/prod/production 等 profile，但 app.security.jwt.secret（APP_JWT_SECRET）为空。"
                            + "请在环境变量或配置中设置长度至少 " + MIN_STRICT_LENGTH + " 的随机密钥。");
        }
        if (secret.length() < MIN_STRICT_LENGTH) {
            throw new IllegalStateException(
                    "[JWT] 当前为严格环境，JWT 密钥长度过短（" + secret.length() + "），至少需要 " + MIN_STRICT_LENGTH
                            + " 个字符。请设置更长的 APP_JWT_SECRET。");
        }
        if (WEAK_PLACEHOLDER.equalsIgnoreCase(secret)) {
            throw new IllegalStateException(
                    "[JWT] 当前为严格环境，不能使用默认占位密钥 \"" + WEAK_PLACEHOLDER + "\"。"
                            + "请为 APP_JWT_SECRET 设置强随机串（建议 32 字符以上）。");
        }
        log.info("[JWT] 严格环境：签名密钥已通过启动期校验（长度={}）。", secret.length());
    }

    /**
     * 宽松模式：弱密钥仅告警，不阻断本地开发。
     */
    private void warnIfWeak(String secret) {
        if (secret.isEmpty() || secret.length() < MIN_STRICT_LENGTH || WEAK_PLACEHOLDER.equalsIgnoreCase(secret)) {
            log.warn(
                    "[JWT] 当前未处于 docker/prod/production profile，JWT 密钥偏弱或未达 {} 字符。"
                            + "仅适用于本地开发；Docker Compose 或线上请使用 profile=docker 等并配置强 APP_JWT_SECRET。",
                    MIN_STRICT_LENGTH);
        }
    }
}
