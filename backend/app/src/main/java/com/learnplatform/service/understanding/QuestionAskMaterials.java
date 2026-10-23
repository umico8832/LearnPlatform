package com.learnplatform.service.understanding;

import tools.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import com.learnplatform.mapper.QuestionTutorMapper;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuestionAskMaterials {
    private final QuestionUnderstandingContent content;
    private final QuestionUnderstandingMapper attempts;
    private final QuestionTutorMapper hints;
    private final QuestionUnderstandingService understanding;
    private final ObjectMapper json;

    public QuestionAskMaterials(QuestionUnderstandingContent content, QuestionUnderstandingMapper attempts,
                                QuestionTutorMapper hints, QuestionUnderstandingService understanding,
                                ObjectMapper json) {
        this.content = content;
        this.attempts = attempts;
        this.hints = hints;
        this.understanding = understanding;
        this.json = json;
    }

    public Snapshot snapshot(QuestionUnderstandingAttempt attempt) {
        var ready = content.ready(attempt.userId(), attempt.questionId(), attempt.contentId(), false);
        Map<String, Object> fact = attempts.practiceFact(attempt.practiceRecordId());
        if (fact == null) { throw new BusinessException(ResultCode.NOT_FOUND); }
        boolean unlocked = ((Number) fact.get("is_correct")).intValue() == 1 || attempt.solutionRevealedTime() != null;
        var visible = new LinkedHashMap<String, Object>();
        visible.put("stem", ready.question().getContent());
        visible.put("options", content.publicOptions(ready.options()));
        visible.put("selected", fact.get("user_answer"));
        var misconceptions = new ArrayList<Map<String, Object>>();
        for (var option : ready.options()) {
            var misconception = attempts.misconception(attempt.contentId(), ((Number) option.get("id")).longValue());
            if (misconception != null) {
                var entry = Map.<String, Object>of("label", option.get("option_label"),
                        "explanation", misconception.get("explanation"));
                misconceptions.add(entry);
                if (option.get("option_label").equals(fact.get("user_answer"))) {
                    visible.put("selectedMisconception", entry);
                }
            }
        }
        var allHints = new ArrayList<String>();
        var received = new ArrayList<String>();
        int highest = hints.highestLevel(attempt.id());
        for (int level = 1; level <= 3; level++) {
            var hint = hints.reviewedHint(attempt.contentId(), level);
            if (hint != null) {
                allHints.add((String) hint.get("hint_text"));
                if (level <= highest) { received.add((String) hint.get("hint_text")); }
            }
        }
        var correct = content.correctOption(ready.options());
        var full = new LinkedHashMap<>(visible);
        full.put("solution", ready.content().get("solution_explanation"));
        full.put("misconceptions", misconceptions);
        full.put("hints", allHints);
        full.put("correctLabel", correct.get("option_label"));
        visible.put("hints", received);
        var generation = new LinkedHashMap<>(unlocked ? full : visible);
        generation.remove("correctLabel");
        return new Snapshot(encode(generation), encode(full), unlocked,
                (String) correct.get("option_label"), (String) correct.get("content"));
    }

    public Map<String, Object> suggestion(long userId, long attemptId) {
        Map<String, Object> next = understanding.tutorNext(userId, attemptId);
        String type = (String) ((Map<?, ?>) next.get("action")).get("type");
        if (!List.of("HINT", "PLAY_VIZ").contains(type)) { return null; }
        return Map.of("action", next.get("action"), "reason", next.get("reason"),
                "reasonSource", next.get("reasonSource"));
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Invalid question materials"); }
    }

    public record Snapshot(String generation, String judgment, boolean unlocked,
                           String correctLabel, String correctContent) { }
}
