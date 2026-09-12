package com.learnplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedisConfigTest {

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
