package com.learnplatform.service.ai;

/** Atomic admission boundary for a single local process or all Redis-connected processes. */
public interface AiAdmissionGate extends AutoCloseable {
    Lease tryAcquire();

    interface Lease extends AutoCloseable {
        /** @return false when this lease no longer belongs to the caller. */
        boolean renew();

        @Override void close();
    }

    @Override default void close() { }
}
