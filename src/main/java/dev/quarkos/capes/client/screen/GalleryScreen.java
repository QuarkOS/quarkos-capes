package dev.quarkos.capes.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Opens the Gallery category. Offline still lists the bundled samples.
public final class GalleryScreen {
    private GalleryScreen() {
    }

    public static Screen create(Screen parent) {
        //? if forge || neoforge {
        /*return new CapeGalleryScreen(parent);
        *///?} else {
        return CapeScreen.create(parent, Component.translatable("text.quarkos_capes.tab.gallery"));
        //?}
    }
}
