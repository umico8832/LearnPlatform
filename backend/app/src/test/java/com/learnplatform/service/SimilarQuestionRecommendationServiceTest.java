package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.dto.SimilarQuestionVO;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimilarQuestionRecommendationServiceTest {
    @Mock private QuestionMapper questionMapper;
    @Mock private PracticeRecordMapper practiceRecordMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private SimilarQuestionRecommendationService service;

    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Question.class);
        TableInfoHelper.initTableInfo(assistant, PracticeRecord.class);
        TableInfoHelper.initTableInfo(assistant, QuestionKnowledgePoint.class);
    }

    @BeforeEach
    void setUp() {
        service = new SimilarQuestionRecommendationService(questionMapper, practiceRecordMapper,
                courseMapper, knowledgePointMapper, questionKnowledgePointMapper);
    }

    @Test
    void filtersUngradableTargetsBeforeSimilarityScoringButKeepsTheSourceReadable() {
        Question source = question(10L, "源题");
        Question eligible = question(12L, "可作答候选");
        when(questionMapper.selectById(10L)).thenReturn(source);
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of());
        when(questionMapper.selectList(any())).thenReturn(List.of(eligible));
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(
                List.of(relation(10L, 3L)), List.of(relation(12L, 3L)));

        SimilarQuestionVO result = service.findSimilarQuestions(7L, 10L, 10);

        assertEquals(10L, result.getSourceQuestionId());
        assertEquals(List.of(12L), result.getSimilarQuestions().stream()
                .map(SimilarQuestionVO.SimilarItem::getQuestionId).toList());
        ArgumentCaptor<LambdaQueryWrapper<Question>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getCustomSqlSegment().contains("question_option"));
    }

    private Question question(Long id, String content) {
        Question question = new Question();
        question.setId(id);
        question.setContent(content);
        question.setQuestionType("SINGLE_CHOICE");
        question.setCourseId(1L);
        question.setDifficulty(2);
        question.setStatus(1);
        question.setVisibility("PUBLIC");
        return question;
    }

    private QuestionKnowledgePoint relation(Long questionId, Long knowledgePointId) {
        QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
        relation.setQuestionId(questionId);
        relation.setKnowledgePointId(knowledgePointId);
        return relation;
    }
}
