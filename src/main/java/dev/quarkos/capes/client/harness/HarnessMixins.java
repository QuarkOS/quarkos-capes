package dev.quarkos.capes.client.harness;

import java.util.HashSet;
import java.util.Set;
import com.google.gson.JsonParser;

/** No Minecraft references: postApply can run before Minecraft itself has loaded. */
public final class HarnessMixins {
    private static final Set<String> APPLIED = new HashSet<>();
    private HarnessMixins() { }
    public static synchronized void applied(String mixin) { APPLIED.add(mixin); }
    public static synchronized int count() { return APPLIED.size(); }
    public static boolean harnessOnly(String mixin) {
        return mixin.endsWith(".ShotCameraMixin") || mixin.endsWith(".ShotSkinMixin");
    }
    public static Set<String> required(String config) {
        var root = JsonParser.parseString(config).getAsJsonObject();
        String prefix = root.get("package").getAsString() + ".";
        Set<String> required = new java.util.TreeSet<>();
        for (String side : new String[]{"mixins", "client"}) {
            if (!root.has(side)) continue;
            for (var name : root.getAsJsonArray(side)) {
                String mixin = prefix + name.getAsString();
                if (!harnessOnly(mixin)) required.add(mixin);
            }
        }
        return Set.copyOf(required);
    }
    public static synchronized Set<String> missing(Set<String> required) {
        Set<String> missing = new java.util.TreeSet<>(required);
        missing.removeAll(APPLIED);
        return Set.copyOf(missing);
    }
    public static int verifyConfigured() throws java.io.IOException {
        try (var input = HarnessMixins.class.getResourceAsStream("/quarkos_capes.mixins.json")) {
            if (input == null) throw new java.io.IOException("Missing mixin config");
            Set<String> required = required(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            if (required.isEmpty()) throw new IllegalStateException("Empty mixin config");
            Set<String> missing = missing(required);
            if (!missing.isEmpty()) throw new IllegalStateException("Configured mod mixins did not apply: " + missing);
            return required.size();
        }
    }
}
