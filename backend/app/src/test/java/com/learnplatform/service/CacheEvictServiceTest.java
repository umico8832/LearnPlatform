package com.learnplatform.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void evictsDiagnosisOnlyAfterCommit() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("learningDiagnosis");
        cacheManager.getCache("learningDiagnosis").put(7L, "stale");
        CacheEvictService service = new CacheEvictService(cacheManager);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.evictLearningDiagnosisAfterCommit();

            assertNotNull(cacheManager.getCache("learningDiagnosis").get(7L));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertNull(cacheManager.getCache("learningDiagnosis").get(7L));
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void retainsDiagnosisWhenTransactionRollsBack() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("learningDiagnosis");
        cacheManager.getCache("learningDiagnosis").put(7L, "stale");
        CacheEvictService service = new CacheEvictService(cacheManager);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.evictLearningDiagnosisAfterCommit();

            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertNotNull(cacheManager.getCache("learningDiagnosis").get(7L));
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void evictsDiagnosisImmediatelyWithoutTransaction() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("learningDiagnosis");
        cacheManager.getCache("learningDiagnosis").put(7L, "stale");

        new CacheEvictService(cacheManager).evictLearningDiagnosisAfterCommit();

        assertNull(cacheManager.getCache("learningDiagnosis").get(7L));
    }
}
