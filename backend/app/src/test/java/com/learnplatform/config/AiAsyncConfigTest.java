package com.learnplatform.config;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.service.ai.AiTaskExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class AiAsyncConfigTest {
    private AiTaskExecutor executor(AiExecutionProperties.Mode mode, int capacity) {
        return new AiAsyncConfig().aiTaskExecutor(
                new AiExecutionProperties(capacity, mode), new SimpleMeterRegistry());
    }

    @ParameterizedTest @EnumSource(AiExecutionProperties.Mode.class)
    void startsAllEightAdmittedTasksWithoutQueueingBehindTwoWorkers(AiExecutionProperties.Mode mode) throws Exception {
        try (var executor = executor(mode, 8)) {
            var started = new CountDownLatch(8);
            var finish = new CountDownLatch(1);
            try {
                for (int i = 0; i < 8; i++) {
                    executor.execute(() -> {
                        started.countDown();
                        try { finish.await(); }
                        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                    });
                }
                assertTrue(started.await(2, TimeUnit.SECONDS), "Eight admitted I/O tasks must start together");
                assertEquals(1007, assertThrows(BusinessException.class,
                        () -> executor.execute(() -> fail("Must not be admitted"))).getCode());
            } finally { finish.countDown(); }
        }
    }

    @ParameterizedTest @EnumSource(AiExecutionProperties.Mode.class)
    void releasesAfterCancellationFailureAndUnsubmittedPreparation(AiExecutionProperties.Mode mode) throws Exception {
        try (var executor = executor(mode, 1)) {
            var started = new CountDownLatch(1);
            var reservation = executor.reserve();
            reservation.execute(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            reservation.cancel();
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                try (var available = executor.reserve()) { assertNotNull(available); }
            });
            var failure = new CompletableFuture<Void>();
            executor.execute(() -> {
                try { throw new IllegalStateException("simulated failure"); }
                catch (RuntimeException exception) { failure.completeExceptionally(exception); }
            });
            assertThrows(java.util.concurrent.ExecutionException.class, () -> failure.get(2, TimeUnit.SECONDS));
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                try (var available = executor.reserve()) { assertNotNull(available); }
            });
            try (var preparation = executor.reserve()) { assertNotNull(preparation); }
            try (var next = executor.reserve()) { assertNotNull(next); }
        }
    }

    @ParameterizedTest @EnumSource(AiExecutionProperties.Mode.class)
    void shutdownNotifiesUnstartedReservationsAndRejectsTheirSubmission(AiExecutionProperties.Mode mode) {
        var executor = executor(mode, 1);
        var reservation = executor.reserve();
        var notified = new java.util.concurrent.atomic.AtomicInteger();
        reservation.onShutdown(notified::incrementAndGet);
        assertTrue(executor.isRunning());
        executor.stop();
        executor.close();
        assertFalse(executor.isRunning());
        assertEquals(1, notified.get());
        assertThrows(BusinessException.class, () -> reservation.execute(() -> fail("Closed executor")));
        assertThrows(BusinessException.class, executor::reserve);
    }

    @ParameterizedTest @EnumSource(AiExecutionProperties.Mode.class)
    void propagatesTraceAndDoesNotLeakItToLaterTasks(AiExecutionProperties.Mode mode) throws Exception {
        try (var executor = executor(mode, 1)) {
            MDC.put("traceId", "trace-one");
            var traced = new CompletableFuture<String>();
            executor.execute(() -> traced.complete(MDC.get("traceId")));
            MDC.clear();
            assertEquals("trace-one", traced.get(2, TimeUnit.SECONDS));
            var untraced = new CompletableFuture<String>();
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> executor.execute(
                    () -> untraced.complete(MDC.get("traceId"))));
            assertNull(untraced.get(2, TimeUnit.SECONDS));
        } finally { MDC.clear(); }
    }
}
