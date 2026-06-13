package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.mapper.ExamAnswerMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private ExamAnswerMapper examAnswerMapper;
    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    private ExamService examService;

    @BeforeEach
    void setUp() {
        examService = new ExamService(examRecordMapper, examAnswerMapper, examPaperMapper,
                examQuestionMapper, questionMapper, questionOptionMapper,
                null, new AnswerEvaluator());
    }

    @Test
    void rejectsQuestionOutsidePaper() {
        stubActiveExam();
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion(10L, 5)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examService.submitExam(request(answer(99L, "A")), 7L));

        assertEquals("提交内容包含非本试卷题目", exception.getMessage());
    }

    @Test
    void rejectsDuplicateQuestion() {
        stubActiveExam();
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion(10L, 5)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examService.submitExam(request(answer(10L, "A"), answer(10L, "A")), 7L));

        assertEquals("同一道题不能重复提交", exception.getMessage());
    }

    @Test
    void marksExpiredExamAsTimedOut() {
        ExamRecord record = record(LocalDateTime.now().minusMinutes(61));
        when(examRecordMapper.selectById(1L)).thenReturn(record);
        when(examPaperMapper.selectById(2L)).thenReturn(paper());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examService.submitExam(request(answer(10L, "A")), 7L));

        assertEquals("考试已超时", exception.getMessage());
        assertEquals(2, record.getStatus());
        verify(examRecordMapper).updateById(record);
    }

    private void stubActiveExam() {
        when(examRecordMapper.selectById(1L)).thenReturn(record(LocalDateTime.now()));
        when(examPaperMapper.selectById(2L)).thenReturn(paper());
    }

    private ExamRecord record(LocalDateTime startTime) {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setUserId(7L);
        record.setExamPaperId(2L);
        record.setStartTime(startTime);
        record.setStatus(0);
        return record;
    }

    private ExamPaper paper() {
        ExamPaper paper = new ExamPaper();
        paper.setId(2L);
        paper.setDuration(60);
        paper.setTotalScore(5);
        return paper;
    }

    private ExamQuestion examQuestion(Long questionId, int score) {
        ExamQuestion question = new ExamQuestion();
        question.setQuestionId(questionId);
        question.setScore(score);
        return question;
    }

    private ExamSubmitRequest request(ExamSubmitRequest.AnswerItem... answers) {
        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(1L);
        request.setAnswers(List.of(answers));
        return request;
    }

    private ExamSubmitRequest.AnswerItem answer(Long questionId, String userAnswer) {
        ExamSubmitRequest.AnswerItem answer = new ExamSubmitRequest.AnswerItem();
        answer.setQuestionId(questionId);
        answer.setUserAnswer(userAnswer);
        return answer;
    }
}
