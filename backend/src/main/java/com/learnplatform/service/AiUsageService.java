package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.AiUsageOverviewVO;
import com.learnplatform.dto.AiUsageReportVO;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 调用分析服务（管理端）
 */
@Service
public class AiUsageService {

    private static final int DEFAULT_REPORT_DAYS = 7;
    private static final int MAX_REPORT_DAYS = 90;
    private static final long MIN_ALERT_CALLS = 5;
    private static final double FAILURE_RATE_ALERT_THRESHOLD = 10.0;
    private static final int SLOW_DURATION_ALERT_THRESHOLD_MS = 5000;
    private static final long USAGE_SPIKE_MIN_DELTA = 10;

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiCallLogMapper aiCallLogMapper;
    private final UserMapper userMapper;

    public AiUsageService(AiCallLogMapper aiCallLogMapper, UserMapper userMapper) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.userMapper = userMapper;
    }

    /**
     * 获取 AI 调用总览
     *
     * @param days 最近天数，默认 30
     */
    public AiUsageOverviewVO getOverview(Integer days) {
        if (days == null || days <= 0) {
            days = 30;
        }

        AiUsageOverviewVO vo = new AiUsageOverviewVO();

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        // 查询时间范围内的所有日志
        List<AiCallLog> allLogs = aiCallLogMapper.selectList(
                new LambdaQueryWrapper<AiCallLog>()
                        .ge(AiCallLog::getCreateTime, since)
                        .orderByDesc(AiCallLog::getCreateTime)
        );

        // 今日日志
        List<AiCallLog> todayLogs = allLogs.stream()
                .filter(log -> log.getCreateTime() != null && !log.getCreateTime().isBefore(todayStart))
                .collect(Collectors.toList());

        // ====== 全局统计 ======
        long totalCalls = allLogs.size();
        long successCalls = allLogs.stream().filter(log -> log.getStatus() != null && log.getStatus() == 1).count();
        long failedCalls = totalCalls - successCalls;
        double successRate = totalCalls > 0 ? Math.round(successCalls * 10000.0 / totalCalls) / 100.0 : 0;
        long totalTokens = allLogs.stream()
                .filter(log -> log.getTokensUsed() != null)
                .mapToLong(AiCallLog::getTokensUsed)
                .sum();
        double avgDuration = allLogs.stream()
                .filter(log -> log.getDuration() != null && log.getDuration() > 0)
                .mapToInt(AiCallLog::getDuration)
                .average()
                .orElse(0);
        avgDuration = Math.round(avgDuration * 100.0) / 100.0;

        vo.setTotalCalls(totalCalls);
        vo.setSuccessCalls(successCalls);
        vo.setFailedCalls(failedCalls);
        vo.setSuccessRate(successRate);
        vo.setTotalTokens(totalTokens);
        vo.setAvgDuration(avgDuration);
        vo.setTodayCalls((long) todayLogs.size());
        vo.setTodayTokens(todayLogs.stream()
                .filter(l -> l.getTokensUsed() != null)
                .mapToLong(AiCallLog::getTokensUsed)
                .sum());
        vo.setTotalCostUsd(sumCosts(allLogs));
        vo.setTodayCostUsd(sumCosts(todayLogs));

        // ====== 按功能分组 ======
        Map<String, List<AiCallLog>> byFunction = allLogs.stream()
                .filter(l -> l.getFunctionType() != null)
                .collect(Collectors.groupingBy(AiCallLog::getFunctionType));
        List<AiUsageOverviewVO.FunctionStats> functionStats = new ArrayList<>();
        for (Map.Entry<String, List<AiCallLog>> entry : byFunction.entrySet()) {
            AiUsageOverviewVO.FunctionStats fs = new AiUsageOverviewVO.FunctionStats();
            fs.setFunctionType(entry.getKey());
            List<AiCallLog> logs = entry.getValue();
            fs.setCount((long) logs.size());
            fs.setSuccessCount(logs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 1).count());
            fs.setFailedCount(fs.getCount() - fs.getSuccessCount());
            fs.setTotalTokens(logs.stream().filter(l -> l.getTokensUsed() != null).mapToLong(AiCallLog::getTokensUsed).sum());
            fs.setTotalCostUsd(sumCosts(logs));
            fs.setAvgDuration(Math.round(logs.stream().filter(l -> l.getDuration() != null && l.getDuration() > 0).mapToInt(AiCallLog::getDuration).average().orElse(0) * 100.0) / 100.0);
            functionStats.add(fs);
        }
        functionStats.sort(Comparator.comparingLong(AiUsageOverviewVO.FunctionStats::getCount).reversed());
        vo.setFunctionStats(functionStats);

        // ====== 按模型分组 ======
        Map<String, List<AiCallLog>> byModel = allLogs.stream()
                .filter(l -> l.getModel() != null && !l.getModel().isEmpty())
                .collect(Collectors.groupingBy(AiCallLog::getModel));
        List<AiUsageOverviewVO.ModelStats> modelStats = new ArrayList<>();
        for (Map.Entry<String, List<AiCallLog>> entry : byModel.entrySet()) {
            AiUsageOverviewVO.ModelStats ms = new AiUsageOverviewVO.ModelStats();
            ms.setModel(entry.getKey());
            List<AiCallLog> logs = entry.getValue();
            ms.setCount((long) logs.size());
            ms.setTotalTokens(logs.stream().filter(l -> l.getTokensUsed() != null).mapToLong(AiCallLog::getTokensUsed).sum());
            ms.setTotalCostUsd(sumCosts(logs));
            ms.setAvgDuration(Math.round(logs.stream().filter(l -> l.getDuration() != null && l.getDuration() > 0).mapToInt(AiCallLog::getDuration).average().orElse(0) * 100.0) / 100.0);
            modelStats.add(ms);
        }
        modelStats.sort(Comparator.comparingLong(AiUsageOverviewVO.ModelStats::getCount).reversed());
        vo.setModelStats(modelStats);

        // ====== 每日趋势 ======
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, List<AiCallLog>> byDate = allLogs.stream()
                .filter(l -> l.getCreateTime() != null)
                .collect(Collectors.groupingBy(l -> l.getCreateTime().format(dtf)));
        List<AiUsageOverviewVO.DailyTrend> dailyTrends = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).format(dtf);
            AiUsageOverviewVO.DailyTrend dt = new AiUsageOverviewVO.DailyTrend();
            dt.setDate(date);
            List<AiCallLog> dayLogs = byDate.getOrDefault(date, Collections.emptyList());
            dt.setTotalCount((long) dayLogs.size());
            dt.setSuccessCount(dayLogs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 1).count());
            dt.setFailedCount(dt.getTotalCount() - dt.getSuccessCount());
            dt.setTotalTokens(dayLogs.stream().filter(l -> l.getTokensUsed() != null).mapToLong(AiCallLog::getTokensUsed).sum());
            dt.setTotalCostUsd(sumCosts(dayLogs));
            dailyTrends.add(dt);
        }
        vo.setDailyTrends(dailyTrends);

        // ====== Top 活跃用户 ======
        Map<Long, List<AiCallLog>> byUser = allLogs.stream()
                .filter(l -> l.getUserId() != null)
                .collect(Collectors.groupingBy(AiCallLog::getUserId));
        List<AiUsageOverviewVO.TopUser> topUsers = new ArrayList<>();
        // 批量查询用户名
        Set<Long> userIds = byUser.keySet();
        Map<Long, String> usernameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            for (User u : users) {
                usernameMap.put(u.getId(), u.getUsername());
            }
        }
        for (Map.Entry<Long, List<AiCallLog>> entry : byUser.entrySet()) {
            AiUsageOverviewVO.TopUser tu = new AiUsageOverviewVO.TopUser();
            tu.setUserId(entry.getKey());
            tu.setUsername(usernameMap.getOrDefault(entry.getKey(), "未知用户"));
            List<AiCallLog> userLogs = entry.getValue();
            tu.setCallCount((long) userLogs.size());
            tu.setTotalTokens(userLogs.stream().filter(l -> l.getTokensUsed() != null).mapToLong(AiCallLog::getTokensUsed).sum());
            tu.setTotalCostUsd(sumCosts(userLogs));
            tu.setAvgDuration(Math.round(userLogs.stream().filter(l -> l.getDuration() != null && l.getDuration() > 0).mapToInt(AiCallLog::getDuration).average().orElse(0) * 100.0) / 100.0);
            topUsers.add(tu);
        }
        topUsers.sort(Comparator.comparingLong(AiUsageOverviewVO.TopUser::getCallCount).reversed());
        if (topUsers.size() > 10) {
            topUsers = topUsers.subList(0, 10);
        }
        vo.setTopUsers(topUsers);

        // ====== 最近失败调用 ======
        List<AiUsageOverviewVO.RecentFailure> recentFailures = allLogs.stream()
                .filter(l -> l.getStatus() != null && l.getStatus() == 0)
                .limit(20)
                .map(l -> {
                    AiUsageOverviewVO.RecentFailure rf = new AiUsageOverviewVO.RecentFailure();
                    rf.setId(l.getId());
                    rf.setUserId(l.getUserId());
                    rf.setFunctionType(l.getFunctionType());
                    rf.setModel(l.getModel());
                    rf.setErrorMessage(l.getErrorMessage());
                    rf.setCreateTime(l.getCreateTime() != null ? l.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
                    return rf;
                })
                .collect(Collectors.toList());
        vo.setRecentFailures(recentFailures);

        log.info("AI usage overview generated: {} calls in last {} days", totalCalls, days);
        return vo;
    }

    /**
     * 生成当前周期相对前一等长周期的运营报告。提醒仅由已有调用日志实时推导，
     * 不会把一次偶发的低样本波动误报为异常。
     */
    public AiUsageReportVO getReport(Integer days) {
        int resolvedDays = resolveReportDays(days);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(resolvedDays);
        LocalDateTime previousStart = currentStart.minusDays(resolvedDays);

        List<AiCallLog> logs = aiCallLogMapper.selectList(
                new LambdaQueryWrapper<AiCallLog>()
                        .ge(AiCallLog::getCreateTime, previousStart)
                        .lt(AiCallLog::getCreateTime, now)
        );
        List<AiCallLog> currentLogs = logs.stream()
                .filter(item -> item.getCreateTime() != null && !item.getCreateTime().isBefore(currentStart))
                .collect(Collectors.toList());
        List<AiCallLog> previousLogs = logs.stream()
                .filter(item -> item.getCreateTime() != null && item.getCreateTime().isBefore(currentStart))
                .collect(Collectors.toList());

        AiUsageReportVO.PeriodStats current = toPeriodStats(currentLogs);
        AiUsageReportVO.PeriodStats previous = toPeriodStats(previousLogs);
        AiUsageReportVO.ChangeStats changes = toChangeStats(current, previous);

        AiUsageReportVO report = new AiUsageReportVO();
        report.setDays(resolvedDays);
        report.setCurrent(current);
        report.setPrevious(previous);
        report.setChanges(changes);
        report.setAlerts(buildAlerts(current, previous, changes));
        return report;
    }

    private int resolveReportDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_REPORT_DAYS;
        }
        return Math.min(days, MAX_REPORT_DAYS);
    }

    private AiUsageReportVO.PeriodStats toPeriodStats(List<AiCallLog> logs) {
        AiUsageReportVO.PeriodStats stats = new AiUsageReportVO.PeriodStats();
        long totalCalls = logs.size();
        long failedCalls = logs.stream().filter(item -> !Integer.valueOf(1).equals(item.getStatus())).count();
        stats.setTotalCalls(totalCalls);
        stats.setFailedCalls(failedCalls);
        stats.setFailureRate(totalCalls == 0 ? 0.0 : round(failedCalls * 100.0 / totalCalls));
        stats.setTotalTokens(logs.stream().filter(item -> item.getTokensUsed() != null).mapToLong(AiCallLog::getTokensUsed).sum());
        stats.setAvgDuration(round(logs.stream().filter(item -> item.getDuration() != null && item.getDuration() > 0)
                .mapToInt(AiCallLog::getDuration).average().orElse(0)));
        stats.setTotalCostUsd(sumCosts(logs));
        return stats;
    }

    private AiUsageReportVO.ChangeStats toChangeStats(AiUsageReportVO.PeriodStats current,
                                                        AiUsageReportVO.PeriodStats previous) {
        AiUsageReportVO.ChangeStats changes = new AiUsageReportVO.ChangeStats();
        changes.setCallsPercent(percentChange(current.getTotalCalls(), previous.getTotalCalls()));
        changes.setTokensPercent(percentChange(current.getTotalTokens(), previous.getTotalTokens()));
        changes.setCostPercent(percentChange(current.getTotalCostUsd(), previous.getTotalCostUsd()));
        changes.setFailureRatePointChange(round(current.getFailureRate() - previous.getFailureRate()));
        changes.setAvgDurationPercent(percentChange(current.getAvgDuration(), previous.getAvgDuration()));
        return changes;
    }

    private List<AiUsageReportVO.Alert> buildAlerts(AiUsageReportVO.PeriodStats current,
                                                      AiUsageReportVO.PeriodStats previous,
                                                      AiUsageReportVO.ChangeStats changes) {
        List<AiUsageReportVO.Alert> alerts = new ArrayList<>();
        if (current.getTotalCalls() >= MIN_ALERT_CALLS && current.getFailureRate() >= FAILURE_RATE_ALERT_THRESHOLD) {
            alerts.add(new AiUsageReportVO.Alert("WARNING", "HIGH_FAILURE_RATE",
                    "当前周期失败率为 " + current.getFailureRate() + "%（" + current.getFailedCalls() + "/" + current.getTotalCalls() + "），请检查上游服务与错误日志。"));
        }
        if (current.getTotalCalls() >= MIN_ALERT_CALLS && previous.getFailureRate() > 0
                && current.getFailureRate() >= previous.getFailureRate() * 2) {
            alerts.add(new AiUsageReportVO.Alert("WARNING", "FAILURE_RATE_SPIKE",
                    "失败率较前一周期从 " + previous.getFailureRate() + "% 升至 " + current.getFailureRate() + "% 。"));
        }
        if (current.getTotalCalls() >= MIN_ALERT_CALLS && current.getAvgDuration() >= SLOW_DURATION_ALERT_THRESHOLD_MS
                && previous.getAvgDuration() > 0 && current.getAvgDuration() >= previous.getAvgDuration() * 1.5) {
            alerts.add(new AiUsageReportVO.Alert("WARNING", "LATENCY_SPIKE",
                    "平均耗时为 " + current.getAvgDuration() + "ms，较前一周期明显升高。"));
        }
        if (changes.getCallsPercent() != null && changes.getCallsPercent() >= 100
                && current.getTotalCalls() - previous.getTotalCalls() >= USAGE_SPIKE_MIN_DELTA) {
            alerts.add(new AiUsageReportVO.Alert("INFO", "CALL_VOLUME_SPIKE",
                    "调用量较前一周期增长 " + changes.getCallsPercent() + "%（+" + (current.getTotalCalls() - previous.getTotalCalls()) + " 次）。"));
        }
        return alerts;
    }

    private Double percentChange(Number current, Number previous) {
        if (previous == null || previous.doubleValue() == 0) {
            return null;
        }
        return round((current.doubleValue() - previous.doubleValue()) * 100 / previous.doubleValue());
    }

    private Double percentChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return round(current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 6, java.math.RoundingMode.HALF_UP).doubleValue());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private BigDecimal sumCosts(List<AiCallLog> logs) {
        return logs.stream()
                .map(AiCallLog::getCostUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }
}
