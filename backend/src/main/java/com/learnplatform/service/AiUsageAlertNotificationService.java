package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiUsageAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 运营提醒站外通知。通知失败只记录日志，不影响提醒持久化和报告生成。
 */
@Service
public class AiUsageAlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageAlertNotificationService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final RestOperations restOperations;

    @Autowired
    public AiUsageAlertNotificationService(AiConfig aiConfig,
                                           RestTemplateBuilder restTemplateBuilder,
                                           ObjectMapper objectMapper) {
        this(aiConfig, objectMapper, restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(resolveTimeout(aiConfig)))
                .setReadTimeout(Duration.ofMillis(resolveTimeout(aiConfig)))
                .build());
    }

    AiUsageAlertNotificationService(AiConfig aiConfig, ObjectMapper objectMapper, RestOperations restOperations) {
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        this.restOperations = restOperations;
    }

    public void notifyCreatedAlert(AiUsageAlert alert) {
        if (!aiConfig.isAlertWebhookEnabled()) {
            return;
        }
        String webhookUrl = aiConfig.getAlertWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("AI usage alert webhook is enabled but url is empty, alertId={}", alert.getId());
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = objectMapper.writeValueAsString(buildPayload(alert));
            ResponseEntity<String> response = restOperations.postForEntity(webhookUrl,
                    new HttpEntity<>(body, headers), String.class);
            log.info("AI usage alert webhook sent: alertId={}, status={}", alert.getId(),
                    response.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Failed to send AI usage alert webhook: alertId={}, type={}", alert.getId(),
                    alert.getAlertType(), e);
        }
    }

    private Map<String, Object> buildPayload(AiUsageAlert alert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "AI_USAGE_ALERT_CREATED");
        payload.put("id", alert.getId());
        payload.put("level", alert.getLevel());
        payload.put("type", alert.getAlertType());
        payload.put("message", alert.getMessage());
        payload.put("periodDays", alert.getPeriodDays());
        payload.put("periodStart", formatDateTime(alert.getPeriodStart()));
        payload.put("periodEnd", formatDateTime(alert.getPeriodEnd()));
        payload.put("metricSnapshot", alert.getMetricSnapshot());
        payload.put("status", alert.getStatus());
        return payload;
    }

    private String formatDateTime(java.time.LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME_FORMATTER);
    }

    private static int resolveTimeout(AiConfig aiConfig) {
        return aiConfig.getAlertWebhookTimeout() > 0 ? aiConfig.getAlertWebhookTimeout() : 5000;
    }
}
