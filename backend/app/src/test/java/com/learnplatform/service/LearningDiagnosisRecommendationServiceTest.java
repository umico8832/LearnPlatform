package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningDiagnosisRecommendationServiceTest {
    private static final Long USER_ID = 7L;

    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    private LearningDiagnosisRecommendationService service;

    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Question.class);
    }

    @BeforeEach
    void setUp() {
        service = new LearningDiagnosisRecommendationService(
                questionKnowledgePointMapper, questionMapper, courseMapper, knowledgePointMapper);
    }

    @Test
    void skipsUngradableRepeatedWrongQuestionsWithoutUsingRecommendationSlots() {
        Question valid = question(12L);
        when(questionMapper.selectList(any())).thenReturn(List.of(valid));

        List<LearningDiagnosisVO.RecommendedQuestion> recommendations = service.recommend(
                USER_ID, List.of(), List.of(wrong(11L), wrong(12L)), List.of(), Map.of());

        assertEquals(List.of(12L), recommendations.stream()
                .map(LearningDiagnosisVO.RecommendedQuestion::getQuestionId).toList());
        assertUsesAutomaticGradingPolicy();
    }

    @Test
    void skipsUngradableWeakKnowledgePointCandidatesBeforeFillingUntriedRecommendations() {
        Question valid = question(12L);
        when(questionMapper.selectList(any())).thenReturn(List.of(valid));
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(
                relation(100L, 3L), relation(11L, 3L), relation(12L, 3L)));
        List<PracticeRecord> records = List.of(record(100L, 0), record(100L, 0));

        List<LearningDiagnosisVO.RecommendedQuestion> recommendations = service.recommend(
                USER_ID, records, List.of(), List.of(knowledgePoint(3L)), Map.of(100L, Set.of(3L)));

        assertEquals(List.of(12L), recommendations.stream()
                .map(LearningDiagnosisVO.RecommendedQuestion::getQuestionId).toList());
        assertUsesAutomaticGradingPolicy();
    }

    @Test
    void recommendsAQuestionAgainWhenItsGradingBasisIsRestored() {
        Question valid = question(12L);
        when(questionMapper.selectList(any())).thenReturn(List.of(), List.of(valid));

        assertTrue(service.recommend(USER_ID, List.of(), List.of(wrong(12L)), List.of(), Map.of()).isEmpty());
        assertEquals(List.of(12L), service.recommend(USER_ID, List.of(), List.of(wrong(12L)), List.of(), Map.of())
                .stream().map(LearningDiagnosisVO.RecommendedQuestion::getQuestionId).toList());
    }

    private void assertUsesAutomaticGradingPolicy() {
        ArgumentCaptor<LambdaQueryWrapper<Question>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getCustomSqlSegment().contains("question_option"));
    }

    private Question question(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setQuestionType("SINGLE_CHOICE");
        question.setCourseId(1L);
        question.setStatus(1);
        question.setVisibility("PUBLIC");
        question.setContent("候选题 " + id);
        question.setDifficulty(2);
        return question;
    }

    private WrongQuestion wrong(Long questionId) {
        WrongQuestion wrong = new WrongQuestion();
        wrong.setQuestionId(questionId);
        wrong.setWrongCount(2);
        wrong.setMasteryLevel(0);
        return wrong;
    }

    private PracticeRecord record(Long questionId, int correct) {
        PracticeRecord record = new PracticeRecord();
        record.setQuestionId(questionId);
        record.setIsCorrect(correct);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private KnowledgePoint knowledgePoint(Long id) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setCourseId(1L);
        return point;
    }

    private QuestionKnowledgePoint relation(Long questionId, Long knowledgePointId) {
        QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
        relation.setQuestionId(questionId);
        relation.setKnowledgePointId(knowledgePointId);
        return relation;
    }
}
