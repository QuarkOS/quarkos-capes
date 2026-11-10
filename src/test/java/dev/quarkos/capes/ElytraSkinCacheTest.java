package dev.quarkos.capes;

import dev.quarkos.capes.client.render.ElytraSkinCache;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class ElytraSkinCacheTest {
    private final ElytraSkinCache cache = new ElytraSkinCache();
    private final UUID player = UUID.randomUUID();
    private final AtomicInteger allocations = new AtomicInteger();
    private final BiFunction<String, String, String> create = (base, texture) -> {
        allocations.incrementAndGet();
        return new String(base + texture);
    };

    @Test void repeatedFramesReuseSkinAndCompareTextureByValue() {
        String base = new String("base");
        String skin = cache.get(player, base, "cape", create);
        for (int frame = 0; frame < 100; frame++) assertSame(skin, cache.get(player, base, new String("cape"), create));
        assertEquals(1, allocations.get());
    }

    @Test void equalButDifferentBaseSkinAndChangedCapeInvalidate() {
        String base = new String("base");
        String old = cache.get(player, base, "cape", create);
        String refreshedBase = new String("base");
        String refreshed = cache.get(player, refreshedBase, "cape", create);
        assertNotSame(old, refreshed);
        assertNotSame(refreshed, cache.get(player, refreshedBase, "new-cape", create));
        assertEquals(3, allocations.get());
    }

    @Test void disabledOrMissingSkinDropsEntryBeforeReenable() {
        String base = "base";
        String old = cache.get(player, base, "cape", create);
        assertSame(base, cache.get(player, base, null, create));
        assertNotSame(old, cache.get(player, base, "cape", create));
        assertNull(cache.get(player, null, "cape", create));
        cache.get(player, base, "cape", create);
        assertEquals(3, allocations.get());
    }

    @Test void departuresArePerPlayerAndDisconnectOrTextureReleaseClearsAll() {
        String base = "base";
        UUID other = UUID.randomUUID();
        String first = cache.get(player, base, "cape", create);
        String second = cache.get(other, base, "cape", create);
        assertNotSame(first, second);
        cache.forget(player);
        assertNotSame(first, cache.get(player, base, "cape", create));
        assertSame(second, cache.get(other, base, "cape", create));
        cache.clear();
        assertNotSame(second, cache.get(other, base, "cape", create));
        assertEquals(4, allocations.get());
    }
}
