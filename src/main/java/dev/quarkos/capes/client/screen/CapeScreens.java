package dev.quarkos.capes.client.screen;

import net.minecraft.client.gui.screens.Screen;

// Opens the settings screen for this loader and version.
public final class CapeScreens {
    private CapeScreens() {
    }

    public static Screen open(Screen parent) {
        //? if forge {
        /*//? if =1.21.1 {
        /^return ClothCapeScreen.create(parent);
        ^///?} else {
        return new ForgeCapeScreen(parent);
        //?}
        *///?} else {
        return CapeScreen.create(parent);
        //?}
    }

    public static Screen openTab(Screen parent, int tab) {
        if (tab == 6) return new BrowseScreen(parent);
        //? if fabric {
        return CapeScreen.create(parent, net.minecraft.network.chat.Component.translatable(tab == 0
                ? "text.quarkos_capes.tab.my_cape" : CapeBoard.TABS[tab]));
        //?} else {
        /*if (tab == 4) return new CapeGalleryScreen(parent);
        //? if =1.21.1 {
        /^Screen screen = ClothCapeScreen.create(parent);
        if (screen instanceof me.shedaniel.clothconfig2.gui.ClothConfigScreen cloth) cloth.selectedCategoryIndex = tab;
        return screen;
        ^///?} else {
        return new ForgeCapeScreen(parent, tab);
        //?}
        *///?}
    }

    public static Screen refreshed(Screen screen) {
        //? if fabric {
        if (CapeScreen.isOpen(screen)) return CapeScreen.refreshed(screen);
        //?} else {
        /*//? if =1.21.1 {
        /^if (screen instanceof me.shedaniel.clothconfig2.gui.ClothConfigScreen cloth) return ClothCapeScreen.refreshed(cloth);
        ^///?}
        *///?}
        return screen;
    }

    public static int[] previewBounds(Screen screen) {
        if (screen instanceof CapeGalleryScreen || screen instanceof LinkScreen) return null;
        if (screen.width < 480 || screen.height < 300) return null;
        if (screen instanceof BrowseScreen) return new int[]{Math.max(150,screen.width*3/5),80,screen.width-8,screen.height-114};
        //? if fabric {
        return CapeScreen.previewBounds(screen);
        //?} else {
        /*if (screen instanceof ForgeCapeScreen) return new int[]{screen.width-120,54,screen.width-8,screen.height-84};
        return new int[]{Math.max(8,screen.width-110),44,screen.width-8,screen.height-60};
        *///?}
    }

    public static boolean isSettings(Screen screen) {
        if (screen instanceof BrowseScreen || screen instanceof LinkScreen || screen instanceof CapeGalleryScreen) return true;
        //? if forge {
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
