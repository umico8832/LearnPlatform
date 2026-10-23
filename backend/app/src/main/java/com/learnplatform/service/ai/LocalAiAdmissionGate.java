package com.learnplatform.service.ai;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** The development-only, single-process admission gate. */
public final class LocalAiAdmissionGate implements AiAdmissionGate {
    private final Semaphore permits;

    public LocalAiAdmissionGate(int limit) { permits = new Semaphore(limit); }

    @Override public Lease tryAcquire() {
        if (!permits.tryAcquire()) { return null; }
        return new Lease() {
            private final AtomicBoolean released = new AtomicBoolean();
            @Override public boolean renew() { return !released.get(); }
            @Override public void close() {
                if (released.compareAndSet(false, true)) { permits.release(); }
            }
        };
    }
}
