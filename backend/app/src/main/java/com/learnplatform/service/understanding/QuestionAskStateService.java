package com.learnplatform.service.understanding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import com.learnplatform.dto.QuestionAskResponse;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class QuestionAskStateService {
    private final JdbcTemplate db;
    private final QuestionUnderstandingMapper attempts;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public QuestionAskStateService(JdbcTemplate db, QuestionUnderstandingMapper attempts, ObjectMapper json,
                                    PlatformTransactionManager transactions) {
        this.db = db;
        this.attempts = attempts;
        this.json = json;
        tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T locked(long userId, long attemptId, Function<QuestionUnderstandingAttempt, T> operation) {
        return tx.execute(status -> {
            db.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE", userId);
            return operation.apply(owned(userId, attemptId, true));
        });
    }

    public QuestionUnderstandingAttempt owned(long userId, long attemptId, boolean lock) {
        var attempt = attempts.attempt(attemptId, lock);
        if (attempt == null || attempt.userId() != userId) { throw new BusinessException(ResultCode.NOT_FOUND); }
        return attempt;
    }

    public int count(long userId, long questionId) {
        return db.queryForObject("SELECT COUNT(*) FROM question_ask_record WHERE user_id=? AND question_id=?",
                Integer.class, userId, questionId);
    }

    public void reserve(QuestionUnderstandingAttempt attempt, String requestId, String message, int limit, int lease) {
        db.update("""
                UPDATE question_ask_record SET status='CANCELLED',error_code=1014
                WHERE user_id=? AND question_id=? AND status='RUNNING' AND expires_at<=CURRENT_TIMESTAMP(6)
                """, attempt.userId(), attempt.questionId());
        if (db.queryForObject("SELECT COUNT(*) FROM question_ask_record WHERE attempt_id=? AND request_id=?",
                Integer.class, attempt.id(), requestId) > 0) { throw new BusinessException(ResultCode.RATE_LIMITED); }
        if (db.queryForObject("""
                SELECT COUNT(*) FROM question_ask_record WHERE user_id=? AND question_id=? AND status='RUNNING'
                """, Integer.class, attempt.userId(), attempt.questionId()) > 0) {
            throw new BusinessException(ResultCode.RATE_LIMITED);
        }
        if (count(attempt.userId(), attempt.questionId()) >= limit) {
            throw new BusinessException(ResultCode.ASK_LIMIT);
        }
        db.update("""
                INSERT INTO question_ask_record(user_id,question_id,attempt_id,request_id,message,expires_at)
                VALUES(?,?,?,?,?,TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP(6)))
                """, attempt.userId(), attempt.questionId(), attempt.id(), requestId, message, lease);
    }

    public boolean active(long attemptId, String requestId) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM question_ask_record WHERE attempt_id=? AND request_id=?
                  AND status='RUNNING' AND expires_at>CURRENT_TIMESTAMP(6)
                """, Integer.class, attemptId, requestId) == 1;
    }

    public void succeed(long attemptId, String requestId, String content, Map<String, Object> suggestion) {
        int changed = db.update("""
                UPDATE question_ask_record SET status='SUCCEEDED',content=?,suggestion=?
                WHERE attempt_id=? AND request_id=? AND status='RUNNING'
                  AND expires_at>CURRENT_TIMESTAMP(6)
                """, content, encode(suggestion), attemptId, requestId);
        if (changed != 1) { throw new BusinessException(ResultCode.ASK_CANCELLED); }
    }

    public void fail(long attemptId, String requestId, int code) {
        db.update("""
                UPDATE question_ask_record SET status=?,error_code=?
                WHERE attempt_id=? AND request_id=? AND status='RUNNING'
                """, code == 1014 ? "CANCELLED" : "FAILED", code, attemptId, requestId);
    }

    public List<QuestionAskResponse.Entry> history(long attemptId) {
        return db.query("""
                SELECT *, (status='RUNNING' AND expires_at<=CURRENT_TIMESTAMP(6)) expired
                FROM question_ask_record WHERE attempt_id=? ORDER BY id
                """, (row, index) -> {
            boolean expired = row.getBoolean("expired");
            try {
                return new QuestionAskResponse.Entry(row.getString("request_id"), row.getLong("attempt_id"),
                        row.getString("message"), row.getString("content"),
                        expired ? "CANCELLED" : row.getString("status"),
                        expired ? Integer.valueOf(1014) : (Integer) row.getObject("error_code"),
                        row.getTimestamp("created_at").toLocalDateTime(), row.getString("suggestion") == null
                                ? null : json.readTree(row.getString("suggestion")));
            } catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid saved suggestion"); }
        }, attemptId);
    }

    private String encode(Map<String, Object> value) {
        if (value == null) { return null; }
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid suggestion"); }
    }
}
