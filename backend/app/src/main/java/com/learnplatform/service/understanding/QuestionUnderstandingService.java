package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import com.learnplatform.service.PracticeAnswerService;
import com.learnplatform.service.CourseLearningEventService;
import com.learnplatform.service.question.QuestionAccessPolicy;
import com.learnplatform.service.review.FsrsReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class QuestionUnderstandingService {
    private final QuestionMapper questions;
    private final QuestionUnderstandingMapper mapper;
    private final PracticeAnswerService practice;
    private final ObjectMapper json;
    private final CourseLearningEventService events;
    private final QuestionTutorService tutor;
    private final QuestionUnderstandingPresentation presentation;
    private final FsrsReviewService fsrs;

    public QuestionUnderstandingService(QuestionMapper questions, QuestionUnderstandingMapper mapper,
                                        PracticeAnswerService practice, ObjectMapper json,
                                        CourseLearningEventService events, QuestionTutorService tutor,
                                        QuestionUnderstandingPresentation presentation, FsrsReviewService fsrs) {
        this.questions = questions;
        this.mapper = mapper;
        this.practice = practice;
        this.json = json;
        this.events = events;
        this.tutor = tutor;
        this.presentation = presentation;
        this.fsrs = fsrs;
    }

    public Map<String, Object> question(long userId, long questionId) {
        Ready ready = ready(userId, questionId, null);
        Map<String, Object> data = map();
        data.put("questionId", questionId);
        data.put("stem", ready.question().getContent());
        data.put("questionType", ready.question().getQuestionType());
        data.put("options", publicOptions(ready.options()));
        data.put("position", position(ready));
        data.put("source", presentation.source(ready.question(), ready.content()));
        return data;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> submit(long userId, long questionId, JsonNode body) {
        Ready ready = ready(userId, questionId, null);
        Answer answer = answer(body, ready.options(), false);
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer(answer.label());
        request.setAnswerTime(answer.time());
        PracticeResultVO practiceResult = practice.submitAnswer(request, userId);
        long contentId = id(ready.content(), "id");
        long attemptId = mapper.insertAttempt(userId, questionId, contentId,
                mapper.approvedVariantId(questionId), practiceResult.getRecordId(),
                tutor.completeReviewedHints(contentId));
        fsrs.score(userId, questionId, FsrsReviewService.Source.PRACTICE,
                practiceResult.getRecordId(), practiceResult.getCorrect(), null);
        Map<String, Object> data = map();
        data.put("attemptId", attemptId);
        data.put("correct", practiceResult.getCorrect());
        data.put("answer", answer.label());
        Map<String, Object> misconception = practiceResult.getCorrect() ? null
                : mapper.misconception(id(ready.content(), "id"), answer.optionId());
        if (misconception == null) {
            data.put("misconception", null);
            data.put("viz", null);
        } else {
            data.put("misconception", Map.of("title", misconception.get("title"),
                    "explanation", misconception.get("explanation"),
                    "source", Map.of("kind", misconception.get("content_source"), "reviewStatus", "REVIEWED")));
            data.put("viz", presentation.viz(misconception, "viz_title", "viz_spec_json"));
        }
        return data;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> reveal(long userId, long attemptId) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, true);
        Ready ready = ready(userId, attempt.questionId(), attempt.contentId());
        if (!tutor.canReveal(tutorContext(attempt, ready))) {
            throw business("请先依次领取三级提示");
        }
        mapper.reveal(attemptId);
        Map<String, Object> data = map();
        data.put("correctAnswer", correctOption(ready.options()).get("option_label"));
        data.put("explanation", ready.content().get("solution_explanation"));
        data.put("explanationSource", presentation.contentSource(
                (String) ready.content().get("solution_content_source")));
        data.put("viz", presentation.viz(ready.content(), "solution_viz_title", "solution_viz_spec_json"));
        return data;
    }

    public Map<String, Object> tutorNext(long userId, long attemptId) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, false);
        Ready ready = ready(userId, attempt.questionId(), attempt.contentId());
        return tutor.next(tutorContext(attempt, ready));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> tutorHint(long userId, long attemptId, JsonNode body) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, true);
        Ready ready = ready(userId, attempt.questionId(), attempt.contentId());
        return tutor.hint(tutorContext(attempt, ready), body);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> tutorVizView(long userId, long attemptId, JsonNode body) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, true);
        Ready ready = ready(userId, attempt.questionId(), attempt.contentId());
        return tutor.view(tutorContext(attempt, ready), body);
    }

    public Map<String, Object> variant(long userId, long attemptId) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, false);
        Ready ready = ready(userId, attempt.questionId(), attempt.contentId());
        Map<String, Object> variant = eligibleVariant(attempt, ready);
        Map<String, Object> data = map();
        data.put("variantId", id(variant, "id"));
        data.put("stem", variant.get("question_content"));
        data.put("options", variantOptions(variant));
        data.put("changedCondition", variant.get("changed_condition"));
        data.put("source", presentation.sourceVariant());
        return data;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> submitVariant(long userId, long variantId, JsonNode body) {
        if (body == null || !body.isObject() || !body.path("originAttemptId").isIntegralNumber()
                || body.has("assistance")) {
            throw validation("变式请求无效");
        }
        long originId = body.get("originAttemptId").longValue();
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, originId, true);
        if (!Objects.equals(attempt.variantId(), variantId)) {
            throw unavailable();
        }
        Ready ready = ready(userId, attempt.questionId(), attempt.contentId());
        Map<String, Object> variant = eligibleVariant(attempt, ready);
        List<Map<String, Object>> options = variantOptions(variant);
        Answer answer = answer(body, options, true);
        Map<String, Object> existing = mapper.variantAttempt(originId);
        if (existing != null) {
            if (!answer.label().equals(existing.get("user_answer"))) {
                throw business("变式答案已锁定");
            }
            return variantResult(existing, variant);
        }
        boolean correct = answer.label().equalsIgnoreCase((String) variant.get("correct_answer"));
        String assistance = tutor.assistance(originId);
        long id = mapper.insertVariantAttempt(originId, userId, variantId, answer.label(), correct,
                assistance, answer.time());
        events.recordQuestionAnswer(userId, ready.question(), "UNDERSTANDING_VARIANT_ANSWERED",
                "UNDERSTANDING_VARIANT", id, correct, null);
        Map<String, Object> saved = map();
        saved.put("id", id);
        saved.put("is_correct", correct ? 1 : 0);
        saved.put("assistance", assistance);
        if ("UNAIDED".equals(assistance)) {
            fsrs.score(userId, attempt.questionId(), FsrsReviewService.Source.UNDERSTANDING_VARIANT,
                    id, correct, null);
        }
        return variantResult(saved, variant);
    }

    private Map<String, Object> variantResult(Map<String, Object> attempt, Map<String, Object> variant) {
        Map<String, Object> data = map();
        data.put("variantAttemptId", id(attempt, "id"));
        data.put("correct", ((Number) attempt.get("is_correct")).intValue() == 1);
        data.put("correctAnswer", variant.get("correct_answer"));
        data.put("explanation", variant.get("analysis"));
        data.put("viz", presentation.viz(variant, "viz_title", "viz_spec_json"));
        data.put("assistance", attempt.get("assistance"));
        data.put("explanationSource", presentation.contentSource("AI_GENERATED"));
        return data;
    }

    private Ready ready(long userId, long questionId, Long contentId) {
        Question question = questions.selectById(questionId);
        if (!QuestionAccessPolicy.canAccess(question, userId) || !Integer.valueOf(1).equals(question.getStatus())) {
            throw unavailable();
        }
        if (!"SINGLE_CHOICE".equals(question.getQuestionType())) {
            throw unavailable();
        }
        Map<String, Object> latest = mapper.latestContent(questionId);
        if (latest == null || !"REVIEWED".equals(latest.get("review_status"))) {
            throw unavailable();
        }
        Map<String, Object> content = contentId == null ? latest : mapper.content(contentId);
        if (content == null || !Objects.equals(id(content, "question_id"), questionId)
                || !"REVIEWED".equals(content.get("review_status"))) {
            throw unavailable();
        }
        Map<String, Object> position = mapper.position(questionId, id(content, "knowledge_point_id"));
        if (position == null) {
            throw unavailable();
        }
        List<Map<String, Object>> options = mapper.options(questionId);
        correctOption(options);
        if (!fingerprint(question, options).equals(content.get("question_fingerprint"))) {
            throw unavailable();
        }
        if (content.get("source_exam_question_id") != null
                && mapper.officialSource(id(content, "source_exam_question_id"), questionId) == null) {
            throw unavailable();
        }
        return new Ready(question, content, options, position);
    }

    private QuestionUnderstandingAttempt ownedAttempt(long userId, long attemptId, boolean lock) {
        QuestionUnderstandingAttempt attempt = mapper.attempt(attemptId, lock);
        if (attempt == null || !Objects.equals(attempt.userId(), userId)) {
            throw unavailable();
        }
        return attempt;
    }

    private Map<String, Object> eligibleVariant(QuestionUnderstandingAttempt attempt, Ready ready) {
        if (attempt.solutionRevealedTime() == null || attempt.variantId() == null) {
            throw unavailable();
        }
        Map<String, Object> variant = mapper.variant(attempt.variantId(), ready.question().getId());
        if (variant == null || variantOptions(variant).stream()
                .filter(option -> option.get("label").equals(variant.get("correct_answer"))).count() != 1) {
            throw unavailable();
        }
        return variant;
    }

    private QuestionTutorService.Context tutorContext(QuestionUnderstandingAttempt attempt, Ready ready) {
        Map<String, Object> fact = mapper.practiceFact(attempt.practiceRecordId());
        if (fact == null) { throw unavailable(); }
        boolean correct = ((Number) fact.get("is_correct")).intValue() == 1;
        Map<String, Object> misconception = null;
        if (!correct) {
            String chosen = (String) fact.get("user_answer");
            for (Map<String, Object> option : ready.options()) {
                if (chosen.equals(option.get("option_label"))) {
                    misconception = mapper.misconception(attempt.contentId(), id(option, "id"));
                    break;
                }
            }
        }
        Map<String, Object> misconceptionViz = misconception == null ? null
                : presentation.viz(misconception, "viz_title", "viz_spec_json");
        Map<String, Object> solutionViz = presentation.viz(ready.content(),
                "solution_viz_title", "solution_viz_spec_json");
        boolean variantAvailable = false;
        if (attempt.variantId() != null) {
            Map<String, Object> variant = mapper.variant(attempt.variantId(), attempt.questionId());
            if (variant != null) {
                try { variantAvailable = !variantOptions(variant).isEmpty(); }
                catch (BusinessException exception) { variantAvailable = false; }
            }
        }
        return new QuestionTutorService.Context(attempt, correct, misconceptionViz, solutionViz, variantAvailable);
    }

    private Answer answer(JsonNode body, List<Map<String, Object>> options, boolean variant) {
        if (body == null || !body.isObject() || !body.path("answer").isTextual()) {
            throw validation("答案无效");
        }
        String label = body.get("answer").asText().trim().toUpperCase(Locale.ROOT);
        Integer time = null;
        if (body.has("answerTime")) {
            JsonNode node = body.get("answerTime");
            if (!node.isInt() || node.intValue() < 0) {
                throw validation("作答时间无效");
            }
            time = node.intValue();
        }
        if (!variant && body.has("assistance")) {
            throw validation("请求字段无效");
        }
        for (Map<String, Object> option : options) {
            Object optionLabel = variant ? option.get("label") : option.get("option_label");
            if (label.equals(optionLabel)) {
                return new Answer(label, time, variant ? null : id(option, "id"));
            }
        }
        throw validation("答案不是有效选项");
    }

    private Map<String, Object> correctOption(List<Map<String, Object>> options) {
        List<Map<String, Object>> correct = options.stream()
                .filter(row -> ((Number) row.get("is_correct")).intValue() == 1).toList();
        if (correct.size() != 1 || options.stream()
                .map(row -> row.get("option_label")).distinct().count() != options.size()) {
            throw business("题目缺少有效判分依据");
        }
        return correct.getFirst();
    }

    private String fingerprint(Question question, List<Map<String, Object>> options) {
        StringBuilder value = new StringBuilder(question.getContent())
                .append('\n').append(question.getQuestionType()).append('\n');
        for (Map<String, Object> option : options) {
            value.append(option.get("option_label")).append('\t')
                .append(option.get("content")).append('\t')
                    .append(((Number) option.get("is_correct")).intValue()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<Map<String, Object>> publicOptions(List<Map<String, Object>> rows) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            options.add(Map.of("label", row.get("option_label"), "content", row.get("content")));
        }
        return options;
    }

    private List<Map<String, Object>> variantOptions(Map<String, Object> variant) {
        try {
            JsonNode nodes = json.readTree((String) variant.get("options_json"));
            if (!nodes.isArray() || nodes.size() == 0) {
                throw unavailable();
            }
            List<Map<String, Object>> options = new ArrayList<>();
            for (JsonNode node : nodes) {
                if (!node.path("label").isTextual() || !node.path("content").isTextual()) {
                    throw unavailable();
                }
                options.add(Map.of("label", node.get("label").asText(), "content", node.get("content").asText()));
            }
            if (options.stream().map(row -> row.get("label")).distinct().count() != options.size()) {
                throw unavailable();
            }
            return options;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private Map<String, Object> position(Ready ready) {
        Map<String, Object> row = ready.position();
        return Map.of("courseId", id(row, "course_id"), "courseName", row.get("course_name"),
                "chapterName", row.get("chapter_name"), "knowledgePointId", id(row, "knowledge_point_id"),
                "knowledgePointName", row.get("knowledge_point_name"));
    }

    private long id(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Map<String, Object> map() { return new LinkedHashMap<>(); }
    private BusinessException unavailable() { return new BusinessException(ResultCode.NOT_FOUND, "题目理解内容不可用"); }
    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
    private BusinessException business(String message) {
        return new BusinessException(ResultCode.BUSINESS_ERROR, message);
    }

    private record Ready(Question question, Map<String, Object> content,
                         List<Map<String, Object>> options, Map<String, Object> position) { }
    private record Answer(String label, Integer time, Long optionId) { }
}
