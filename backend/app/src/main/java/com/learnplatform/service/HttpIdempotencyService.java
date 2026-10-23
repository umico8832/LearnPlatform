package com.learnplatform.service;

import java.util.HexFormat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Replays the first committed HTTP write response for one user, operation and client UUID. */
@Service
public class HttpIdempotencyService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public HttpIdempotencyService(JdbcTemplate db, ObjectMapper json, PlatformTransactionManager transactions) {
        this.db = db;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T execute(long userId, String operation, String requestKey, Object request,
                         Class<T> responseType, Supplier<T> action) {
        validateKey(requestKey);
        String hash = hash(request);
        return tx.execute(status -> executeInTransaction(userId, operation, requestKey, hash, responseType, action));
    }

    private <T> T executeInTransaction(long userId, String operation, String requestKey, String hash,
                                       Class<T> responseType, Supplier<T> action) {
        boolean claimed;
        try {
            db.update("INSERT INTO http_idempotency_record(user_id,operation,request_key,request_hash) VALUES(?,?,?,?)",
                    userId, operation, requestKey, hash);
            claimed = true;
        } catch (DuplicateKeyException exception) {
            claimed = false;
        }
        if (claimed) {
            T response = action.get();
            String snapshot = encode(response);
            db.update("""
                    UPDATE http_idempotency_record SET response_json=?
                    WHERE user_id=? AND operation=? AND request_key=?
                    """,
                    snapshot, userId, operation, requestKey);
            return decode(snapshot, responseType);
        }
        List<StoredResponse> rows = db.query("""
                SELECT request_hash,response_json FROM http_idempotency_record
                WHERE user_id=? AND operation=? AND request_key=? FOR UPDATE
                """, (result, row) -> new StoredResponse(result.getString(1), result.getString(2)),
                userId, operation, requestKey);
        if (rows.isEmpty()) {
            // A competing first request rolled back after this transaction waited on its unique key.
            return executeInTransaction(userId, operation, requestKey, hash, responseType, action);
        }
        StoredResponse stored = rows.getFirst();
        if (!stored.requestHash().equals(hash)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "幂等请求标识与请求内容不一致");
        }
        if (stored.responseJson() == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "幂等请求尚未完成");
        }
        return decode(stored.responseJson(), responseType);
    }

    private <T> T decode(String snapshot, Class<T> responseType) {
        try {
            return json.readValue(snapshot, responseType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid stored idempotency response", exception);
        }
    }

    private String encode(Object response) {
        try { return json.writeValueAsString(response); }
        catch (JacksonException exception) {
            throw new IllegalStateException("Cannot store idempotency response", exception);
        }
    }

    private String hash(Object request) {
        try {
            byte[] bytes = json.writeValueAsBytes(canonical(json.valueToTree(request)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash idempotency request", exception);
        }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var sorted = json.createObjectNode();
            value.properties().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.set(entry.getKey(), canonical(entry.getValue())));
            return sorted;
        }
        if (value.isArray()) {
            var array = json.createArrayNode();
            value.forEach(entry -> array.add(canonical(entry)));
            return array;
        }
        return value;
    }

    private void validateKey(String requestKey) {
        try {
            if (requestKey == null || !UUID.fromString(requestKey).toString().equals(requestKey)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "Idempotency-Key 必须是 UUID");
        }
    }

    private record StoredResponse(String requestHash, String responseJson) { }
}
