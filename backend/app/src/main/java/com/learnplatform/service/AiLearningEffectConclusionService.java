package com.learnplatform.service;

import com.learnplatform.dto.AiLearningEffectVO;
import org.springframework.stereotype.Service;

@Service
public class AiLearningEffectConclusionService {
    private static final long MIN_SAMPLE = 5L;
    private static final long MIN_USERS = 3L;

    public void apply(AiLearningEffectVO view) {
        if (view.getAfterViewPracticeCount() < MIN_SAMPLE
                || view.getBaselinePracticeCount() < MIN_SAMPLE
                || view.getAfterViewUserCount() < MIN_USERS
                || view.getBaselineUserCount() < MIN_USERS
                || view.getCorrectRateLift() == null) {
            view.setConclusionLevel("INSUFFICIENT_DATA");
            view.setConclusion("任一同题对照组需至少 " + MIN_SAMPLE
                    + " 条作答且覆盖 " + MIN_USERS + " 位学习者；当前代表性不足，暂不判断学习效果。");
        } else if (view.getCorrectRateLift() >= 5.0) {
            view.setConclusionLevel("POSITIVE_ASSOCIATION");
            view.setConclusion("阅读 AI 学习资产后的同题作答正确率更高，已观察到正向关联；仍需结合样本结构持续验证。");
        } else if (view.getCorrectRateLift() <= -5.0) {
            view.setConclusionLevel("NEEDS_ATTENTION");
            view.setConclusion("阅读后的同题作答正确率未体现提升，建议结合资产反馈和题目难度检查内容质量。");
        } else {
            view.setConclusionLevel("NO_CLEAR_DIFFERENCE");
            view.setConclusion("两组正确率差异较小，当前尚未观察到明确关联，建议继续按资产类型跟踪。");
        }
    }
}
