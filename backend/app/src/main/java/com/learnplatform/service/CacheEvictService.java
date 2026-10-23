package com.learnplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class CacheEvictService {
    private static final Logger log = LoggerFactory.getLogger(CacheEvictService.class);
    private final CacheManager cacheManager;

    public CacheEvictService(CacheManager cacheManager) { this.cacheManager = cacheManager; }

    public void evictUserStatistics(Long userId) { evictUserStatistics(userId, null); }

    public void evictUserStatistics(Long userId, Long courseId) {
        if (userId == null) { return; }
        for (String name : new String[] {"statistics", "dailyTrend", "courseStats",
                "learningReport", "learningDiagnosis"}) {
            enqueue(new Eviction(name, userId, false));
        }
        for (String name : new String[] {"learningPath", "knowledgeGraph"}) {
            enqueue(new Eviction(name, userId + ":null", false));
            if (courseId != null) { enqueue(new Eviction(name, userId + ":" + courseId, false)); }
        }
    }

    public void evictAdminStatistics() { enqueue(new Eviction("adminStatistics", null, true)); }

    public void evictLearningDiagnosisAfterCommit() { enqueue(new Eviction("learningDiagnosis", null, true)); }

    private void enqueue(Eviction eviction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            apply(eviction);
            return;
        }
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PendingEvictions pending && pending.owner == this) {
                pending.evictions.add(eviction);
                return;
            }
        }
        var pending = new PendingEvictions(this);
        pending.evictions.add(eviction);
        TransactionSynchronizationManager.registerSynchronization(pending);
    }

    private void apply(Eviction eviction) {
        try {
            var cache = cacheManager.getCache(eviction.cache());
            if (cache == null) { return; }
            // Immediate variants avoid deferring again inside a transaction-aware cache's afterCommit callback.
            if (eviction.all()) {
                cache.invalidate();
            } else {
                cache.evictIfPresent(eviction.key());
            }
        } catch (RuntimeException exception) {
            log.warn("Cache invalidation failed: cache={}", eviction.cache());
        }
    }

    private record Eviction(String cache, Object key, boolean all) { }

    private static final class PendingEvictions implements TransactionSynchronization {
        private final CacheEvictService owner;
        private final Set<Eviction> evictions = new LinkedHashSet<>();

        private PendingEvictions(CacheEvictService owner) { this.owner = owner; }

        @Override public void afterCommit() { evictions.forEach(owner::apply); }
    }
}
