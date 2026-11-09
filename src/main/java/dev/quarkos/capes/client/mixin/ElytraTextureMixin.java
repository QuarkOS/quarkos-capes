//? if >=1.21.11 {
package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.render.CapeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class ElytraTextureMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void quarkosCapes(Avatar avatar, AvatarRenderState state, float partial, CallbackInfo ci) {
        boolean local = Minecraft.getInstance().player == avatar;
        Identifier id = CapeBook.elytraFor(avatar.getUUID(), local);
        if (id == null || state.skin == null) {
            return;
        }
        state.skin = new PlayerSkin(state.skin.body(), state.skin.cape(), new ClientAsset.ResourceTexture(id, id), state.skin.model(), state.skin.secure());
    }
}
//?}
