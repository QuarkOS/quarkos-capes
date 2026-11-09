package dev.quarkos.capes.client.harness;

import java.util.HashSet;
import java.util.Set;

/** No Minecraft references: postApply can run before Minecraft itself has loaded. */
public final class HarnessMixins {
    private static final Set<String> APPLIED = new HashSet<>();
    private HarnessMixins() { }
    public static synchronized void applied(String mixin) { APPLIED.add(mixin); }
    public static synchronized int count() { return APPLIED.size(); }
}
