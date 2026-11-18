package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settings own the full screen, including the space normally occupied by the hotbar. */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
public class SettingsHudMixin {
    //? if >=26.2 {
    /*@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //?}
    private void quarkosSettingsHud(CallbackInfo ci) {
        if (CapeScreens.isSettings(Minecraft.getInstance().screen)) ci.cancel();
    }
}
