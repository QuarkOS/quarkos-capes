package dev.quarkos.capes.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21 && <1.21.11 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}
//? if >=1.21.11 {
import net.minecraft.world.entity.player.PlayerSkin;
//?}

/** Swaps in the short-haired harness skin. Idle when that skin was not installed. */
@Mixin(AbstractClientPlayer.class)
public class ShotSkinMixin {
    //? if <1.21 {
    /*@Inject(method = "getSkinTextureLocation", at = @At("HEAD"))
    private void quarkosShortHair(CallbackInfoReturnable<net.minecraft.resources.Identifier> cir) {
    }
    *///?} else if <1.21.11 {
    /*@Inject(method = "getSkin", at = @At("HEAD"))
    private void quarkosShortHair(CallbackInfoReturnable<PlayerSkin> cir) {
    }
    *///?} else {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void quarkosShortHair(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!dev.quarkos.capes.DevFlags.HARNESS) {
            return;
        }
        PlayerSkin skin = dev.quarkos.capes.client.harness.ShotView.skin();
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
    //?}
}
