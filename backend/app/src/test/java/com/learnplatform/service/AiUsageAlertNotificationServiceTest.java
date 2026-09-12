package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiUsageAlert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import java.time.LocalDateTime;
import java.lang.reflect.Constructor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUsageAlertNotificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestOperations restOperations = mock(RestOperations.class);

    @Test
    @DisplayName("Spring 使用 webhook 通知服务的配置构造器")
    void shouldMarkConfigurationConstructorForSpringInjection() {
        Constructor<?> constructor = java.util.Arrays.stream(AiUsageAlertNotificationService.class.getConstructors())
                .filter(candidate -> candidate.getParameterCount() == 3)
                .findFirst()
                .orElseThrow();

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    @DisplayName("默认关闭时不发送 webhook")
    void shouldSkipWebhookWhenDisabled() {
        AiConfig config = new AiConfig();
        AiUsageAlertNotificationService service = new AiUsageAlertNotificationService(config, objectMapper, restOperations);

        service.notifyCreatedAlert(createAlert());

        verify(restOperations, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    @DisplayName("开启 webhook 后发送结构化提醒 payload")
    void shouldSendWebhookPayloadWhenEnabled() {
        AiConfig config = new AiConfig();
        config.setAlertWebhookEnabled(true);
        config.setAlertWebhookUrl("https://example.com/ai-alerts");
        when(restOperations.postForEntity(eq("https://example.com/ai-alerts"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));
        AiUsageAlertNotificationService service = new AiUsageAlertNotificationService(config, objectMapper, restOperations);

        service.notifyCreatedAlert(createAlert());

        verify(restOperations).postForEntity(eq("https://example.com/ai-alerts"), any(), eq(String.class));
    }

    @Test
    @DisplayName("webhook 地址为空时不发送请求")
    void shouldSkipWebhookWhenUrlIsBlank() {
        AiConfig config = new AiConfig();
        config.setAlertWebhookEnabled(true);
        config.setAlertWebhookUrl(" ");
        AiUsageAlertNotificationService service = new AiUsageAlertNotificationService(config, objectMapper, restOperations);

        service.notifyCreatedAlert(createAlert());

        verify(restOperations, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    @DisplayName("webhook 失败不向外抛异常")
    void shouldSwallowWebhookException() {
        AiConfig config = new AiConfig();
        config.setAlertWebhookEnabled(true);
        config.setAlertWebhookUrl("https://example.com/ai-alerts");
        when(restOperations.postForEntity(eq("https://example.com/ai-alerts"), any(), eq(String.class)))
                .thenThrow(new IllegalStateException("network error"));
        AiUsageAlertNotificationService service = new AiUsageAlertNotificationService(config, objectMapper, restOperations);

        service.notifyCreatedAlert(createAlert());

        verify(restOperations).postForEntity(eq("https://example.com/ai-alerts"), any(), eq(String.class));
    }

    private AiUsageAlert createAlert() {
        AiUsageAlert alert = new AiUsageAlert();
        alert.setId(12L);
        alert.setLevel("WARNING");
        alert.setAlertType("HIGH_FAILURE_RATE");
        alert.setMessage("失败率过高");
        alert.setPeriodDays(7);
        alert.setPeriodStart(LocalDateTime.of(2026, 7, 1, 10, 0));
        alert.setPeriodEnd(LocalDateTime.of(2026, 7, 2, 10, 0));
        alert.setMetricSnapshot("{\"currentFailureRate\":20.0}");
        alert.setStatus("OPEN");
        return alert;
    }
}
