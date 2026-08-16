package com.learnplatform.service.ai;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容的 AI Provider 实现
 * 支持 OpenAI API 以及兼容接口（如 DeepSeek、通义千问等）
 */
@Component
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    private final AiConfig aiConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    /** Provider 是单例；使用 ThreadLocal 避免并发调用之间串用 usage。 */
    private final ThreadLocal<AiTokenUsage> lastTokenUsage = new ThreadLocal<>();

    public OpenAiProvider(AiConfig aiConfig, RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofMillis(aiConfig.getTimeout());
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        validateConfig();
        lastTokenUsage.remove();

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
                lastTokenUsage.set(parseTokenUsage(response.getBody()));
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

    @Override
    public void chatStream(String systemPrompt, String userPrompt, Consumer<String> onContent) {
        validateConfig();
        lastTokenUsage.remove();

        String url = aiConfig.getApiBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        Map<String, Object> body = buildRequestBody(systemPrompt, userPrompt, true);

        try {
            log.info("调用 AI 流式 API: model={}, url={}", aiConfig.getModel(), url);
            restTemplate.execute(url, HttpMethod.POST, request -> {
                request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                request.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                request.getHeaders().setBearerAuth(aiConfig.getApiKey());
                objectMapper.writeValue(request.getBody(), body);
            }, response -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new BusinessException(ResultCode.BUSINESS_ERROR,
                            "AI 服务返回异常: HTTP " + response.getStatusCode().value());
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        response.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        JsonNode event = parseStreamEvent(line, objectMapper);
                        if (event != null) {
                            AiTokenUsage usage = parseTokenUsage(event);
                            if (usage != null) {
                                lastTokenUsage.set(usage);
                            }
                        }
                        String content = parseStreamContent(event);
                        if (content != null && !content.isEmpty()) {
                            onContent.accept(content);
                        }
                    }
                }
                return null;
            });
            log.info("AI 流式调用完成");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 流式调用失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 服务调用失败: " + e.getMessage());
        }
    }

    static String parseStreamContent(String line, ObjectMapper objectMapper) {
        if (line == null || !line.startsWith("data:")) {
            return null;
        }

        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }

        try {
            return parseStreamContent(objectMapper.readTree(data));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 流式响应解析失败");
        }
    }

    static JsonNode parseStreamEvent(String line, ObjectMapper objectMapper) {
        if (line == null || !line.startsWith("data:")) {
            return null;
        }
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 流式响应解析失败");
        }
    }

    static String parseStreamContent(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode content = root.path("choices").path(0).path("delta").path("content");
        return content.isTextual() ? content.asText() : null;
    }

    static AiTokenUsage parseTokenUsage(Map<String, Object> response) {
        Object usage = response.get("usage");
        if (!(usage instanceof Map<?, ?> usageMap)) {
            return null;
        }
        return toTokenUsage(usageMap.get("prompt_tokens"), usageMap.get("completion_tokens"),
                usageMap.get("total_tokens"));
    }

    static AiTokenUsage parseTokenUsage(JsonNode response) {
        JsonNode usage = response.path("usage");
        if (!usage.isObject()) {
            return null;
        }
        return toTokenUsage(usage.path("prompt_tokens"), usage.path("completion_tokens"), usage.path("total_tokens"));
    }

    private static AiTokenUsage toTokenUsage(Object prompt, Object completion, Object total) {
        Integer promptTokens = toInteger(prompt);
        Integer completionTokens = toInteger(completion);
        Integer totalTokens = toInteger(total);
        return totalTokens == null ? null : new AiTokenUsage(promptTokens, completionTokens, totalTokens);
    }

    private static AiTokenUsage toTokenUsage(JsonNode prompt, JsonNode completion, JsonNode total) {
        return total.isInt() || total.isLong()
                ? new AiTokenUsage(prompt.isInt() || prompt.isLong() ? prompt.asInt() : null,
                completion.isInt() || completion.isLong() ? completion.asInt() : null, total.asInt())
                : null;
    }

    private static Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    @Override
    public AiTokenUsage getLastTokenUsage() {
        return lastTokenUsage.get();
    }

    private void validateConfig() {
        if (!aiConfig.isEnabled()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 功能未启用，请在环境变量中配置 AI_ENABLED=true 和 AI_API_KEY");
        }
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI API Key 未配置，请在环境变量中设置 AI_API_KEY");
        }
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", aiConfig.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("max_tokens", aiConfig.getMaxTokens());
        body.put("temperature", 0.7);
        body.put("stream", stream);
        if (stream && aiConfig.isStreamIncludeUsage()) {
            body.put("stream_options", Map.of("include_usage", true));
        }
        return body;
    }
}
