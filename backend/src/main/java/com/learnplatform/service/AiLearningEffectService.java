package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.dto.AiVariantTrainingVO;
import com.learnplatform.entity.AiAssetFeedback;
import com.learnplatform.entity.AiAssetView;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiAssetFeedbackMapper;
import com.learnplatform.mapper.AiAssetViewMapper;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 学习资产使用与后续练习表现统计。
 *
 * 该统计只表达行为数据中的相关性，不将正确率差异解释为 AI 内容造成的因果提升。
 */
@Service
public class AiLearningEffectService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 90;
    private static final long MIN_COMPARISON_SAMPLE = 5L;

    private final AiAssetViewMapper aiAssetViewMapper;
    private final QuestionAiAssetMapper questionAiAssetMapper;
    private final AiAssetFeedbackMapper aiAssetFeedbackMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final AiVariantTrainingMapper aiVariantTrainingMapper;

    public AiLearningEffectService(AiAssetViewMapper aiAssetViewMapper,
                                   QuestionAiAssetMapper questionAiAssetMapper,
                                   AiAssetFeedbackMapper aiAssetFeedbackMapper,
                                   PracticeRecordMapper practiceRecordMapper,
                                   AiVariantTrainingMapper aiVariantTrainingMapper) {
        this.aiAssetViewMapper = aiAssetViewMapper;
        this.questionAiAssetMapper = questionAiAssetMapper;
        this.aiAssetFeedbackMapper = aiAssetFeedbackMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.aiVariantTrainingMapper = aiVariantTrainingMapper;
    }

    /**
     * 记录用户确实看到某类已缓存学习资产。数据库按天原子聚合重复查看。
     */
    @Transactional
    public AiVariantTrainingVO recordAssetView(Long questionId, AiAssetType assetType, Long userId) {
        QuestionAiAsset asset = questionAiAssetMapper.selectOne(new LambdaQueryWrapper<QuestionAiAsset>()
                .eq(QuestionAiAsset::getQuestionId, questionId)
                .eq(QuestionAiAsset::getAssetType, assetType.name()));
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学习资产不存在");
        }
        aiAssetViewMapper.upsertDailyView(userId, questionId, assetType.name());
        if (assetType != AiAssetType.VARIANT) {
            return null;
        }
        aiVariantTrainingMapper.upsertStarted(userId, questionId, asset.getId());
        return toVariantTrainingVO(findVariantTraining(userId, asset.getId()));
    }

    /**
     * 用户显式确认完成当前缓存版本的变式训练。重复确认保持幂等。
     */
    @Transactional
    public AiVariantTrainingVO completeVariantTraining(Long questionId, Long userId) {
        QuestionAiAsset asset = questionAiAssetMapper.selectOne(new LambdaQueryWrapper<QuestionAiAsset>()
                .eq(QuestionAiAsset::getQuestionId, questionId)
                .eq(QuestionAiAsset::getAssetType, AiAssetType.VARIANT.name()));
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变式题学习资产不存在");
        }
        AiVariantTraining training = findVariantTraining(userId, asset.getId());
        if (training == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先查看变式题，再标记训练完成");
        }
        if (!"COMPLETED".equals(training.getStatus())) {
            training.setStatus("COMPLETED");
            training.setCompletedTime(LocalDateTime.now());
            aiVariantTrainingMapper.updateById(training);
        }
        return toVariantTrainingVO(training);
    }

    public AiLearningEffectVO getLearningEffect(Integer requestedDays) {
        int days = normalizeDays(requestedDays);
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(days - 1L);
        LocalDateTime startTime = periodStart.atStartOfDay();
        LocalDateTime endExclusive = periodEnd.plusDays(1).atStartOfDay();

        List<AiAssetView> allRelevantViews = aiAssetViewMapper.selectList(new LambdaQueryWrapper<AiAssetView>()
                .lt(AiAssetView::getFirstViewTime, endExclusive));
        List<AiAssetView> periodViews = allRelevantViews.stream()
                .filter(view -> view.getViewDate() != null
                        && !view.getViewDate().isBefore(periodStart)
                        && !view.getViewDate().isAfter(periodEnd))
                .toList();
        List<AiAssetFeedback> periodFeedback = aiAssetFeedbackMapper.selectList(
                new LambdaQueryWrapper<AiAssetFeedback>()
                        .ge(AiAssetFeedback::getCreateTime, startTime)
                        .lt(AiAssetFeedback::getCreateTime, endExclusive));
        List<PracticeRecord> practices = practiceRecordMapper.selectList(new LambdaQueryWrapper<PracticeRecord>()
                .ge(PracticeRecord::getCreateTime, startTime)
                .lt(PracticeRecord::getCreateTime, endExclusive));
        List<AiVariantTraining> variantTrainingCohort = aiVariantTrainingMapper.selectList(
                        new LambdaQueryWrapper<AiVariantTraining>()
                                .ge(AiVariantTraining::getStartedTime, startTime)
                                .lt(AiVariantTraining::getStartedTime, endExclusive))
                .stream()
                .filter(training -> training.getStartedTime() != null)
                .toList();

        Map<UserQuestionKey, LocalDateTime> firstViewByUserQuestion = new HashMap<>();
        for (AiAssetView view : allRelevantViews) {
            if (view.getFirstViewTime() == null) continue;
            UserQuestionKey key = new UserQuestionKey(view.getUserId(), view.getQuestionId());
            firstViewByUserQuestion.merge(key, view.getFirstViewTime(),
                    (left, right) -> left.isBefore(right) ? left : right);
        }

        long afterViewPracticeCount = 0;
        long afterViewCorrectCount = 0;
        long baselinePracticeCount = 0;
        long baselineCorrectCount = 0;
        for (PracticeRecord practice : practices) {
            LocalDateTime firstView = firstViewByUserQuestion.get(
                    new UserQuestionKey(practice.getUserId(), practice.getQuestionId()));
            boolean afterView = firstView != null && practice.getCreateTime() != null
                    && !practice.getCreateTime().isBefore(firstView);
            if (afterView) {
                afterViewPracticeCount++;
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) afterViewCorrectCount++;
            } else {
                baselinePracticeCount++;
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) baselineCorrectCount++;
            }
        }

        Double afterViewRate = percentage(afterViewCorrectCount, afterViewPracticeCount);
        Double baselineRate = percentage(baselineCorrectCount, baselinePracticeCount);
        Double lift = afterViewRate == null || baselineRate == null
                ? null : roundOne(afterViewRate - baselineRate);

        AiLearningEffectVO vo = new AiLearningEffectVO();
        vo.setDays(days);
        vo.setPeriodStart(periodStart);
        vo.setPeriodEnd(periodEnd);
        vo.setAssetViewCount(periodViews.stream()
                .mapToLong(view -> view.getViewCount() == null ? 0 : view.getViewCount()).sum());
        vo.setEngagedUserCount(periodViews.stream().map(AiAssetView::getUserId).distinct().count());
        vo.setViewedQuestionCount(periodViews.stream().map(AiAssetView::getQuestionId).distinct().count());
        vo.setFeedbackCount((long) periodFeedback.size());
        vo.setHelpfulRate(percentage(periodFeedback.stream().filter(item -> Boolean.TRUE.equals(item.getHelpful())).count(),
                periodFeedback.size()));
        long completedVariantTrainingCount = variantTrainingCohort.stream()
                .filter(training -> training.getCompletedTime() != null
                        && training.getCompletedTime().isBefore(endExclusive))
                .count();
        vo.setVariantTrainingStartedCount((long) variantTrainingCohort.size());
        vo.setVariantTrainingCompletedCount(completedVariantTrainingCount);
        vo.setVariantTrainingCompletionRate(percentage(completedVariantTrainingCount, variantTrainingCohort.size()));
        vo.setAfterViewPracticeCount(afterViewPracticeCount);
        vo.setAfterViewCorrectRate(afterViewRate);
        vo.setBaselinePracticeCount(baselinePracticeCount);
        vo.setBaselineCorrectRate(baselineRate);
        vo.setCorrectRateLift(lift);
        applyConclusion(vo);
        vo.setAssetTypeStats(buildAssetTypeStats(periodViews, periodFeedback));
        return vo;
    }

    private List<AiLearningEffectVO.AssetTypeEffect> buildAssetTypeStats(List<AiAssetView> views,
                                                                          List<AiAssetFeedback> feedback) {
        Set<String> assetTypes = new LinkedHashSet<>();
        views.stream().map(AiAssetView::getAssetType).filter(type -> type != null).forEach(assetTypes::add);
        feedback.stream().map(AiAssetFeedback::getAssetType).filter(type -> type != null).forEach(assetTypes::add);

        List<AiLearningEffectVO.AssetTypeEffect> result = new ArrayList<>();
        for (String assetType : assetTypes) {
            List<AiAssetView> typeViews = views.stream()
                    .filter(view -> assetType.equals(view.getAssetType())).toList();
            List<AiAssetFeedback> typeFeedback = feedback.stream()
                    .filter(item -> assetType.equals(item.getAssetType())).toList();

            AiLearningEffectVO.AssetTypeEffect item = new AiLearningEffectVO.AssetTypeEffect();
            item.setAssetType(assetType);
            item.setAssetTypeLabel(assetTypeLabel(assetType));
            item.setViewCount(typeViews.stream()
                    .mapToLong(view -> view.getViewCount() == null ? 0 : view.getViewCount()).sum());
            item.setUserCount(typeViews.stream().map(AiAssetView::getUserId).distinct().count());
            item.setFeedbackCount((long) typeFeedback.size());
            item.setHelpfulRate(percentage(
                    typeFeedback.stream().filter(entry -> Boolean.TRUE.equals(entry.getHelpful())).count(),
                    typeFeedback.size()));
            result.add(item);
        }
        result.sort(Comparator.comparingLong(AiLearningEffectVO.AssetTypeEffect::getViewCount).reversed());
        return result;
    }

    private void applyConclusion(AiLearningEffectVO vo) {
        if (vo.getAfterViewPracticeCount() < MIN_COMPARISON_SAMPLE
                || vo.getBaselinePracticeCount() < MIN_COMPARISON_SAMPLE
                || vo.getCorrectRateLift() == null) {
            vo.setConclusionLevel("INSUFFICIENT_DATA");
            vo.setConclusion("当前对照样本不足，先继续积累真实查看与后续作答数据，暂不判断学习效果。");
        } else if (vo.getCorrectRateLift() >= 5.0) {
            vo.setConclusionLevel("POSITIVE_ASSOCIATION");
            vo.setConclusion("阅读 AI 学习资产后的同题作答正确率更高，已观察到正向关联；仍需结合样本结构持续验证。");
        } else if (vo.getCorrectRateLift() <= -5.0) {
            vo.setConclusionLevel("NEEDS_ATTENTION");
            vo.setConclusion("阅读后的同题作答正确率未体现提升，建议结合资产反馈和题目难度检查内容质量。");
        } else {
            vo.setConclusionLevel("NO_CLEAR_DIFFERENCE");
            vo.setConclusion("两组正确率差异较小，当前尚未观察到明确关联，建议继续按资产类型跟踪。");
        }
    }

    private int normalizeDays(Integer requestedDays) {
        if (requestedDays == null) return DEFAULT_DAYS;
        return Math.max(1, Math.min(requestedDays, MAX_DAYS));
    }

    private Double percentage(long numerator, long denominator) {
        if (denominator <= 0) return null;
        return roundOne(numerator * 100.0 / denominator);
    }

    private Double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String assetTypeLabel(String assetType) {
        try {
            return AiAssetType.valueOf(assetType).getLabel();
        } catch (IllegalArgumentException ex) {
            return assetType;
        }
    }

    private AiVariantTraining findVariantTraining(Long userId, Long assetId) {
        return aiVariantTrainingMapper.selectOne(new LambdaQueryWrapper<AiVariantTraining>()
                .eq(AiVariantTraining::getUserId, userId)
                .eq(AiVariantTraining::getAssetId, assetId));
    }

    private AiVariantTrainingVO toVariantTrainingVO(AiVariantTraining training) {
        if (training == null) return null;
        AiVariantTrainingVO vo = new AiVariantTrainingVO();
        vo.setQuestionId(training.getQuestionId());
        vo.setAssetId(training.getAssetId());
        vo.setStatus(training.getStatus());
        vo.setCompleted("COMPLETED".equals(training.getStatus()));
        vo.setStartedTime(training.getStartedTime());
        vo.setCompletedTime(training.getCompletedTime());
        return vo;
    }

    private record UserQuestionKey(Long userId, Long questionId) {}
}
