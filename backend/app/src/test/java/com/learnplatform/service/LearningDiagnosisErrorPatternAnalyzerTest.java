package com.learnplatform.service;

import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningDiagnosisErrorPatternAnalyzerTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private CourseMapper courseMapper;

    private LearningDiagnosisErrorPatternAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new LearningDiagnosisErrorPatternAnalyzer(questionMapper, courseMapper);
    }

    @Test
    void countsTwoAndThreeWrongAttemptsAndKeepsTheirMasteryInTheDetailedList() {
        List<WrongQuestion> wrongs = List.of(
                wrong(1L, 1, 0), wrong(2L, 2, 1), wrong(3L, 3, 2));
        stubQuestions(1L, 2L, 3L);

        LearningDiagnosisVO.ErrorPatternSummary summary = analyzer.compute(
                wrongs, List.of(), List.of(), Map.of());

        assertEquals(2, summary.getRepeatedErrorCount());
        assertEquals(List.of(3L, 2L), summary.getRepeatedErrors().stream()
                .map(LearningDiagnosisVO.RepeatedErrorItem::getQuestionId).toList());
        assertEquals(List.of(2, 1), summary.getRepeatedErrors().stream()
                .map(LearningDiagnosisVO.RepeatedErrorItem::getMasteryLevel).toList());
    }

    @Test
    void countsEveryRepeatedErrorEvenWhenDetailedListIsLimitedToTen() {
        List<WrongQuestion> wrongs = java.util.stream.LongStream.rangeClosed(1, 11)
                .mapToObj(id -> wrong(id, 2, 0))
                .toList();
        stubQuestions(wrongs.stream().map(WrongQuestion::getQuestionId).toList());

        LearningDiagnosisVO.ErrorPatternSummary summary = analyzer.compute(
                wrongs, List.of(), List.of(), Map.of());

        assertEquals(11, summary.getRepeatedErrorCount());
        assertEquals(10, summary.getRepeatedErrors().size());
    }

    private void stubQuestions(Long... ids) {
        stubQuestions(List.of(ids));
    }

    private void stubQuestions(List<Long> ids) {
        when(questionMapper.selectList(any())).thenReturn(ids.stream().map(this::question).toList());
    }

    private WrongQuestion wrong(Long questionId, int count, int masteryLevel) {
        WrongQuestion wrong = new WrongQuestion();
        wrong.setQuestionId(questionId);
        wrong.setWrongCount(count);
        wrong.setMasteryLevel(masteryLevel);
        return wrong;
    }

    private Question question(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setContent("题目" + id);
        question.setQuestionType("SINGLE_CHOICE");
        question.setDifficulty(2);
        return question;
    }
}
