package com.learnplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiAsyncConfig {

    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-stream-");
        executor.setTaskDecorator(task -> {
            var captured = org.slf4j.MDC.getCopyOfContextMap();
            return () -> {
                var previous = org.slf4j.MDC.getCopyOfContextMap();
                try {
                    if (captured == null) { org.slf4j.MDC.clear(); }
                    else { org.slf4j.MDC.setContextMap(captured); }
                    task.run();
                } finally {
                    if (previous == null) { org.slf4j.MDC.clear(); }
                    else { org.slf4j.MDC.setContextMap(previous); }
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
