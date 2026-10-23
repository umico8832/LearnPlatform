package com.learnplatform.service.ai;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.AiExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class AiTaskExecutorLeaseTest {
    @Test void renewalLossInterruptsTheRunningTaskAndReleasesOnlyItsLease() throws Exception {
        var gate = new LosingLeaseGate();
        try (var executor = new AiTaskExecutor(properties(), new SimpleMeterRegistry(), gate)) {
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            var reservation = executor.reserve();
            var leaseLost = new AtomicInteger();
            reservation.onLeaseLost(leaseLost::incrementAndGet);
            reservation.execute(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException exception) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertEquals(1, gate.renewals.get()));
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertEquals(1, leaseLost.get());
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertEquals(1, gate.releases.get()));
        }
    }

    @Test void gateFailureIsReportedAsCapacityRejectionWithoutLocalFallback() {
        AiAdmissionGate unavailable = () -> { throw new IllegalStateException("redis unavailable"); };
        try (var executor = new AiTaskExecutor(properties(), new SimpleMeterRegistry(), unavailable)) {
            assertEquals(1007, assertThrows(BusinessException.class, executor::reserve).getCode());
        }
    }

    @Test void independentExecutorsShareOneAdmissionBudget() {
        var sharedGate = new LocalAiAdmissionGate(1);
        try (var first = new AiTaskExecutor(properties(), new SimpleMeterRegistry(), sharedGate);
             var second = new AiTaskExecutor(properties(), new SimpleMeterRegistry(), sharedGate);
             var held = first.reserve()) {
            assertEquals(1007, assertThrows(BusinessException.class, second::reserve).getCode());
        }
    }

    @Test void leaseLossRegisteredBeforeTheCallerAddsItsHandlerIsStillReportedOnce() throws Exception {
        var gate = new LosingLeaseGate();
        try (var executor = new AiTaskExecutor(properties(), new SimpleMeterRegistry(), gate);
             var reservation = executor.reserve()) {
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertEquals(1, gate.renewals.get()));
            var notified = new AtomicInteger();
            reservation.onLeaseLost(notified::incrementAndGet);
            assertEquals(1, notified.get());
        }
    }

    @Test void executorClosesItsDedicatedAdmissionConnectionDuringShutdown() {
        var gate = new LosingLeaseGate();
        try (var executor = new AiTaskExecutor(properties(), new SimpleMeterRegistry(), gate)) {
            assertTrue(executor.isRunning());
        }
        assertEquals(1, gate.closed.get());
    }

    @Test void shutdownReleasesReservationsBeforeClosingTheDedicatedAdmissionConnection() {
        var gate = new OrderingGate();
        try (var executor = new AiTaskExecutor(properties(), new SimpleMeterRegistry(), gate)) {
            assertNotNull(executor.reserve());
        }
        assertEquals(1, gate.releases.get());
        assertEquals(1, gate.closedAfterRelease.get());
    }

    private AiExecutionProperties properties() {
        return new AiExecutionProperties(8, AiExecutionProperties.Mode.PLATFORM, Duration.ofMillis(10),
                Duration.ofSeconds(1));
    }

    private static final class LosingLeaseGate implements AiAdmissionGate {
        private final AtomicInteger renewals = new AtomicInteger();
        private final AtomicInteger releases = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        @Override public Lease tryAcquire() {
            return new Lease() {
                private final AtomicBoolean closed = new AtomicBoolean();
                @Override public boolean renew() { renewals.incrementAndGet(); return false; }
                @Override public void close() { if (closed.compareAndSet(false, true)) { releases.incrementAndGet(); } }
            };
        }
        @Override public void close() { closed.incrementAndGet(); }
    }

    private static final class OrderingGate implements AiAdmissionGate {
        private final AtomicInteger releases = new AtomicInteger();
        private final AtomicInteger closedAfterRelease = new AtomicInteger();
        @Override public Lease tryAcquire() {
            return new Lease() {
                @Override public boolean renew() { return true; }
                @Override public void close() { releases.incrementAndGet(); }
            };
        }
        @Override public void close() {
            if (releases.get() == 1) { closedAfterRelease.incrementAndGet(); }
        }
    }
}
