package dev.quarkos.capes.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/** One derived skin per player; the base must match by identity, the texture by value. */
public final class ElytraSkinCache {
    private record Entry(Object base, Object texture, Object skin) { }
    private final Map<UUID, Entry> entries = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <S, T> S get(UUID player, S base, T texture, BiFunction<S, T, S> create) {
        if (base == null || texture == null) {
            forget(player);
            return base;
        }
        Entry entry = entries.get(player);
        if (entry != null && entry.base == base && Objects.equals(entry.texture, texture)) return (S) entry.skin;
        S skin = create.apply(base, texture);
        entries.put(player, new Entry(base, texture, skin));
        return skin;
    }

    public void forget(UUID player) { entries.remove(player); }
    public void clear() { entries.clear(); }
}
