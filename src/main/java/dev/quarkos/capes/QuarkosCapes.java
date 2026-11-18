package dev.quarkos.capes;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.harness.ClientHarness;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.ClothSim;
import dev.quarkos.capes.client.render.PlayerFacts;
import dev.quarkos.capes.client.screen.CapeScreens;
//? if forge || neoforge {
/* *///?} else {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//?}
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

//? if forge || neoforge {
/*public final class QuarkosCapes {
*///?} else {
public final class QuarkosCapes implements ClientModInitializer {
//?}
    public static KeyMapping OPEN;

    public static void bootstrap() {
        LocalFiles.load();
        OPEN = McBridge.registerKey();
    }

    //? if fabric {
    @Override
    public void onInitializeClient() {
        bootstrap();
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (Boolean.getBoolean("quarkos_capes.store")) {
                dev.quarkos.capes.client.StoreCapture.beforeTick(client);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(QuarkosCapes::tick);
    }
    //?}

    public static void tick(Minecraft client) {
        boolean harness = Boolean.getBoolean("quarkos_capes.harness");
        if (dev.quarkos.capes.DevFlags.HARNESS) {
            boolean ui = dev.quarkos.capes.client.harness.UiHarness.selected();
            if (harness || ui) ClientHarness.start(client);
            PlayerFacts.tick(client);
            ClothSim.clientTick();
            if (!ui && (!harness || !dev.quarkos.capes.client.harness.AuditHarness.selected())) {
                CapeHttp.tick(client);
                dev.quarkos.capes.client.net.SiteApi.tick(client);
            }
        } else {
            PlayerFacts.tick(client);
            ClothSim.clientTick();
            CapeHttp.tick(client);
            dev.quarkos.capes.client.net.SiteApi.tick(client);
        }
        if ((client.screen == null || client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) && OPEN != null && OPEN.consumeClick()) {
            McBridge.setScreen(client, CapeScreens.open(client.screen));
        }
        if (Boolean.getBoolean("quarkos_capes.prodSmoke")) {
            dev.quarkos.capes.client.ProdSmoke.tick(client);
        }
        CapeScreens.tick(client.screen);
        LocalFiles.autosave(CapeScreens.isSettings(client.screen));
        if (!CapeScreens.isSettings(client.screen)) {
            CapeBook.preview = false;
            CapeBook.draft = null;
        }
        if (Boolean.getBoolean("quarkos_capes.store")) {
            dev.quarkos.capes.client.StoreCapture.tick(client);
        }
    }
}
