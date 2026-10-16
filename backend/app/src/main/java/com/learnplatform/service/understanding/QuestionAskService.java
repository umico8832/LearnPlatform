package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import com.learnplatform.dto.QuestionAskResponse;
import com.learnplatform.mapper.QuestionTutorMapper;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import com.learnplatform.service.AiCallGovernanceService;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionAskService {
    private final QuestionAskStateService state;
    private final QuestionUnderstandingContent content;
    private final QuestionUnderstandingMapper attempts;
    private final QuestionTutorMapper hints;
    private final QuestionUnderstandingService understanding;
    private final AiInvocationService ai;
    private final AiCallGovernanceService governance;
    private final AiConfig config;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String, Cancellation> running = new ConcurrentHashMap<>();

    public QuestionAskService(QuestionAskStateService state, QuestionUnderstandingContent content,
                              QuestionUnderstandingMapper attempts, QuestionTutorMapper hints,
                              QuestionUnderstandingService understanding, AiInvocationService ai,
                              AiCallGovernanceService governance, AiConfig config, ObjectMapper json) {
        this.state = state;
        this.content = content;
        this.attempts = attempts;
        this.hints = hints;
        this.understanding = understanding;
        this.ai = ai;
        this.governance = governance;
        this.config = config;
        this.json = json;
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
            var ready = content.ready(userId, attempt.questionId(), attempt.contentId(), false);
            if (disabledReason() != null) { throw new BusinessException(ResultCode.ASK_UNAVAILABLE); }
            governance.checkDailyQuota(userId);
            Map<String, Object> fact = attempts.practiceFact(attempt.practiceRecordId());
            if (fact == null) { throw new BusinessException(ResultCode.NOT_FOUND); }
            boolean unlocked = ((Number) fact.get("is_correct")).intValue() == 1
                    || attempt.solutionRevealedTime() != null;
            var candidates = candidates(attempt, ready, fact);
            var materials = new LinkedHashMap<String, Object>();
            materials.put("stem", ready.question().getContent());
            materials.put("options", content.publicOptions(ready.options()));
            materials.put("selected", fact.get("user_answer"));
            materials.put("solutionUnlocked", unlocked);
            var request = QuestionAskPolicy.request(encode(materials), message, candidates, ai.defaultOptions());
            state.reserve(attempt, requestId, message, config.getQuestionAskLimit(), config.getTimeout() / 1000 + 60);
            return new Job(userId, attemptId, attempt.questionId(), requestId, request,
                    List.copyOf(candidates), new Cancellation());
        });
    }

    public QuestionAskResponse.Done execute(Job job, java.util.function.Consumer<String> output) {
        String key = key(job.attemptId(), job.requestId());
        running.put(key, job.cancellation());
        try {
            check(job);
            var completed = ai.generateStream(new AiCallContext(job.userId(), "question_ask", null), job.request(),
                    event -> {
                        if (!(event instanceof ModelEvent.TextDelta)) { return; }
                        job.cancellation().check();
                        if (!state.active(job.attemptId(), job.requestId())) {
                            throw new ModelException(ModelException.Code.CANCELLED);
                        }
                    }, job.cancellation(), result -> state.locked(job.userId(), job.attemptId(), attempt -> {
                        check(job);
                        var ready = content.ready(job.userId(), attempt.questionId(), attempt.contentId(), false);
                        var current = candidates(attempt, ready, attempts.practiceFact(attempt.practiceRecordId()));
                        String answer = QuestionAskPolicy.checked(result.requireCompleteText(), job.candidates());
                        QuestionAskPolicy.checked(answer, current);
                        var suggestion = suggestion(job.userId(), job.attemptId());
                        state.succeed(job.attemptId(), job.requestId(), answer, suggestion);
                        return new Completed(answer, suggestion);
                    }));
            String answer = completed.answer();
            int offset = 0;
            while (offset < answer.length()) {
                int end = answer.offsetByCodePoints(
                        offset, Math.min(32, answer.codePointCount(offset, answer.length())));
                output.accept(answer.substring(offset, end));
                offset = end;
            }
            var quota = remaining(job.userId(), job.questionId());
            return new QuestionAskResponse.Done(
                    job.requestId(), quota.question(), quota.daily(), completed.suggestion());
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
            content.ready(userId, attempt.questionId(), attempt.contentId(), false);
            validateRequestId(requestId);
            state.fail(attemptId, requestId, 1014);
            Cancellation cancellation = running.get(key(attemptId, requestId));
            if (cancellation != null) { cancellation.cancel(); }
            return Map.of("stopped", true);
        });
    }

    private List<String> candidates(QuestionUnderstandingAttempt attempt, QuestionUnderstandingContent.Ready ready,
                                    Map<String, Object> fact) {
        var result = new ArrayList<>(QuestionAskPolicy.SAFE);
        boolean unlocked = ((Number) fact.get("is_correct")).intValue() == 1
                    || attempt.solutionRevealedTime() != null;
        if (unlocked) {
            result.add((String) ready.content().get("solution_explanation"));
            for (int level = 1; level <= 3; level++) {
                var hint = hints.reviewedHint(attempt.contentId(), level);
                if (hint != null) { result.add((String) hint.get("hint_text")); }
            }
            for (var option : ready.options()) {
                if (option.get("option_label").equals(fact.get("user_answer"))) {
                    var misconception = attempts.misconception(
                            attempt.contentId(), ((Number) option.get("id")).longValue());
                    if (misconception != null) { result.add((String) misconception.get("explanation")); }
                }
            }
        }
        result.removeIf(value -> value == null || value.isBlank());
        return List.copyOf(result);
    }

    private record Completed(String answer, Map<String, Object> suggestion) { }

    private void check(Job job) {
        job.cancellation().check();
        if (!state.active(job.attemptId(), job.requestId())) {
            job.cancellation().cancel();
            throw new ModelException(ModelException.Code.CANCELLED);
        }
        if (disabledReason() != null) { throw new ModelException(ModelException.Code.CONFIGURATION); }
        checkedAttempt(job.userId(), job.attemptId());
    }

    private QuestionUnderstandingAttempt checkedAttempt(long userId, long attemptId) {
        var attempt = state.owned(userId, attemptId, false);
        content.ready(userId, attempt.questionId(), attempt.contentId(), false);
        return attempt;
    }

    private Map<String, Object> suggestion(long userId, long attemptId) {
        Map<String, Object> next = understanding.tutorNext(userId, attemptId);
        String type = (String) ((Map<?, ?>) next.get("action")).get("type");
        if (!List.of("HINT", "PLAY_VIZ").contains(type)) { return null; }
        return Map.of("action", next.get("action"), "reason", next.get("reason"),
                "reasonSource", next.get("reasonSource"));
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
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Invalid question materials"); }
    }

    public record Job(long userId, long attemptId, long questionId, String requestId, ModelRequest request,
                      List<String> candidates, Cancellation cancellation) { }
}
