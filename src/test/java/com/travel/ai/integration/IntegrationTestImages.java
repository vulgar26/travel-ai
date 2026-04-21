package com.travel.ai.integration;

import org.testcontainers.utility.DockerImageName;

/**
 * Central place for Testcontainers image names used by Spring Boot integration tests.
 * <p>
 * When Docker Hub is slow or unreachable (DNS, firewall, corporate proxy), set
 * {@code TRAVEL_AI_TEST_REDIS_IMAGE} and/or {@code TRAVEL_AI_TEST_POSTGRES_IMAGE} to a mirror
 * or private registry path, or pre-pull the default images while the network works.
 */
final class IntegrationTestImages {

    private IntegrationTestImages() {
    }

    static DockerImageName redis() {
        return DockerImageName.parse(
                firstNonBlank(
                        System.getenv("TRAVEL_AI_TEST_REDIS_IMAGE"),
                        System.getProperty("travelai.test.redis-image"),
                        "redis:7-alpine"));
    }

    static DockerImageName postgresPgvector() {
        String raw = firstNonBlank(
                System.getenv("TRAVEL_AI_TEST_POSTGRES_IMAGE"),
                System.getProperty("travelai.test.postgres-image"),
                "pgvector/pgvector:pg16");
        return DockerImageName.parse(raw).asCompatibleSubstituteFor("postgres");
    }

    private static String firstNonBlank(String env, String prop, String fallback) {
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return fallback;
    }
}
