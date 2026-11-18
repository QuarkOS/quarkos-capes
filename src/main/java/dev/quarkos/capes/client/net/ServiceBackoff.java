package dev.quarkos.capes.client.net;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded retry delay shared by all requests to a configured service URL. */
final class ServiceBackoff {
    record State(int failures, long retryAt, ServiceResponse.OfflineReason reason, boolean healthProbe) { }
    private final Map<String, State> states = new LinkedHashMap<>();
    private final LongSupplier clock;
    ServiceBackoff() { this(System::currentTimeMillis); }
    ServiceBackoff(LongSupplier clock) { this.clock = clock; }
    synchronized State before(String base) throws CatalogClient.Failure {
        State state = states.get(base);
        if (state != null && state.retryAt > clock.getAsLong()) throw new CatalogClient.Failure(0, "offline");
        return state;
    }

    synchronized ServiceResponse.OfflineReason reason(String base) {
        State state = states.get(base);
        return state == null ? null : state.reason();
    }

    synchronized void failed(String base, ServiceResponse.OfflineReason reason, String retryAfter, boolean healthProbe) {
        State old = states.get(base);
        int failures = old == null ? 1 : Math.min(7, old.failures + 1);
        long delay = Math.min(60_000L, 1000L << failures);
        try { delay = Math.max(delay, Math.clamp(Long.parseLong(retryAfter), 0L, 300L) * 1000L); }
        catch (RuntimeException ignored) { }
        if (states.size() >= 128 && old == null) states.remove(states.keySet().iterator().next());
        states.put(base, new State(failures, clock.getAsLong() + delay, reason, healthProbe));
    }

    synchronized void success(String base, State before) {
        if (states.get(base) == before) states.remove(base);
    }

    synchronized boolean retry(String base) {
        State state = states.get(base);
        if (state != null && state.reason() == ServiceResponse.OfflineReason.RATE_LIMITED && state.retryAt > clock.getAsLong()) return false;
        if (state != null) states.put(base, new State(state.failures(), 0, null, false));
        return true;
    }
}
