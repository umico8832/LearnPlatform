package com.learnplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.cache")
public class CacheTtlProperties {

    private Duration defaultTtl = Duration.ofMinutes(10);
    private Map<String, Duration> ttl = new HashMap<>();

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Map<String, Duration> getTtl() {
        return ttl;
    }

    public void setTtl(Map<String, Duration> ttl) {
        this.ttl = ttl;
    }

    public Duration ttlOf(String cacheName, Duration fallback) {
        if (ttl == null) {
            return fallback;
        }
        return ttl.getOrDefault(cacheName, fallback);
    }
}
