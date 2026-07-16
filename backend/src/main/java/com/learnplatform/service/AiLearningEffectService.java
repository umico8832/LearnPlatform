package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.dto.AiVariantTrainingVO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final int CROSS_QUESTION_WINDOW_DAYS = 30;
    private static final long MIN_COMPARISON_SAMPLE = 5L;

    private final AiAssetViewMapper aiAssetViewMapper;
    private final QuestionAiAssetMapper questionAiAssetMapper;
    private final AiAssetFeedbackMapper aiAssetFeedbackMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final AiVariantTrainingMapper aiVariantTrainingMapper;
    private final AiVariantQuestionMapper aiVariantQuestionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final AiVariantQuestionService aiVariantQuestionService;

    public AiLearningEffectService(AiAssetViewMapper aiAssetViewMapper,
                                   QuestionAiAssetMapper questionAiAssetMapper,
                                   AiAssetFeedbackMapper aiAssetFeedbackMapper,
                                   PracticeRecordMapper practiceRecordMapper,
                                   AiVariantTrainingMapper aiVariantTrainingMapper,
                                   AiVariantQuestionMapper aiVariantQuestionMapper,
                                   QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                   AiVariantQuestionService aiVariantQuestionService) {
        this.aiAssetViewMapper = aiAssetViewMapper;
        this.questionAiAssetMapper = questionAiAssetMapper;
        this.aiAssetFeedbackMapper = aiAssetFeedbackMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.aiVariantTrainingMapper = aiVariantTrainingMapper;
        this.aiVariantQuestionMapper = aiVariantQuestionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.aiVariantQuestionService = aiVariantQuestionService;
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
        if (aiVariantQuestionService.hasStructuredQuestion(asset.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "结构化变式题请提交答案完成训练");
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

    public AiVariantTrainingVO submitVariantAnswer(Long questionId, Long userId, String userAnswer) {
        return aiVariantQuestionService.submitAnswer(questionId, userId, userAnswer);
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
        CrossQuestionStats crossQuestionStats = buildCrossQuestionStats(allRelevantViews, practices);

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
        vo.setMinimumComparisonSample(MIN_COMPARISON_SAMPLE);
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
        vo.setAfterViewCorrectRate(afterViewRate);
        vo.setBaselinePracticeCount(baselinePracticeCount);
        vo.setBaselineCorrectRate(baselineRate);
        vo.setCorrectRateLift(lift);
        applyConclusion(vo);
        applyCrossQuestionStats(vo, crossQuestionStats);
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
            long correctCount = difficultyTrainings.stream()
                    .filter(training -> Integer.valueOf(1).equals(training.getIsCorrect()))
                    .count();
            boolean sampleSufficient = answeredCount >= MIN_COMPARISON_SAMPLE;
            if (answeredCount > 0) coveredCount++;
            if (sampleSufficient) sufficientCount++;

            AiLearningEffectVO.VariantDifficultyEffect item = new AiLearningEffectVO.VariantDifficultyEffect();
            item.setDifficulty(difficulty);
            item.setDifficultyLabel(difficultyLabel(difficulty));
            item.setAnsweredCount(answeredCount);
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
                    + " 条首次判分，可开始分难度观察；结论仍只代表样本结构与相关性。");
        } else if (answeredTrainings.isEmpty()) {
            vo.setVariantDifficultyReadiness("INSUFFICIENT_DATA");
            vo.setVariantDifficultyConclusion("暂无结构化变式首次判分样本，先继续积累真实作答，不进入难度分层。");
        } else {
            vo.setVariantDifficultyReadiness("INSUFFICIENT_DATA");
            vo.setVariantDifficultyConclusion("当前覆盖 " + coveredCount + " 个难度档，仅 "
                    + sufficientCount + " 个达到每档 " + MIN_COMPARISON_SAMPLE
                    + " 条门槛，继续积累后再进行难度分层。");
        }
    }

    /**
     * 观察用户阅读一题的 AI 资产后，是否能在共享知识点的另一道题上迁移。
     * 原题重答不计入；前后组都限制在相关阅读前后 30 天内，且对照组不包含已有更早暴露的用户。
     */
    private CrossQuestionStats buildCrossQuestionStats(List<AiAssetView> views, List<PracticeRecord> practices) {
        Set<Long> questionIds = new HashSet<>();
        views.stream().map(AiAssetView::getQuestionId).filter(id -> id != null).forEach(questionIds::add);
        practices.stream().map(PracticeRecord::getQuestionId).filter(id -> id != null).forEach(questionIds::add);
        if (questionIds.isEmpty()) return new CrossQuestionStats();

        List<QuestionKnowledgePoint> relations = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .in(QuestionKnowledgePoint::getQuestionId, questionIds));
        Map<Long, Set<Long>> knowledgePointsByQuestion = new HashMap<>();
        for (QuestionKnowledgePoint relation : relations) {
            if (relation.getQuestionId() == null || relation.getKnowledgePointId() == null) continue;
            knowledgePointsByQuestion
                    .computeIfAbsent(relation.getQuestionId(), key -> new HashSet<>())
                    .add(relation.getKnowledgePointId());
        }

        Map<Long, List<SourceView>> viewsByUser = new HashMap<>();
        for (AiAssetView view : views) {
            if (view.getUserId() == null || view.getQuestionId() == null || view.getFirstViewTime() == null) continue;
            if (!knowledgePointsByQuestion.containsKey(view.getQuestionId())) continue;
            viewsByUser.computeIfAbsent(view.getUserId(), key -> new ArrayList<>())
                    .add(new SourceView(view.getQuestionId(), view.getFirstViewTime()));
        }

        CrossQuestionStats result = new CrossQuestionStats();
        for (PracticeRecord practice : practices) {
            if (practice.getUserId() == null || practice.getQuestionId() == null || practice.getCreateTime() == null) {
                continue;
            }
            Set<Long> targetKnowledgePoints = knowledgePointsByQuestion.get(practice.getQuestionId());
            if (targetKnowledgePoints == null || targetKnowledgePoints.isEmpty()) continue;

            boolean hasPriorRelatedView = false;
            boolean hasRecentPriorRelatedView = false;
            boolean hasUpcomingRelatedView = false;
            for (SourceView sourceView : viewsByUser.getOrDefault(practice.getUserId(), List.of())) {
                if (sourceView.questionId().equals(practice.getQuestionId())) continue;
                Set<Long> sourceKnowledgePoints = knowledgePointsByQuestion.get(sourceView.questionId());
                if (!sharesKnowledgePoint(sourceKnowledgePoints, targetKnowledgePoints)) continue;

                if (!sourceView.viewTime().isAfter(practice.getCreateTime())) {
                    hasPriorRelatedView = true;
                    if (!practice.getCreateTime().isAfter(
                            sourceView.viewTime().plusDays(CROSS_QUESTION_WINDOW_DAYS))) {
                        hasRecentPriorRelatedView = true;
                    }
                } else if (!sourceView.viewTime().isAfter(
                        practice.getCreateTime().plusDays(CROSS_QUESTION_WINDOW_DAYS))) {
                    hasUpcomingRelatedView = true;
                }
            }

            if (hasRecentPriorRelatedView) {
                result.afterViewPracticeCount++;
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) result.afterViewCorrectCount++;
            } else if (!hasPriorRelatedView && hasUpcomingRelatedView) {
                result.baselinePracticeCount++;
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) result.baselineCorrectCount++;
            }
        }
        return result;
    }

    private boolean sharesKnowledgePoint(Set<Long> left, Set<Long> right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) return false;
        Set<Long> smaller = left.size() <= right.size() ? left : right;
        Set<Long> larger = smaller == left ? right : left;
        return smaller.stream().anyMatch(larger::contains);
    }

    private void applyCrossQuestionStats(AiLearningEffectVO vo, CrossQuestionStats stats) {
        Double afterRate = percentage(stats.afterViewCorrectCount, stats.afterViewPracticeCount);
        Double baselineRate = percentage(stats.baselineCorrectCount, stats.baselinePracticeCount);
        Double lift = afterRate == null || baselineRate == null ? null : roundOne(afterRate - baselineRate);

        vo.setCrossQuestionWindowDays(CROSS_QUESTION_WINDOW_DAYS);
        vo.setCrossQuestionAfterViewPracticeCount(stats.afterViewPracticeCount);
        vo.setCrossQuestionAfterViewCorrectRate(afterRate);
        vo.setCrossQuestionBaselinePracticeCount(stats.baselinePracticeCount);
        vo.setCrossQuestionBaselineCorrectRate(baselineRate);
        vo.setCrossQuestionCorrectRateLift(lift);

        if (stats.afterViewPracticeCount < MIN_COMPARISON_SAMPLE
                || stats.baselinePracticeCount < MIN_COMPARISON_SAMPLE || lift == null) {
            vo.setCrossQuestionConclusionLevel("INSUFFICIENT_DATA");
            vo.setCrossQuestionConclusion("跨题对照样本不足，先积累共享知识点题目的真实作答，暂不判断迁移效果。");
        } else if (lift >= 5.0) {
            vo.setCrossQuestionConclusionLevel("POSITIVE_ASSOCIATION");
            vo.setCrossQuestionConclusion("30 天窗口内，共享知识点的跨题作答正确率更高，已观察到正向关联；该结果仍不代表因果提升。");
        } else if (lift <= -5.0) {
            vo.setCrossQuestionConclusionLevel("NEEDS_ATTENTION");
            vo.setCrossQuestionConclusion("30 天窗口内的跨题作答未体现提升，建议结合知识点映射、题目难度和资产质量继续排查。");
        } else {
            vo.setCrossQuestionConclusionLevel("NO_CLEAR_DIFFERENCE");
            vo.setCrossQuestionConclusion("30 天窗口内两组跨题正确率差异较小，尚未观察到明确的知识迁移关联。");
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
                if (!assetType.equals(view.getAssetType()) || view.getFirstViewTime() == null) continue;
                UserQuestionKey key = new UserQuestionKey(view.getUserId(), view.getQuestionId());
                firstTypeViewByUserQuestion.merge(key, view.getFirstViewTime(),
                        (left, right) -> left.isBefore(right) ? left : right);
            }

            long afterViewPracticeCount = 0;
            long afterViewCorrectCount = 0;
            long baselinePracticeCount = 0;
            long baselineCorrectCount = 0;
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
                    if (Integer.valueOf(1).equals(practice.getIsCorrect())) afterViewCorrectCount++;
                } else {
                    baselinePracticeCount++;
                    if (Integer.valueOf(1).equals(practice.getIsCorrect())) baselineCorrectCount++;
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
            item.setAfterViewCorrectRate(percentage(afterViewCorrectCount, afterViewPracticeCount));
            item.setBaselinePracticeCount(baselinePracticeCount);
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
                && item.getCorrectRateLift() != null;
        item.setSampleSufficient(sampleSufficient);
        if (!sampleSufficient) {
            item.setConclusionLevel("INSUFFICIENT_DATA");
            item.setConclusion("该资产类型的任一对照组少于 " + MIN_COMPARISON_SAMPLE
                    + " 条作答，继续积累真实样本，不进入内容价值判断。");
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
        AiVariantTrainingVO enriched = aiVariantQuestionService.enrichTrainingVO(training, vo);
        return enriched != null ? enriched : vo;
    }

    private record UserQuestionKey(Long userId, Long questionId) {}
    private record SourceView(Long questionId, LocalDateTime viewTime) {}

    private static class CrossQuestionStats {
        private long afterViewPracticeCount;
        private long afterViewCorrectCount;
        private long baselinePracticeCount;
        private long baselineCorrectCount;
    }
}
