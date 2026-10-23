package com.learnplatform.service.understanding;

import tools.jackson.databind.JsonNode;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.dto.QuestionAskResponse;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import com.learnplatform.service.AiCallGovernanceService;
import com.learnplatform.service.ai.AiCallContext;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionAskService {
    private final QuestionAskStateService state;
    private final QuestionAskMaterials materials;
    private final QuestionAskAnswerService answers;
    private final AiCallGovernanceService governance;
    private final AiConfig config;
    private final ConcurrentHashMap<String, Cancellation> running = new ConcurrentHashMap<>();

    public QuestionAskService(QuestionAskStateService state, QuestionAskMaterials materials,
                              QuestionAskAnswerService answers, AiCallGovernanceService governance, AiConfig config) {
        this.state = state;
        this.materials = materials;
        this.answers = answers;
        this.governance = governance;
        this.config = config;
    }

    public QuestionAskResponse.Status status(long userId, long attemptId) {
        var attempt = checkedAttempt(userId, attemptId);
        var quota = remaining(userId, attempt.questionId());
        String reason = disabledReason();
        return new QuestionAskResponse.Status(reason == null, reason, quota.question(), quota.daily(),
                state.history(attemptId));
    }

    public Job prepare(long userId, long attemptId, JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2 || !body.path("message").isTextual()
                || !body.path("requestId").isTextual()) { throw new BusinessException(ResultCode.VALIDATION_ERROR); }
        String message = body.get("message").asText().strip();
        String requestId = body.get("requestId").asText();
        validateRequestId(requestId);
        if (message.isBlank() || message.codePointCount(0, message.length()) > 1000) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR);
        }
        return state.locked(userId, attemptId, attempt -> {
            var snapshot = materials.snapshot(attempt);
            if (disabledReason() != null) { throw new BusinessException(ResultCode.ASK_UNAVAILABLE); }
            governance.checkDailyQuota(userId);
            state.reserve(attempt, requestId, message, config.getQuestionAskLimit(),
                    4 * config.getTimeout() / 1000 + 60);
            return new Job(userId, attemptId, attempt.questionId(), requestId, message, snapshot,
                    UUID.nameUUIDFromBytes(key(attemptId, requestId).getBytes(StandardCharsets.UTF_8)),
                    4L * config.getTimeout() + 60000, new Cancellation());
        });
    }

    public QuestionAskResponse.Done execute(Job job, java.util.function.Consumer<String> output) {
        String key = key(job.attemptId(), job.requestId());
        running.put(key, job.cancellation());
        try {
            check(job);
            String answer = answers.answer(new AiCallContext(job.userId(), "question_ask", job.runId()),
                    job.materials(), job.message(), job.cancellation(), () -> check(job), () -> active(job));
            var suggestion = state.locked(job.userId(), job.attemptId(), attempt -> {
                check(job);
                var next = materials.suggestion(job.userId(), job.attemptId());
                state.succeed(job.attemptId(), job.requestId(), answer, next);
                return next;
            });
            int offset = 0;
            while (offset < answer.length()) {
                int end = answer.offsetByCodePoints(
                        offset, Math.min(32, answer.codePointCount(offset, answer.length())));
                output.accept(answer.substring(offset, end));
                offset = end;
            }
            var quota = remaining(job.userId(), job.questionId());
            return new QuestionAskResponse.Done(
                    job.requestId(), quota.question(), quota.daily(), suggestion);
        } catch (ModelException exception) {
            ResultCode code = switch (exception.code()) {
                case TIMEOUT -> ResultCode.ASK_TIMEOUT;
                case CANCELLED -> ResultCode.ASK_CANCELLED;
                case CONFIGURATION -> ResultCode.ASK_UNAVAILABLE;
                default -> ResultCode.ASK_UPSTREAM;
            };
            state.fail(job.attemptId(), job.requestId(), code.getCode());
            throw new BusinessException(code);
        } catch (BusinessException exception) {
            state.fail(job.attemptId(), job.requestId(), exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            state.fail(job.attemptId(), job.requestId(), ResultCode.ASK_UPSTREAM.getCode());
            throw new BusinessException(ResultCode.ASK_UPSTREAM);
        } finally { running.remove(key, job.cancellation()); }
    }

    public Map<String, Object> stop(long userId, long attemptId, String requestId) {
        return state.locked(userId, attemptId, attempt -> {
            materials.snapshot(attempt);
            validateRequestId(requestId);
            state.fail(attemptId, requestId, 1014);
            Cancellation cancellation = running.get(key(attemptId, requestId));
            if (cancellation != null) { cancellation.cancel(); }
            return Map.of("stopped", true);
        });
    }

    public void reject(Job job) {
        state.fail(job.attemptId(), job.requestId(), ResultCode.RATE_LIMITED.getCode());
    }

    public void cancel(Job job) {
        job.cancellation().cancel();
        state.fail(job.attemptId(), job.requestId(), ResultCode.ASK_CANCELLED.getCode());
    }

    private void active(Job job) {
        job.cancellation().check();
        if (!state.active(job.attemptId(), job.requestId())) {
            job.cancellation().cancel();
            throw new ModelException(ModelException.Code.CANCELLED);
        }
    }

    private void check(Job job) {
        active(job);
        if (disabledReason() != null) { throw new ModelException(ModelException.Code.CONFIGURATION); }
        var attempt = state.owned(job.userId(), job.attemptId(), false);
        if (!materials.snapshot(attempt).equals(job.materials())) {
            throw new ModelException(ModelException.Code.SCHEMA);
        }
    }

    private QuestionUnderstandingAttempt checkedAttempt(long userId, long attemptId) {
        var attempt = state.owned(userId, attemptId, false);
        materials.snapshot(attempt);
        return attempt;
    }

    private record Quota(int question, Integer daily) { }

    private Quota remaining(long userId, long questionId) {
        int[] usage = governance.getDailyUsage(userId);
        return new Quota(Math.max(0, config.getQuestionAskLimit() - state.count(userId, questionId)),
                usage[1] <= 0 ? null : Math.max(0, usage[1] - usage[0]));
    }

    private String disabledReason() {
        if (!config.isEnabled() || !config.isQuestionAskEnabled()) { return "DISABLED"; }
        try {
            URI endpoint = URI.create(config.getApiBaseUrl());
            if (!List.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                    || config.getApiKey() == null || config.getApiKey().isBlank()
                    || !"deepseek-flash".equals(config.getModel()) || config.getQuestionAskLimit() <= 0
                    || config.getTimeout() <= 0 || config.getTimeout() > 120000) { return "UNCONFIGURED"; }
            return null;
        } catch (RuntimeException exception) { return "UNCONFIGURED"; }
    }

    private void validateRequestId(String requestId) {
        try {
            if (requestId == null || requestId.length() != 36
                    || !UUID.fromString(requestId).toString().equals(requestId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) { throw new BusinessException(ResultCode.VALIDATION_ERROR); }
    }

    private String key(long attemptId, String requestId) { return attemptId + ":" + requestId; }
    public record Job(long userId, long attemptId, long questionId, String requestId, String message,
                      QuestionAskMaterials.Snapshot materials, UUID runId, long timeoutMillis,
                      Cancellation cancellation) { }
}
