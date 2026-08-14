package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.TutorLearningContextVO;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 将课程内已有学习事实聚合为 Tutor 可消费的最小上下文。 */
@Service
public class TutorLearningContextService {

    private static final List<String> CONTEXT_EVENT_SOURCES =
            List.of("PAPER_LEARNING", "PAPER_LEARNING_AI", "REVIEW");
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseLearningEventMapper courseLearningEventMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionReviewScheduleMapper reviewScheduleMapper;

    public TutorLearningContextService(KnowledgePointMapper knowledgePointMapper,
                                       QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                       CourseLearningEventMapper courseLearningEventMapper,
                                       WrongQuestionMapper wrongQuestionMapper,
                                       QuestionReviewScheduleMapper reviewScheduleMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseLearningEventMapper = courseLearningEventMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.reviewScheduleMapper = reviewScheduleMapper;
    }

    public TutorLearningContextVO summarize(Long userId, Long courseId, Long knowledgePointId) {
        TutorLearningContextVO context = new TutorLearningContextVO();
        Set<Long> scopeKnowledgePointIds = lineage(courseId, knowledgePointId);
        if (scopeKnowledgePointIds.isEmpty()) {
            return context;
        }
        List<Long> questionIds = questionKnowledgePointMapper.selectList(
                        new LambdaQueryWrapper<QuestionKnowledgePoint>()
                                .in(QuestionKnowledgePoint::getKnowledgePointId, scopeKnowledgePointIds))
                .stream()
                .map(QuestionKnowledgePoint::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return context;
        }

        context.setPaperAnswerCount(countEvents(userId, courseId, questionIds, "PAPER_LEARNING", false));
        context.setPaperIncorrectCount(countEvents(
                userId, courseId, questionIds, "PAPER_LEARNING", true));
        context.setPaperAiAssistanceCount(countEvents(
                userId, courseId, questionIds, "PAPER_LEARNING_AI", false));
        context.setReviewAnswerCount(countEvents(userId, courseId, questionIds, "REVIEW", false));
        context.setUnresolvedWrongCount(Math.toIntExact(wrongQuestionMapper.selectCount(
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .in(WrongQuestion::getQuestionId, questionIds)
                        .lt(WrongQuestion::getMasteryLevel, 2))));
        context.setDueReviewCount(Math.toIntExact(reviewScheduleMapper.selectCount(
                new LambdaQueryWrapper<QuestionReviewSchedule>()
                        .eq(QuestionReviewSchedule::getUserId, userId)
                        .in(QuestionReviewSchedule::getQuestionId, questionIds)
                        .le(QuestionReviewSchedule::getNextReviewDate, LocalDate.now()))));
        CourseLearningEvent latest = courseLearningEventMapper.selectOne(
                baseEventQuery(userId, courseId, questionIds)
                        .in(CourseLearningEvent::getEventSource, CONTEXT_EVENT_SOURCES)
                        .orderByDesc(CourseLearningEvent::getOccurredTime)
                        .last("LIMIT 1"));
        if (latest != null) {
            context.setLatestEvidenceAt(latest.getOccurredTime());
        }
        return context;
    }

    private int countEvents(Long userId, Long courseId, List<Long> questionIds,
                            String eventSource, boolean incorrectOnly) {
        LambdaQueryWrapper<CourseLearningEvent> query = baseEventQuery(userId, courseId, questionIds)
                .eq(CourseLearningEvent::getEventSource, eventSource);
        if (incorrectOnly) {
            query.apply("JSON_EXTRACT(payload_json, '$.isCorrect') = false");
        }
        return Math.toIntExact(courseLearningEventMapper.selectCount(query));
    }

    private LambdaQueryWrapper<CourseLearningEvent> baseEventQuery(
            Long userId, Long courseId, List<Long> questionIds) {
        return new LambdaQueryWrapper<CourseLearningEvent>()
                .eq(CourseLearningEvent::getUserId, userId)
                .eq(CourseLearningEvent::getCourseId, courseId)
                .eq(CourseLearningEvent::getSubjectType, "QUESTION")
                .in(CourseLearningEvent::getSubjectId, questionIds);
    }

    private Set<Long> lineage(Long courseId, Long knowledgePointId) {
        Set<Long> ids = new LinkedHashSet<>();
        Long currentId = knowledgePointId;
        for (int depth = 0; currentId != null && depth < 32; depth++) {
            if (!ids.add(currentId)) {
                break;
            }
            KnowledgePoint current = knowledgePointMapper.selectById(currentId);
            if (current == null || !Objects.equals(courseId, current.getCourseId())) {
                return Set.of();
            }
            currentId = current.getParentId();
            if (currentId != null && currentId <= 0) {
                currentId = null;
            }
        }
        return ids;
    }
}
