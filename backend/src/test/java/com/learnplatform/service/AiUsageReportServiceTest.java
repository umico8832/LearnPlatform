package com.learnplatform.service;

import com.learnplatform.dto.AiUsageReportVO;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.AiUsageAlert;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.AiUsageAlertMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiUsageReportServiceTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @Mock
    private AiUsageAlertMapper aiUsageAlertMapper;

    @Mock
    private AiUsageAlertNotificationService alertNotificationService;

    @InjectMocks
    private AiUsageReportService aiUsageReportService;

    @Nested
    @DisplayName("getReport 测试")
    class GetReportTests {

        @Test
        @DisplayName("高失败率和调用量翻倍时生成可行动提醒")
        void shouldGenerateAlertsForHighFailureRateAndUsageSpike() {
            List<AiCallLog> logs = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                logs.add(createLog(i + 1L, 1, 1000, LocalDateTime.now().minusDays(10)));
            }
            for (int i = 0; i < 15; i++) {
                logs.add(createLog(i + 10L, i < 3 ? 0 : 1, 1200, LocalDateTime.now().minusDays(1)));
            }
            when(aiCallLogMapper.selectList(any())).thenReturn(logs);

            AiUsageReportVO report = aiUsageReportService.getReport(7);

            assertEquals(7, report.getDays());
            assertEquals(15L, report.getCurrent().getTotalCalls());
            assertEquals(20.0, report.getCurrent().getFailureRate());
            assertEquals(200.0, report.getChanges().getCallsPercent());
            assertTrue(report.getAlerts().stream().anyMatch(item -> "HIGH_FAILURE_RATE".equals(item.getType())));
            assertTrue(report.getAlerts().stream().anyMatch(item -> "CALL_VOLUME_SPIKE".equals(item.getType())));
            verify(aiUsageAlertMapper, atLeastOnce()).insert(any(AiUsageAlert.class));
            verify(alertNotificationService, atLeastOnce()).notifyCreatedAlert(any(AiUsageAlert.class));
        }

        @Test
        @DisplayName("前一周期无调用时不伪造环比百分比")
        void shouldKeepPercentChangeNullWhenPreviousPeriodIsEmpty() {
            when(aiCallLogMapper.selectList(any())).thenReturn(List.of(
                    createLog(1L, 1, 1000, LocalDateTime.now().minusHours(1))
            ));

            AiUsageReportVO report = aiUsageReportService.getReport(null);

            assertEquals(7, report.getDays());
            assertNull(report.getChanges().getCallsPercent());
            assertNull(report.getChanges().getTokensPercent());
            assertNull(report.getChanges().getAvgDurationPercent());
        }

        @Test
        @DisplayName("报告周期最大限制为 90 天")
        void shouldCapReportDaysAt90() {
            when(aiCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

            AiUsageReportVO report = aiUsageReportService.getReport(365);

            assertEquals(90, report.getDays());
        }

        @Test
        @DisplayName("生成提醒时保存可确认的运营提醒")
        void shouldPersistGeneratedAlertForAcknowledgement() {
            List<AiCallLog> logs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                logs.add(createLog(i + 1L, i < 2 ? 0 : 1, 1200, LocalDateTime.now().minusHours(1)));
            }
            when(aiCallLogMapper.selectList(any())).thenReturn(logs);

            aiUsageReportService.getReport(1);

            var captor = forClass(AiUsageAlert.class);
            verify(aiUsageAlertMapper, atLeastOnce()).insert(captor.capture());
            AiUsageAlert saved = captor.getAllValues().stream()
                    .filter(item -> "HIGH_FAILURE_RATE".equals(item.getAlertType()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("OPEN", saved.getStatus());
            assertEquals(1, saved.getPeriodDays());
            assertTrue(saved.getMetricSnapshot().contains("\"currentFailureRate\""));
            verify(alertNotificationService, atLeastOnce()).notifyCreatedAlert(any(AiUsageAlert.class));
        }

        @Test
        @DisplayName("复用当天未确认提醒时不重复发送站外通知")
        void shouldNotNotifyWebhookWhenReusingOpenAlert() {
            List<AiCallLog> logs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                logs.add(createLog(i + 1L, i < 2 ? 0 : 1, 1200, LocalDateTime.now().minusHours(1)));
            }
            AiUsageAlert existing = new AiUsageAlert();
            existing.setId(5L);
            existing.setLevel("WARNING");
            existing.setAlertType("HIGH_FAILURE_RATE");
            existing.setMessage("旧提醒");
            existing.setPeriodDays(1);
            existing.setPeriodStart(LocalDateTime.now().minusDays(1));
            existing.setPeriodEnd(LocalDateTime.now());
            existing.setStatus("OPEN");
            when(aiCallLogMapper.selectList(any())).thenReturn(logs);
            when(aiUsageAlertMapper.selectOne(any())).thenReturn(existing);

            AiUsageReportVO report = aiUsageReportService.getReport(1);

            assertEquals(5L, report.getAlerts().stream()
                    .filter(item -> "HIGH_FAILURE_RATE".equals(item.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getId());
            verify(aiUsageAlertMapper, never()).insert(any(AiUsageAlert.class));
            verify(aiUsageAlertMapper, atLeastOnce()).updateById(existing);
            verify(alertNotificationService, never()).notifyCreatedAlert(any(AiUsageAlert.class));
        }

        @Test
        @DisplayName("确认提醒时写入管理员和确认时间")
        void shouldAcknowledgeOpenAlert() {
            AiUsageAlert alert = new AiUsageAlert();
            alert.setId(9L);
            alert.setLevel("WARNING");
            alert.setAlertType("HIGH_FAILURE_RATE");
            alert.setMessage("失败率过高");
            alert.setPeriodDays(7);
            alert.setStatus("OPEN");
            when(aiUsageAlertMapper.selectById(9L)).thenReturn(alert);

            var result = aiUsageReportService.acknowledgeAlert(9L, 1L);

            assertEquals("ACKNOWLEDGED", result.getStatus());
            assertEquals(1L, result.getAcknowledgedBy());
            assertNotNull(result.getAcknowledgedTime());
            verify(aiUsageAlertMapper).updateById(alert);
        }

        private AiCallLog createLog(Long id, int status, int duration, LocalDateTime createTime) {
            AiCallLog log = new AiCallLog();
            log.setId(id);
            log.setStatus(status);
            log.setDuration(duration);
            log.setTokensUsed(100);
            log.setCreateTime(createTime);
            return log;
        }
    }
}
