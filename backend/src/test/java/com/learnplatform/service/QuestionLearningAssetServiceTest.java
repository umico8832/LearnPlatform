package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.AiConfig;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.QuestionLearningAssetVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionLearningAssetServiceTest {

    @Mock private AiProvider aiProvider;
    @Mock private AiConfig aiConfig;
    @Mock private AiService aiService;
    @Mock private QuestionAiAssetMapper questionAiAssetMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private CourseMapper courseMapper;

    private QuestionLearningAssetService service;

    @BeforeEach
    void setUp() {
        service = new QuestionLearningAssetService(
                aiProvider, aiConfig, aiService,
                questionAiAssetMapper, questionMapper, questionOptionMapper,
                questionKnowledgePointMapper, knowledgePointMapper, courseMapper
        );
    }

    // ======================== getAssets ========================

    @Test
    void getAssetsThrowsWhenQuestionNotFound() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getAssets(999L));
        assertEquals("题目不存在", ex.getMessage());
    }

    @Test
    void getAssetsReturnsEmptyListWhenNoCachedAssets() {
        when(questionMapper.selectById(1L)).thenReturn(stubQuestion());
        when(questionAiAssetMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<QuestionLearningAssetVO> result = service.getAssets(1L);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void getAssetsReturnsCachedAssets() {
        when(questionMapper.selectById(1L)).thenReturn(stubQuestion());
        QuestionAiAsset asset = stubAsset(1L, "FULL_EXPLANATION", "explanation content");
        when(questionAiAssetMapper.selectList(any())).thenReturn(List.of(asset));

        List<QuestionLearningAssetVO> result = service.getAssets(1L);

        assertEquals(1, result.size());
        QuestionLearningAssetVO vo = result.get(0);
        assertEquals(1L, vo.getQuestionId());
        assertEquals("FULL_EXPLANATION", vo.getAssetType());
        assertEquals("标准解析", vo.getAssetTypeLabel());
        assertEquals("explanation content", vo.getContent());
    }

    @Test
    void getAssetsReturnsMultipleCachedAssets() {
        when(questionMapper.selectById(1L)).thenReturn(stubQuestion());
        when(questionAiAssetMapper.selectList(any())).thenReturn(List.of(
                stubAsset(1L, "FULL_EXPLANATION", "full"),
                stubAsset(1L, "BEGINNER_EXPLANATION", "beginner"),
                stubAsset(1L, "VARIANT", "variant")
        ));

        List<QuestionLearningAssetVO> result = service.getAssets(1L);

        assertEquals(3, result.size());
        assertEquals("FULL_EXPLANATION", result.get(0).getAssetType());
        assertEquals("BEGINNER_EXPLANATION", result.get(1).getAssetType());
        assertEquals("VARIANT", result.get(2).getAssetType());
    }

    // ======================== getAsset ========================

    @Test
    void getAssetReturnsNullWhenNotCached() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);

        QuestionLearningAssetVO result = service.getAsset(1L, AiAssetType.FULL_EXPLANATION);

        assertNull(result);
    }

    @Test
    void getAssetReturnsCachedAsset() {
        QuestionAiAsset asset = stubAsset(1L, "FULL_EXPLANATION", "content");
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);

        QuestionLearningAssetVO result = service.getAsset(1L, AiAssetType.FULL_EXPLANATION);

        assertNotNull(result);
        assertEquals("FULL_EXPLANATION", result.getAssetType());
        assertEquals("content", result.getContent());
    }

    // ======================== generateOrGetAsset ========================

    @Test
    void generateOrGetAssetReturnsCachedContentWithoutCallingAi() {
        QuestionAiAsset cached = stubAsset(1L, "FULL_EXPLANATION", "cached content");
        when(questionAiAssetMapper.selectOne(any())).thenReturn(cached);

        QuestionLearningAssetVO result = service.generateOrGetAsset(1L, AiAssetType.FULL_EXPLANATION, 7L);

        assertEquals("cached content", result.getContent());
        verify(aiProvider, never()).chat(anyString(), anyString());
        verify(aiService, never()).checkDailyQuota(any());
    }

    @Test
    void generateOrGetAssetCallsAiWhenNoCache() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);
        setupFullQuestionContext();

        when(aiConfig.getModel()).thenReturn("gpt-4");
        when(aiProvider.chat(anyString(), anyString())).thenReturn("AI generated content");
        when(questionAiAssetMapper.insert(any())).thenReturn(1);

        QuestionLearningAssetVO result = service.generateOrGetAsset(1L, AiAssetType.FULL_EXPLANATION, 7L);

        assertNotNull(result);
        assertEquals("AI generated content", result.getContent());
        assertEquals("FULL_EXPLANATION", result.getAssetType());
        assertEquals("标准解析", result.getAssetTypeLabel());

        verify(aiService).checkDailyQuota(7L);
        verify(aiProvider).chat(anyString(), anyString());

        ArgumentCaptor<QuestionAiAsset> captor = ArgumentCaptor.forClass(QuestionAiAsset.class);
        verify(questionAiAssetMapper).insert(captor.capture());
        QuestionAiAsset saved = captor.getValue();
        assertEquals(1L, saved.getQuestionId());
        assertEquals("FULL_EXPLANATION", saved.getAssetType());
        assertEquals("AI generated content", saved.getContent());
        assertEquals("gpt-4", saved.getModel());
    }

    @Test
    void generateOrGetAssetLogsCallOnSuccess() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);
        setupFullQuestionContext();
        when(aiConfig.getModel()).thenReturn("gpt-4");
        when(aiProvider.chat(anyString(), anyString())).thenReturn("content");
        when(questionAiAssetMapper.insert(any())).thenReturn(1);

        service.generateOrGetAsset(1L, AiAssetType.BEGINNER_EXPLANATION, 7L);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).logCall(eq(7L), typeCaptor.capture(), eq(true), eq(null), any(Integer.class));
        assertEquals("asset_beginner_explanation", typeCaptor.getValue());
    }

    @Test
    void generateOrGetAssetLogsCallOnFailure() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);
        setupFullQuestionContext();
        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("API error"));

        assertThrows(RuntimeException.class,
                () -> service.generateOrGetAsset(1L, AiAssetType.FULL_EXPLANATION, 7L));

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).logCall(eq(7L), eq("asset_full_explanation"), eq(false), errorCaptor.capture(), any(Integer.class));
        assertEquals("API error", errorCaptor.getValue());
    }

    @Test
    void generateOrGetAssetGeneratesAllSixAssetTypes() {
        for (AiAssetType type : AiAssetType.values()) {
            // Reset mocks for each iteration
            org.mockito.Mockito.reset(questionAiAssetMapper, questionMapper, questionOptionMapper,
                    questionKnowledgePointMapper, knowledgePointMapper, courseMapper, aiProvider);

            when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
            doNothing().when(aiService).checkDailyQuota(7L);
            setupFullQuestionContext();
            when(aiConfig.getModel()).thenReturn("gpt-4");
            when(aiProvider.chat(anyString(), anyString())).thenReturn("content for " + type);
            when(questionAiAssetMapper.insert(any())).thenReturn(1);

            QuestionLearningAssetVO result = service.generateOrGetAsset(1L, type, 7L);

            assertNotNull(result);
            assertEquals(type.name(), result.getAssetType());
            assertEquals(type.getLabel(), result.getAssetTypeLabel());
        }
    }

    // ======================== generateAssetStream ========================

    @Test
    void generateAssetStreamReturnsCachedContentDirectly() {
        QuestionAiAsset cached = stubAsset(1L, "FULL_EXPLANATION", "cached stream");
        when(questionAiAssetMapper.selectOne(any())).thenReturn(cached);

        List<String> receivedChunks = new ArrayList<>();
        service.generateAssetStream(1L, AiAssetType.FULL_EXPLANATION, 7L, receivedChunks::add);

        assertEquals(1, receivedChunks.size());
        assertEquals("cached stream", receivedChunks.get(0));
        verify(aiProvider, never()).chatStream(anyString(), anyString(), any());
    }

    @Test
    void generateAssetStreamCallsAiStreamWhenNoCache() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);
        setupFullQuestionContext();
        when(aiConfig.getModel()).thenReturn("gpt-4");

        // Simulate streaming chunks
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<String> callback = invocation.getArgument(2);
            callback.accept("chunk1");
            callback.accept("chunk2");
            callback.accept("chunk3");
            return null;
        }).when(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));

        when(questionAiAssetMapper.insert(any())).thenReturn(1);

        List<String> receivedChunks = new ArrayList<>();
        service.generateAssetStream(1L, AiAssetType.STEP_BY_STEP, 7L, receivedChunks::add);

        assertEquals(3, receivedChunks.size());
        assertEquals("chunk1", receivedChunks.get(0));
        assertEquals("chunk2", receivedChunks.get(1));
        assertEquals("chunk3", receivedChunks.get(2));

        verify(aiService).checkDailyQuota(7L);
        verify(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));

        // Verify full content was saved
        ArgumentCaptor<QuestionAiAsset> captor = ArgumentCaptor.forClass(QuestionAiAsset.class);
        verify(questionAiAssetMapper).insert(captor.capture());
        assertEquals("chunk1chunk2chunk3", captor.getValue().getContent());
    }

    @Test
    void generateAssetStreamLogsCallOnSuccess() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);
        setupFullQuestionContext();
        when(aiConfig.getModel()).thenReturn("gpt-4");
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<String> callback = invocation.getArgument(2);
            callback.accept("content");
            return null;
        }).when(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));
        when(questionAiAssetMapper.insert(any())).thenReturn(1);

        service.generateAssetStream(1L, AiAssetType.VARIANT, 7L, chunk -> {});

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).logCall(eq(7L), typeCaptor.capture(), eq(true), eq(null), any(Integer.class));
        assertEquals("asset_variant_stream", typeCaptor.getValue());
    }

    @Test
    void generateAssetStreamLogsCallOnFailure() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);
        setupFullQuestionContext();
        org.mockito.Mockito.doThrow(new RuntimeException("stream error"))
                .when(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));

        assertThrows(RuntimeException.class,
                () -> service.generateAssetStream(1L, AiAssetType.COMMON_MISTAKES, 7L, chunk -> {}));

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).logCall(eq(7L), eq("asset_common_mistakes_stream"), eq(false), errorCaptor.capture(), any(Integer.class));
        assertEquals("stream error", errorCaptor.getValue());
    }

    @Test
    void generateAssetStreamContinuesWhenCacheSaveFails() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);
        setupFullQuestionContext();
        when(aiConfig.getModel()).thenReturn("gpt-4");
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<String> callback = invocation.getArgument(2);
            callback.accept("content");
            return null;
        }).when(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));
        when(questionAiAssetMapper.insert(any())).thenThrow(new RuntimeException("db error"));

        // Should not throw - cache save failure is swallowed
        List<String> receivedChunks = new ArrayList<>();
        service.generateAssetStream(1L, AiAssetType.FULL_EXPLANATION, 7L, receivedChunks::add);

        assertEquals(1, receivedChunks.size());
        assertEquals("content", receivedChunks.get(0));
    }

    // ======================== clearAssets ========================

    @Test
    void clearAssetsDeletesAllAssetsForQuestion() {
        QuestionAiAsset asset1 = stubAsset(1L, "FULL_EXPLANATION", "c1");
        asset1.setId(10L);
        QuestionAiAsset asset2 = stubAsset(1L, "BEGINNER_EXPLANATION", "c2");
        asset2.setId(11L);
        when(questionAiAssetMapper.selectList(any())).thenReturn(List.of(asset1, asset2));

        // Track deleteById calls via doAnswer since deleteById has ambiguous overloads
        List<Long> deletedIds = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            deletedIds.add(invocation.getArgument(0));
            return 1;
        }).when(questionAiAssetMapper).deleteById(anyLong());

        service.clearAssets(1L);

        assertEquals(2, deletedIds.size());
        assertEquals(10L, deletedIds.get(0));
        assertEquals(11L, deletedIds.get(1));
    }

    @Test
    void clearAssetsHandlesNoAssets() {
        when(questionAiAssetMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.clearAssets(1L);

        verify(questionAiAssetMapper, times(0)).deleteById(anyLong());
    }

    // ======================== Prompt content verification ========================

    @Test
    void generateOrGetAssetBuildsCorrectQuestionContext() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);

        // Set up question with full context
        Question question = stubQuestion();
        question.setContent("What is polymorphism?");
        question.setAnalysis("Polymorphism means many forms");
        question.setCourseId(100L);
        when(questionMapper.selectById(1L)).thenReturn(question);

        QuestionOption optA = new QuestionOption();
        optA.setOptionLabel("A");
        optA.setContent("Many forms");
        optA.setIsCorrect(1);
        QuestionOption optB = new QuestionOption();
        optB.setOptionLabel("B");
        optB.setContent("Single form");
        optB.setIsCorrect(0);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(optA, optB));

        QuestionKnowledgePoint kp = new QuestionKnowledgePoint();
        kp.setKnowledgePointId(200L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(kp));

        KnowledgePoint kpEntity = new KnowledgePoint();
        kpEntity.setName("OOP Basics");
        when(knowledgePointMapper.selectBatchIds(any())).thenReturn(List.of(kpEntity));

        Course course = new Course();
        course.setName("Java Programming");
        when(courseMapper.selectById(100L)).thenReturn(course);

        when(aiConfig.getModel()).thenReturn("gpt-4");
        when(aiProvider.chat(anyString(), anyString())).thenReturn("explanation");

        // Capture the user prompt sent to AI
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(questionAiAssetMapper.insert(any())).thenReturn(1);

        service.generateOrGetAsset(1L, AiAssetType.FULL_EXPLANATION, 7L);

        verify(aiProvider).chat(anyString(), userPromptCaptor.capture());
        String userPrompt = userPromptCaptor.getValue();

        // Verify question context contains all expected info
        assertContains(userPrompt, "题型：单选题");
        assertContains(userPrompt, "难度：3/5");
        assertContains(userPrompt, "题目：What is polymorphism?");
        assertContains(userPrompt, "A. Many forms [正确答案]");
        assertContains(userPrompt, "B. Single form");
        assertContains(userPrompt, "原始解析：Polymorphism means many forms");
        assertContains(userPrompt, "知识点：OOP Basics");
        assertContains(userPrompt, "所属课程：Java Programming");
    }

    @Test
    void generateOrGetAssetHandlesQuestionWithNoOptions() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);
        doNothing().when(aiService).checkDailyQuota(7L);

        Question question = stubQuestion();
        question.setQuestionType("SHORT_ANSWER");
        when(questionMapper.selectById(1L)).thenReturn(question);
        when(questionOptionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(aiConfig.getModel()).thenReturn("gpt-4");
        when(aiProvider.chat(anyString(), anyString())).thenReturn("answer");
        when(questionAiAssetMapper.insert(any())).thenReturn(1);

        QuestionLearningAssetVO result = service.generateOrGetAsset(1L, AiAssetType.STEP_BY_STEP, 7L);

        assertNotNull(result);
        assertEquals("STEP_BY_STEP", result.getAssetType());
    }

    // ======================== Type label verification ========================

    @Test
    void toVOMapsKnownAssetTypeLabels() {
        QuestionAiAsset asset = stubAsset(1L, "STEP_BY_STEP", "steps");
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);

        QuestionLearningAssetVO result = service.getAsset(1L, AiAssetType.STEP_BY_STEP);

        assertEquals("步骤拆解", result.getAssetTypeLabel());
    }

    @Test
    void toVOHandlesUnknownAssetTypeLabel() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(1L);
        asset.setQuestionId(1L);
        asset.setAssetType("UNKNOWN_TYPE");
        asset.setContent("content");
        asset.setModel("gpt-4");

        // Use getAssets since getAsset requires AiAssetType enum
        when(questionMapper.selectById(1L)).thenReturn(stubQuestion());
        when(questionAiAssetMapper.selectList(any())).thenReturn(List.of(asset));

        List<QuestionLearningAssetVO> result = service.getAssets(1L);

        assertEquals(1, result.size());
        assertEquals("UNKNOWN_TYPE", result.get(0).getAssetTypeLabel());
    }

    // ======================== Helpers ========================

    private Question stubQuestion() {
        Question question = new Question();
        question.setId(1L);
        question.setContent("Test question content");
        question.setQuestionType("SINGLE_CHOICE");
        question.setDifficulty(3);
        question.setCourseId(1L);
        return question;
    }

    private QuestionAiAsset stubAsset(Long questionId, String assetType, String content) {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(questionId * 100 + assetType.hashCode() % 100);
        asset.setQuestionId(questionId);
        asset.setAssetType(assetType);
        asset.setContent(content);
        asset.setModel("gpt-4");
        return asset;
    }

    private void setupFullQuestionContext() {
        Question question = stubQuestion();
        when(questionMapper.selectById(1L)).thenReturn(question);
        when(questionOptionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    private void assertContains(String text, String expected) {
        if (!text.contains(expected)) {
            throw new AssertionError("Expected text to contain '" + expected + "' but was: " + text);
        }
    }
}