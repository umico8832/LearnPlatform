package com.learnplatform.ai.model;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Cancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Cancellation parent;

    public Cancellation() { this(null); }
    private Cancellation(Cancellation parent) { this.parent = parent; }
    public Cancellation child() { return new Cancellation(this); }
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted() || parent != null && parent.isCancelled();
    }
    public void check() {
        if (isCancelled()) {
            throw new ModelException(ModelException.Code.CANCELLED);
        }
    }
}
