package com.learnplatform.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.junit.jupiter.api.Assertions.assertNull;

class CacheEvictServiceTest {
    @Test
    void evictsStatisticsUsingTheCacheableLongKey() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "statistics", "dailyTrend", "courseStats", "learningReport", "learningDiagnosis");
        cacheManager.getCache("statistics").put(7L, "stale");
        cacheManager.getCache("learningDiagnosis").put(7L, "stale");

        new CacheEvictService(cacheManager).evictUserStatistics(7L);

        assertNull(cacheManager.getCache("statistics").get(7L));
        assertNull(cacheManager.getCache("learningDiagnosis").get(7L));
    }
}
