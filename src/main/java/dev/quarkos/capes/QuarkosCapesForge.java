//? if forge {

/*package dev.quarkos.capes;

import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if <1.21.6 {
/^import net.minecraftforge.eventbus.api.IEventBus;
^///?}

@Mod("quarkos_capes")
public final class QuarkosCapesForge {
    public QuarkosCapesForge() {
        QuarkosCapes.bootstrap();
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(CapeScreens::open)
        );
        //? if <1.21.6 {
        /^IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::beforeScreen);
        ^///?}
        //? if >=1.21.6 {
        RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(this::registerKeys);
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> QuarkosCapes.tick(Minecraft.getInstance()));
        //?}
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        if (QuarkosCapes.OPEN != null) {
            event.register(QuarkosCapes.OPEN);
        }
    }

    //? if <1.21.6 {
    /^private void beforeScreen(net.minecraftforge.client.event.ScreenEvent.Render.Pre event) {
        if (CapeScreens.isSettings(event.getScreen())) event.getGuiGraphics().fill(0, 0, event.getScreen().width, event.getScreen().height, 0xFF10141C);
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        QuarkosCapes.tick(Minecraft.getInstance());
    }
    ^///?}
}
*///?}
