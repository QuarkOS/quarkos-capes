package dev.quarkos.capes.client.render;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.net.LabyCatalog;

/** Local preview of one catalog texture. Absent unless the dev catalog flag is on. */
public final class LabyWear {
    static final String KEY = "laby-catalog";

    private LabyWear() {
    }

    public static boolean showing() {
        ClientSettings settings = ClientSettings.get();
        return LabyCatalog.enabled()
                && settings.enableLabyMod
                && !settings.localOnly
                && CapeTextures.get(KEY) != null;
    }

    public static void wear(byte[] png) {
        if (!LabyCatalog.enabled() || png == null || png.length == 0) {
            return;
        }
        if (CapeTextures.upload(KEY, png, true, false, true) == null) {
            clear();
        }
    }

    public static void clear() {
        CapeTextures.clear(KEY);
    }

    public static CapeBook.Choice choice() {
        CapeTextures.Image image = CapeTextures.get(KEY);
        if (image == null) {
            return new CapeBook.Choice(CapeBook.Kind.NONE, null, false, 1, 64, 32, new int[] {100}, false, null);
        }
        return CapeBook.vanillaBox(KEY, image);
    }
}
