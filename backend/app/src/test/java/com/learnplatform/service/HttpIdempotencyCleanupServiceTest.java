package com.learnplatform.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import com.learnplatform.common.time.BusinessTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpIdempotencyCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-23T16:30:00Z");
    private static final LocalDateTime NEW_YORK_NOW = LocalDateTime.of(2026, 10, 23, 12, 30);

    @Mock private JdbcTemplate jdbc;

    @Test
    void deletesOnlyOneBoundedBatchStrictlyOlderThanTheSevenDayReplayWindow() {
        when(jdbc.update(any(String.class), any(Timestamp.class))).thenReturn(1000);
        var service = new HttpIdempotencyCleanupService(jdbc, Clock.fixed(NOW, BusinessTime.ZONE));

        int deleted = service.cleanupExpiredRecords();

        var sql = ArgumentCaptor.forClass(String.class);
        var cutoff = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbc).update(sql.capture(), cutoff.capture());
        assertEquals(1000, deleted);
        assertEquals(Timestamp.valueOf(NEW_YORK_NOW.minusDays(7)), cutoff.getValue());
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("create_time < ?"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("LIMIT 1000"));
    }

    @Test
    void doesNotRunAnotherBatchWhenAFullBatchWasDeleted() {
        when(jdbc.update(contains("http_idempotency_record"), any(Timestamp.class))).thenReturn(1000);
        var service = new HttpIdempotencyCleanupService(jdbc, Clock.fixed(NOW, BusinessTime.ZONE));

        service.cleanupExpiredRecords();

        verify(jdbc).update(any(String.class), any(Timestamp.class));
    }
}
