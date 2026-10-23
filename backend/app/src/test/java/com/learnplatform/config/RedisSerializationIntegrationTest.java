package com.learnplatform.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class RedisSerializationIntegrationTest {
    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory factory;
    private static RedisCacheManager manager;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
        factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        manager = (RedisCacheManager) new RedisConfig().redisCacheManager(factory, new CacheTtlProperties());
        manager.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (factory != null) { factory.destroy(); }
        if (redis != null) { redis.stop(); }
    }

    @TestFactory
    Stream<DynamicTest> everyCacheRetainsItsValueTypesAndFields() {
        return RedisCacheFixtures.values().entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey(), () -> {
                    var cache = manager.getCache(entry.getKey());
                    cache.put("round-trip", entry.getValue());
                    await().untilAsserted(() -> assertThat(cache.get("round-trip")).isNotNull());
                    RedisCacheFixtures.assertSameValue(entry.getValue(), cache.get("round-trip").get());
                }));
    }
}
