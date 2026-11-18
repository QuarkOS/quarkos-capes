package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.screen.ui.CapeUiScreen;
import net.minecraft.client.gui.screens.Screen;

public final class CapeScreens {
    enum SettingsTab {
        PHYSICS("tab.physics"), SOURCES("tab.sources"), ADVANCED("tab.advanced");

        final String key;
        SettingsTab(String key) { this.key = key; }
    }

    private CapeScreens() { }

    public static Screen open(Screen parent) {
        return new CapeScreen(parent);
    }

    public static Screen settings(Screen parent) {
        return settings(parent, SettingsTab.PHYSICS);
    }

    private static Screen settings(Screen parent, SettingsTab tab) {
        //? if fabric || <1.21.2 {
        return ClothCapeScreen.create(parent, tab);
        //?} else {
        /*return new ForgeCapeScreen(parent, tab);
        *///?}
    }

    public static Screen openTab(Screen parent, int tab) {
        return switch (tab) {
            case 2 -> settings(parent, SettingsTab.PHYSICS);
            case 3 -> settings(parent, SettingsTab.SOURCES);
            case 4 -> new CapeGalleryScreen(parent);
            case 5 -> settings(parent, SettingsTab.ADVANCED);
            case 6 -> new BrowseScreen(parent);
            default -> open(parent);
        };
    }

    public static boolean isClothSettings(Screen screen) {
        //? if fabric || <1.21.2 {
        return ClothCapeScreen.isOpen(screen);
        //?} else {
        /*return false;
        *///?}
    }

    public static void tick(Screen screen) {
        //? if fabric || <1.21.2 {
        ClothCapeScreen.tick(screen);
        //?}
    }

    public static boolean isSettings(Screen screen) {
        return screen instanceof CapeUiScreen || isClothSettings(screen);
    }
}
