package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.mapper.QuestionTutorMapper;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import com.learnplatform.service.understanding.QuestionUnderstandingContent.Answer;
import com.learnplatform.service.understanding.QuestionUnderstandingContent.Ready;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuestionUnderstandingPreviewService {
    private static final Set<String> FIELDS = Set.of("answer", "hintLevel", "misconceptionVizViewed",
            "solutionVizViewed", "revealSolution", "variantAnswer");
    private final QuestionUnderstandingContent content;
    private final QuestionUnderstandingMapper mapper;
    private final QuestionTutorMapper hints;
    private final QuestionUnderstandingPresentation presentation;
    private final QuestionTutorService tutor;

    public QuestionUnderstandingPreviewService(QuestionUnderstandingContent content, QuestionUnderstandingMapper mapper,
                                               QuestionTutorMapper hints,
                                               QuestionUnderstandingPresentation presentation,
                                               QuestionTutorService tutor) {
        this.content = content;
        this.mapper = mapper;
        this.hints = hints;
        this.presentation = presentation;
        this.tutor = tutor;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(long userId, String role, long questionId, JsonNode body) {
        if (!"ADMIN".equals(role)) { throw unavailable(); }
        validate(body);
        Ready ready = content.ready(userId, questionId, null, true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("preview", true);
        result.put("question", question(ready));
        result.put("attempt", null);
        result.put("hints", List.of());
        result.put("solution", null);
        result.put("variant", null);
        result.put("variantResult", null);
        result.put("next", null);
        int level = body.path("hintLevel").asInt(0);
        boolean revealed = body.path("revealSolution").asBoolean(false);
        boolean misconceptionViewed = body.path("misconceptionVizViewed").asBoolean(false);
        boolean solutionViewed = body.path("solutionVizViewed").asBoolean(false);
        if (!body.has("answer")) {
            if (level != 0 || revealed || misconceptionViewed || solutionViewed || body.has("variantAnswer")) {
                throw invalid();
            }
            return result;
        }
        Answer answer = content.answer(body, ready.options(), false);
        boolean correct = answer.label().equals(content.correctOption(ready.options()).get("option_label"));
        long contentId = number(ready.content(), "id");
        String status = ready.content().get("review_status").toString();
        Map<String, Object> misconception = correct ? null : mapper.misconception(contentId, answer.optionId());
        Map<String, Object> misconceptionViz = misconception == null ? null
                : viz(misconception, "viz_title", "viz_spec_json", status);
        Map<String, Object> solutionViz = viz(ready.content(), "solution_viz_title", "solution_viz_spec_json", status);
        List<Map<String, Object>> chain = hints.previewHints(contentId);
        boolean completeChain = chain.size() == 3;
        if (level > 0 && (correct || !completeChain) || misconceptionViewed && misconceptionViz == null
                || solutionViewed && (!revealed || solutionViz == null)) { throw invalid(); }
        if (revealed && !correct && completeChain && level < 3) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先依次领取三级提示");
        }
        result.put("attempt", attempt(answer, correct, misconception, misconceptionViz, status));
        result.put("hints", hintViews(chain, level));
        Long variantId = mapper.variantId(questionId, true);
        Map<String, Object> variant = variantId == null ? null : mapper.variant(variantId, questionId, true);
        if (variant != null) {
            var options = content.variantOptions(variant);
            Object correctAnswer = variant.get("correct_answer");
            if (options.stream().filter(o -> o.get("label").equals(correctAnswer)).count() != 1) {
                variant = null;
            }
        }
        if (revealed) {
            result.put("solution", solution(ready, status, solutionViz));
            result.put("variant", variant == null ? null : variantView(variant));
        }
        if (body.has("variantAnswer")) {
            if (!revealed || variant == null) { throw invalid(); }
            result.put("variantResult", variantResult(body, variant, level));
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("nextHintLevel", !correct && !revealed && completeChain && level < 3 ? level + 1 : null);
        state.put("hintsUsed", level);
        state.put("misconceptionVizViewed", misconceptionViewed);
        state.put("solutionVizViewed", solutionViewed);
        state.put("solutionRevealed", revealed);
        state.put("variantAttempted", body.has("variantAnswer"));
        result.put("next", tutor.recommend(new QuestionTutorService.Recommendation(correct, revealed,
                variant == null ? null : number(variant, "id"), misconceptionViz, solutionViz), state));
        return result;
    }

    private Map<String, Object> question(Ready ready) {
        return Map.of("questionId", ready.question().getId(), "stem", ready.question().getContent(),
                "questionType", ready.question().getQuestionType(), "options", content.publicOptions(ready.options()),
                "position", content.position(ready), "source", presentation.source(ready.question(), ready.content()));
    }

    private Map<String, Object> attempt(Answer answer, boolean correct, Map<String, Object> misconception,
                                        Map<String, Object> viz, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer.label());
        result.put("correct", correct);
        result.put("misconception", misconception == null ? null : Map.of("title", misconception.get("title"),
                "explanation", misconception.get("explanation"), "source",
                presentation.contentSource(misconception.get("content_source").toString(), status)));
        result.put("viz", viz);
        return result;
    }

    private List<Map<String, Object>> hintViews(List<Map<String, Object>> chain, int level) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < level; i++) {
            Map<String, Object> row = chain.get(i);
            result.add(Map.of("level", row.get("hint_level"), "text", row.get("hint_text"), "source",
                    presentation.contentSource(row.get("content_source").toString(),
                            row.get("review_status").toString())));
        }
        return result;
    }

    private Map<String, Object> solution(Ready ready, String status, Map<String, Object> viz) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("correctAnswer", content.correctOption(ready.options()).get("option_label"));
        result.put("explanation", ready.content().get("solution_explanation"));
        result.put("explanationSource", presentation.contentSource(
                ready.content().get("solution_content_source").toString(), status));
        result.put("viz", viz);
        return result;
    }

    private Map<String, Object> variantView(Map<String, Object> row) {
        return Map.of("variantId", number(row, "id"), "stem", row.get("question_content"),
                "options", content.variantOptions(row), "changedCondition", row.get("changed_condition"),
                "source", presentation.sourceVariant(variantStatus(row)));
    }

    private Map<String, Object> variantResult(JsonNode body, Map<String, Object> row, int level) {
        JsonNode variantBody = body.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) variantBody).set("answer", body.get("variantAnswer"));
        Answer answer = content.answer(variantBody, content.variantOptions(row), true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("correct", answer.label().equals(row.get("correct_answer")));
        result.put("correctAnswer", row.get("correct_answer"));
        result.put("explanation", row.get("analysis"));
        result.put("explanationSource", presentation.contentSource("AI_GENERATED", variantStatus(row)));
        result.put("viz", viz(row, "viz_title", "viz_spec_json", variantStatus(row)));
        result.put("assistance", level == 0 ? "UNAIDED" : "AIDED");
        return result;
    }

    private String variantStatus(Map<String, Object> row) {
        return "APPROVED".equals(row.get("review_status")) ? "REVIEWED" : "PENDING";
    }

    private Map<String, Object> viz(Map<String, Object> row, String title, String spec, String status) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        copy.put("review_status", status);
        return presentation.viz(copy, title, spec);
    }

    private void validate(JsonNode body) {
        if (body == null || !body.isObject()) { throw invalid(); }
        body.fieldNames().forEachRemaining(field -> { if (!FIELDS.contains(field)) { throw invalid(); } });
        for (String flag : List.of("revealSolution", "misconceptionVizViewed", "solutionVizViewed")) {
            if (body.has(flag) && !body.get(flag).isBoolean()) { throw invalid(); }
        }
        if (body.has("hintLevel") && (!body.get("hintLevel").isInt()
                || body.get("hintLevel").intValue() < 0 || body.get("hintLevel").intValue() > 3)) { throw invalid(); }
    }

    private long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private BusinessException invalid() { return new BusinessException(ResultCode.VALIDATION_ERROR, "预览进度无效"); }
    private BusinessException unavailable() { return new BusinessException(ResultCode.NOT_FOUND, "题目理解内容不可用"); }
}
