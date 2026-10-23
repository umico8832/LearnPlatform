package com.learnplatform.service.ai;

import java.util.Set;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.AiExecutionProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AiTaskExecutor implements Executor, AutoCloseable, SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AiTaskExecutor.class);
    private final ExecutorService workers;
    private final ScheduledThreadPoolExecutor timer;
    private final Semaphore permits;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger active = new AtomicInteger();
    private final Set<Reservation> reservations = ConcurrentHashMap.newKeySet();
    private final Counter rejected;
    private final long heartbeatMillis;

    public AiTaskExecutor(AiExecutionProperties properties, MeterRegistry meters) {
        int limit = properties.concurrency();
        permits = new Semaphore(limit);
        heartbeatMillis = properties.heartbeat().toMillis();
        if (properties.mode() == AiExecutionProperties.Mode.VIRTUAL) {
            workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ai-virtual-", 0).factory());
        } else {
            var pool = new ThreadPoolExecutor(limit, limit, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(limit), Thread.ofPlatform().name("ai-platform-", 0).factory());
            pool.prestartAllCoreThreads();
            workers = pool;
        }
        timer = new ScheduledThreadPoolExecutor(2,
                Thread.ofPlatform().daemon().name("ai-heartbeat-", 0).factory());
        timer.setRemoveOnCancelPolicy(true);
        rejected = Counter.builder("ai.admission.rejected").register(meters);
        Gauge.builder("ai.admission.inflight", permits, s -> limit - s.availablePermits()).register(meters);
        Gauge.builder("executor.active", active, AtomicInteger::get).baseUnit("threads")
                .tag("name", "aiTaskExecutor").register(meters);
        Gauge.builder("executor.queued", workers, e -> e instanceof ThreadPoolExecutor pool
                ? pool.getQueue().size() : 0).baseUnit("tasks").tag("name", "aiTaskExecutor").register(meters);
    }

    public Reservation reserve() {
        if (closed.get() || !permits.tryAcquire()) { throw rejection(); }
        var reservation = new Reservation();
        reservations.add(reservation);
        if (closed.get()) {
            reservation.close();
            throw rejection();
        }
        return reservation;
    }

    @Override public void execute(Runnable command) { reserve().execute(command); }

    public ScheduledFuture<?> heartbeat(Runnable command) {
        return timer.scheduleAtFixedRate(command, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
    }

    private BusinessException rejection() {
        rejected.increment();
        return new AiCapacityException();
    }

    @Override public void close() {
        closed.set(true);
        reservations.forEach(Reservation::shutdown);
        // Admitted wrappers must run their finally blocks, including tasks not started yet.
        workers.shutdown();
        timer.shutdownNow();
    }

    @Override public void start() { }
    @Override public boolean isRunning() { return !closed.get(); }
    @Override public int getPhase() { return Integer.MAX_VALUE; }

    @Override public void stop() {
        close();
        try { workers.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    @Override public void stop(Runnable completion) {
        try { stop(); }
        finally { completion.run(); }
    }

    public final class Reservation implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicBoolean submitted = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<Thread> worker = new AtomicReference<>();
        private final AtomicReference<Runnable> shutdownAction = new AtomicReference<>();
        private final AtomicBoolean shutdownNotified = new AtomicBoolean();

        public void onShutdown(Runnable action) {
            shutdownAction.set(action);
            if (closed.get()) { shutdown(); }
        }

        private void shutdown() {
            Runnable action = shutdownAction.get();
            try {
                if (action != null && shutdownNotified.compareAndSet(false, true)) { action.run(); }
            } catch (RuntimeException exception) {
                log.warn("AI shutdown state cleanup failed; the reservation lease remains authoritative");
            } finally { cancel(); }
        }

        public void execute(Runnable command) {
            if (!submitted.compareAndSet(false, true)) { throw new IllegalStateException("Already submitted"); }
            var captured = MDC.getCopyOfContextMap();
            try {
                workers.execute(() -> {
                    worker.set(Thread.currentThread());
                    active.incrementAndGet();
                    var previous = MDC.getCopyOfContextMap();
                    try {
                        if (captured == null) {
                            MDC.clear();
                        } else {
                            MDC.setContextMap(captured);
                        }
                        if (!cancelled.get()) { command.run(); }
                    } finally {
                        if (previous == null) {
                            MDC.clear();
                        } else {
                            MDC.setContextMap(previous);
                        }
                        worker.set(null);
                        active.decrementAndGet();
                        close();
                    }
                });
            } catch (RuntimeException exception) {
                close();
                throw rejection();
            }
        }

        public void cancel() {
            cancelled.set(true);
            Thread thread = worker.get();
            if (thread != null) { thread.interrupt(); }
        }

        @Override public void close() {
            if (released.compareAndSet(false, true)) {
                reservations.remove(this);
                permits.release();
            }
        }
    }
}
