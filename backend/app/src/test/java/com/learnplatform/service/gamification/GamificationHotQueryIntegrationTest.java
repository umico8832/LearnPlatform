package com.learnplatform.service.gamification;

import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration") @SpringBootTest @ActiveProfiles("integration") @Transactional
class GamificationHotQueryIntegrationTest extends IntegrationTestBase {
    @Autowired JdbcTemplate db;

    @Test void coveringRangeSkipsZeroRewardAuditRowsAndExistenceStopsAtFirstMatch() throws Exception {
        long user = 987070L;
        var rows = new ArrayList<Object[]>();
        for (int i = 0; i < 5000; i++) {
            rows.add(new Object[] {user, 9700000L + i, i == 0 ? 10 : 0});
        }
        db.batchUpdate("""
                INSERT INTO gamification_xp_ledger(user_id,event_id,course_id,subject_type,subject_id,
                event_type,event_date,xp,reward_reason,occurred_time)
                VALUES(?,?,1,'QUESTION',1,'PRACTICE_ANSWERED','2026-10-23',?,'TEST','2026-10-23 10:00:00')
                """, rows);
        String subject = " WHERE user_id=" + user
                + " AND course_id=1 AND subject_type='QUESTION' AND subject_id=1 AND event_date='2026-10-23'";
        String count = "SELECT COUNT(*) FROM gamification_xp_ledger" + subject;
        String exists = "SELECT EXISTS(SELECT 1 FROM gamification_xp_ledger" + subject + ")";
        String positive = " WHERE user_id=" + user + " AND event_date='2026-10-23' AND xp>0";
        String originalPositive = "SELECT COUNT(*) FROM gamification_xp_ledger FORCE INDEX"
                + " (idx_gamification_xp_user_date)" + positive;
        String coveredPositive = "SELECT COUNT(*) FROM gamification_xp_ledger" + positive;
        assertEquals(5000, db.queryForObject(count, Integer.class));
        assertEquals(true, db.queryForObject(exists, Boolean.class));
        assertEquals(1, db.queryForObject(originalPositive, Integer.class));
        assertEquals(1, db.queryForObject(coveredPositive, Integer.class));
        String plan = db.queryForObject("EXPLAIN FORMAT=JSON " + coveredPositive, String.class);
        assertTrue(plan.contains("idx_gamification_xp_user_date_xp"), plan);
        assertTrue(plan.contains("using_index"), plan);
        var output = Path.of("target", "performance", "gamification-explain.txt");
        Files.createDirectories(output.getParent());
        var evidence = new StringBuilder("Fixture: 5000 same-user/day/subject ledger rows; 1 positive, 4999 zero.\n");
        for (String sql : new String[] {count, exists, originalPositive, coveredPositive}) {
            evidence.append(sql).append('\n')
                    .append(db.queryForObject("EXPLAIN ANALYZE " + sql, String.class)).append("\n\n");
        }
        Files.writeString(output, evidence.toString());
        assertEquals(5000, db.queryForObject(count, Integer.class), "Optimization must retain every audit row");
    }
}
