package com.learnplatform.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AiAsyncConfigTest {
    @Test void propagatesTraceAndDoesNotLeakItToLaterTasks() throws Exception {
        var executor = (ThreadPoolTaskExecutor) new AiAsyncConfig().aiTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        try {
            MDC.put("traceId", "trace-one");
            var traced = new CompletableFuture<String>();
            executor.execute(() -> traced.complete(MDC.get("traceId")));
            MDC.clear();
            assertEquals("trace-one", traced.get(2, TimeUnit.SECONDS));
            var untraced = new CompletableFuture<String>();
            executor.execute(() -> untraced.complete(MDC.get("traceId")));
            assertNull(untraced.get(2, TimeUnit.SECONDS));
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }
}
