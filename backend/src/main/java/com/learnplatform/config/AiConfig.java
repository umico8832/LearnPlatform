package com.learnplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private boolean enabled = false;
    private String apiBaseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-3.5-turbo";
    private int timeout = 30000;
    private int maxTokens = 2000;
    /** 是否在流式请求中请求上游返回最终 usage（部分兼容服务可能不支持） */
    private boolean streamIncludeUsage = true;
    /** 每用户每日 AI 调用次数上限，0 或负数表示不限制 */
    private int dailyQuota = 50;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public boolean isStreamIncludeUsage() { return streamIncludeUsage; }
    public void setStreamIncludeUsage(boolean streamIncludeUsage) { this.streamIncludeUsage = streamIncludeUsage; }

    public int getDailyQuota() { return dailyQuota; }
    public void setDailyQuota(int dailyQuota) { this.dailyQuota = dailyQuota; }
}
