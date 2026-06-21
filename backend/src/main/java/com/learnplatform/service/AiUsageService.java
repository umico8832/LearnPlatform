package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.AiUsageOverviewVO;
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

    private BigDecimal sumCosts(List<AiCallLog> logs) {
        return logs.stream()
                .map(AiCallLog::getCostUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }
}
