package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamPaperServiceTest {

    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private CourseMapper courseMapper;
    private ExamPaperService examPaperService;

    @BeforeEach
    void setUp() {
        examPaperService = new ExamPaperService(examPaperMapper, examQuestionMapper,
                questionMapper, questionOptionMapper, courseMapper);
    }

    @Test
    void rejectsUpdatingPublishedPaper() {
        ExamPaper paper = paper(1, 3);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.updateExamPaper(1L, new ExamPaperCreateRequest()));

        assertEquals("已发布试卷不能修改", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    @Test
    void rejectsPublishingEmptyPaper() {
        ExamPaper paper = paper(0, 0);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.publishExamPaper(1L));

        assertEquals("空试卷不能发布", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    @Test
    void rejectsCreatingPublishedPaperWithoutQuestions() {
        ExamPaperCreateRequest request = new ExamPaperCreateRequest();
        request.setStatus(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.createExamPaper(request, 1L));

        assertEquals("空试卷不能发布", exception.getMessage());
        verify(examPaperMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPublishingDraftThroughUpdateWithoutQuestions() {
        ExamPaper paper = paper(0, 0);
        ExamPaperCreateRequest request = new ExamPaperCreateRequest();
        request.setStatus(1);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.updateExamPaper(1L, request));

        assertEquals("空试卷不能发布", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    private ExamPaper paper(int status, int questionCount) {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setStatus(status);
        paper.setQuestionCount(questionCount);
        return paper;
    }
}
