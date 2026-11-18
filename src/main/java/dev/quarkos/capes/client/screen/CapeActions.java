package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;

/** Sample and style changes shared by the Cloth screen and the capture harness. */
public final class CapeActions {
    private CapeActions() {
    }

    public static void bands() {
        OwnerCape.set(OwnerCape.sampleBands());
        ClientSettings.get().equippedGalleryId = null;
        CapeBook.draft = OwnerCape.get();
    }

    public static void slide() {
        OwnerCape.set(OwnerCape.sampleSlide());
        ClientSettings.get().equippedGalleryId = null;
        CapeBook.draft = OwnerCape.get();
    }

    public static void cloak() {
        OwnerCape.set(OwnerCape.sampleCloak());
        ClientSettings.get().equippedGalleryId = null;
        CapeBook.draft = OwnerCape.get();
    }

    public static void style(String style, boolean animate) {
        OwnerCape cape = OwnerCape.get();
        cape.style = style;
        if ("model".equals(style) && cape.modelJson == null) {
            OwnerCape.set(OwnerCape.sampleCloak());
            cape = OwnerCape.get();
            cape.style = "model";
        }
        if (!"model".equals(style)) {
            cape.modelJson = null;
        }
        cape.animateFrames = animate;
        CapeBook.draft = cape;
    }

    /** Harness and gametest keys. The Cloth rows are not top-level buttons. */
    public static void applyKey(String key) {
        if (key.endsWith("sample.bands")) {
            bands();
        } else if (key.endsWith("sample.slide")) {
            slide();
        } else if (key.endsWith("sample.cloak")) {
            cloak();
        } else if (key.endsWith("style.flat")) {
            style("flat", false);
        } else if (key.endsWith("style.frames")) {
            style("frames", true);
        } else if (key.endsWith("style.wave")) {
            style("wave", true);
        } else if (key.endsWith("style.model")) {
            style("model", false);
        } else {
            throw new IllegalArgumentException(key);
        }
    }

}
