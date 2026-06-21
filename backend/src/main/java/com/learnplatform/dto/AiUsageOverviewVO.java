package com.learnplatform.dto;

import java.util.List;
import java.math.BigDecimal;

/**
 * AI 调用分析总览 VO
 */
public class AiUsageOverviewVO {

    /** 总调用次数 */
    private Long totalCalls;

    /** 成功调用次数 */
    private Long successCalls;

    /** 失败调用次数 */
    private Long failedCalls;

    /** 成功率（百分比，0-100） */
    private Double successRate;

    /** 总 tokens 使用量 */
    private Long totalTokens;

    /** 平均耗时（毫秒） */
    private Double avgDuration;

    /** 今日调用次数 */
    private Long todayCalls;

    /** 今日 tokens 使用量 */
    private Long todayTokens;

    /** 已配置价格且 usage 完整的调用成本合计（USD） */
    private BigDecimal totalCostUsd;

    /** 今日已配置价格且 usage 完整的调用成本合计（USD） */
    private BigDecimal todayCostUsd;

    /** 按功能分组的调用统计 */
    private List<FunctionStats> functionStats;

    /** 按模型分组的调用统计 */
    private List<ModelStats> modelStats;

    /** 近 N 天每日调用趋势 */
    private List<DailyTrend> dailyTrends;

    /** Top 活跃用户 */
    private List<TopUser> topUsers;

    /** 最近失败调用列表 */
    private List<RecentFailure> recentFailures;

    // ======================== inner classes ========================

    public static class FunctionStats {
        private String functionType;
        private Long count;
        private Long successCount;
        private Long failedCount;
        private Long totalTokens;
        private BigDecimal totalCostUsd;
        private Double avgDuration;

        public String getFunctionType() { return functionType; }
        public void setFunctionType(String functionType) { this.functionType = functionType; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
        public Long getSuccessCount() { return successCount; }
        public void setSuccessCount(Long successCount) { this.successCount = successCount; }
        public Long getFailedCount() { return failedCount; }
        public void setFailedCount(Long failedCount) { this.failedCount = failedCount; }
        public Long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
        public BigDecimal getTotalCostUsd() { return totalCostUsd; }
        public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
        public Double getAvgDuration() { return avgDuration; }
        public void setAvgDuration(Double avgDuration) { this.avgDuration = avgDuration; }
    }

    public static class ModelStats {
        private String model;
        private Long count;
        private Long totalTokens;
        private BigDecimal totalCostUsd;
        private Double avgDuration;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
        public Long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
        public BigDecimal getTotalCostUsd() { return totalCostUsd; }
        public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
        public Double getAvgDuration() { return avgDuration; }
        public void setAvgDuration(Double avgDuration) { this.avgDuration = avgDuration; }
    }

    public static class DailyTrend {
        private String date;
        private Long totalCount;
        private Long successCount;
        private Long failedCount;
        private Long totalTokens;
        private BigDecimal totalCostUsd;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public Long getTotalCount() { return totalCount; }
        public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }
        public Long getSuccessCount() { return successCount; }
        public void setSuccessCount(Long successCount) { this.successCount = successCount; }
        public Long getFailedCount() { return failedCount; }
        public void setFailedCount(Long failedCount) { this.failedCount = failedCount; }
        public Long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
        public BigDecimal getTotalCostUsd() { return totalCostUsd; }
        public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
    }

    public static class TopUser {
        private Long userId;
        private String username;
        private Long callCount;
        private Long totalTokens;
        private BigDecimal totalCostUsd;
        private Double avgDuration;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Long getCallCount() { return callCount; }
        public void setCallCount(Long callCount) { this.callCount = callCount; }
        public Long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
        public BigDecimal getTotalCostUsd() { return totalCostUsd; }
        public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
        public Double getAvgDuration() { return avgDuration; }
        public void setAvgDuration(Double avgDuration) { this.avgDuration = avgDuration; }
    }

    public static class RecentFailure {
        private Long id;
        private Long userId;
        private String functionType;
        private String model;
        private String errorMessage;
        private String createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getFunctionType() { return functionType; }
        public void setFunctionType(String functionType) { this.functionType = functionType; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }

    // ======================== top-level getter/setter ========================

    public Long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Long totalCalls) { this.totalCalls = totalCalls; }

    public Long getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(Long successCalls) { this.successCalls = successCalls; }

    public Long getFailedCalls() { return failedCalls; }
    public void setFailedCalls(Long failedCalls) { this.failedCalls = failedCalls; }

    public Double getSuccessRate() { return successRate; }
    public void setSuccessRate(Double successRate) { this.successRate = successRate; }

    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }

    public Double getAvgDuration() { return avgDuration; }
    public void setAvgDuration(Double avgDuration) { this.avgDuration = avgDuration; }

    public Long getTodayCalls() { return todayCalls; }
    public void setTodayCalls(Long todayCalls) { this.todayCalls = todayCalls; }

    public Long getTodayTokens() { return todayTokens; }
    public void setTodayTokens(Long todayTokens) { this.todayTokens = todayTokens; }

    public BigDecimal getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }

    public BigDecimal getTodayCostUsd() { return todayCostUsd; }
    public void setTodayCostUsd(BigDecimal todayCostUsd) { this.todayCostUsd = todayCostUsd; }

    public List<FunctionStats> getFunctionStats() { return functionStats; }
    public void setFunctionStats(List<FunctionStats> functionStats) { this.functionStats = functionStats; }

    public List<ModelStats> getModelStats() { return modelStats; }
    public void setModelStats(List<ModelStats> modelStats) { this.modelStats = modelStats; }

    public List<DailyTrend> getDailyTrends() { return dailyTrends; }
    public void setDailyTrends(List<DailyTrend> dailyTrends) { this.dailyTrends = dailyTrends; }

    public List<TopUser> getTopUsers() { return topUsers; }
    public void setTopUsers(List<TopUser> topUsers) { this.topUsers = topUsers; }

    public List<RecentFailure> getRecentFailures() { return recentFailures; }
    public void setRecentFailures(List<RecentFailure> recentFailures) { this.recentFailures = recentFailures; }
}
