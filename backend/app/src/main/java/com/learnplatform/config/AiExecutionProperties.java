package com.learnplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ai.execution")
public record AiExecutionProperties(@DefaultValue("8") int concurrency,
                                    @DefaultValue("PLATFORM") Mode mode) {
    public enum Mode { PLATFORM, VIRTUAL }

    public AiExecutionProperties {
        if (concurrency < 1 || mode == null) {
            throw new IllegalArgumentException("Invalid AI execution configuration");
        }
    }
}
