package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamPaperLearningContextServiceTest {

    @Mock
    private ExamPaperMapper examPaperMapper;

    @Mock
    private ExamQuestionMapper examQuestionMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionOptionMapper questionOptionMapper;

    @Mock
    private UserCourseMapper userCourseMapper;

    private ExamPaperLearningContextService contextService;

    @BeforeEach
    void setUp() {
        contextService = new ExamPaperLearningContextService(
                examPaperMapper, examQuestionMapper, questionMapper, questionOptionMapper, userCourseMapper);
    }

    @Test
    void rejectsLearningCoursePaperOutsideUserLibrary() {
        when(examPaperMapper.selectById(2L)).thenReturn(paper());
        when(userCourseMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> contextService.loadEligiblePaper(2L, 7L));

        assertEquals("请先将课程加入课程库", exception.getMessage());
    }

    @Test
    void hidesAnotherUsersPrivatePaper() {
        ExamPaper paper = paper();
        paper.setVisibility("PRIVATE");
        paper.setOwnerUserId(8L);
        when(examPaperMapper.selectById(2L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> contextService.loadEligiblePaper(2L, 7L));

        assertEquals("试卷不存在", exception.getMessage());
        verifyNoInteractions(userCourseMapper);
    }

    @Test
    void rejectsQuestionOutsidePaperCourse() {
        ExamQuestion paperQuestion = examQuestion();
        Question question = question();
        question.setCourseId(21L);
        when(questionMapper.selectById(10L)).thenReturn(question);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> contextService.loadPaperQuestion(paper(), paperQuestion));

        assertEquals("试卷题目不属于试卷课程", exception.getMessage());
    }

    @Test
    void loadsEligiblePaperQuestionsAndOptions() {
        ExamPaper paper = paper();
        ExamQuestion paperQuestion = examQuestion();
        Question question = question();
        QuestionOption option = new QuestionOption();
        option.setQuestionId(10L);
        option.setSortOrder(1);
        when(examPaperMapper.selectById(2L)).thenReturn(paper);
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(paperQuestion));
        when(questionMapper.selectById(10L)).thenReturn(question);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(option));

        assertEquals(paper, contextService.loadEligiblePaper(2L, 7L));
        assertEquals(List.of(paperQuestion), contextService.loadPaperQuestions(paper));
        assertEquals(List.of(option), contextService.loadOptions(10L));
    }

    private ExamPaper paper() {
        ExamPaper paper = new ExamPaper();
        paper.setId(2L);
        paper.setCourseId(20L);
        paper.setStatus(1);
        return paper;
    }

    private ExamQuestion examQuestion() {
        ExamQuestion item = new ExamQuestion();
        item.setExamPaperId(2L);
        item.setQuestionId(10L);
        item.setSortOrder(1);
        return item;
    }

    private Question question() {
        Question question = new Question();
        question.setId(10L);
        question.setCourseId(20L);
        question.setStatus(1);
        return question;
    }
}
