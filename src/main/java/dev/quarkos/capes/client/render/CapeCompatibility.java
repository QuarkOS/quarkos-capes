package dev.quarkos.capes.client.render;

/** Yield our cape layer when another renderer owns it; settings and downloads remain usable. */
public final class CapeCompatibility {
    private static boolean otherRenderer;
    private CapeCompatibility() { }
    public static void detect() {
        for (String id : new String[]{"capes", "waveycapes", "minecraftcapes"}) {
            //? if forge {
            /*boolean loaded = net.minecraftforge.fml.ModList.get().isLoaded(id);
            *///?} else {
            boolean loaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
            //?}
            if (loaded && !otherRenderer) {
                otherRenderer = true;
                org.slf4j.LoggerFactory.getLogger("quarkos_capes").warn("Another cape renderer ({}) is installed; Capes yields cape rendering to avoid duplicate capes.", id);
            }
        }
    }
    public static boolean otherRenderer() { return otherRenderer; }
}
