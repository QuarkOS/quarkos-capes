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
import org.spongepowered.asm.mixin.Unique;
//?}

@Mixin(AbstractClientPlayer.class)
public class ElytraTextureMixin121 {
    //? if >=1.21 {
    @Unique
    private Identifier quarkosElytraId;
    @Unique
    private Identifier quarkosBody;
    @Unique
    private Identifier quarkosCape;
    @Unique
    private PlayerSkin quarkosElytraSkin;
    //?}

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
        if (id == null || skin == null) {
            quarkosElytraSkin = null;
            return;
        }
        if (quarkosElytraSkin != null
                && id.equals(quarkosElytraId)
                && skin.texture().equals(quarkosBody)
                && java.util.Objects.equals(skin.capeTexture(), quarkosCape)) {
            cir.setReturnValue(quarkosElytraSkin);
            return;
        }
        quarkosElytraId = id;
        quarkosBody = skin.texture();
        quarkosCape = skin.capeTexture();
        quarkosElytraSkin = new PlayerSkin(skin.texture(), skin.textureUrl(), skin.capeTexture(), id, skin.model(), skin.secure());
        cir.setReturnValue(quarkosElytraSkin);
    }
    //?}
}
*///?}
