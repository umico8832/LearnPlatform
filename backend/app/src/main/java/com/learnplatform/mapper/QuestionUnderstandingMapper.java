package com.learnplatform.mapper;

import java.time.LocalDateTime;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class QuestionUnderstandingMapper {
    private final JdbcTemplate db;

    public QuestionUnderstandingMapper(JdbcTemplate db) {
        this.db = db;
    }

    public Map<String, Object> latestContent(long questionId) {
        return one("""
                SELECT * FROM question_understanding_content
                WHERE question_id=? ORDER BY content_version DESC LIMIT 1
                """, questionId);
    }

    public Map<String, Object> content(long id) {
        return one("SELECT * FROM question_understanding_content WHERE id=?", id);
    }

    public List<Map<String, Object>> options(long questionId) {
        return db.queryForList("""
                SELECT id, option_label, content, is_correct FROM question_option
                WHERE question_id=? AND deleted=0 ORDER BY sort_order,id
                """, questionId);
    }

    public Map<String, Object> misconception(long contentId, long optionId) {
        return one("""
                SELECT * FROM question_understanding_misconception WHERE content_id=? AND option_id=?
                """, contentId, optionId);
    }

    public Map<String, Object> position(long questionId, long pointId) {
        return positions(Map.of(questionId, pointId)).get(questionId);
    }

    public Map<Long, Map<String, Object>> positions(Map<Long, Long> points) {
        if (points.isEmpty()) { return Map.of(); }
        List<Map<String, Object>> rows = new NamedParameterJdbcTemplate(db).queryForList("""
                WITH RECURSIVE ancestors AS (
                  SELECT q.id question_id,k.id knowledge_point_id,k.name knowledge_point_name,
                    c.id course_id,c.name course_name,k.id,k.parent_id,k.name,0 AS depth
                  FROM question q JOIN course c ON c.id=q.course_id AND c.deleted=0 AND c.status=1
                  JOIN question_knowledge_point rel ON rel.question_id=q.id
                  JOIN knowledge_point k ON k.id=rel.knowledge_point_id AND k.deleted=0 AND k.course_id=c.id
                  WHERE q.id IN (:questionIds) AND k.id IN (:pointIds)
                  UNION ALL
                  SELECT a.question_id,a.knowledge_point_id,a.knowledge_point_name,a.course_id,a.course_name,
                    p.id,p.parent_id,p.name,a.depth+1
                  FROM knowledge_point p JOIN ancestors a ON p.id=a.parent_id
                  WHERE p.deleted=0 AND p.course_id=a.course_id AND a.depth<20
                )
                SELECT question_id,knowledge_point_id,knowledge_point_name,course_id,course_name,name chapter_name
                FROM ancestors WHERE parent_id IS NULL OR parent_id=0 ORDER BY depth
                """, Map.of("questionIds", points.keySet(), "pointIds", points.values()));
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long questionId = number(row, "question_id");
            if (points.get(questionId).equals(number(row, "knowledge_point_id"))) {
                result.put(questionId, row);
            }
        }
        return result;
    }

    public List<Map<String, Object>> latestContents(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) { return List.of(); }
        return new NamedParameterJdbcTemplate(db).queryForList("""
                SELECT c.question_id,c.knowledge_point_id,c.source_exam_question_id,
                  c.review_status,c.question_fingerprint
                FROM question_understanding_content c WHERE c.question_id IN (:ids)
                  AND NOT EXISTS (SELECT 1 FROM question_understanding_content newer
                    WHERE newer.question_id=c.question_id AND newer.content_version>c.content_version)
                """, Map.of("ids", questionIds));
    }

    public List<Map<String, Object>> options(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) { return List.of(); }
        return new NamedParameterJdbcTemplate(db).queryForList("""
                SELECT id,question_id,option_label,content,is_correct FROM question_option
                WHERE question_id IN (:ids) AND deleted=0 ORDER BY sort_order,id
                """, Map.of("ids", questionIds));
    }

    public Map<Long, Map<String, Object>> officialSources(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) { return Map.of(); }
        List<Map<String, Object>> rows = new NamedParameterJdbcTemplate(db).queryForList("""
                SELECT e.id,e.question_id,p.exam_name,p.exam_year,p.source_reference,e.display_number
                FROM exam_question e JOIN exam_paper p ON p.id=e.exam_paper_id
                WHERE e.question_id IN (:ids) AND p.deleted=0 AND p.status=1
                  AND p.paper_type='OFFICIAL_EXAM' AND p.source_verified=1
                """, Map.of("ids", questionIds));
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) { result.put(number(row, "id"), row); }
        return result;
    }

    public Map<String, Object> officialSource(long examQuestionId, long questionId) {
        return officialSources(List.of(questionId)).get(examQuestionId);
    }

    public Map<String, Object> variant(long variantId, long questionId) {
        return variant(variantId, questionId, false);
    }

    public Map<String, Object> variant(long variantId, long questionId, boolean preview) {
        return one("""
                SELECT v.* FROM ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id
                WHERE v.id=? AND a.question_id=? AND a.deleted=0 AND a.asset_type='VARIANT'
                  AND (v.review_status='APPROVED' OR (? AND v.review_status='PENDING'))
                  AND v.question_type='SINGLE_CHOICE'
                  AND v.changed_condition IS NOT NULL AND TRIM(v.changed_condition)<>''
                """, variantId, questionId, preview);
    }

    public Long approvedVariantId(long questionId) {
        return variantId(questionId, false);
    }

    public Long variantId(long questionId, boolean preview) {
        Map<String, Object> row = one("""
                SELECT v.id FROM ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id
                WHERE a.question_id=? AND a.deleted=0 AND a.asset_type='VARIANT'
                  AND (v.review_status='APPROVED' OR (? AND v.review_status='PENDING'))
                  AND v.question_type='SINGLE_CHOICE'
                  AND v.changed_condition IS NOT NULL AND TRIM(v.changed_condition)<>''
                ORDER BY v.id LIMIT 1
                """, questionId, preview);
        return row == null ? null : number(row, "id");
    }

    public QuestionUnderstandingAttempt attempt(long attemptId, boolean lock) {
        String sql = "SELECT * FROM question_understanding_attempt WHERE id=?" + (lock ? " FOR UPDATE" : "");
        List<QuestionUnderstandingAttempt> rows = db.query(sql, (result, rowNumber) ->
                new QuestionUnderstandingAttempt(
                        result.getLong("id"), result.getLong("user_id"), result.getLong("question_id"),
                        result.getLong("content_id"), (Long) result.getObject("variant_id"),
                        result.getLong("practice_record_id"),
                        result.getBoolean("hint_chain_enabled"),
                        result.getObject("solution_revealed_time", LocalDateTime.class)), attemptId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long insertAttempt(long userId, long questionId, long contentId, Long variantId,
                              long practiceRecordId, boolean hintChainEnabled) {
        return insert("""
                INSERT INTO question_understanding_attempt
                (user_id,question_id,content_id,variant_id,practice_record_id,hint_chain_enabled) VALUES(?,?,?,?,?,?)
                """,
                userId, questionId, contentId, variantId, practiceRecordId, hintChainEnabled ? 1 : 0);
    }

    public void reveal(long attemptId) {
        db.update("""
                UPDATE question_understanding_attempt SET solution_revealed_time=CURRENT_TIMESTAMP
                WHERE id=? AND solution_revealed_time IS NULL
                """, attemptId);
    }

    public Map<String, Object> variantAttempt(long originAttemptId) {
        return one("SELECT * FROM question_understanding_variant_attempt WHERE origin_attempt_id=?", originAttemptId);
    }

    public Map<String, Object> practiceFact(long practiceRecordId) {
        return one("SELECT user_answer,is_correct FROM practice_record WHERE id=?", practiceRecordId);
    }

    public long insertVariantAttempt(long originAttemptId, long userId, long variantId,
                                     String answer, boolean correct, String assistance, Integer answerTime) {
        return insert("""
                INSERT INTO question_understanding_variant_attempt
                (origin_attempt_id,user_id,variant_id,user_answer,is_correct,assistance,answer_time)
                VALUES(?,?,?,?,?,?,?)
                """, originAttemptId, userId, variantId, answer, correct ? 1 : 0, assistance, answerTime);
    }

    public Map<String, Object> review(long userId, long questionId, String source, long sourceId) {
        return db.queryForObject("""
                SELECT (SELECT next_review_date FROM question_review_schedule
                        WHERE user_id=? AND question_id=? AND deleted=0) AS next_review_date,
                       EXISTS (SELECT 1 FROM fsrs_review_event
                        WHERE user_id=? AND question_id=? AND source_type=? AND source_id=?) AS counted
                """, (row, index) -> {
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    java.sql.Date date = row.getDate("next_review_date");
                    result.put("nextReviewDate", date == null ? null : date.toLocalDate());
                    result.put("counted", row.getBoolean("counted"));
                    return result;
                }, userId, questionId, userId, questionId, source, sourceId);
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = db.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private long insert(String sql, Object... values) {
        KeyHolder key = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
