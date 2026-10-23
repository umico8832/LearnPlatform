package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.support.MySqlRaceSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.learnplatform.support.MySqlRaceSupport.awaitLatch;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production idempotency transaction boundary against MySQL rather than mock calls. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Import(HttpIdempotencyIntegrationTest.RaceConfiguration.class)
class HttpIdempotencyIntegrationTest extends IntegrationTestBase {
    private static final long USER = 981190L;
    private static final String KEY = "00000000-0000-4000-8000-000000000119";
    @Autowired HttpIdempotencyService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MySqlRaceSupport race;

    @BeforeEach void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS idempotency_test_fact (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, marker VARCHAR(64) NOT NULL)");
        jdbc.update("DELETE FROM idempotency_test_fact WHERE user_id IN (?,?,?,?)", USER, USER + 1, USER + 2, USER + 3);
        jdbc.update("DELETE FROM http_idempotency_record WHERE user_id IN (?,?,?,?)", USER, USER + 1, USER + 2, USER + 3);
    }

    @Test void sameKeyWaitsForCommittedFirstResponseAndWritesOneFact() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1), releaseFirst = new CountDownLatch(1), contenderReady = new CountDownLatch(1);
        AtomicLong firstConnection = new AtomicLong(), contenderConnection = new AtomicLong();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.execute(USER, "practice.submit", KEY, Map.of("answer", "A"), Map.class, () -> {
                firstConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                jdbc.update("INSERT INTO idempotency_test_fact(user_id,marker) VALUES(?,?)", USER, "A");
                firstWritten.countDown();
                try { assertTrue(releaseFirst.await(20, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                return Map.of("marker", "A");
            }));
            awaitLatch(firstWritten);
            var second = pool.submit(() -> {
                var outer = new TransactionTemplate(transactions);
                return outer.execute(status -> {
                    contenderConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                    contenderReady.countDown();
                    return submit(USER, "practice.submit", KEY, "A");
                });
            });
            awaitLatch(contenderReady);
            assertNotEquals(firstConnection.get(), contenderConnection.get());
            race.awaitTransactionLockWait(contenderConnection.get());
            releaseFirst.countDown();
            assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM idempotency_test_fact WHERE user_id=?", USER));
        assertEquals(1, count("SELECT COUNT(*) FROM http_idempotency_record WHERE user_id=?", USER));
    }

    @Test void scopesAndConflictingBodiesRemainIndependent() {
        assertEquals(Map.of("marker", "A"), submit(USER, "practice.submit", KEY, "A"));
        assertThrows(BusinessException.class, () -> submit(USER, "practice.submit", KEY, "B"));
        assertEquals(Map.of("marker", "A"), submit(USER + 1, "practice.submit", KEY, "A"));
        assertEquals(Map.of("marker", "A"), submit(USER, "review.submit", KEY, "A"));
        assertEquals(3, count("SELECT COUNT(*) FROM idempotency_test_fact WHERE user_id IN (?,?)", USER, USER + 1));
    }

    @Test void lockingReplaySeesACommitMadeAfterAnOuterRepeatableReadSnapshot() throws Exception {
        var snapshot = new CountDownLatch(1);
        var published = new CountDownLatch(1);
        var rr = new TransactionTemplate(transactions);
        rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var replay = pool.submit(() -> rr.execute(status -> {
                assertEquals(0, count("SELECT COUNT(*) FROM http_idempotency_record WHERE user_id=?", USER + 2));
                snapshot.countDown();
                try { awaitLatch(published); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                return submit(USER + 2, "rr", KEY, "A");
            }));
            try {
                awaitLatch(snapshot);
                assertEquals(Map.of("marker", "A"), submit(USER + 2, "rr", KEY, "A"));
            } finally { published.countDown(); }
            assertEquals(Map.of("marker", "A"), replay.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM idempotency_test_fact WHERE user_id=?", USER + 2));
    }

    @Test void aContenderWaitsForTheFirstRollbackThenCreatesOneCompleteFact() throws Exception {
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ready = new CountDownLatch(1);
        var firstId = new AtomicLong();
        var secondId = new AtomicLong();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.execute(USER + 3, "rollback", KEY, Map.of("answer", "A"), Map.class, () -> {
                firstId.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                jdbc.update("INSERT INTO idempotency_test_fact(user_id,marker) VALUES(?,?)", USER + 3, "A");
                inserted.countDown();
                try { awaitLatch(release); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                throw new IllegalStateException("rollback fixture");
            }));
            try {
                awaitLatch(inserted);
                var second = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                    secondId.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                    ready.countDown();
                    return submit(USER + 3, "rollback", KEY, "A");
                }));
                awaitLatch(ready);
                assertNotEquals(firstId.get(), secondId.get());
                race.awaitTransactionLockWait(secondId.get());
                release.countDown();
                assertThrows(java.util.concurrent.ExecutionException.class, () -> first.get(30, TimeUnit.SECONDS));
                assertEquals(Map.of("marker", "A"), second.get(30, TimeUnit.SECONDS));
            } finally { release.countDown(); }
        }
        assertEquals(1, count("SELECT COUNT(*) FROM idempotency_test_fact WHERE user_id=?", USER + 3));
        assertEquals(1, count("SELECT COUNT(*) FROM http_idempotency_record WHERE user_id=?", USER + 3));
    }

    @Test void objectFieldOrderDoesNotChangeTheRequestIdentity() throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults().build();
        var first = json.readTree("{\"answer\":\"A\",\"time\":3}");
        var reversed = json.readTree("{\"time\":3,\"answer\":\"A\"}");
        var expected = Map.of("saved", true);
        assertEquals(expected, service.execute(USER, "canonical", KEY, first, Map.class, () -> expected));
        assertEquals(expected, service.execute(USER, "canonical", KEY, reversed, Map.class,
                () -> { fail("Must replay the first result"); return null; }));
    }

    private Map submit(long user, String operation, String key, String marker) {
        return service.execute(user, operation, key, Map.of("answer", marker), Map.class, () -> {
            jdbc.update("INSERT INTO idempotency_test_fact(user_id,marker) VALUES(?,?)", user, marker);
            return Map.of("marker", marker);
        });
    }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    @TestConfiguration static class RaceConfiguration { @Bean MySqlRaceSupport httpIdempotencyRaceSupport(Environment env) { return new MySqlRaceSupport(env); } }
}
