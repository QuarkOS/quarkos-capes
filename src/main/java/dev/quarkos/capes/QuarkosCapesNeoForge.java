//? if neoforge {
/*
package dev.quarkos.capes;

import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "quarkos_capes", dist = Dist.CLIENT)
public final class QuarkosCapesNeoForge {
    public QuarkosCapesNeoForge(IEventBus modBus, ModContainer container) {
        QuarkosCapes.bootstrap();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> CapeScreens.open(parent));
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> QuarkosCapes.tick(Minecraft.getInstance()));
        //? if <1.21.2 {
        /^NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) -> {
            if (CapeScreens.isClothSettings(event.getScreen())) {
                dev.quarkos.capes.client.screen.ui.CapeTheme.background(event.getGuiGraphics(), event.getScreen().width, event.getScreen().height);
            }
        });
        ^///?}
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        if (QuarkosCapes.OPEN != null) {
            event.register(QuarkosCapes.OPEN);
        }
    }
}
*///?}
