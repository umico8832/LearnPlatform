package com.learnplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.learnplatform.service.ai.AiTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(AiExecutionProperties.class)
public class AiAsyncConfig {

    @Bean(name = "aiTaskExecutor")
    public AiTaskExecutor aiTaskExecutor(AiExecutionProperties properties, MeterRegistry meters) {
        return new AiTaskExecutor(properties, meters);
    }
}
