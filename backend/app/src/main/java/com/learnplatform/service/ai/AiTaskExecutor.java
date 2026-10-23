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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AiTaskExecutor implements Executor, AutoCloseable, SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AiTaskExecutor.class);
    private final ExecutorService workers;
    private final ScheduledThreadPoolExecutor heartbeatTimer;
    private final ScheduledThreadPoolExecutor leaseTimer;
    private final AiAdmissionGate admission;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger admitted = new AtomicInteger();
    private final Set<Reservation> reservations = ConcurrentHashMap.newKeySet();
    private final Counter rejected;
    private final long heartbeatMillis;
    private final long renewalMillis;

    public AiTaskExecutor(AiExecutionProperties properties, MeterRegistry meters) {
        this(properties, meters, new LocalAiAdmissionGate(properties.concurrency()));
    }

    public AiTaskExecutor(AiExecutionProperties properties, MeterRegistry meters, AiAdmissionGate admission) {
        int limit = properties.concurrency();
        this.admission = admission;
        heartbeatMillis = properties.heartbeat().toMillis();
        renewalMillis = Math.max(100, properties.lease().toMillis() / 3);
        if (properties.mode() == AiExecutionProperties.Mode.VIRTUAL) {
            workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ai-virtual-", 0).factory());
        } else {
            var pool = new ThreadPoolExecutor(limit, limit, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(limit), Thread.ofPlatform().name("ai-platform-", 0).factory());
            pool.prestartAllCoreThreads();
            workers = pool;
        }
        heartbeatTimer = new ScheduledThreadPoolExecutor(2,
                Thread.ofPlatform().daemon().name("ai-heartbeat-", 0).factory());
        heartbeatTimer.setRemoveOnCancelPolicy(true);
        leaseTimer = new ScheduledThreadPoolExecutor(limit,
                Thread.ofPlatform().daemon().name("ai-lease-renewal-", 0).factory());
        leaseTimer.setRemoveOnCancelPolicy(true);
        rejected = Counter.builder("ai.admission.rejected").register(meters);
        Gauge.builder("ai.admission.inflight", admitted, AtomicInteger::get).register(meters);
        Gauge.builder("executor.active", active, AtomicInteger::get).baseUnit("threads")
                .tag("name", "aiTaskExecutor").register(meters);
        Gauge.builder("executor.queued", workers, e -> e instanceof ThreadPoolExecutor pool
                ? pool.getQueue().size() : 0).baseUnit("tasks").tag("name", "aiTaskExecutor").register(meters);
    }

    public Reservation reserve() {
        if (closed.get()) { throw rejection(); }
        AiAdmissionGate.Lease lease;
        try { lease = admission.tryAcquire(); }
        catch (RuntimeException exception) { throw rejection(); }
        if (lease == null) { throw rejection(); }
        Reservation reservation;
        try { reservation = new Reservation(lease); }
        catch (RuntimeException exception) {
            try { lease.close(); } catch (RuntimeException ignored) { /* lease expiry remains the fallback */ }
            throw rejection();
        }
        admitted.incrementAndGet();
        reservations.add(reservation);
        if (closed.get()) {
            reservation.close();
            throw rejection();
        }
        return reservation;
    }

    @Override public void execute(Runnable command) { reserve().execute(command); }

    public ScheduledFuture<?> heartbeat(Runnable command) {
        return heartbeatTimer.scheduleAtFixedRate(command, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
    }

    private BusinessException rejection() {
        rejected.increment();
        return new AiCapacityException();
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) { return; }
        reservations.forEach(Reservation::shutdown);
        // Admitted wrappers must run their finally blocks, including tasks not started yet.
        workers.shutdown();
        heartbeatTimer.shutdownNow();
        leaseTimer.shutdownNow();
        awaitWorkers();
        try { admission.close(); }
        catch (RuntimeException exception) { log.warn("AI admission connection cleanup failed"); }
    }

    @Override public void start() { }
    @Override public boolean isRunning() { return !closed.get(); }
    @Override public int getPhase() { return Integer.MAX_VALUE; }

    @Override public void stop() {
        close();
    }

    private void awaitWorkers() {
        try { workers.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    @Override public void stop(Runnable completion) {
        try { stop(); }
        finally { completion.run(); }
    }

    public final class Reservation implements AutoCloseable {
        private final AiAdmissionGate.Lease lease;
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicBoolean submitted = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<Thread> worker = new AtomicReference<>();
        private final AtomicReference<Runnable> shutdownAction = new AtomicReference<>();
        private final AtomicBoolean shutdownNotified = new AtomicBoolean();
        private final AtomicReference<Runnable> leaseLostAction = new AtomicReference<>();
        private final AtomicBoolean leaseLostNotified = new AtomicBoolean();
        private final AtomicBoolean leaseLostActionNotified = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> renewal = new AtomicReference<>();

        private Reservation(AiAdmissionGate.Lease lease) {
            this.lease = lease;
            renewal.set(leaseTimer.scheduleAtFixedRate(this::renew, renewalMillis, renewalMillis,
                    TimeUnit.MILLISECONDS));
        }

        private void renew() {
            if (released.get()) { return; }
            try {
                if (!lease.renew()) { leaseLost(); }
            } catch (RuntimeException exception) {
                // A Redis outage cannot safely become a per-instance budget.
                leaseLost();
            }
        }

        public void onLeaseLost(Runnable action) {
            leaseLostAction.set(action);
            if (leaseLostNotified.get()) { notifyLeaseLost(action); }
        }

        private void leaseLost() {
            if (leaseLostNotified.compareAndSet(false, true)) { notifyLeaseLost(leaseLostAction.get()); }
            cancel();
        }

        private void notifyLeaseLost(Runnable action) {
            try {
                if (action != null && leaseLostActionNotified.compareAndSet(false, true)) { action.run(); }
            } catch (RuntimeException exception) {
                log.warn("AI lease-loss cancellation callback failed");
            }
        }

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
            } finally {
                cancel();
                // A reservation prepared before the executor shuts down has no worker finally block.
                if (!submitted.get()) { close(); }
            }
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
                admitted.decrementAndGet();
                ScheduledFuture<?> scheduled = renewal.getAndSet(null);
                if (scheduled != null) { scheduled.cancel(false); }
                // Lettuce observes the worker's interrupt flag and otherwise skips the release script.
                boolean interrupted = Thread.interrupted();
                try { lease.close(); }
                catch (RuntimeException exception) {
                    log.warn("AI admission lease release failed; expiry will reclaim it");
                } finally {
                    if (interrupted) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }
}
