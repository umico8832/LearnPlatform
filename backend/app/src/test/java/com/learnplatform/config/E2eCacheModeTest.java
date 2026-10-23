package com.learnplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class E2eCacheModeTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=e2e")
            .withUserConfiguration(RedisConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    void e2eProfileUsesRedisWhenComposeRequestsIt() {
        context.withPropertyValues("CACHE_TYPE=redis").run(application ->
                assertThat(application.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class));
    }

    @Test
    void e2eProfileHonorsAnExplicitSimpleOverride() {
        context.withPropertyValues("CACHE_TYPE=simple").run(application ->
                assertThat(application.getBean(CacheManager.class)).isInstanceOf(ConcurrentMapCacheManager.class));
    }
}
