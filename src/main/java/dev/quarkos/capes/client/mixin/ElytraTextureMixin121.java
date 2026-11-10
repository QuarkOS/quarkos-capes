//? if <1.21.11 {
/*package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.render.CapeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21 {
import net.minecraft.client.resources.PlayerSkin;
//?}

@Mixin(AbstractClientPlayer.class)
public class ElytraTextureMixin121 {
    //? if <1.21 {
    @Inject(method = "getElytraTextureLocation", at = @At("RETURN"), cancellable = true)
    private void quarkosCapes(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        boolean local = Minecraft.getInstance().player == player;
        Identifier id = CapeBook.elytraFor(player.getUUID(), local);
        if (id != null) {
            cir.setReturnValue(id);
        }
    }
    //?} else {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void quarkosCapes(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        boolean local = Minecraft.getInstance().player == player;
        Identifier id = CapeBook.elytraFor(player.getUUID(), local);
        PlayerSkin skin = cir.getReturnValue();
        cir.setReturnValue(CapeBook.ELYTRA_SKINS.get(player.getUUID(), skin, id,
                (base, texture) -> new PlayerSkin(base.texture(), base.textureUrl(), base.capeTexture(), texture, base.model(), base.secure())));
    }
    //?}
}
*///?}
