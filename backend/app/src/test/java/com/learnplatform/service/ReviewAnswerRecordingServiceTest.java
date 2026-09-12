package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewAnswerRecordingServiceTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private PracticeRecordMapper practiceRecordMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private AnswerEvaluator answerEvaluator;
    @Mock private CourseLearningEventService courseLearningEventService;

    private ReviewAnswerRecordingService service;

    @BeforeEach
    void setUp() {
        service = new ReviewAnswerRecordingService(
                questionMapper,
                questionOptionMapper,
                practiceRecordMapper,
                wrongQuestionMapper,
                answerEvaluator,
                courseLearningEventService);
    }

    @Test
    void evaluateAndRecordCorrectAnswerPersistsAttemptAndRemovesWrongQuestion() {
        ReviewSubmitRequest request = request(" A ");
        Question question = question();
        QuestionOption option = correctOption();
        WrongQuestion wrongQuestion = new WrongQuestion();
        wrongQuestion.setId(31L);
        when(questionMapper.selectById(12L)).thenReturn(question);
        when(questionOptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(option));
        when(answerEvaluator.buildCorrectAnswer(List.of(option), "SINGLE_CHOICE")).thenReturn("A");
        when(answerEvaluator.isCorrect("SINGLE_CHOICE", " A ", "A")).thenReturn(true);
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wrongQuestion);

        assertTrue(service.evaluateAndRecord(request, 7L));

        ArgumentCaptor<PracticeRecord> recordCaptor = ArgumentCaptor.forClass(PracticeRecord.class);
        verify(practiceRecordMapper).insert(recordCaptor.capture());
        assertEquals("A", recordCaptor.getValue().getUserAnswer());
        assertEquals(1, recordCaptor.getValue().getIsCorrect());
        verify(wrongQuestionMapper).deleteById(31L);
        verify(courseLearningEventService).recordQuestionAnswer(
                7L, question, "REVIEW_ANSWERED", "REVIEW", null, true, null);
    }

    @Test
    void evaluateAndRecordWrongAnswerCreatesWrongQuestion() {
        ReviewSubmitRequest request = request("B");
        QuestionOption option = correctOption();
        when(questionMapper.selectById(12L)).thenReturn(question());
        when(questionOptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(option));
        when(answerEvaluator.buildCorrectAnswer(List.of(option), "SINGLE_CHOICE")).thenReturn("A");
        when(answerEvaluator.isCorrect("SINGLE_CHOICE", "B", "A")).thenReturn(false);
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertFalse(service.evaluateAndRecord(request, 7L));

        ArgumentCaptor<WrongQuestion> wrongCaptor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionMapper).insert(wrongCaptor.capture());
        assertEquals(12L, wrongCaptor.getValue().getQuestionId());
        assertEquals("B", wrongCaptor.getValue().getLastWrongAnswer());
        assertEquals(1, wrongCaptor.getValue().getWrongCount());
    }

    @Test
    void evaluateAndRecordRejectsMissingQuestionBeforeWritingFacts() {
        ReviewSubmitRequest request = request("A");
        when(questionMapper.selectById(12L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.evaluateAndRecord(request, 7L));

        verify(practiceRecordMapper, never()).insert(any());
        verify(wrongQuestionMapper, never()).insert(any());
    }

    private ReviewSubmitRequest request(String answer) {
        ReviewSubmitRequest request = new ReviewSubmitRequest();
        request.setQuestionId(12L);
        request.setUserAnswer(answer);
        request.setAnswerTime(30);
        return request;
    }

    private Question question() {
        Question question = new Question();
        question.setId(12L);
        question.setQuestionType("SINGLE_CHOICE");
        return question;
    }

    private QuestionOption correctOption() {
        QuestionOption option = new QuestionOption();
        option.setOptionLabel("A");
        option.setIsCorrect(1);
        return option;
    }
}
