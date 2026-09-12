package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.service.ai.AiCallContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@SpringBootTest(classes = AiModelCallIntegrationTest.Configuration.class)
@ActiveProfiles("integration")
class AiModelCallIntegrationTest extends IntegrationTestBase {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
            org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class})
    @org.mybatis.spring.annotation.MapperScan("com.learnplatform.mapper")
    @org.springframework.context.annotation.Import({AiCallGovernanceService.class, AiCallReservationService.class,
            AiCallLogQueryService.class, com.learnplatform.config.AiConfig.class})
    static class Configuration { }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AiCallGovernanceService governance;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private AiCallLogQueryService query;
    private Long userId;
    private final ModelRequest request = ModelRequest.text("system", "user", new ModelRequest.Options("test", 20, 0.7));

    @BeforeEach void createUser() {
        String name = "ai_quota_" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO user (username, password, ai_daily_quota) VALUES (?, 'test-only', 2)", name);
        userId = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, name);
    }
    @AfterEach void removeFixture() {
        jdbc.update("DELETE FROM ai_call_log WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user WHERE id = ?", userId);
    }

    @Test void simultaneousReservationsCannotOvershootUserQuota() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    try {
                        governance.begin(new AiCallContext(userId, "quota_test", null), request);
                        return true;
                    } catch (BusinessException exception) {
                        assertEquals(1006, exception.getCode());
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var future : futures) { if (future.get(20, TimeUnit.SECONDS)) { accepted++; } }
            assertEquals(2, accepted);
            assertEquals(2, governance.countTodayCalls(userId));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_log WHERE user_id = ? AND outcome = 'RUNNING'",
                    Integer.class, userId));
        }
    }

    @Test void outerRollbackDoesNotEraseAnAdmittedCall() {
        var transaction = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            governance.begin(new AiCallContext(userId, "rollback_test", null), request);
            throw new IllegalStateException("domain rollback");
        }));
        assertEquals(1, governance.countTodayCalls(userId));
    }

    @Test void callIdentityIsUniqueAndCompletionDoesNotConsumeQuotaTwice() {
        long failuresBefore = query.getStats().fail();
        var ticket = governance.begin(new AiCallContext(userId, "completion_test", UUID.randomUUID()), request);
        assertEquals(failuresBefore, query.getStats().fail());
        governance.finish(ticket, null, "TIMEOUT", 200);
        assertEquals(1, governance.countTodayCalls(userId));
        assertEquals("TIMEOUT", jdbc.queryForObject("SELECT outcome FROM ai_call_log WHERE call_id = ?",
                String.class, ticket.entry().getCallId()));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO ai_call_log (user_id, function_type, call_id, status) VALUES (?, 'duplicate', ?, 0)",
                userId, ticket.entry().getCallId()));
    }
}
