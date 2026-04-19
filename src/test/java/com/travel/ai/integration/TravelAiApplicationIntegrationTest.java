package com.travel.ai.integration;

import com.travel.ai.TravelAiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 3 Day 4：用 Testcontainers 拉起 Postgres（pgvector 镜像）与 Redis，
 * 验证 Spring 上下文、Flyway 迁移、Redis、匿名 health 探活，以及登录与鉴权边界（升级 P4-2）。
 * <p>
 * 需本机 Docker 可用；无 Docker 时本类会失败（符合集成测试预期）。
 */
@SpringBootTest(classes = TravelAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class TravelAiApplicationIntegrationTest {

    private static final DockerImageName PGVECTOR =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR)
            .withDatabaseName("ragent")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    @Test
    void flywayAppliedVectorStore() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(migrations).isNotNull().isGreaterThanOrEqualTo(1);

        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vector_store'",
                Integer.class);
        assertThat(tables).isEqualTo(1);
    }

    @Test
    void redisReadWrite() {
        stringRedisTemplate.opsForValue().set("tc:ping", "ok");
        assertThat(stringRedisTemplate.opsForValue().get("tc:ping")).isEqualTo("ok");
    }

    @Test
    void actuatorHealthAnonymousUp() {
        ResponseEntity<String> res = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull().contains("UP");
    }

    /**
     * 演示账号登录应返回 JWT；不依赖外网 DashScope，仅测 AuthController + Security 链。
     */
    @Test
    void loginWithDemoCredentialsReturnsToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"demo\",\"password\":\"demo123\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> res = restTemplate.postForEntity("/auth/login", entity, String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull().contains("token");
    }

    /**
     * 收紧 anyRequest().authenticated() 后：未带 Token 访问受保护业务接口应 401。
     */
    @Test
    void travelChatWithoutTokenReturns401() {
        ResponseEntity<String> res = restTemplate.getForEntity("/travel/chat/c1?query=hi", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void evalChatWithoutGatewayKeyReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"query\":\"ping\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> res = restTemplate.postForEntity("/api/v1/eval/chat", entity, String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).isNotNull().contains("EVAL_GATEWAY");
    }

    @Test
    void evalChatWithGatewayKeyReturns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Eval-Gateway-Key", "it-eval-gateway-key");
        String body = "{\"query\":\"ping\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> res = restTemplate.postForEntity("/api/v1/eval/chat", entity, String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull().contains("\"behavior\"");
    }
}
