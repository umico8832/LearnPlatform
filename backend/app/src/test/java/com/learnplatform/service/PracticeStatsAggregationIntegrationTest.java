package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class PracticeStatsAggregationIntegrationTest extends IntegrationTestBase {
    @Autowired private PracticeHistoryService history;
    @Autowired private JdbcTemplate db;

    @Test void aggregateKeepsDraftExclusionMissingHistoryAndOriginalRounding() {
        long user = 900122L;
        db.update("""
                INSERT INTO question(id,content,question_type,course_id,visibility)
                VALUES(900122001,'Public','SINGLE_CHOICE',1,'PUBLIC'),
                      (900122002,'Draft','SINGLE_CHOICE',1,'DRAFT'),
                      (900122003,'Private','SINGLE_CHOICE',1,'PRIVATE')
                """);
        db.update("""
                INSERT INTO practice_record(user_id,question_id,user_answer,is_correct)
                VALUES(900122,900122001,'A',1),(900122,900122002,'A',1),
                      (900122,900122003,'B',0),(900122,900122004,'A',1),
                      (900123,900122001,'A',1)
                """);
        var result = history.getUserPracticeStats(user);
        assertEquals(3, result.get("totalAnswered"));
        assertEquals(2, result.get("correctCount"));
        assertEquals(1, result.get("wrongCount"));
        assertEquals(66.7, result.get("correctRate"));
        var empty = history.getUserPracticeStats(900124L);
        assertEquals(0, empty.get("totalAnswered"));
        assertEquals(0, empty.get("correctCount"));
        assertEquals(0.0, empty.get("correctRate"));
        assertEquals(5, db.queryForObject("SELECT COUNT(*) FROM practice_record WHERE user_id IN (900122,900123)", Integer.class));
    }
}
