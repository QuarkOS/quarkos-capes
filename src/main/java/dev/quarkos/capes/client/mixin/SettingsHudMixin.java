package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settings own the full screen, including the space normally occupied by the hotbar. */
@Mixin(Gui.class)
public class SettingsHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quarkosSettingsHud(CallbackInfo ci) {
        if (CapeScreens.isSettings(Minecraft.getInstance().screen)) ci.cancel();
    }
}
