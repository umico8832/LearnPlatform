package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("integration") @SpringBootTest @ActiveProfiles("integration")
class CacheCommitIntegrationTest extends IntegrationTestBase {
    @Autowired CacheEvictService invalidations;
    @Autowired CacheManager manager;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate db;

    @Test void aRealCommitEvictsOnlyTheAffectedUserCourseAndAggregateWhileRollbackKeepsValues() {
        long user = 987401L;
        for (String name : new String[] {"learningPath", "knowledgeGraph"}) {
            manager.getCache(name).put(user + ":1", "before");
            manager.getCache(name).put(user + ":null", "before");
            manager.getCache(name).put(user + ":2", "other course");
            manager.getCache(name).put("987402:1", "other user");
        }
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            assertEquals(1, db.queryForObject("SELECT 1", Integer.class));
            invalidations.evictUserStatistics(user, 1L);
            assertNotNull(manager.getCache("learningPath").get(user + ":1"));
            status.setRollbackOnly();
        });
        assertNotNull(manager.getCache("learningPath").get(user + ":1"));
        tx.executeWithoutResult(status -> {
            invalidations.evictUserStatistics(user, 1L);
            assertNotNull(manager.getCache("learningPath").get(user + ":1"));
        });
        for (String name : new String[] {"learningPath", "knowledgeGraph"}) {
            assertNull(manager.getCache(name).get(user + ":1"));
            assertNull(manager.getCache(name).get(user + ":null"));
            assertNotNull(manager.getCache(name).get(user + ":2"));
            assertNotNull(manager.getCache(name).get("987402:1"));
        }
    }

    @Test void requiresNewCommitDoesNotJoinAnOuterTransactionsPendingInvalidations() {
        var cache = manager.getCache("statistics");
        cache.put(987403L, "outer");
        cache.put(987404L, "inner");
        var outer = new TransactionTemplate(transactions);
        var inner = new TransactionTemplate(transactions);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            invalidations.evictUserStatistics(987403L, 1L);
            inner.executeWithoutResult(nested -> invalidations.evictUserStatistics(987404L, 1L));
            assertNull(cache.get(987404L));
            assertNotNull(cache.get(987403L));
            status.setRollbackOnly();
        });
        assertNotNull(cache.get(987403L));
        assertNull(cache.get(987404L));
    }
}
