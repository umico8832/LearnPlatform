package com.learnplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RedisConfigTest {

    @Test
    void redisModeKeepsTtlsStringKeysNullPolicyAndTransactionAwareness() {
        var ttl = new CacheTtlProperties();
        ttl.setTtl(Map.of("learningPath", Duration.ofMinutes(17)));
        var manager = (RedisCacheManager) new RedisConfig().redisCacheManager(
                mock(RedisConnectionFactory.class), ttl);
        manager.afterPropertiesSet();
        assertThat(manager.isTransactionAware()).isTrue();
        var minutes = Map.ofEntries(
                Map.entry("statistics", 5), Map.entry("adminStatistics", 3),
                Map.entry("dailyTrend", 5), Map.entry("courseStats", 5),
                Map.entry("learningReport", 10), Map.entry("learningPath", 17),
                Map.entry("knowledgeGraph", 10), Map.entry("learningDiagnosis", 10),
                Map.entry("submissionQuality", 30), Map.entry("submissionKPTagging", 30),
                Map.entry("submissionDifficulty", 30), Map.entry("questionReviewSuggestion", 30),
                Map.entry("globalSearch", 5));
        assertThat(manager.getCacheNames()).containsExactlyInAnyOrderElementsOf(minutes.keySet());
        minutes.forEach((name, duration) -> {
            var cache = (RedisCache) ((TransactionAwareCacheDecorator) manager.getCache(name)).getTargetCache();
            var config = cache.getCacheConfiguration();
            assertThat(config.getTtlFunction().getTimeToLive("key", "value"))
                    .isEqualTo(Duration.ofMinutes(duration));
            assertThat(config.getAllowCacheNullValues()).isFalse();
            assertThat(config.getKeyPrefixFor(name)).isEqualTo("v2::" + name + "::");
            assertThat(config.getKeySerializationPair().read(
                    ByteBuffer.wrap("key".getBytes(StandardCharsets.UTF_8)))).isEqualTo("key");
        });
    }

    @Test
    void simpleModeDoesNotRetainAdminStatisticsWithoutATtl() {
        var manager = new RedisConfig().simpleCacheManager();
        var admin = manager.getCache("adminStatistics");
        assertNotNull(admin);
        admin.put("overview", "stale");
        assertNull(admin.get("overview"), "Non-expiring local cache must not retain global admin statistics");
        manager.getCache("statistics").put(7L, "user cache remains enabled");
        assertNotNull(manager.getCache("statistics").get(7L));
    }

    @Test
    void simpleCacheManager_registersApplicationCacheNames() {
        CacheManager cacheManager = new RedisConfig().simpleCacheManager();

        assertNotNull(cacheManager.getCache("statistics"));
        assertNotNull(cacheManager.getCache("dailyTrend"));
        assertNotNull(cacheManager.getCache("courseStats"));
        assertNotNull(cacheManager.getCache("adminStatistics"));
        assertNotNull(cacheManager.getCache("globalSearch"));
    }
}
