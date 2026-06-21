package com.learnplatform.service;

import com.learnplatform.dto.*;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiCostCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 复习建议功能单元测试
 *
 * 测试 AiService 中基于复习上下文构建 Prompt 和调用 AI 的逻辑。
 * SpacedRepetitionService.buildReviewContext 依赖数据库，
 * 此处只测 AiService 的 Prompt 构建和调用链路。
 */
@ExtendWith(MockitoExtension.class)
class ReviewAISuggestionTest {

    @Mock
    private AiProvider aiProvider;

    @Mock
    private AiCostCalculator aiCostCalculator;

    @Mock
    private com.learnplatform.config.AiConfig aiConfig;

    @Mock
    private com.learnplatform.mapper.AiCallLogMapper aiCallLogMapper;

    @Mock
    private com.learnplatform.mapper.QuestionMapper questionMapper;

    @Mock
    private com.learnplatform.mapper.QuestionOptionMapper questionOptionMapper;

    @Mock
    private com.learnplatform.mapper.QuestionKnowledgePointMapper questionKnowledgePointMapper;

    @Mock
    private com.learnplatform.mapper.KnowledgePointMapper knowledgePointMapper;

    @Mock
    private com.learnplatform.mapper.CourseMapper courseMapper;

    @Mock
    private com.learnplatform.mapper.WrongQuestionMapper wrongQuestionMapper;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiService(
                aiProvider, aiCostCalculator, aiConfig, aiCallLogMapper,
                questionMapper, questionOptionMapper,
                questionKnowledgePointMapper, knowledgePointMapper,
                courseMapper, wrongQuestionMapper);
    }

    @Test
    void buildPromptWithContext_shouldIncludeStats() {
        // Given
        ReviewStatsVO stats = new ReviewStatsVO();
        stats.setTotalCards(50);
        stats.setDueToday(10);
        stats.setOverdue(3);
        stats.setReviewedToday(5);
        stats.setNewCards(5);
        stats.setLearningCards(30);
        stats.setMasteredCards(15);
        stats.setDifficultCards(4);
        stats.setStreakDays(7);
        stats.setAvgEaseFactor(2.35);

        ReviewContextVO ctx = new ReviewContextVO();
        ctx.setStats(stats);
        ctx.setDifficultCards(Collections.emptyList());
        ctx.setOverdueCards(Collections.emptyList());
        ctx.setRecentDailyReviews(Arrays.asList(3, 5, 0, 2, 4, 6, 5));

        // When
        AiService.AiPrompt prompt = aiService.buildReviewSuggestionPromptWithContext(ctx);

        // Then
        assertNotNull(prompt);
        String userPrompt = prompt.userPrompt();
        assertTrue(userPrompt.contains("总卡片数：50"), "Should contain totalCards");
        assertTrue(userPrompt.contains("今日待复习：10"), "Should contain dueToday");
        assertTrue(userPrompt.contains("逾期未复习：3"), "Should contain overdue");
        assertTrue(userPrompt.contains("连续复习天数：7"), "Should contain streak");
        assertTrue(userPrompt.contains("平均简易因子：2.35"), "Should contain avgEaseFactor");
        assertTrue(userPrompt.contains("近 7 天复习量"), "Should contain daily reviews section");
        assertTrue(userPrompt.contains("今天：5 题"), "Should contain today's review count");

        String sysPrompt = prompt.systemPrompt();
        assertTrue(sysPrompt.contains("间隔重复"), "System prompt should mention spaced repetition");
        assertTrue(sysPrompt.contains("Markdown"), "System prompt should require Markdown output");
    }

    @Test
    void buildPromptWithContext_shouldIncludeDifficultCards() {
        // Given
        ReviewStatsVO stats = new ReviewStatsVO();
        stats.setTotalCards(10);

        ReviewScheduleVO difficult = new ReviewScheduleVO();
        difficult.setQuestionId(100L);
        difficult.setQuestionContent("二叉树的遍历方式");
        difficult.setEaseFactor(new BigDecimal("1.65"));
        difficult.setIntervalDays(2);
        difficult.setTotalReviews(5);
        difficult.setCourseName("数据结构");

        ReviewContextVO ctx = new ReviewContextVO();
        ctx.setStats(stats);
        ctx.setDifficultCards(List.of(difficult));
        ctx.setOverdueCards(Collections.emptyList());
        ctx.setRecentDailyReviews(Arrays.asList(0, 0, 0, 0, 0, 0, 0));

        // When
        AiService.AiPrompt prompt = aiService.buildReviewSuggestionPromptWithContext(ctx);

        // Then
        String userPrompt = prompt.userPrompt();
        assertTrue(userPrompt.contains("困难卡片"), "Should contain difficult cards section");
        assertTrue(userPrompt.contains("二叉树的遍历方式"), "Should contain difficult card content");
        assertTrue(userPrompt.contains("1.65"), "Should contain ease factor");
        assertTrue(userPrompt.contains("数据结构"), "Should contain course name");
    }

    @Test
    void buildPromptWithContext_shouldIncludeOverdueCards() {
        // Given
        ReviewStatsVO stats = new ReviewStatsVO();
        stats.setTotalCards(10);

        ReviewScheduleVO overdue = new ReviewScheduleVO();
        overdue.setQuestionId(200L);
        overdue.setQuestionContent("TCP 三次握手过程");
        overdue.setOverdueDays(5);
        overdue.setCourseName("计算机网络");

        ReviewContextVO ctx = new ReviewContextVO();
        ctx.setStats(stats);
        ctx.setDifficultCards(Collections.emptyList());
        ctx.setOverdueCards(List.of(overdue));
        ctx.setRecentDailyReviews(Arrays.asList(0, 0, 0, 0, 0, 0, 0));

        // When
        AiService.AiPrompt prompt = aiService.buildReviewSuggestionPromptWithContext(ctx);

        // Then
        String userPrompt = prompt.userPrompt();
        assertTrue(userPrompt.contains("逾期卡片"), "Should contain overdue cards section");
        assertTrue(userPrompt.contains("TCP 三次握手过程"), "Should contain overdue card content");
        assertTrue(userPrompt.contains("逾期 5 天"), "Should contain overdue days");
    }

    @Test
    void generateReviewBasedSuggestionWithContext_shouldCallAiProvider() {
        // Given
        ReviewStatsVO stats = new ReviewStatsVO();
        stats.setTotalCards(20);

        ReviewContextVO ctx = new ReviewContextVO();
        ctx.setStats(stats);
        ctx.setDifficultCards(Collections.emptyList());
        ctx.setOverdueCards(Collections.emptyList());
        ctx.setRecentDailyReviews(Arrays.asList(1, 2, 3, 4, 5, 6, 7));

        Long userId = 1L;

        when(aiConfig.getDailyQuota()).thenReturn(50);
        when(aiProvider.chat(anyString(), anyString())).thenReturn("AI 复习建议内容");
        when(aiCallLogMapper.insert(any())).thenReturn(1);

        // When
        AiResponse response = aiService.generateReviewBasedSuggestionWithContext(userId, ctx);

        // Then
        assertNotNull(response);
        assertEquals("AI 复习建议内容", response.getContent());
        verify(aiProvider).chat(anyString(), anyString());
    }

    @Test
    void generateReviewBasedSuggestionStreamWithContext_shouldCallStreamApi() {
        // Given
        ReviewStatsVO stats = new ReviewStatsVO();
        stats.setTotalCards(10);

        ReviewContextVO ctx = new ReviewContextVO();
        ctx.setStats(stats);
        ctx.setDifficultCards(Collections.emptyList());
        ctx.setOverdueCards(Collections.emptyList());
        ctx.setRecentDailyReviews(Arrays.asList(0, 0, 0, 0, 0, 0, 0));

        Long userId = 1L;
        AtomicReference<String> captured = new AtomicReference<>("");
        Consumer<String> onContent = captured::set;

        when(aiConfig.getDailyQuota()).thenReturn(50);
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("流式内容片段");
            return null;
        }).when(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));
        when(aiCallLogMapper.insert(any())).thenReturn(1);

        // When
        aiService.generateReviewBasedSuggestionStreamWithContext(userId, ctx, onContent);

        // Then
        assertEquals("流式内容片段", captured.get());
        verify(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));
    }
}
