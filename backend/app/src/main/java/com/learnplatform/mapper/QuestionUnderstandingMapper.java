package com.learnplatform.mapper;

import com.learnplatform.entity.QuestionUnderstandingAttempt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
        return one("""
                WITH RECURSIVE ancestors AS (
                  SELECT id,parent_id,name,course_id,0 AS depth
                  FROM knowledge_point WHERE id=? AND deleted=0
                  UNION ALL
                  SELECT parent.id,parent.parent_id,parent.name,parent.course_id,ancestors.depth+1
                  FROM knowledge_point parent JOIN ancestors ON parent.id=ancestors.parent_id
                  WHERE parent.deleted=0 AND parent.course_id=ancestors.course_id AND ancestors.depth<20
                )
                SELECT c.id course_id,c.name course_name,k.id knowledge_point_id,k.name knowledge_point_name,
                  root.name chapter_name
                FROM question q JOIN course c ON c.id=q.course_id AND c.deleted=0 AND c.status=1
                JOIN question_knowledge_point qkp ON qkp.question_id=q.id AND qkp.knowledge_point_id=?
                JOIN knowledge_point k ON k.id=qkp.knowledge_point_id AND k.course_id=c.id AND k.deleted=0
                JOIN (SELECT name,course_id FROM ancestors
                  WHERE parent_id IS NULL OR parent_id=0 ORDER BY depth DESC LIMIT 1) root
                  ON root.course_id=c.id
                WHERE q.id=?
                """, pointId, pointId, questionId);
    }

    public Map<String, Object> officialSource(long examQuestionId, long questionId) {
        return one("""
                SELECT p.exam_name,p.exam_year,p.source_reference,e.display_number
                FROM exam_question e JOIN exam_paper p ON p.id=e.exam_paper_id
                WHERE e.id=? AND e.question_id=? AND p.deleted=0 AND p.status=1
                  AND p.paper_type='OFFICIAL_EXAM' AND p.source_verified=1
                """, examQuestionId, questionId);
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
                        result.getTimestamp("solution_revealed_time") == null ? null
                                : result.getTimestamp("solution_revealed_time").toLocalDateTime()), attemptId);
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
