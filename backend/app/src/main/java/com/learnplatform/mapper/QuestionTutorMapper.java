package com.learnplatform.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class QuestionTutorMapper {
    private final JdbcTemplate db;

    public QuestionTutorMapper(JdbcTemplate db) { this.db = db; }

    public boolean completeReviewedHints(long contentId) {
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM question_understanding_hint
                WHERE content_id=? AND hint_level BETWEEN 1 AND 3 AND review_status='REVIEWED'
                """, Integer.class, contentId);
        return count != null && count == 3;
    }

    public Map<String, Object> reviewedHint(long contentId, int level) {
        return one("""
                SELECT id,hint_level,hint_text,content_source,review_status
                FROM question_understanding_hint
                WHERE content_id=? AND hint_level=? AND review_status='REVIEWED'
                """, contentId, level);
    }

    public List<Map<String, Object>> previewHints(long contentId) {
        return db.queryForList("""
                SELECT hint_level,hint_text,content_source,review_status FROM question_understanding_hint
                WHERE content_id=? AND hint_level BETWEEN 1 AND 3 AND review_status IN ('PENDING','REVIEWED')
                ORDER BY hint_level
                """, contentId);
    }

    public Map<String, Object> receipt(long attemptId, String requestId) {
        return one("""
                SELECT h.hint_level,h.hint_text,h.content_source,h.review_status
                FROM question_understanding_hint_receipt r
                JOIN question_understanding_hint h ON h.id=r.hint_id
                WHERE r.attempt_id=? AND r.request_id=? AND h.review_status='REVIEWED'
                """, attemptId, requestId);
    }

    public int highestLevel(long attemptId) {
        Integer level = db.queryForObject("""
                SELECT COALESCE(MAX(hint_level),0) FROM question_understanding_hint_receipt WHERE attempt_id=?
                """, Integer.class, attemptId);
        return level == null ? 0 : level;
    }

    public void recordHint(long attemptId, long hintId, int level, String requestId) {
        db.update("""
                INSERT INTO question_understanding_hint_receipt(attempt_id,hint_id,hint_level,request_id)
                VALUES(?,?,?,?)
                """, attemptId, hintId, level, requestId);
    }

    public boolean viewed(long attemptId, String context) {
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM question_understanding_viz_view WHERE attempt_id=? AND context=?
                """, Integer.class, attemptId, context);
        return count != null && count > 0;
    }

    public void recordView(long attemptId, String context) {
        db.update("""
                INSERT INTO question_understanding_viz_view(attempt_id,context)
                VALUES(?,?) ON DUPLICATE KEY UPDATE id=id
                """, attemptId, context);
    }

    public boolean variantAttempted(long attemptId) {
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM question_understanding_variant_attempt WHERE origin_attempt_id=?
                """, Integer.class, attemptId);
        return count != null && count > 0;
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = db.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
