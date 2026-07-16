package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.entity.AiAssetFeedback;
import com.learnplatform.entity.AiAssetView;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiAssetFeedbackMapper;
import com.learnplatform.mapper.AiAssetViewMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
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

    private AiLearningEffectService service() {
        return new AiLearningEffectService(aiAssetViewMapper, questionAiAssetMapper,
                aiAssetFeedbackMapper, practiceRecordMapper);
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
    }

    @Test
    void getLearningEffectReturnsEmptyObservationWhenNoData() {
        when(aiAssetViewMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiLearningEffectVO result = service().getLearningEffect(30);

        assertEquals(30, result.getDays());
        assertEquals(0L, result.getAssetViewCount());
        assertEquals(0L, result.getAfterViewPracticeCount());
        assertNull(result.getAfterViewCorrectRate());
        assertNull(result.getCorrectRateLift());
        assertEquals("INSUFFICIENT_DATA", result.getConclusionLevel());
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
        assertEquals("POSITIVE_ASSOCIATION", result.getConclusionLevel());
        assertEquals(1, result.getAssetTypeStats().size());
        assertEquals("标准解析", result.getAssetTypeStats().get(0).getAssetTypeLabel());
    }

    @Test
    void getLearningEffectClampsPeriodToNinetyDays() {
        when(aiAssetViewMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(aiAssetFeedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        AiLearningEffectVO result = service().getLearningEffect(365);

        assertEquals(90, result.getDays());
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
}
