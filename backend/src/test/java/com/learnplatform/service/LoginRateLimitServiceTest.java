package com.learnplatform.service;

import com.learnplatform.security.LoginRateLimitService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimitServiceTest {

    @Test
    void shouldNotBlockWhenNoFailures() {
        LoginRateLimitService service = new LoginRateLimitService(5, 60);
        assertFalse(service.isBlocked("127.0.0.1"));
    }

    @Test
    void shouldBlockAfterMaxAttempts() {
        LoginRateLimitService service = new LoginRateLimitService(3, 60);
        String ip = "10.0.0.1";

        service.recordFailure(ip);
        service.recordFailure(ip);
        assertFalse(service.isBlocked(ip));

        service.recordFailure(ip);
        assertTrue(service.isBlocked(ip));
    }

    @Test
    void shouldClearRecordOnSuccess() {
        LoginRateLimitService service = new LoginRateLimitService(3, 60);
        String ip = "10.0.0.2";

        service.recordFailure(ip);
        service.recordFailure(ip);
        assertTrue(service.isBlocked(ip) == false); // not yet blocked

        service.recordFailure(ip);
        assertTrue(service.isBlocked(ip));

        service.clearRecord(ip);
        assertFalse(service.isBlocked(ip));
    }

    @Test
    void shouldReturnRemainingBlockSeconds() {
        LoginRateLimitService service = new LoginRateLimitService(2, 120);
        String ip = "10.0.0.3";

        service.recordFailure(ip);
        service.recordFailure(ip);
        assertTrue(service.isBlocked(ip));

        long remaining = service.getRemainingBlockSeconds(ip);
        // should be roughly 120 seconds, allow some slack
        assertTrue(remaining > 115 && remaining <= 120, "Remaining seconds: " + remaining);
    }

    @Test
    void shouldReturnZeroWhenNotBlocked() {
        LoginRateLimitService service = new LoginRateLimitService(5, 60);
        assertEquals(0, service.getRemainingBlockSeconds("10.0.0.4"));
    }

    @Test
    void shouldTrackDifferentIpsIndependently() {
        LoginRateLimitService service = new LoginRateLimitService(3, 60);

        // IP A: 3 failures -> blocked
        service.recordFailure("10.0.1.1");
        service.recordFailure("10.0.1.1");
        service.recordFailure("10.0.1.1");
        assertTrue(service.isBlocked("10.0.1.1"));

        // IP B: 1 failure -> not blocked
        service.recordFailure("10.0.1.2");
        assertFalse(service.isBlocked("10.0.1.2"));
    }
}
