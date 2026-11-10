package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.render.MissingCapeLayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wavey Capes removes {@code CapeLayer} from the player renderer. This adds a separate layer
 * afterwards. It is not a {@code CapeLayer}, so that removal leaves it in place.
 */
//? if <1.21.11 {
/*@Mixin(net.minecraft.client.renderer.entity.player.PlayerRenderer.class)
*///?} else {
@Mixin(net.minecraft.client.renderer.entity.player.AvatarRenderer.class)
//?}
public abstract class MissingCapeHook {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void quarkosCapes(CallbackInfo ci) {
        LivingEntityRendererAccess access = (LivingEntityRendererAccess) (Object) this;
        for (Object layer : access.quarkos$layers()) {
            if (layer instanceof MissingCapeLayer) {
                return;
            }
        }
        access.quarkos$addLayer(new MissingCapeLayer((LivingEntityRenderer) (Object) this));
    }
}
