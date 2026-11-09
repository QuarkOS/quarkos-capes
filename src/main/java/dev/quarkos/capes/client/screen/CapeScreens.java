package dev.quarkos.capes.client.screen;

import net.minecraft.client.gui.screens.Screen;

// Opens the settings screen for this loader and version.
public final class CapeScreens {
    private CapeScreens() {
    }

    public static Screen open(Screen parent) {
        //? if forge || neoforge {
        /*//? if <1.21.2 {
        return ClothCapeScreen.create(parent);
        //?} else {
        return new ForgeCapeScreen(parent);
        //?}
        *///?} else {
        return CapeScreen.create(parent);
        //?}
    }

    public static Screen openTab(Screen parent, int tab) {
        //? if fabric {
        return CapeScreen.create(parent, net.minecraft.network.chat.Component.translatable(tab == 0
                ? "text.quarkos_capes.tab.my_cape" : CapeBoard.TABS[tab]));
        //?} else {
        /*if (tab == 4) return new CapeGalleryScreen(parent);
        //? if <1.21.2 {
        Screen screen = ClothCapeScreen.create(parent);
        if (screen instanceof me.shedaniel.clothconfig2.gui.ClothConfigScreen cloth) cloth.selectedCategoryIndex = tab;
        return screen;
        //?} else {
        return new ForgeCapeScreen(parent, tab);
        //?}
        *///?}
    }

    public static boolean isSettings(Screen screen) {
        if (screen instanceof CapeGalleryScreen) return true;
        //? if forge || neoforge {
        /*if (screen instanceof ForgeCapeScreen) {
            return true;
        }
        *///?}
        //? if fabric {
        if (CapeScreen.isOpen(screen)) {
            return true;
        }
        //?}
        return screen != null && screen.getClass().getName().contains("ClothConfig");
    }
}
