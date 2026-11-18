package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.QuarkosCapes;
import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Title-screen smoke test of the installed release jar. No world or service is needed. */
public final class BootHarness {
    private static long began;
    private static int opened;
    private static boolean finished;
    private BootHarness() { }

    public static void tick(Minecraft client) {
        if (finished || !Boolean.getBoolean("quarkos_capes.harness") || !"boot".equals(System.getProperty("quarkos_capes.only"))) return;
        if (began == 0) began = System.nanoTime();
        try {
            if (System.nanoTime() - began > 120_000_000_000L) throw new IllegalStateException("Timed out at " + (client.screen == null ? "no screen" : client.screen.getClass().getName()));
            if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                //? if >=1.21 {
                client.options.onboardingAccessibilityFinished();
                //?}
                client.setScreen(new TitleScreen());
                return;
            }
            if (opened == 0) {
                if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
                if (QuarkosCapes.OPEN == null) throw new IllegalStateException("Settings key was not registered");
                KeyMapping.click(QuarkosCapes.OPEN.getDefaultKey());
                opened = 1;
                return;
            }
            if (!CapeScreens.isSettings(client.screen)) throw new IllegalStateException("Settings key did not open the screen");
            if (++opened < 12) return;
            // Resolve every configured target: title-only boot would otherwise leave cape mixins unloaded.
            Class<?>[] targets = {
                    net.minecraft.client.gui.screens.PauseScreen.class,
                    net.minecraft.client.renderer.entity.layers.CapeLayer.class,
                    net.minecraft.client.Camera.class, net.minecraft.client.player.AbstractClientPlayer.class,
                    //? if >=26.2 {
                    /*net.minecraft.client.gui.Hud.class,
                    *///?} else {
                    net.minecraft.client.gui.Gui.class,
                    //?}
                    net.minecraft.client.renderer.entity.LivingEntityRenderer.class,
                    //? if >=26.1 {
                    /*net.minecraft.client.gui.render.pip.PictureInPictureRenderer.class,
                    *///?} else if >=1.21.6 {
                    net.minecraft.client.gui.render.pip.GuiEntityRenderer.class,
                    //?} else {
                    /*net.minecraft.client.gui.screens.inventory.InventoryScreen.class,
                    *///?}
                    //? if <1.21.11 {
                    /*net.minecraft.client.renderer.entity.player.PlayerRenderer.class
                    *///?} else {
                    net.minecraft.client.renderer.feature.ModelFeatureRenderer.class,
                    net.minecraft.client.renderer.entity.player.AvatarRenderer.class
                    //?}
            };
            for (Class<?> target : targets) Class.forName(target.getName(), false, target.getClassLoader());
            //? if fabric {
            var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            if (loader.isDevelopmentEnvironment()) throw new IllegalStateException("Boot requires a production loader environment");
            if (loader.isModLoaded("modmenu")) throw new IllegalStateException("Mod Menu must be absent");
            if (!loader.isModLoaded("fabric-api")) throw new IllegalStateException("Fabric API was not loaded");
            //?}
            int count = HarnessMixins.count();
            int required = HarnessMixins.verifyConfigured();
            if (client.screen.children().isEmpty()) throw new IllegalStateException("Settings screen has no widgets");
            System.out.println("CAPES_BOOT PASS mixins=" + count + " required=" + required + " screen=" + client.screen.getClass().getName());
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            System.err.println("CAPES_BOOT FAIL reason=" + failure);
            System.exit(1);
        }
    }
}
