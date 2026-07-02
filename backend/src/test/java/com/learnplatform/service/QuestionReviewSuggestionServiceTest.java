package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.QuestionReviewSuggestionVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.ai.AiProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionReviewSuggestionServiceTest {

    @Mock private AiProvider aiProvider;
    @Mock private AiService aiService;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private CourseMapper courseMapper;

    private QuestionReviewSuggestionService service;
    private Question question;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QuestionOption.class);
        service = new QuestionReviewSuggestionService(
                aiProvider,
                aiService,
                questionMapper,
                questionOptionMapper,
                questionKnowledgePointMapper,
                knowledgePointMapper,
                courseMapper,
                new ObjectMapper());

        question = new Question();
        question.setId(1L);
        question.setContent("以下哪个是 Java 的基本数据类型？");
        question.setQuestionType("SINGLE_CHOICE");
        question.setCourseId(1L);
        question.setDifficulty(2);
        question.setAnalysis("int 是 Java 的基本数据类型，String 是引用类型。");
        question.setSourceType("MANUAL");
        question.setStatus(1);
        question.setReviewRounds(0);
    }

    @Test
    @DisplayName("题目不存在时抛异常")
    void generateSuggestion_questionNotFound_throws() {
        when(questionMapper.selectById(404L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.generateSuggestion(404L, 7L));
    }

    @Test
    @DisplayName("AI 返回标准 JSON 时正确解析复审建议")
    void generateSuggestion_aiReturnsValidJson_parsesCorrectly() {
        when(questionMapper.selectById(1L)).thenReturn(question);
        when(questionOptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(option("A", "int", 1)));
        when(questionKnowledgePointMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiProvider.chat(anyString(), anyString())).thenReturn("""
                {"recommendation":"REVISE","confidenceScore":88,"summary":"题目可用但解析可增强",
                "suggestedContent":"以下哪个属于 Java 基本数据类型？","suggestedDifficulty":2,
                "riskPoints":["解析略短"],"suggestions":["补充错误选项说明"],
                "answerAnalysis":"答案正确","knowledgeAnalysis":"知识点匹配 Java 基础"}
                """);

        QuestionReviewSuggestionVO result = service.generateSuggestion(1L, 7L);

        assertEquals("REVISE", result.getRecommendation());
        assertEquals(88, result.getConfidenceScore());
        assertEquals(2, result.getSuggestedDifficulty());
        assertEquals("答案正确", result.getAnswerAnalysis());
        assertEquals(List.of("解析略短"), result.getRiskPoints());
        assertEquals(List.of("补充错误选项说明"), result.getSuggestions());
        verify(aiService).checkDailyQuota(7L);
        verify(aiService).logCall(eq(7L), eq("question_re_review_suggestion"), eq(true), isNull(), anyInt());
    }

    @Test
    @DisplayName("AI 调用失败时返回基础规则回退建议")
    void generateSuggestion_aiFails_returnsFallback() {
        question.setAnalysis("");
        when(questionMapper.selectById(1L)).thenReturn(question);
        when(questionOptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(questionKnowledgePointMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));

        QuestionReviewSuggestionVO result = service.generateSuggestion(1L, 7L);

        assertEquals("REVISE", result.getRecommendation());
        assertTrue(result.getRiskPoints().contains("解析缺失或过短"));
        assertTrue(result.getSuggestions().contains("补充解析，说明答案依据和易错点"));
        verify(aiService).logCall(eq(7L), eq("question_re_review_suggestion"), eq(false), anyString(), anyInt());
    }

    private QuestionOption option(String label, String content, int isCorrect) {
        QuestionOption option = new QuestionOption();
        option.setQuestionId(1L);
        option.setOptionLabel(label);
        option.setContent(content);
        option.setIsCorrect(isCorrect);
        option.setSortOrder(1);
        return option;
    }
}
