package com.learnplatform.service;

import com.learnplatform.dto.TutorLearningContextVO;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TutorLearningContextServiceTest {

    @Test
    void aggregatesQuestionEvidenceFromTargetAndCourseAncestors() {
        KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        QuestionKnowledgePointMapper mappings = mock(QuestionKnowledgePointMapper.class);
        CourseLearningEventMapper events = mock(CourseLearningEventMapper.class);
        WrongQuestionMapper wrongQuestions = mock(WrongQuestionMapper.class);
        QuestionReviewScheduleMapper reviews = mock(QuestionReviewScheduleMapper.class);
        KnowledgePoint target = point(30L, 10L, 20L);
        KnowledgePoint parent = point(20L, 10L, 0L);
        when(points.selectById(30L)).thenReturn(target);
        when(points.selectById(20L)).thenReturn(parent);
        when(mappings.selectList(any())).thenReturn(List.of(mapping(101L, 30L), mapping(102L, 20L)));
        when(events.selectCount(any())).thenReturn(3L, 2L, 1L, 4L);
        when(wrongQuestions.selectCount(any())).thenReturn(2L);
        when(reviews.selectCount(any())).thenReturn(1L);
        CourseLearningEvent latest = new CourseLearningEvent();
        latest.setOccurredTime(LocalDateTime.of(2026, 8, 15, 9, 30));
        when(events.selectOne(any())).thenReturn(latest);

        TutorLearningContextVO context = new TutorLearningContextService(
                points, mappings, events, wrongQuestions, reviews).summarize(7L, 10L, 30L);

        assertEquals(3, context.getPaperAnswerCount());
        assertEquals(2, context.getPaperIncorrectCount());
        assertEquals(1, context.getPaperAiAssistanceCount());
        assertEquals(2, context.getUnresolvedWrongCount());
        assertEquals(1, context.getDueReviewCount());
        assertEquals(4, context.getReviewAnswerCount());
        assertEquals(LocalDateTime.of(2026, 8, 15, 9, 30), context.getLatestEvidenceAt());
    }

    @Test
    void returnsEmptyContextWithoutMappedQuestions() {
        KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        QuestionKnowledgePointMapper mappings = mock(QuestionKnowledgePointMapper.class);
        CourseLearningEventMapper events = mock(CourseLearningEventMapper.class);
        WrongQuestionMapper wrongQuestions = mock(WrongQuestionMapper.class);
        QuestionReviewScheduleMapper reviews = mock(QuestionReviewScheduleMapper.class);
        when(points.selectById(30L)).thenReturn(point(30L, 10L, null));
        when(mappings.selectList(any())).thenReturn(List.of());

        TutorLearningContextVO context = new TutorLearningContextService(
                points, mappings, events, wrongQuestions, reviews).summarize(7L, 10L, 30L);

        assertEquals(0, context.getPaperAnswerCount());
        verify(events, never()).selectCount(any());
        verify(wrongQuestions, never()).selectCount(any());
        verify(reviews, never()).selectCount(any());
    }

    private KnowledgePoint point(Long id, Long courseId, Long parentId) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setCourseId(courseId);
        point.setParentId(parentId);
        return point;
    }

    private QuestionKnowledgePoint mapping(Long questionId, Long knowledgePointId) {
        QuestionKnowledgePoint mapping = new QuestionKnowledgePoint();
        mapping.setQuestionId(questionId);
        mapping.setKnowledgePointId(knowledgePointId);
        return mapping;
    }
}
