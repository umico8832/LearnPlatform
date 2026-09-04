package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.entity.AiAssetView;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiLearningCrossQuestionService {
    private static final int WINDOW_DAYS = 30;
    private static final long MIN_SAMPLE = 5L;
    private static final long MIN_USERS = 3L;

    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;

    public AiLearningCrossQuestionService(QuestionKnowledgePointMapper questionKnowledgePointMapper) {
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
    }

    public void apply(AiLearningEffectVO view, List<AiAssetView> assetViews,
                      List<PracticeRecord> practices) {
        CrossQuestionStats stats = calculate(assetViews, practices);
        Double afterRate = percentage(stats.afterViewCorrectCount, stats.afterViewPracticeCount);
        Double baselineRate = percentage(stats.baselineCorrectCount, stats.baselinePracticeCount);
        Double lift = afterRate == null || baselineRate == null ? null : roundOne(afterRate - baselineRate);

        view.setCrossQuestionWindowDays(WINDOW_DAYS);
        view.setCrossQuestionAfterViewPracticeCount(stats.afterViewPracticeCount);
        view.setCrossQuestionAfterViewUserCount((long) stats.afterViewUsers.size());
        view.setCrossQuestionAfterViewCorrectRate(afterRate);
        view.setCrossQuestionBaselinePracticeCount(stats.baselinePracticeCount);
        view.setCrossQuestionBaselineUserCount((long) stats.baselineUsers.size());
        view.setCrossQuestionBaselineCorrectRate(baselineRate);
        view.setCrossQuestionCorrectRateLift(lift);

        if (stats.afterViewPracticeCount < MIN_SAMPLE || stats.baselinePracticeCount < MIN_SAMPLE
                || stats.afterViewUsers.size() < MIN_USERS || stats.baselineUsers.size() < MIN_USERS
                || lift == null) {
            view.setCrossQuestionConclusionLevel("INSUFFICIENT_DATA");
            view.setCrossQuestionConclusion("跨题任一对照组需至少 " + MIN_SAMPLE
                    + " 条作答且覆盖 " + MIN_USERS + " 位学习者；当前代表性不足，暂不判断迁移效果。");
        } else if (lift >= 5.0) {
            view.setCrossQuestionConclusionLevel("POSITIVE_ASSOCIATION");
            view.setCrossQuestionConclusion("30 天窗口内，共享知识点的跨题作答正确率更高，已观察到正向关联；该结果仍不代表因果提升。");
        } else if (lift <= -5.0) {
            view.setCrossQuestionConclusionLevel("NEEDS_ATTENTION");
            view.setCrossQuestionConclusion("30 天窗口内的跨题作答未体现提升，建议结合知识点映射、题目难度和资产质量继续排查。");
        } else {
            view.setCrossQuestionConclusionLevel("NO_CLEAR_DIFFERENCE");
            view.setCrossQuestionConclusion("30 天窗口内两组跨题正确率差异较小，尚未观察到明确的知识迁移关联。");
        }
    }

    private CrossQuestionStats calculate(List<AiAssetView> views, List<PracticeRecord> practices) {
        Set<Long> questionIds = new HashSet<>();
        views.stream().map(AiAssetView::getQuestionId).filter(id -> id != null).forEach(questionIds::add);
        practices.stream().map(PracticeRecord::getQuestionId).filter(id -> id != null).forEach(questionIds::add);
        if (questionIds.isEmpty()) { return new CrossQuestionStats(); }

        List<QuestionKnowledgePoint> relations = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .in(QuestionKnowledgePoint::getQuestionId, questionIds));
        Map<Long, Set<Long>> knowledgePointsByQuestion = new HashMap<>();
        for (QuestionKnowledgePoint relation : relations) {
            if (relation.getQuestionId() == null || relation.getKnowledgePointId() == null) { continue; }
            knowledgePointsByQuestion.computeIfAbsent(relation.getQuestionId(), key -> new HashSet<>())
                    .add(relation.getKnowledgePointId());
        }

        Map<Long, List<SourceView>> viewsByUser = new HashMap<>();
        for (AiAssetView view : views) {
            if (view.getUserId() == null || view.getQuestionId() == null || view.getFirstViewTime() == null
                    || !knowledgePointsByQuestion.containsKey(view.getQuestionId())) { continue; }
            viewsByUser.computeIfAbsent(view.getUserId(), key -> new ArrayList<>())
                    .add(new SourceView(view.getQuestionId(), view.getFirstViewTime()));
        }

        CrossQuestionStats result = new CrossQuestionStats();
        for (PracticeRecord practice : practices) {
            if (practice.getUserId() == null || practice.getQuestionId() == null || practice.getCreateTime() == null) {
                continue;
            }
            Set<Long> targetPoints = knowledgePointsByQuestion.get(practice.getQuestionId());
            if (targetPoints == null || targetPoints.isEmpty()) { continue; }
            boolean prior = false;
            boolean recentPrior = false;
            boolean upcoming = false;
            for (SourceView source : viewsByUser.getOrDefault(practice.getUserId(), List.of())) {
                if (source.questionId().equals(practice.getQuestionId())
                        || !sharesKnowledgePoint(knowledgePointsByQuestion.get(source.questionId()), targetPoints)) {
                    continue;
                }
                if (!source.viewTime().isAfter(practice.getCreateTime())) {
                    prior = true;
                    if (!practice.getCreateTime().isAfter(source.viewTime().plusDays(WINDOW_DAYS))) {
                        recentPrior = true;
                    }
                } else if (!source.viewTime().isAfter(practice.getCreateTime().plusDays(WINDOW_DAYS))) {
                    upcoming = true;
                }
            }
            if (recentPrior) {
                result.afterViewPracticeCount++;
                result.afterViewUsers.add(practice.getUserId());
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) { result.afterViewCorrectCount++; }
            } else if (!prior && upcoming) {
                result.baselinePracticeCount++;
                result.baselineUsers.add(practice.getUserId());
                if (Integer.valueOf(1).equals(practice.getIsCorrect())) { result.baselineCorrectCount++; }
            }
        }
        return result;
    }

    private boolean sharesKnowledgePoint(Set<Long> left, Set<Long> right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) { return false; }
        Set<Long> smaller = left.size() <= right.size() ? left : right;
        Set<Long> larger = smaller == left ? right : left;
        return smaller.stream().anyMatch(larger::contains);
    }

    private Double percentage(long numerator, long denominator) {
        return denominator <= 0 ? null : roundOne(numerator * 100.0 / denominator);
    }

    private Double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record SourceView(Long questionId, LocalDateTime viewTime) { }

    private static class CrossQuestionStats {
        private long afterViewPracticeCount;
        private long afterViewCorrectCount;
        private long baselinePracticeCount;
        private long baselineCorrectCount;
        private final Set<Long> afterViewUsers = new HashSet<>();
        private final Set<Long> baselineUsers = new HashSet<>();
    }
}
