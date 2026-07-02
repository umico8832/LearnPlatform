package com.learnplatform.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 调用运营报告。比较最近一个统计周期与其前一等长周期，供管理端日/周报和异常提醒使用。
 */
public class AiUsageReportVO {

    private Integer days;
    private PeriodStats current;
    private PeriodStats previous;
    private ChangeStats changes;
    private List<Alert> alerts;

    public static class PeriodStats {
        private Long totalCalls;
        private Long failedCalls;
        private Double failureRate;
        private Long totalTokens;
        private Double avgDuration;
        private BigDecimal totalCostUsd;

        public Long getTotalCalls() { return totalCalls; }
        public void setTotalCalls(Long totalCalls) { this.totalCalls = totalCalls; }
        public Long getFailedCalls() { return failedCalls; }
        public void setFailedCalls(Long failedCalls) { this.failedCalls = failedCalls; }
        public Double getFailureRate() { return failureRate; }
        public void setFailureRate(Double failureRate) { this.failureRate = failureRate; }
        public Long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
        public Double getAvgDuration() { return avgDuration; }
        public void setAvgDuration(Double avgDuration) { this.avgDuration = avgDuration; }
        public BigDecimal getTotalCostUsd() { return totalCostUsd; }
        public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
    }

    /** 与前一等长周期相比的百分比变化；前一周期为零时为 null，避免虚假的无限增长。 */
    public static class ChangeStats {
        private Double callsPercent;
        private Double tokensPercent;
        private Double costPercent;
        private Double failureRatePointChange;
        private Double avgDurationPercent;

        public Double getCallsPercent() { return callsPercent; }
        public void setCallsPercent(Double callsPercent) { this.callsPercent = callsPercent; }
        public Double getTokensPercent() { return tokensPercent; }
        public void setTokensPercent(Double tokensPercent) { this.tokensPercent = tokensPercent; }
        public Double getCostPercent() { return costPercent; }
        public void setCostPercent(Double costPercent) { this.costPercent = costPercent; }
        public Double getFailureRatePointChange() { return failureRatePointChange; }
        public void setFailureRatePointChange(Double failureRatePointChange) { this.failureRatePointChange = failureRatePointChange; }
        public Double getAvgDurationPercent() { return avgDurationPercent; }
        public void setAvgDurationPercent(Double avgDurationPercent) { this.avgDurationPercent = avgDurationPercent; }
    }

    public static class Alert {
        private Long id;
        private String level;
        private String type;
        private String message;
        private String status;
        private String periodStart;
        private String periodEnd;

        public Alert() { }
        public Alert(String level, String type, String message) {
            this.level = level;
            this.type = type;
            this.message = message;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPeriodStart() { return periodStart; }
        public void setPeriodStart(String periodStart) { this.periodStart = periodStart; }
        public String getPeriodEnd() { return periodEnd; }
        public void setPeriodEnd(String periodEnd) { this.periodEnd = periodEnd; }
    }

    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public PeriodStats getCurrent() { return current; }
    public void setCurrent(PeriodStats current) { this.current = current; }
    public PeriodStats getPrevious() { return previous; }
    public void setPrevious(PeriodStats previous) { this.previous = previous; }
    public ChangeStats getChanges() { return changes; }
    public void setChanges(ChangeStats changes) { this.changes = changes; }
    public List<Alert> getAlerts() { return alerts; }
    public void setAlerts(List<Alert> alerts) { this.alerts = alerts; }
}
