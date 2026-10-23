package com.learnplatform.service.understanding;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import com.learnplatform.mapper.QuestionTutorMapper;
import com.learnplatform.service.tutor.TutorAgentHintPolicy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class QuestionTutorService {
    private final QuestionTutorMapper mapper;

    public QuestionTutorService(QuestionTutorMapper mapper) { this.mapper = mapper; }

    public boolean completeReviewedHints(long contentId) { return mapper.completeReviewedHints(contentId); }

    public Map<String, Object> next(Context context) {
        Map<String, Object> state = state(context);
        return recommend(new Recommendation(context.correct(), context.attempt().solutionRevealedTime() != null,
                context.variantAvailable() ? context.attempt().variantId() : null,
                context.misconceptionViz(), context.solutionViz()), state);
    }

    public Map<String, Object> recommend(Recommendation context, Map<String, Object> state) {
        Object nextLevel = state.get("nextHintLevel");
        Map<String, Object> action;
        String reason;
        if (nextLevel != null) {
            action = Map.of("type", "HINT", "level", nextLevel);
            reason = switch ((int) nextLevel) {
                case 1 -> "先从核心概念找到方向。";
                case 2 -> "接着按步骤检查推理。";
                default -> "再看一个相似情境，自己完成本题。";
            };
        } else if (!context.solutionRevealed()) {
            if (!context.correct() && context.misconceptionViz() != null
                    && !Boolean.TRUE.equals(state.get("misconceptionVizViewed"))) {
                action = vizAction("MISCONCEPTION", context.misconceptionViz());
                reason = "用本题数据看所选步骤在哪里走偏。";
            } else {
                action = Map.of("type", "REVEAL_SOLUTION");
                reason = "查看完整推理并核对答案。";
            }
        } else if (context.solutionViz() != null && !Boolean.TRUE.equals(state.get("solutionVizViewed"))
                && !sameStory(context.misconceptionViz(), context.solutionViz())) {
            action = vizAction("SOLUTION", context.solutionViz());
            reason = "跟着本题数据再走一遍正确过程。";
        } else if (context.variantId() != null && !Boolean.TRUE.equals(state.get("variantAttempted"))) {
            action = Map.of("type", "TRY_VARIANT", "variantId", context.variantId());
            reason = "换一个条件，独立检验是否掌握。";
        } else {
            action = Map.of("type", "DONE");
            reason = "本次理解练习已完成。";
        }
        Map<String, Object> result = map();
        result.put("action", action);
        result.put("reason", reason);
        result.put("reasonSource", source("CURATED"));
        result.put("state", state);
        return result;
    }

    public Map<String, Object> hint(Context context, JsonNode body) {
        String requestId = requestId(body);
        Map<String, Object> previous = mapper.receipt(context.attempt().id(), requestId);
        if (previous != null) { return hintResult(context, previous); }
        if (context.correct() || context.attempt().solutionRevealedTime() != null || !chainAvailable(context)) {
            throw unavailable();
        }
        int level = TutorAgentHintPolicy.nextLevel(mapper.highestLevel(context.attempt().id()))
                .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_ERROR, "提示已经全部领取"));
        Map<String, Object> selected = mapper.reviewedHint(context.attempt().contentId(), level);
        if (selected == null) { throw unavailable(); }
        mapper.recordHint(context.attempt().id(), ((Number) selected.get("id")).longValue(), level, requestId);
        return hintResult(context, selected);
    }

    public Map<String, Object> view(Context context, JsonNode body) {
        if (body == null || !body.isObject() || !body.path("context").isTextual()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "可视化上下文无效");
        }
        String kind = body.get("context").asText();
        if (!"MISCONCEPTION".equals(kind) && !"SOLUTION".equals(kind)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "可视化上下文无效");
        }
        boolean allowed = "MISCONCEPTION".equals(kind) && !context.correct() && context.misconceptionViz() != null
                || "SOLUTION".equals(kind) && context.attempt().solutionRevealedTime() != null
                && context.solutionViz() != null;
        if (!allowed) { throw unavailable(); }
        mapper.recordView(context.attempt().id(), kind);
        Map<String, Object> result = map();
        result.put("context", kind);
        result.put("viewed", true);
        result.put("state", state(context));
        return result;
    }

    public boolean canReveal(Context context) {
        return context.correct() || context.attempt().solutionRevealedTime() != null
                || !chainAvailable(context)
                || TutorAgentHintPolicy.nextLevel(mapper.highestLevel(context.attempt().id())).isEmpty();
    }

    public String assistance(long attemptId) {
        return mapper.highestLevel(attemptId) == 0 && !mapper.asked(attemptId) ? "UNAIDED" : "AIDED";
    }

    private boolean chainAvailable(Context context) {
        return context.attempt().hintChainEnabled() && mapper.completeReviewedHints(context.attempt().contentId());
    }

    private Map<String, Object> state(Context context) {
        int used = mapper.highestLevel(context.attempt().id());
        boolean canHint = !context.correct() && context.attempt().solutionRevealedTime() == null
                && chainAvailable(context) && TutorAgentHintPolicy.nextLevel(used).isPresent();
        Map<String, Object> state = map();
        state.put("nextHintLevel", canHint ? TutorAgentHintPolicy.nextLevel(used).getAsInt() : null);
        state.put("hintsUsed", used);
        state.put("misconceptionVizViewed", mapper.viewed(context.attempt().id(), "MISCONCEPTION"));
        state.put("solutionVizViewed", mapper.viewed(context.attempt().id(), "SOLUTION"));
        state.put("solutionRevealed", context.attempt().solutionRevealedTime() != null);
        state.put("variantAttempted", mapper.variantAttempted(context.attempt().id()));
        return state;
    }

    private Map<String, Object> hintResult(Context context, Map<String, Object> selected) {
        Map<String, Object> result = map();
        result.put("level", selected.get("hint_level"));
        result.put("text", selected.get("hint_text"));
        result.put("source", source((String) selected.get("content_source")));
        result.put("state", state(context));
        return result;
    }

    private Map<String, Object> vizAction(String context, Map<String, Object> viz) {
        return Map.of("type", "PLAY_VIZ", "context", context, "viz", viz);
    }

    private boolean sameStory(Map<String, Object> first, Map<String, Object> second) {
        if (first == null || second == null) { return false; }
        JsonNode left = (JsonNode) first.get("spec");
        JsonNode right = (JsonNode) second.get("spec");
        if (left == null || right == null) { return false; }
        JsonNode a = left.deepCopy();
        JsonNode b = right.deepCopy();
        if (a.isObject()) { ((ObjectNode) a).remove("pauseAt"); }
        if (b.isObject()) { ((ObjectNode) b).remove("pauseAt"); }
        return a.equals(b);
    }

    private String requestId(JsonNode body) {
        if (body == null || !body.isObject() || !body.path("requestId").isTextual()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "请求 ID 无效");
        }
        String value = body.get("requestId").asText();
        try {
            if (value.length() != 36 || !UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException("Not canonical UUID");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "请求 ID 无效");
        }
    }

    private Map<String, Object> source(String kind) {
        return Map.of("kind", kind, "reviewStatus", "REVIEWED");
    }

    private Map<String, Object> map() { return new LinkedHashMap<>(); }
    private BusinessException unavailable() {
        return new BusinessException(ResultCode.NOT_FOUND, "题目导师内容不可用");
    }

    public record Recommendation(boolean correct, boolean solutionRevealed, Long variantId,
                                 Map<String, Object> misconceptionViz, Map<String, Object> solutionViz) { }

    public record Context(QuestionUnderstandingAttempt attempt, boolean correct,
                          Map<String, Object> misconceptionViz, Map<String, Object> solutionViz,
                          boolean variantAvailable) { }
}
