package com.learnplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RedisConfigTest {

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
