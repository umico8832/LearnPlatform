package com.learnplatform.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import com.learnplatform.common.time.BusinessTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Removes expired HTTP idempotency snapshots without extending the replay window. */
@Service
public class HttpIdempotencyCleanupService {
    private static final Logger log = LoggerFactory.getLogger(HttpIdempotencyCleanupService.class);

    private final JdbcTemplate db;
    private final Clock clock;

    @Autowired
    public HttpIdempotencyCleanupService(JdbcTemplate db) {
        this(db, BusinessTime.clock());
    }

    HttpIdempotencyCleanupService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /**
     * Each invocation deliberately handles one batch only. The strict boundary preserves records
     * created exactly seven New York calendar days ago until the next run, so their replay window is intact.
     */
    @Scheduled(fixedDelayString = "${app.http-idempotency.cleanup-delay-ms:300000}")
    public int cleanupExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), BusinessTime.ZONE).minusDays(7);
        int deleted = db.update("""
                DELETE FROM http_idempotency_record
                WHERE create_time < ?
                ORDER BY create_time
                LIMIT 1000
                """, Timestamp.valueOf(cutoff));
        if (deleted > 0) {
            log.info("清理过期 HTTP 幂等快照 {} 条", deleted);
        }
        return deleted;
    }
}
