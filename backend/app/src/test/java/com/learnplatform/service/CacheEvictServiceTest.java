package com.learnplatform.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheEvictServiceTest {
    @Test
    void answeringOneUsersQuestionRetainsAnotherUsersCourseCaches() {
        var manager = new ConcurrentMapCacheManager("learningPath", "knowledgeGraph");
        for (String name : new String[] {"learningPath", "knowledgeGraph"}) {
            manager.getCache(name).put("7:10", "stale");
            manager.getCache(name).put("8:10", "unaffected");
            manager.getCache(name).put("7:11", "unaffected course");
            manager.getCache(name).put("7:null", "stale aggregate");
        }
        new CacheEvictService(manager).evictUserStatistics(7L, 10L);
        assertNull(manager.getCache("learningPath").get("7:10"));
        assertNull(manager.getCache("learningPath").get("7:null"));
        assertNotNull(manager.getCache("learningPath").get("7:11"));
        assertNotNull(manager.getCache("learningPath").get("8:10"));
        assertNotNull(manager.getCache("knowledgeGraph").get("8:10"));
    }

    @Test
    void retainsUserStatisticsUntilTheTransactionCommitsOrRollsBack() {
        var manager = new ConcurrentMapCacheManager("statistics");
        manager.getCache("statistics").put(7L, "before");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            new CacheEvictService(manager).evictUserStatistics(7L);
            assertNotNull(manager.getCache("statistics").get(7L));
            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertNotNull(manager.getCache("statistics").get(7L));
        } finally { TransactionSynchronizationManager.clear(); }
    }

    @Test
    void commitsOneDeduplicatedInvalidationWithoutDeferringItAgain() {
        var target = new ConcurrentMapCacheManager("statistics", "learningPath", "knowledgeGraph");
        var manager = new TransactionAwareCacheManagerProxy(target);
        manager.getCache("statistics").put(7L, "before");
        manager.getCache("learningPath").put("7:10", "before");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            var service = new CacheEvictService(manager);
            service.evictUserStatistics(7L, 10L);
            service.evictUserStatistics(7L, 10L);
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            assertNotNull(target.getCache("statistics").get(7L));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertNull(target.getCache("statistics").get(7L));
            assertNull(target.getCache("learningPath").get("7:10"));
        } finally { TransactionSynchronizationManager.clear(); }
    }

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
