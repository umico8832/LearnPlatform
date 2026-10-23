package com.learnplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("ai.execution")
public record AiExecutionProperties(@DefaultValue("8") int concurrency,
                                    @DefaultValue("PLATFORM") Mode mode,
                                    @DefaultValue("5s") Duration heartbeat) {
    public enum Mode { PLATFORM, VIRTUAL }

    public AiExecutionProperties {
        if (concurrency < 1 || mode == null || heartbeat == null
                || heartbeat.toMillis() < 1) {
            throw new IllegalArgumentException("Invalid AI execution configuration");
        }
    }
}
