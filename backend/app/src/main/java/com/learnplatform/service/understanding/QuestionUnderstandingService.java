package com.learnplatform.service.understanding;

import tools.jackson.databind.JsonNode;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.entity.QuestionUnderstandingAttempt;
import com.learnplatform.service.understanding.QuestionUnderstandingContent.Ready;
import com.learnplatform.service.understanding.QuestionUnderstandingContent.Answer;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import com.learnplatform.service.PracticeAnswerService;
import com.learnplatform.service.CourseLearningEventService;
import com.learnplatform.service.review.FsrsReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class QuestionUnderstandingService {
    private final QuestionUnderstandingContent content;
    private final QuestionUnderstandingMapper mapper;
    private final PracticeAnswerService practice;
    private final CourseLearningEventService events;
    private final QuestionTutorService tutor;
    private final QuestionUnderstandingPresentation presentation;
    private final FsrsReviewService fsrs;
    private final com.learnplatform.service.ContentReferenceLockService referenceLocks;

    public QuestionUnderstandingService(QuestionUnderstandingContent content, QuestionUnderstandingMapper mapper,
                                        PracticeAnswerService practice,
                                        CourseLearningEventService events, QuestionTutorService tutor,
                                        QuestionUnderstandingPresentation presentation, FsrsReviewService fsrs,
                                        com.learnplatform.service.ContentReferenceLockService referenceLocks) {
        this.content = content;
        this.mapper = mapper;
        this.practice = practice;
        this.events = events;
        this.tutor = tutor;
        this.presentation = presentation;
        this.fsrs = fsrs;
        this.referenceLocks = referenceLocks;
    }

    public Map<String, Object> question(long userId, long questionId) {
        Ready ready = content.ready(userId, questionId, null, false);
        Map<String, Object> data = map();
        data.put("questionId", questionId);
        data.put("stem", ready.question().getContent());
        data.put("questionType", ready.question().getQuestionType());
        data.put("options", content.publicOptions(ready.options()));
        data.put("position", content.position(ready));
        data.put("source", presentation.source(ready.question(), ready.content()));
        return data;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> submit(long userId, long questionId, JsonNode body) {
        Ready ready = content.ready(userId, questionId, null, false);
        Answer answer = content.answer(body, ready.options(), false);
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
        data.put("review", mapper.review(userId, questionId, "PRACTICE", practiceResult.getRecordId()));
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
        Ready ready = content.ready(userId, attempt.questionId(), attempt.contentId(), false);
        if (!tutor.canReveal(tutorContext(attempt, ready))) {
            throw business("请先依次领取三级提示");
        }
        mapper.reveal(attemptId);
        Map<String, Object> data = map();
        data.put("correctAnswer", content.correctOption(ready.options()).get("option_label"));
        data.put("explanation", ready.content().get("solution_explanation"));
        data.put("explanationSource", presentation.contentSource(
                (String) ready.content().get("solution_content_source")));
        data.put("viz", presentation.viz(ready.content(), "solution_viz_title", "solution_viz_spec_json"));
        return data;
    }

    public Map<String, Object> tutorNext(long userId, long attemptId) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, false);
        Ready ready = content.ready(userId, attempt.questionId(), attempt.contentId(), false);
        return tutor.next(tutorContext(attempt, ready));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> tutorHint(long userId, long attemptId, JsonNode body) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, true);
        Ready ready = content.ready(userId, attempt.questionId(), attempt.contentId(), false);
        return tutor.hint(tutorContext(attempt, ready), body);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> tutorVizView(long userId, long attemptId, JsonNode body) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, true);
        Ready ready = content.ready(userId, attempt.questionId(), attempt.contentId(), false);
        return tutor.view(tutorContext(attempt, ready), body);
    }

    public Map<String, Object> variant(long userId, long attemptId) {
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, attemptId, false);
        Ready ready = content.ready(userId, attempt.questionId(), attempt.contentId(), false);
        Map<String, Object> variant = eligibleVariant(attempt, ready);
        Map<String, Object> data = map();
        data.put("variantId", id(variant, "id"));
        data.put("stem", variant.get("question_content"));
        data.put("options", content.variantOptions(variant));
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
        QuestionUnderstandingAttempt preview = ownedAttempt(userId, originId, false);
        Ready previewReady = content.ready(userId, preview.questionId(), preview.contentId(), false);
        referenceLocks.lockQuestions(List.of(previewReady.question().getId()), List.of());
        QuestionUnderstandingAttempt attempt = ownedAttempt(userId, originId, true);
        if (!Objects.equals(attempt.variantId(), variantId)) {
            throw unavailable();
        }
        Ready ready = content.ready(userId, attempt.questionId(), attempt.contentId(), false);
        Map<String, Object> variant = eligibleVariant(attempt, ready);
        List<Map<String, Object>> options = content.variantOptions(variant);
        Answer answer = content.answer(body, options, true);
        Map<String, Object> existing = mapper.variantAttempt(originId);
        if (existing != null) {
            if (!answer.label().equals(existing.get("user_answer"))) {
                throw business("变式答案已锁定");
            }
            return variantResult(existing, variant, userId, attempt.questionId());
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
        return variantResult(saved, variant, userId, attempt.questionId());
    }

    private Map<String, Object> variantResult(Map<String, Object> attempt, Map<String, Object> variant,
                                              long userId, long questionId) {
        Map<String, Object> data = map();
        data.put("variantAttemptId", id(attempt, "id"));
        data.put("review", mapper.review(userId, questionId, "UNDERSTANDING_VARIANT", id(attempt, "id")));
        data.put("correct", ((Number) attempt.get("is_correct")).intValue() == 1);
        data.put("correctAnswer", variant.get("correct_answer"));
        data.put("explanation", variant.get("analysis"));
        data.put("viz", presentation.viz(variant, "viz_title", "viz_spec_json"));
        data.put("assistance", attempt.get("assistance"));
        data.put("explanationSource", presentation.contentSource("AI_GENERATED"));
        return data;
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
        if (variant == null || content.variantOptions(variant).stream()
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
                try { variantAvailable = !content.variantOptions(variant).isEmpty(); }
                catch (BusinessException exception) { variantAvailable = false; }
            }
        }
        return new QuestionTutorService.Context(attempt, correct, misconceptionViz, solutionViz, variantAvailable);
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

}
