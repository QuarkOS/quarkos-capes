package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
/*import net.minecraft.client.renderer.entity.player.PlayerRenderer;
*///?} else {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
//?}

/** Suppress both the player name and the scoreboard line before either is submitted. */
//? if <1.21.11 {
/*@Mixin(PlayerRenderer.class)
*///?} else {
@Mixin(AvatarRenderer.class)
//?}
public class SettingsNameTagMixin {
    //? if <1.21.11 {
    /*@Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    //?}
    private void quarkosSettingsName(CallbackInfo ci) {
        if (CapeScreens.isSettings(Minecraft.getInstance().screen)) ci.cancel();
    }
}
