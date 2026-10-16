package com.learnplatform.mapper;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class QuestionLearnerStateMapper {
    private final NamedParameterJdbcTemplate db;

    public QuestionLearnerStateMapper(NamedParameterJdbcTemplate db) { this.db = db; }

    public Map<Long, State> forQuestions(long userId, List<Long> questionIds) {
        if (questionIds.isEmpty()) { return Map.of(); }
        Map<Long, State> states = new HashMap<>();
        db.query("""
                SELECT q.id,
                  EXISTS (SELECT 1 FROM wrong_question w WHERE w.user_id=:userId AND w.question_id=q.id
                    AND w.deleted=0 AND w.mastery_level!=2) unresolved,
                  EXISTS (SELECT 1 FROM fsrs_review_event e
                    WHERE e.user_id=:userId AND e.question_id=q.id) answered,
                  s.next_review_date
                FROM question q LEFT JOIN question_review_schedule s
                  ON s.user_id=:userId AND s.question_id=q.id AND s.deleted=0
                WHERE q.id IN (:ids)
                """, Map.of("userId", userId, "ids", questionIds), row -> {
                    String status = row.getBoolean("unresolved") ? "TO_REDO"
                            : row.getBoolean("answered") ? "DONE" : "NEW";
                    java.sql.Date date = row.getDate("next_review_date");
                    states.put(row.getLong("id"), new State(status,
                            "TO_REDO".equals(status) && date != null ? date.toLocalDate() : null));
                });
        return states;
    }

    public record State(String learnerStatus, LocalDate redoDueDate) { }
}
