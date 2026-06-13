package com.learnplatform.service.ai;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 AI Provider 实现
 * 支持 OpenAI API 以及兼容接口（如 DeepSeek、通义千问等）
 */
@Component
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    private final AiConfig aiConfig;
    private final RestTemplate restTemplate;

    public OpenAiProvider(AiConfig aiConfig, RestTemplateBuilder restTemplateBuilder) {
        this.aiConfig = aiConfig;
        Duration timeout = Duration.ofMillis(aiConfig.getTimeout());
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!aiConfig.isEnabled()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 功能未启用，请在环境变量中配置 AI_ENABLED=true 和 AI_API_KEY");
        }
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI API Key 未配置，请在环境变量中设置 AI_API_KEY");
        }

        String url = aiConfig.getApiBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + aiConfig.getApiKey());

        Map<String, Object> body = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "max_tokens", aiConfig.getMaxTokens(),
                "temperature", 0.7
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            log.info("调用 AI API: model={}, url={}", aiConfig.getModel(), url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        String content = (String) message.get("content");
                        log.info("AI 调用成功，返回 {} 字符", content != null ? content.length() : 0);
                        return content;
                    }
                }
            }

            log.warn("AI API 返回异常: status={}", response.getStatusCode());
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 服务返回异常，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI API 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 服务调用失败: " + e.getMessage());
        }
    }
}
