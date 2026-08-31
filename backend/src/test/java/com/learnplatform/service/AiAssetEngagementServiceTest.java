package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiVariantTrainingVO;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiAssetViewMapper;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssetEngagementServiceTest {

    @Mock private AiAssetViewMapper aiAssetViewMapper;
    @Mock private QuestionAiAssetMapper questionAiAssetMapper;
    @Mock private AiVariantTrainingMapper aiVariantTrainingMapper;
    @Mock private AiVariantQuestionService aiVariantQuestionService;

    private AiAssetEngagementService service() {
        return new AiAssetEngagementService(aiAssetViewMapper, questionAiAssetMapper,
                aiVariantTrainingMapper, aiVariantQuestionService);
    }

    @Test
    void recordAssetViewRequiresExistingAsset() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().recordAssetView(8L, AiAssetType.FULL_EXPLANATION, 3L));

        assertEquals("学习资产不存在", exception.getMessage());
        verify(aiAssetViewMapper, never()).upsertDailyView(any(), any(), any());
    }

    @Test
    void recordAssetViewUsesAtomicDailyUpsert() {
        when(questionAiAssetMapper.selectOne(any())).thenReturn(new QuestionAiAsset());

        service().recordAssetView(8L, AiAssetType.STEP_BY_STEP, 3L);

        verify(aiAssetViewMapper).upsertDailyView(3L, 8L, "STEP_BY_STEP");
        verify(aiVariantTrainingMapper, never()).upsertStarted(any(), any(), any());
    }

    @Test
    void recordVariantViewStartsTrainingForCurrentAsset() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(15L);
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);
        AiVariantTraining training = variantTraining(3L, 8L, 15L, "STARTED",
                LocalDateTime.now(), null);
        when(aiVariantTrainingMapper.selectOne(any())).thenReturn(training);

        var result = service().recordAssetView(8L, AiAssetType.VARIANT, 3L);

        verify(aiAssetViewMapper).upsertDailyView(3L, 8L, "VARIANT");
        verify(aiVariantTrainingMapper).upsertStarted(3L, 8L, 15L);
        assertEquals("STARTED", result.getStatus());
        assertEquals(false, result.getCompleted());
    }

    @Test
    void completeVariantTrainingRequiresStartedRecord() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(15L);
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);
        when(aiVariantTrainingMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().completeVariantTraining(8L, 3L));

        assertEquals("请先查看变式题，再标记训练完成", exception.getMessage());
        verify(aiVariantTrainingMapper, never()).updateById(any());
    }

    @Test
    void completeVariantTrainingMarksCurrentAssetAndReturnsStatus() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(15L);
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);
        AiVariantTraining training = variantTraining(3L, 8L, 15L, "STARTED",
                LocalDateTime.now().minusMinutes(5), null);
        training.setId(20L);
        when(aiVariantTrainingMapper.selectOne(any())).thenReturn(training);

        var result = service().completeVariantTraining(8L, 3L);

        verify(aiVariantTrainingMapper).updateById(training);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(true, result.getCompleted());
    }

    @Test
    void completeVariantTrainingRejectsStructuredQuestion() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(15L);
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);
        when(aiVariantQuestionService.hasStructuredQuestion(15L)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().completeVariantTraining(8L, 3L));

        assertEquals("结构化变式题请提交答案完成训练", exception.getMessage());
        verify(aiVariantTrainingMapper, never()).selectOne(any());
    }

    @Test
    void submitVariantAnswerDelegatesAuthenticatedContextUnchanged() {
        AiVariantTrainingVO expected = new AiVariantTrainingVO();
        when(aiVariantQuestionService.submitAnswer(8L, 3L, "B")).thenReturn(expected);

        AiVariantTrainingVO result = service().submitVariantAnswer(8L, 3L, "B");

        assertSame(expected, result);
        verify(aiVariantQuestionService).submitAnswer(8L, 3L, "B");
    }

    private AiVariantTraining variantTraining(Long userId, Long questionId, Long assetId, String status,
                                              LocalDateTime startedTime, LocalDateTime completedTime) {
        AiVariantTraining training = new AiVariantTraining();
        training.setUserId(userId);
        training.setQuestionId(questionId);
        training.setAssetId(assetId);
        training.setStatus(status);
        training.setStartedTime(startedTime);
        training.setCompletedTime(completedTime);
        return training;
    }
}
