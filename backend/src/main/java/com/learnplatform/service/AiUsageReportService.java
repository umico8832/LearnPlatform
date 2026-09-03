package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiUsageAlertVO;
import com.learnplatform.dto.AiUsageReportVO;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.AiUsageAlert;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.AiUsageAlertMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AiUsageReportService {

    private static final int DEFAULT_REPORT_DAYS = 7;
    private static final int MAX_REPORT_DAYS = 90;
    private static final long MIN_ALERT_CALLS = 5;
    private static final double FAILURE_RATE_ALERT_THRESHOLD = 10.0;
    private static final int SLOW_DURATION_ALERT_THRESHOLD_MS = 5000;
    private static final long USAGE_SPIKE_MIN_DELTA = 10;
    private static final String ALERT_STATUS_OPEN = "OPEN";
    private static final String ALERT_STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiCallLogMapper aiCallLogMapper;
    private final AiUsageAlertMapper aiUsageAlertMapper;
    private final AiUsageAlertNotificationService alertNotificationService;

    public AiUsageReportService(AiCallLogMapper aiCallLogMapper,
                                AiUsageAlertMapper aiUsageAlertMapper,
                                AiUsageAlertNotificationService alertNotificationService) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.aiUsageAlertMapper = aiUsageAlertMapper;
        this.alertNotificationService = alertNotificationService;
    }

    /**
     * 生成当前周期相对前一等长周期的运营报告。提醒仅由已有调用日志实时推导，
     * 不会把一次偶发的低样本波动误报为异常。
     */
    @Transactional
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
        report.setAlerts(syncAlerts(buildAlerts(current, previous, changes), resolvedDays, currentStart,
                now, current, previous, changes));
        return report;
    }

    public List<AiUsageAlertVO> getOpenAlerts(Integer limit) {
        int resolvedLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return aiUsageAlertMapper.selectList(new LambdaQueryWrapper<AiUsageAlert>()
                        .eq(AiUsageAlert::getStatus, ALERT_STATUS_OPEN)
                        .orderByDesc(AiUsageAlert::getPeriodEnd)
                        .orderByDesc(AiUsageAlert::getCreateTime)
                        .last("LIMIT " + resolvedLimit))
                .stream()
                .map(this::toAlertVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AiUsageAlertVO acknowledgeAlert(Long alertId, Long adminUserId) {
        AiUsageAlert alert = aiUsageAlertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "AI 运营提醒不存在");
        }
        if (!ALERT_STATUS_OPEN.equals(alert.getStatus())) {
            return toAlertVO(alert);
        }
        alert.setStatus(ALERT_STATUS_ACKNOWLEDGED);
        alert.setAcknowledgedBy(adminUserId);
        alert.setAcknowledgedTime(LocalDateTime.now());
        aiUsageAlertMapper.updateById(alert);
        return toAlertVO(alert);
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
        stats.setTotalTokens(logs.stream().filter(item -> item.getTokensUsed() != null)
                .mapToLong(AiCallLog::getTokensUsed).sum());
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
                    "当前周期失败率为 " + current.getFailureRate() + "%（" + current.getFailedCalls()
                    + "/" + current.getTotalCalls() + "），请检查上游服务与错误日志。"));
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
                    "调用量较前一周期增长 " + changes.getCallsPercent() + "%（+"
                    + (current.getTotalCalls() - previous.getTotalCalls()) + " 次）。"));
        }
        return alerts;
    }

    private List<AiUsageReportVO.Alert> syncAlerts(List<AiUsageReportVO.Alert> alerts,
                                                   int days,
                                                   LocalDateTime periodStart,
                                                   LocalDateTime periodEnd,
                                                   AiUsageReportVO.PeriodStats current,
                                                   AiUsageReportVO.PeriodStats previous,
                                                   AiUsageReportVO.ChangeStats changes) {
        if (alerts.isEmpty()) {
            return alerts;
        }
        String snapshot = buildMetricSnapshot(current, previous, changes);
        List<AiUsageReportVO.Alert> synced = new ArrayList<>();
        for (AiUsageReportVO.Alert alert : alerts) {
            AiUsageAlert entity = findOpenAlert(alert.getType(), days, periodEnd);
            if (entity == null) {
                entity = new AiUsageAlert();
                entity.setAlertType(alert.getType());
                entity.setPeriodDays(days);
                entity.setPeriodStart(periodStart);
                entity.setPeriodEnd(periodEnd);
                entity.setStatus(ALERT_STATUS_OPEN);
                entity.setLevel(alert.getLevel());
                entity.setMessage(alert.getMessage());
                entity.setMetricSnapshot(snapshot);
                aiUsageAlertMapper.insert(entity);
                alertNotificationService.notifyCreatedAlert(entity);
            } else {
                entity.setLevel(alert.getLevel());
                entity.setMessage(alert.getMessage());
                entity.setMetricSnapshot(snapshot);
                aiUsageAlertMapper.updateById(entity);
            }
            alert.setId(entity.getId());
            alert.setStatus(entity.getStatus());
            alert.setPeriodStart(formatDateTime(entity.getPeriodStart()));
            alert.setPeriodEnd(formatDateTime(entity.getPeriodEnd()));
            synced.add(alert);
        }
        return synced;
    }

    private AiUsageAlert findOpenAlert(String type, int days, LocalDateTime periodEnd) {
        LocalDateTime reportDayStart = LocalDateTime.of(periodEnd.toLocalDate(), LocalTime.MIN);
        LocalDateTime reportDayEnd = reportDayStart.plusDays(1);
        return aiUsageAlertMapper.selectOne(new LambdaQueryWrapper<AiUsageAlert>()
                .eq(AiUsageAlert::getAlertType, type)
                .eq(AiUsageAlert::getPeriodDays, days)
                .eq(AiUsageAlert::getStatus, ALERT_STATUS_OPEN)
                .ge(AiUsageAlert::getPeriodEnd, reportDayStart)
                .lt(AiUsageAlert::getPeriodEnd, reportDayEnd)
                .orderByDesc(AiUsageAlert::getUpdateTime)
                .last("LIMIT 1"));
    }

    private String buildMetricSnapshot(AiUsageReportVO.PeriodStats current,
                                       AiUsageReportVO.PeriodStats previous,
                                       AiUsageReportVO.ChangeStats changes) {
        return "{" +
                "\"currentCalls\":" + current.getTotalCalls() +
                ",\"currentFailedCalls\":" + current.getFailedCalls() +
                ",\"currentFailureRate\":" + current.getFailureRate() +
                ",\"currentAvgDuration\":" + current.getAvgDuration() +
                ",\"previousCalls\":" + previous.getTotalCalls() +
                ",\"previousFailureRate\":" + previous.getFailureRate() +
                ",\"callsPercent\":" + nullableNumber(changes.getCallsPercent()) +
                ",\"avgDurationPercent\":" + nullableNumber(changes.getAvgDurationPercent()) +
                "}";
    }

    private String nullableNumber(Number value) {
        return value == null ? "null" : value.toString();
    }

    private AiUsageAlertVO toAlertVO(AiUsageAlert alert) {
        AiUsageAlertVO vo = new AiUsageAlertVO();
        vo.setId(alert.getId());
        vo.setLevel(alert.getLevel());
        vo.setType(alert.getAlertType());
        vo.setMessage(alert.getMessage());
        vo.setPeriodDays(alert.getPeriodDays());
        vo.setPeriodStart(formatDateTime(alert.getPeriodStart()));
        vo.setPeriodEnd(formatDateTime(alert.getPeriodEnd()));
        vo.setStatus(alert.getStatus());
        vo.setAcknowledgedBy(alert.getAcknowledgedBy());
        vo.setAcknowledgedTime(formatDateTime(alert.getAcknowledgedTime()));
        vo.setCreateTime(formatDateTime(alert.getCreateTime()));
        vo.setUpdateTime(formatDateTime(alert.getUpdateTime()));
        return vo;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME_FORMATTER);
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
        return round(current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 6, java.math.RoundingMode.HALF_UP).doubleValue());
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
