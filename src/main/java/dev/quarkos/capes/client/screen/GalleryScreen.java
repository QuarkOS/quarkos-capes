package dev.quarkos.capes.client.screen;

import net.minecraft.client.gui.screens.Screen;

public final class GalleryScreen {
    private GalleryScreen() { }

    public static Screen create(Screen parent) {
        return new CapeGalleryScreen(parent);
    }
}
