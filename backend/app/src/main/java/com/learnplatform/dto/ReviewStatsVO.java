package com.learnplatform.dto;

/** 复习排期与最近评分事实；计数均非空。 */
public class ReviewStatsVO {

    /** 有效复习卡片总数。 */
    private int totalCards;

    /** 到期且可自动判分的卡片数，包含逾期。 */
    private int dueToday;

    /** 逾期且可自动判分的卡片数。 */
    private int overdue;

    /** 当前卡片中最近评分日期为今天的题目数，包含独立练习。 */
    private int reviewedToday;

    /** 没有 FSRS 状态的新卡、重置卡及旧卡。 */
    private int pendingRatingCards;

    /** FSRS LEARNING 状态卡片数。 */
    private int learningCards;

    /** FSRS REVIEW 状态卡片数，不表示掌握。 */
    private int reviewCards;

    /** FSRS RELEARNING 状态卡片数。 */
    private int relearningCards;

    /** 最近一次 FSRS 评分为 AGAIN 的卡片数。 */
    private int lastAgainCards;

    /** 最近一次 FSRS 评分为 HARD 的卡片数。 */
    private int lastHardCards;

    public int getTotalCards() { return totalCards; }
    public void setTotalCards(int value) { totalCards = value; }
    public int getDueToday() { return dueToday; }
    public void setDueToday(int value) { dueToday = value; }
    public int getOverdue() { return overdue; }
    public void setOverdue(int value) { overdue = value; }
    public int getReviewedToday() { return reviewedToday; }
    public void setReviewedToday(int value) { reviewedToday = value; }
    public int getPendingRatingCards() { return pendingRatingCards; }
    public void setPendingRatingCards(int value) { pendingRatingCards = value; }
    public int getLearningCards() { return learningCards; }
    public void setLearningCards(int value) { learningCards = value; }
    public int getReviewCards() { return reviewCards; }
    public void setReviewCards(int value) { reviewCards = value; }
    public int getRelearningCards() { return relearningCards; }
    public void setRelearningCards(int value) { relearningCards = value; }
    public int getLastAgainCards() { return lastAgainCards; }
    public void setLastAgainCards(int value) { lastAgainCards = value; }
    public int getLastHardCards() { return lastHardCards; }
    public void setLastHardCards(int value) { lastHardCards = value; }
}
