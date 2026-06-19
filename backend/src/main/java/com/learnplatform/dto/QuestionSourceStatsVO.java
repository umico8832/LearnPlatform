package com.learnplatform.dto;

/**
 * 题目来源统计 VO
 */
public class QuestionSourceStatsVO {

    private String sourceType;
    private long count;

    public QuestionSourceStatsVO() {}

    public QuestionSourceStatsVO(String sourceType, long count) {
        this.sourceType = sourceType;
        this.count = count;
    }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}