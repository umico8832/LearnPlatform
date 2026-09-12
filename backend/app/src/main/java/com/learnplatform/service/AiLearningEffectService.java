package com.learnplatform.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.entity.AiAssetFeedback;
import com.learnplatform.entity.AiAssetView;
import com.learnplatform.entity.AiVariantQuestion;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.mapper.AiAssetFeedbackMapper;
import com.learnplatform.mapper.AiAssetViewMapper;
import com.learnplatform.mapper.AiVariantQuestionMapper;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final long MIN_DISTINCT_USERS = 3L;

    private final AiAssetViewMapper aiAssetViewMapper;
    private final AiAssetFeedbackMapper aiAssetFeedbackMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final AiVariantTrainingMapper aiVariantTrainingMapper;
    private final AiVariantQuestionMapper aiVariantQuestionMapper;
    private final AiLearningCrossQuestionService crossQuestionService;
    private final AiLearningEffectConclusionService conclusionService;

    public AiLearningEffectService(AiAssetViewMapper aiAssetViewMapper,
                                   AiAssetFeedbackMapper aiAssetFeedbackMapper,
                                   PracticeRecordMapper practiceRecordMapper,
                                   AiVariantTrainingMapper aiVariantTrainingMapper,
                                   AiVariantQuestionMapper aiVariantQuestionMapper,
                                   AiLearningCrossQuestionService crossQuestionService,
                                   AiLearningEffectConclusionService conclusionService) {
        this.aiAssetViewMapper = aiAssetViewMapper;
        this.aiAssetFeedbackMapper = aiAssetFeedbackMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.aiVariantTrainingMapper = aiVariantTrainingMapper;
        this.aiVariantQuestionMapper = aiVariantQuestionMapper;
        this.crossQuestionService = crossQuestionService;
        this.conclusionService = conclusionService;
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
            if (view.getFirstViewTime() == null) { continue; }
            UserQuestionKey key = new UserQuestionKey(view.getUserId(), view.getQuestionId());
            firstViewByUserQuestion.merge(key, view.getFirstViewTime(),
                    (left, right) -> left.isBefore(right) ? left : right);
        }

        long afterViewPracticeCount = 0;
        long afterViewCorrectCount = 0;
        long baselinePracticeCount = 0;
        long baselineCorrectCount = 0;
        Set<Long> afterViewUsers = new HashSet<>();
        Set<Long> baselineUsers = new HashSet<>();
        for (PracticeRecord practice : practices) {
            LocalDateTime firstView = firstViewByUserQuestion.get(
                    new UserQuestionKey(practice.getUserId(), practice.getQuestionId()));
            boolean afterView = firstView != null && practice.getCreateTime() != null
                    && !practice.getCreateTime().isBefore(firstView);
            if (afterView) {
                afterViewPracticeCount++;
                if (practice.getUserId() != null) { afterViewUsers.add(practice.getUserId()); }
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) { afterViewCorrectCount++; }
            } else {
                baselinePracticeCount++;
                if (practice.getUserId() != null) { baselineUsers.add(practice.getUserId()); }
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) { baselineCorrectCount++; }
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
        vo.setHelpfulRate(percentage(periodFeedback.stream()
                .filter(item -> Boolean.TRUE.equals(item.getHelpful())).count(),
                periodFeedback.size()));
        vo.setMinimumComparisonSample(MIN_COMPARISON_SAMPLE);
        vo.setMinimumDistinctUsers(MIN_DISTINCT_USERS);
        long completedVariantTrainingCount = variantTrainingCohort.stream()
                .filter(training -> training.getCompletedTime() != null
                        && training.getCompletedTime().isBefore(endExclusive))
                .count();
        long answeredVariantTrainingCount = variantTrainingCohort.stream()
                .filter(training -> training.getAnsweredTime() != null
                        && training.getAnsweredTime().isBefore(endExclusive))
                .count();
        long correctVariantTrainingCount = variantTrainingCohort.stream()
                .filter(training -> training.getAnsweredTime() != null
                        && training.getAnsweredTime().isBefore(endExclusive)
                        && Integer.valueOf(1).equals(training.getIsCorrect()))
                .count();
        vo.setVariantTrainingStartedCount((long) variantTrainingCohort.size());
        vo.setVariantTrainingCompletedCount(completedVariantTrainingCount);
        vo.setVariantTrainingCompletionRate(percentage(completedVariantTrainingCount, variantTrainingCohort.size()));
        vo.setVariantTrainingAnsweredCount(answeredVariantTrainingCount);
        vo.setVariantTrainingCorrectCount(correctVariantTrainingCount);
        vo.setVariantTrainingCorrectRate(percentage(correctVariantTrainingCount, answeredVariantTrainingCount));
        applyVariantDifficultyStats(vo, variantTrainingCohort, endExclusive);
        vo.setAfterViewPracticeCount(afterViewPracticeCount);
        vo.setAfterViewUserCount((long) afterViewUsers.size());
        vo.setAfterViewCorrectRate(afterViewRate);
        vo.setBaselinePracticeCount(baselinePracticeCount);
        vo.setBaselineUserCount((long) baselineUsers.size());
        vo.setBaselineCorrectRate(baselineRate);
        vo.setCorrectRateLift(lift);
        conclusionService.apply(vo);
        crossQuestionService.apply(vo, allRelevantViews, practices);
        vo.setAssetTypeStats(buildAssetTypeStats(
                periodViews, periodFeedback, allRelevantViews, practices));
        return vo;
    }

    /**
     * 仅按结构化变式题的服务端首次判分统计难度样本结构。
     * 每个难度档至少达到同一最小样本门槛，且至少两个难度档达标后，才提示可开始分层观察。
     */
    private void applyVariantDifficultyStats(AiLearningEffectVO vo,
                                             List<AiVariantTraining> trainingCohort,
                                             LocalDateTime endExclusive) {
        List<AiVariantTraining> answeredTrainings = trainingCohort.stream()
                .filter(training -> training.getAssetId() != null
                        && training.getAnsweredTime() != null
                        && training.getAnsweredTime().isBefore(endExclusive))
                .toList();
        Map<Long, Integer> difficultyByAssetId = new HashMap<>();
        if (!answeredTrainings.isEmpty()) {
            Set<Long> assetIds = answeredTrainings.stream()
                    .map(AiVariantTraining::getAssetId)
                    .collect(java.util.stream.Collectors.toSet());
            for (AiVariantQuestion question : aiVariantQuestionMapper.selectList(
                    new LambdaQueryWrapper<AiVariantQuestion>().in(AiVariantQuestion::getAssetId, assetIds))) {
                if (question.getAssetId() != null && question.getDifficulty() != null) {
                    difficultyByAssetId.put(question.getAssetId(), question.getDifficulty());
                }
            }
        }

        List<AiLearningEffectVO.VariantDifficultyEffect> stats = new ArrayList<>();
        long coveredCount = 0;
        long sufficientCount = 0;
        for (int difficulty = 1; difficulty <= 5; difficulty++) {
            final int currentDifficulty = difficulty;
            List<AiVariantTraining> difficultyTrainings = answeredTrainings.stream()
                    .filter(training -> Integer.valueOf(currentDifficulty)
                            .equals(difficultyByAssetId.get(training.getAssetId())))
                    .toList();
            long answeredCount = difficultyTrainings.size();
            long answeredUserCount = difficultyTrainings.stream()
                    .map(AiVariantTraining::getUserId)
                    .filter(userId -> userId != null)
                    .distinct()
                    .count();
            long correctCount = difficultyTrainings.stream()
                    .filter(training -> Integer.valueOf(1).equals(training.getIsCorrect()))
                    .count();
            boolean sampleSufficient = answeredCount >= MIN_COMPARISON_SAMPLE
                    && answeredUserCount >= MIN_DISTINCT_USERS;
            if (answeredCount > 0) { coveredCount++; }
            if (sampleSufficient) { sufficientCount++; }

            AiLearningEffectVO.VariantDifficultyEffect item = new AiLearningEffectVO.VariantDifficultyEffect();
            item.setDifficulty(difficulty);
            item.setDifficultyLabel(difficultyLabel(difficulty));
            item.setAnsweredCount(answeredCount);
            item.setAnsweredUserCount(answeredUserCount);
            item.setCorrectCount(correctCount);
            item.setCorrectRate(percentage(correctCount, answeredCount));
            item.setSampleSufficient(sampleSufficient);
            stats.add(item);
        }

        vo.setVariantDifficultyMinimumSample(MIN_COMPARISON_SAMPLE);
        vo.setVariantDifficultyCoveredCount(coveredCount);
        vo.setVariantDifficultySufficientCount(sufficientCount);
        vo.setVariantDifficultyStats(stats);
        if (sufficientCount >= 2) {
            vo.setVariantDifficultyReadiness("READY");
            vo.setVariantDifficultyConclusion("已有 " + sufficientCount
                    + " 个难度档各不少于 " + MIN_COMPARISON_SAMPLE
                    + " 条首次判分且覆盖至少 " + MIN_DISTINCT_USERS
                    + " 位学习者，可开始分难度观察；结论仍只代表样本结构与相关性。");
        } else if (answeredTrainings.isEmpty()) {
            vo.setVariantDifficultyReadiness("INSUFFICIENT_DATA");
            vo.setVariantDifficultyConclusion("暂无结构化变式首次判分样本，先继续积累真实作答，不进入难度分层。");
        } else {
            vo.setVariantDifficultyReadiness("INSUFFICIENT_DATA");
            vo.setVariantDifficultyConclusion("当前覆盖 " + coveredCount + " 个难度档，仅 "
                    + sufficientCount + " 个同时达到每档 " + MIN_COMPARISON_SAMPLE
                    + " 条、" + MIN_DISTINCT_USERS + " 位学习者门槛，继续积累后再进行难度分层。");
        }
    }
    private List<AiLearningEffectVO.AssetTypeEffect> buildAssetTypeStats(List<AiAssetView> periodViews,
                                                                          List<AiAssetFeedback> feedback,
                                                                          List<AiAssetView> allRelevantViews,
                                                                          List<PracticeRecord> practices) {
        Set<String> assetTypes = new LinkedHashSet<>();
        periodViews.stream().map(AiAssetView::getAssetType).filter(type -> type != null).forEach(assetTypes::add);
        feedback.stream().map(AiAssetFeedback::getAssetType).filter(type -> type != null).forEach(assetTypes::add);

        List<AiLearningEffectVO.AssetTypeEffect> result = new ArrayList<>();
        for (String assetType : assetTypes) {
            List<AiAssetView> typeViews = periodViews.stream()
                    .filter(view -> assetType.equals(view.getAssetType())).toList();
            List<AiAssetFeedback> typeFeedback = feedback.stream()
                    .filter(item -> assetType.equals(item.getAssetType())).toList();
            Map<UserQuestionKey, LocalDateTime> firstTypeViewByUserQuestion = new HashMap<>();
            for (AiAssetView view : allRelevantViews) {
                if (!assetType.equals(view.getAssetType()) || view.getFirstViewTime() == null) { continue; }
                UserQuestionKey key = new UserQuestionKey(view.getUserId(), view.getQuestionId());
                firstTypeViewByUserQuestion.merge(key, view.getFirstViewTime(),
                        (left, right) -> left.isBefore(right) ? left : right);
            }

            long afterViewPracticeCount = 0;
            long afterViewCorrectCount = 0;
            long baselinePracticeCount = 0;
            long baselineCorrectCount = 0;
            Set<Long> afterViewUsers = new HashSet<>();
            Set<Long> baselineUsers = new HashSet<>();
            for (PracticeRecord practice : practices) {
                if (practice.getUserId() == null || practice.getQuestionId() == null
                        || practice.getCreateTime() == null) {
                    continue;
                }
                LocalDateTime firstTypeView = firstTypeViewByUserQuestion.get(
                        new UserQuestionKey(practice.getUserId(), practice.getQuestionId()));
                boolean afterView = firstTypeView != null
                        && !practice.getCreateTime().isBefore(firstTypeView);
                if (afterView) {
                    afterViewPracticeCount++;
                    afterViewUsers.add(practice.getUserId());
                    if (Integer.valueOf(1).equals(practice.getIsCorrect())) { afterViewCorrectCount++; }
                } else {
                    baselinePracticeCount++;
                    baselineUsers.add(practice.getUserId());
                    if (Integer.valueOf(1).equals(practice.getIsCorrect())) { baselineCorrectCount++; }
                }
            }

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
            item.setAfterViewPracticeCount(afterViewPracticeCount);
            item.setAfterViewUserCount((long) afterViewUsers.size());
            item.setAfterViewCorrectRate(percentage(afterViewCorrectCount, afterViewPracticeCount));
            item.setBaselinePracticeCount(baselinePracticeCount);
            item.setBaselineUserCount((long) baselineUsers.size());
            item.setBaselineCorrectRate(percentage(baselineCorrectCount, baselinePracticeCount));
            item.setCorrectRateLift(item.getAfterViewCorrectRate() == null || item.getBaselineCorrectRate() == null
                    ? null : roundOne(item.getAfterViewCorrectRate() - item.getBaselineCorrectRate()));
            applyAssetTypeConclusion(item);
            result.add(item);
        }
        result.sort(Comparator.comparingLong(AiLearningEffectVO.AssetTypeEffect::getViewCount).reversed());
        return result;
    }

    private void applyAssetTypeConclusion(AiLearningEffectVO.AssetTypeEffect item) {
        boolean sampleSufficient = item.getAfterViewPracticeCount() >= MIN_COMPARISON_SAMPLE
                && item.getBaselinePracticeCount() >= MIN_COMPARISON_SAMPLE
                && item.getAfterViewUserCount() >= MIN_DISTINCT_USERS
                && item.getBaselineUserCount() >= MIN_DISTINCT_USERS
                && item.getCorrectRateLift() != null;
        item.setSampleSufficient(sampleSufficient);
        if (!sampleSufficient) {
            item.setConclusionLevel("INSUFFICIENT_DATA");
            item.setConclusion("该资产类型任一对照组需至少 " + MIN_COMPARISON_SAMPLE
                    + " 条作答且覆盖 " + MIN_DISTINCT_USERS
                    + " 位学习者，继续积累真实样本，不进入内容价值判断。");
        } else if (item.getCorrectRateLift() >= 5.0) {
            item.setConclusionLevel("POSITIVE_ASSOCIATION");
            item.setConclusion("该资产类型阅读后的同题正确率更高，当前仅视为观察性正向关联。");
        } else if (item.getCorrectRateLift() <= -5.0) {
            item.setConclusionLevel("NEEDS_ATTENTION");
            item.setConclusion("该资产类型阅读后的同题正确率未体现提升，建议结合反馈与题目难度排查。");
        } else {
            item.setConclusionLevel("NO_CLEAR_DIFFERENCE");
            item.setConclusion("该资产类型两组正确率差异较小，尚未观察到明确关联。");
        }
    }


    private int normalizeDays(Integer requestedDays) {
        if (requestedDays == null) { return DEFAULT_DAYS; }
        return Math.max(1, Math.min(requestedDays, MAX_DAYS));
    }

    private Double percentage(long numerator, long denominator) {
        if (denominator <= 0) { return null; }
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

    private String difficultyLabel(int difficulty) {
        return switch (difficulty) {
            case 1 -> "入门";
            case 2 -> "简单";
            case 3 -> "中等";
            case 4 -> "较难";
            case 5 -> "困难";
            default -> "未知";
        };
    }

    private record UserQuestionKey(Long userId, Long questionId) {}
}
