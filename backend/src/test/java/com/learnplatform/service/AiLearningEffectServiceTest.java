package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.entity.AiAssetFeedback;
import com.learnplatform.entity.AiAssetView;
import com.learnplatform.entity.AiVariantQuestion;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.mapper.AiAssetFeedbackMapper;
import com.learnplatform.mapper.AiAssetViewMapper;
import com.learnplatform.mapper.AiVariantQuestionMapper;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiLearningEffectServiceTest {

    @Mock private AiAssetViewMapper aiAssetViewMapper;
    @Mock private QuestionAiAssetMapper questionAiAssetMapper;
    @Mock private AiAssetFeedbackMapper aiAssetFeedbackMapper;
    @Mock private PracticeRecordMapper practiceRecordMapper;
    @Mock private AiVariantTrainingMapper aiVariantTrainingMapper;
    @Mock private AiVariantQuestionMapper aiVariantQuestionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private AiVariantQuestionService aiVariantQuestionService;

    private AiLearningEffectService service() {
        return new AiLearningEffectService(aiAssetViewMapper, questionAiAssetMapper,
                aiAssetFeedbackMapper, practiceRecordMapper, aiVariantTrainingMapper,
                aiVariantQuestionMapper, questionKnowledgePointMapper, aiVariantQuestionService);
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
    void getLearningEffectReturnsEmptyObservationWhenNoData() {
        when(aiAssetViewMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiVariantTrainingMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiLearningEffectVO result = service().getLearningEffect(30);

        assertEquals(30, result.getDays());
        assertEquals(5L, result.getMinimumComparisonSample());
        assertEquals(0L, result.getAssetViewCount());
        assertEquals(0L, result.getAfterViewPracticeCount());
        assertNull(result.getAfterViewCorrectRate());
        assertNull(result.getCorrectRateLift());
        assertEquals("INSUFFICIENT_DATA", result.getConclusionLevel());
        assertEquals("INSUFFICIENT_DATA", result.getVariantDifficultyReadiness());
        assertEquals(5, result.getVariantDifficultyStats().size());
    }

    @Test
    void getLearningEffectSeparatesAnswersBeforeAndAfterFirstView() {
        LocalDateTime now = LocalDateTime.now();
        AiAssetView view = view(1L, 10L, "FULL_EXPLANATION", now.minusDays(10), 3);
        when(aiAssetViewMapper.selectList(any())).thenReturn(List.of(view));

        AiAssetFeedback helpful = feedback("FULL_EXPLANATION", true, now.minusDays(2));
        AiAssetFeedback unhelpful = feedback("FULL_EXPLANATION", false, now.minusDays(1));
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(List.of(helpful, unhelpful));

        List<PracticeRecord> practices = new ArrayList<>();
        practices.add(practice(1L, 10L, false, now.minusDays(11)));
        for (int i = 0; i < 5; i++) {
            practices.add(practice(1L, 10L, i < 4, now.minusDays(9).plusHours(i)));
            practices.add(practice(2L, 10L, i < 2, now.minusDays(8).plusHours(i)));
        }
        when(practiceRecordMapper.selectList(any())).thenReturn(practices);
        AiVariantTraining answeredTraining = variantTraining(
                1L, 10L, 100L, "COMPLETED", now.minusDays(4), now.minusDays(3));
        answeredTraining.setAnsweredTime(now.minusDays(3));
        answeredTraining.setIsCorrect(1);
        answeredTraining.setUserAnswer("B");
        when(aiVariantTrainingMapper.selectList(any())).thenReturn(List.of(
                answeredTraining,
                variantTraining(2L, 10L, 100L, "STARTED", now.minusDays(2), null)
        ));
        when(aiVariantQuestionMapper.selectList(any())).thenReturn(List.of(variantQuestion(100L, 3)));

        AiLearningEffectVO result = service().getLearningEffect(30);

        assertEquals(3L, result.getAssetViewCount());
        assertEquals(1L, result.getEngagedUserCount());
        assertEquals(1L, result.getViewedQuestionCount());
        assertEquals(2L, result.getFeedbackCount());
        assertEquals(50.0, result.getHelpfulRate());
        assertEquals(5L, result.getAfterViewPracticeCount());
        assertEquals(80.0, result.getAfterViewCorrectRate());
        assertEquals(6L, result.getBaselinePracticeCount());
        assertEquals(33.3, result.getBaselineCorrectRate());
        assertEquals(46.7, result.getCorrectRateLift());
        assertEquals(2L, result.getVariantTrainingStartedCount());
        assertEquals(1L, result.getVariantTrainingCompletedCount());
        assertEquals(50.0, result.getVariantTrainingCompletionRate());
        assertEquals(1L, result.getVariantTrainingAnsweredCount());
        assertEquals(1L, result.getVariantTrainingCorrectCount());
        assertEquals(100.0, result.getVariantTrainingCorrectRate());
        assertEquals(1L, result.getVariantDifficultyCoveredCount());
        assertEquals(0L, result.getVariantDifficultySufficientCount());
        assertEquals(1L, result.getVariantDifficultyStats().get(2).getAnsweredCount());
        assertEquals(100.0, result.getVariantDifficultyStats().get(2).getCorrectRate());
        assertEquals("POSITIVE_ASSOCIATION", result.getConclusionLevel());
        assertEquals(1, result.getAssetTypeStats().size());
        assertEquals("标准解析", result.getAssetTypeStats().get(0).getAssetTypeLabel());
        assertEquals(5L, result.getAssetTypeStats().get(0).getAfterViewPracticeCount());
        assertEquals(80.0, result.getAssetTypeStats().get(0).getAfterViewCorrectRate());
        assertEquals(6L, result.getAssetTypeStats().get(0).getBaselinePracticeCount());
        assertEquals(33.3, result.getAssetTypeStats().get(0).getBaselineCorrectRate());
        assertEquals(46.7, result.getAssetTypeStats().get(0).getCorrectRateLift());
        assertEquals(true, result.getAssetTypeStats().get(0).getSampleSufficient());
        assertEquals("POSITIVE_ASSOCIATION", result.getAssetTypeStats().get(0).getConclusionLevel());
    }

    @Test
    void getLearningEffectKeepsAssetTypeExposureCohortsIndependent() {
        LocalDateTime now = LocalDateTime.now();
        when(aiAssetViewMapper.selectList(any())).thenReturn(List.of(
                view(1L, 10L, "FULL_EXPLANATION", now.minusDays(10), 1),
                view(1L, 10L, "STEP_BY_STEP", now.minusDays(4), 1)));
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiVariantTrainingMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(
                practice(1L, 10L, true, now.minusDays(9)),
                practice(1L, 10L, true, now.minusDays(3)),
                practice(2L, 10L, false, now.minusDays(2))));

        AiLearningEffectVO result = service().getLearningEffect(30);

        AiLearningEffectVO.AssetTypeEffect fullExplanation = result.getAssetTypeStats().stream()
                .filter(item -> "FULL_EXPLANATION".equals(item.getAssetType()))
                .findFirst().orElseThrow();
        AiLearningEffectVO.AssetTypeEffect stepByStep = result.getAssetTypeStats().stream()
                .filter(item -> "STEP_BY_STEP".equals(item.getAssetType()))
                .findFirst().orElseThrow();
        assertEquals(2L, fullExplanation.getAfterViewPracticeCount());
        assertEquals(1L, fullExplanation.getBaselinePracticeCount());
        assertEquals(1L, stepByStep.getAfterViewPracticeCount());
        assertEquals(2L, stepByStep.getBaselinePracticeCount());
        assertEquals(false, fullExplanation.getSampleSufficient());
        assertEquals("INSUFFICIENT_DATA", stepByStep.getConclusionLevel());
    }

    @Test
    void getLearningEffectClampsPeriodToNinetyDays() {
        when(aiAssetViewMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiVariantTrainingMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiLearningEffectVO result = service().getLearningEffect(365);

        assertEquals(90, result.getDays());
    }

    @Test
    void getLearningEffectMeasuresCrossQuestionTransferWithinSharedKnowledgePointWindow() {
        LocalDateTime now = LocalDateTime.now();
        when(aiAssetViewMapper.selectList(any())).thenReturn(List.of(
                view(1L, 10L, "FULL_EXPLANATION", now.minusDays(10), 1),
                view(2L, 12L, "FULL_EXPLANATION", now.minusDays(40), 1),
                view(2L, 10L, "FULL_EXPLANATION", now.minusDays(2), 1)));
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiVariantTrainingMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(
                relation(10L, 100L), relation(11L, 100L), relation(12L, 100L),
                relation(20L, 200L)));

        List<PracticeRecord> practices = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            practices.add(practice(1L, 11L, i < 4, now.minusDays(9).plusHours(i)));
            practices.add(practice(1L, 11L, i < 2, now.minusDays(11).plusHours(i)));
        }
        practices.add(practice(1L, 10L, true, now.minusDays(9)));
        practices.add(practice(1L, 20L, true, now.minusDays(9)));
        practices.add(practice(2L, 11L, true, now.minusDays(5)));
        when(practiceRecordMapper.selectList(any())).thenReturn(practices);

        AiLearningEffectVO result = service().getLearningEffect(30);

        assertEquals(30, result.getCrossQuestionWindowDays());
        assertEquals(5L, result.getCrossQuestionAfterViewPracticeCount());
        assertEquals(80.0, result.getCrossQuestionAfterViewCorrectRate());
        assertEquals(5L, result.getCrossQuestionBaselinePracticeCount());
        assertEquals(40.0, result.getCrossQuestionBaselineCorrectRate());
        assertEquals(40.0, result.getCrossQuestionCorrectRateLift());
        assertEquals("POSITIVE_ASSOCIATION", result.getCrossQuestionConclusionLevel());
    }

    @Test
    void getLearningEffectMarksDifficultyStratificationReadyWithTwoSufficientBuckets() {
        LocalDateTime now = LocalDateTime.now();
        when(aiAssetViewMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<AiVariantTraining> trainings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AiVariantTraining easy = variantTraining((long) i + 1, 10L, 100L, "COMPLETED",
                    now.minusDays(2), now.minusDays(1));
            easy.setAnsweredTime(now.minusDays(1));
            easy.setIsCorrect(i < 4 ? 1 : 0);
            trainings.add(easy);
            AiVariantTraining hard = variantTraining((long) i + 11, 20L, 200L, "COMPLETED",
                    now.minusDays(2), now.minusDays(1));
            hard.setAnsweredTime(now.minusDays(1));
            hard.setIsCorrect(i < 2 ? 1 : 0);
            trainings.add(hard);
        }
        when(aiVariantTrainingMapper.selectList(any())).thenReturn(trainings);
        when(aiVariantQuestionMapper.selectList(any())).thenReturn(List.of(
                variantQuestion(100L, 2), variantQuestion(200L, 4)));

        AiLearningEffectVO result = service().getLearningEffect(30);

        assertEquals("READY", result.getVariantDifficultyReadiness());
        assertEquals(2L, result.getVariantDifficultyCoveredCount());
        assertEquals(2L, result.getVariantDifficultySufficientCount());
        assertEquals(80.0, result.getVariantDifficultyStats().get(1).getCorrectRate());
        assertEquals(40.0, result.getVariantDifficultyStats().get(3).getCorrectRate());
    }

    private AiAssetView view(Long userId, Long questionId, String assetType,
                             LocalDateTime firstViewTime, int count) {
        AiAssetView view = new AiAssetView();
        view.setUserId(userId);
        view.setQuestionId(questionId);
        view.setAssetType(assetType);
        view.setViewDate(LocalDate.now().minusDays(10));
        view.setViewCount(count);
        view.setFirstViewTime(firstViewTime);
        return view;
    }

    private AiAssetFeedback feedback(String assetType, boolean helpful, LocalDateTime createTime) {
        AiAssetFeedback feedback = new AiAssetFeedback();
        feedback.setAssetType(assetType);
        feedback.setHelpful(helpful);
        feedback.setCreateTime(createTime);
        return feedback;
    }

    private PracticeRecord practice(Long userId, Long questionId, boolean correct, LocalDateTime createTime) {
        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setIsCorrect(correct ? 1 : 0);
        record.setCreateTime(createTime);
        return record;
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

    private QuestionKnowledgePoint relation(Long questionId, Long knowledgePointId) {
        QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
        relation.setQuestionId(questionId);
        relation.setKnowledgePointId(knowledgePointId);
        return relation;
    }

    private AiVariantQuestion variantQuestion(Long assetId, int difficulty) {
        AiVariantQuestion question = new AiVariantQuestion();
        question.setAssetId(assetId);
        question.setDifficulty(difficulty);
        return question;
    }
}
