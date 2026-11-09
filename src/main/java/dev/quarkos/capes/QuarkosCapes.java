package dev.quarkos.capes;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.harness.ClientHarness;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.ClothSim;
import dev.quarkos.capes.client.render.PlayerFacts;
import dev.quarkos.capes.client.screen.CapeScreens;
//? if forge {
 //?} else {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//?}
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

//? if forge {
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
        ClientTickEvents.END_CLIENT_TICK.register(QuarkosCapes::tick);
    }
    //?}

    public static void tick(Minecraft client) {
        ClientHarness.start(client);
        PlayerFacts.tick(client);
        ClothSim.clientTick();
        if (!dev.quarkos.capes.client.harness.AuditHarness.selected()) {
            CapeHttp.tick(client);
            dev.quarkos.capes.client.net.SiteApi.tick(client);
        }
        if (client.screen == null && OPEN != null && OPEN.consumeClick()) {
            McBridge.setScreen(client, CapeScreens.open(null));
        }
        LocalFiles.autosave(CapeScreens.isSettings(client.screen));
        if (!CapeScreens.isSettings(client.screen)) {
            CapeBook.preview = false;
            CapeBook.draft = null;
        }
    }
}
