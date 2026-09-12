package com.learnplatform.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Service
public class TurnstileService {
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final boolean enabled;
    private final String secretKey;
    private final RestTemplate restTemplate;

    @Autowired
    public TurnstileService(@Value("${turnstile.enabled:false}") boolean enabled,
                            @Value("${turnstile.secret-key:}") String secretKey,
                            RestTemplateBuilder restTemplateBuilder) {
        this.enabled = enabled;
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    TurnstileService(boolean enabled, String secretKey, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.restTemplate = restTemplate;
    }

    public void verify(String token, String remoteIp) {
        if (!enabled) {
            return;
        }
        if (secretKey.isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "人机验证配置错误");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "请完成人机验证");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token.trim());
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            TurnstileResponse response = restTemplate.postForObject(
                    VERIFY_URL, new HttpEntity<>(form, headers), TurnstileResponse.class);
            if (response == null || !response.success()) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "人机验证失败，请刷新后重试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "人机验证服务暂时不可用，请稍后重试");
        }
    }

    private record TurnstileResponse(boolean success,
                                     @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
