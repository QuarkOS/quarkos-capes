package dev.quarkos.capes.client.mixin;

//? if >=26.3 {
/*import com.mojang.blaze3d.platform.InputConstants;
import dev.quarkos.capes.QuarkosCapes;
import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class CapeKeyMixin {
    // Friends uses O too and handles the key before vanilla updates KeyMappings.
    @Inject(method = "handleGlobalKeyPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;toggleFriendsScreen()Z"), cancellable = true)
    private void quarkosWardrobeKey(InputConstants.Key key, boolean controlDown, CallbackInfoReturnable<Boolean> result) {
        var client = (Minecraft) (Object) this;
        if (client.player != null && client.screen == null && client.getOverlay() == null
                && QuarkosCapes.OPEN != null && QuarkosCapes.OPEN.matches(key)) {
            client.setScreen(CapeScreens.open(null));
            result.setReturnValue(true);
        }
    }
}
*///?}
