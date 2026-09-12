package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.SubjectiveAnswerReviewVO;
import com.learnplatform.dto.SubjectiveGradingRequest;
import com.learnplatform.entity.ExamAnswer;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.SubjectiveGradingPoint;
import com.learnplatform.mapper.ExamAnswerMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.SubjectiveGradingPointMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectiveExamGradingServiceTest {
    @Mock ExamAnswerMapper answerMapper;
    @Mock ExamRecordMapper recordMapper;
    @Mock ExamPaperMapper paperMapper;
    @Mock ExamQuestionMapper examQuestionMapper;
    @Mock QuestionMapper questionMapper;
    @Mock SubjectiveGradingPointMapper gradingPointMapper;
    @Mock CacheEvictService cacheEvictService;
    SubjectiveExamGradingService service;

    @BeforeEach
    void setUp() {
        service = new SubjectiveExamGradingService(answerMapper, recordMapper, paperMapper, examQuestionMapper,
                questionMapper, gradingPointMapper, new ObjectMapper(), cacheEvictService);
    }

    @Test
    void reviewerScoresEveryRubricPointAndFinalizesRecord() {
        ExamAnswer answer = answer();
        ExamRecord record = record();
        when(answerMapper.selectByIdForUpdate(5L)).thenReturn(answer);
        when(recordMapper.selectByIdForUpdate(7L)).thenReturn(record);
        when(recordMapper.selectById(7L)).thenReturn(record);
        when(gradingPointMapper.selectList(any())).thenReturn(List.of(
                point("idea", 4), point("code", 8), point("output", 1)));
        when(answerMapper.selectList(any())).thenReturn(List.of(answer));
        stubPresentation();

        SubjectiveAnswerReviewVO result = service.grade(5L,
                request(score("idea", 4), score("code", 6), score("output", 1)), 99L);

        assertEquals(11, result.getScore());
        assertEquals("REVIEWED", answer.getGradingStatus());
        assertEquals(11, record.getScore());
        assertEquals(1, record.getStatus());
        verify(answerMapper).updateById(answer);
        verify(recordMapper).updateById(record);
    }

    @Test
    void rejectsMissingRubricPoint() {
        when(answerMapper.selectByIdForUpdate(5L)).thenReturn(answer());
        when(recordMapper.selectByIdForUpdate(7L)).thenReturn(record());
        when(gradingPointMapper.selectList(any())).thenReturn(List.of(point("idea", 4), point("code", 8)));

        assertThrows(RuntimeException.class,
                () -> service.grade(5L, request(score("idea", 4)), 99L));
    }

    private void stubPresentation() {
        ExamQuestion relation = new ExamQuestion();
        relation.setQuestionId(11L);
        relation.setExamPaperId(8L);
        relation.setScore(13);
        relation.setDisplayNumber("第41题");
        when(examQuestionMapper.selectOne(any())).thenReturn(relation);
        Question question = new Question();
        question.setId(11L);
        question.setContent("题干");
        when(questionMapper.selectById(11L)).thenReturn(question);
        ExamPaper paper = new ExamPaper();
        paper.setTitle("2026 年 408 真题");
        when(paperMapper.selectById(8L)).thenReturn(paper);
    }

    private ExamAnswer answer() {
        ExamAnswer answer = new ExamAnswer();
        answer.setId(5L);
        answer.setExamRecordId(7L);
        answer.setQuestionId(11L);
        answer.setUserAnswer("作答");
        answer.setGradingStatus("PENDING");
        return answer;
    }

    private ExamRecord record() {
        ExamRecord record = new ExamRecord();
        record.setId(7L);
        record.setUserId(3L);
        record.setExamPaperId(8L);
        record.setScore(0);
        record.setStatus(3);
        return record;
    }

    private SubjectiveGradingPoint point(String key, int maxScore) {
        SubjectiveGradingPoint point = new SubjectiveGradingPoint();
        point.setQuestionId(11L);
        point.setPointKey(key);
        point.setTitle(key);
        point.setDescription(key);
        point.setReferenceAnswer(key);
        point.setMaxScore(maxScore);
        point.setSortOrder(1);
        return point;
    }

    private SubjectiveGradingRequest request(SubjectiveGradingRequest.PointScore... points) {
        SubjectiveGradingRequest request = new SubjectiveGradingRequest();
        request.setPoints(List.of(points));
        request.setReviewComment("批阅完成");
        return request;
    }

    private SubjectiveGradingRequest.PointScore score(String key, int score) {
        SubjectiveGradingRequest.PointScore point = new SubjectiveGradingRequest.PointScore();
        point.setPointKey(key);
        point.setAwardedScore(score);
        return point;
    }
}
