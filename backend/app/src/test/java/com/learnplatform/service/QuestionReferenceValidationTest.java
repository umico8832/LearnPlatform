package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionReferenceValidationTest {
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final CourseMapper courses = mock(CourseMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final QuestionKnowledgePointMapper links = mock(QuestionKnowledgePointMapper.class);
    private final QuestionMutationService service = new QuestionMutationService(questions,
            mock(QuestionOptionMapper.class), links, courses, points, mock(ExamQuestionMapper.class),
            mock(QuestionVersionService.class));
    private QuestionCreateRequest request;

    @BeforeEach
    void setUp() {
        Question question = new Question();
        question.setId(10L);
        question.setCourseId(1L);
        question.setVisibility("PUBLIC");
        when(questions.selectById(10L)).thenReturn(question);
        request = new QuestionCreateRequest();
        request.setCourseId(2L);
    }

    @Test
    void updateRejectsMissingCourseBeforeWriting() {
        assertThrows(BusinessException.class, () -> service.update(10L, request, 7L));
        verify(questions, never()).updateById(any(Question.class));
    }

    @Test
    void createRejectsKnowledgePointFromAnotherCourse() {
        stubCourseAndPoint();
        request.setKnowledgePointIds(List.of(20L));
        assertThrows(BusinessException.class, () -> service.create(request, 7L, "MANUAL", null, null));
        verify(questions, never()).insert(any(Question.class));
    }

    @Test
    void updateRejectsKnowledgePointFromAnotherCourse() {
        stubCourseAndPoint();
        request.setKnowledgePointIds(List.of(20L));
        assertThrows(BusinessException.class, () -> service.update(10L, request, 7L));
        verify(questions, never()).updateById(any(Question.class));
    }

    @Test
    void changingCourseAlsoValidatesRetainedKnowledgePoints() {
        stubCourseAndPoint();
        var link = new QuestionKnowledgePoint();
        link.setKnowledgePointId(20L);
        when(links.selectList(any())).thenReturn(List.of(link));
        assertThrows(BusinessException.class, () -> service.update(10L, request, 7L));
        verify(questions, never()).updateById(any(Question.class));
    }

    @Test
    void acceptsMovingToAnotherCourseWhenOldLinksAreExplicitlyRemoved() {
        when(courses.selectById(2L)).thenReturn(new Course());
        request.setKnowledgePointIds(List.of());
        service.update(10L, request, 7L);
        verify(questions).updateById(any(Question.class));
    }

    private void stubCourseAndPoint() {
        when(courses.selectById(2L)).thenReturn(new Course());
        KnowledgePoint point = new KnowledgePoint();
        point.setId(20L);
        point.setCourseId(1L);
        when(points.selectById(20L)).thenReturn(point);
    }
}
