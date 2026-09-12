package com.learnplatform.dto;

import java.util.List;

/**
 * 复习上下文 VO —— 用于构建 AI 复习建议 Prompt
 */
public class ReviewContextVO {

    /** 复习统计概览 */
    private ReviewStatsVO stats;

    /** 困难卡片列表（EF < 2.0，最多 10 条） */
    private List<ReviewScheduleVO> difficultCards;

    /** 逾期卡片列表（nextReviewDate < today，最多 10 条） */
    private List<ReviewScheduleVO> overdueCards;

    /** 近 7 天复习量（每天数量，长度=7，索引 0=6天前, 6=今天） */
    private List<Integer> recentDailyReviews;

    public ReviewStatsVO getStats() { return stats; }
    public void setStats(ReviewStatsVO stats) { this.stats = stats; }

    public List<ReviewScheduleVO> getDifficultCards() { return difficultCards; }
    public void setDifficultCards(List<ReviewScheduleVO> difficultCards) { this.difficultCards = difficultCards; }

    public List<ReviewScheduleVO> getOverdueCards() { return overdueCards; }
    public void setOverdueCards(List<ReviewScheduleVO> overdueCards) { this.overdueCards = overdueCards; }

    public List<Integer> getRecentDailyReviews() { return recentDailyReviews; }
    public void setRecentDailyReviews(List<Integer> recentDailyReviews) {
        this.recentDailyReviews = recentDailyReviews;
    }
}