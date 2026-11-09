package dev.quarkos.capes.client.net;

import java.util.concurrent.atomic.AtomicBoolean;

/** A refresh remains in flight while queued as well as while running. */
public final class RefreshGate {
    private final AtomicBoolean pending = new AtomicBoolean();
    public boolean begin() { return pending.compareAndSet(false, true); }
    public void finish() { pending.set(false); }
}
