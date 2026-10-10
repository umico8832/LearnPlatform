package com.learnplatform.mapper;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class WrongQuestionInsightMapper {
    private final NamedParameterJdbcTemplate db;

    public WrongQuestionInsightMapper(NamedParameterJdbcTemplate db) { this.db = db; }

    public Map<Long, Insight> forQuestions(long userId, List<Long> questionIds) {
        if (questionIds.isEmpty()) { return Map.of(); }
        var parameters = new MapSqlParameterSource("userId", userId).addValue("questionIds", questionIds);
        List<Insight> rows = db.query("""
                SELECT w.question_id,s.next_review_date,m.title,m.explanation,m.content_source
                FROM wrong_question w
                LEFT JOIN question_review_schedule s ON s.user_id=w.user_id
                  AND s.question_id=w.question_id AND s.deleted=0
                LEFT JOIN question q ON q.id=w.question_id AND q.deleted=0 AND q.status=1
                  AND (q.visibility IS NULL OR q.visibility='PUBLIC'
                    OR (q.visibility='PRIVATE' AND q.owner_user_id=w.user_id))
                LEFT JOIN course course ON course.id=q.course_id AND course.deleted=0 AND course.status=1
                LEFT JOIN question_understanding_content c ON c.question_id=q.id
                  AND c.id=(SELECT latest.id FROM question_understanding_content latest
                    WHERE latest.question_id=q.id ORDER BY latest.content_version DESC LIMIT 1)
                  AND c.review_status='REVIEWED' AND course.id IS NOT NULL
                  AND c.question_fingerprint=SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
                    (SELECT GROUP_CONCAT(CONCAT(opt.option_label,CHAR(9),opt.content,CHAR(9),
                      opt.is_correct,CHAR(10)) ORDER BY opt.sort_order,opt.id SEPARATOR '')
                      FROM question_option opt WHERE opt.question_id=q.id AND opt.deleted=0)),256)
                LEFT JOIN question_option chosen ON chosen.question_id=q.id AND chosen.deleted=0
                  AND chosen.is_correct=0 AND chosen.option_label=UPPER(TRIM(w.last_wrong_answer))
                  AND (SELECT COUNT(*) FROM question_option valid_option WHERE valid_option.question_id=q.id
                    AND valid_option.deleted=0 AND valid_option.option_label=chosen.option_label)=1
                LEFT JOIN question_understanding_misconception m ON m.content_id=c.id AND m.option_id=chosen.id
                  AND m.content_source IN ('CURATED','AI_GENERATED')
                WHERE w.user_id=:userId AND w.deleted=0 AND w.question_id IN (:questionIds)
                """, parameters, (result, rowNumber) -> new Insight(
                result.getLong("question_id"),
                result.getDate("next_review_date") == null ? null
                        : result.getDate("next_review_date").toLocalDate(),
                result.getString("title"), result.getString("explanation"), result.getString("content_source")));
        Map<Long, Insight> insights = new HashMap<>();
        for (Insight row : rows) { insights.put(row.questionId(), row); }
        return insights;
    }

    public record Insight(long questionId, LocalDate nextReviewDate,
                          String title, String explanation, String contentSource) { }
}
