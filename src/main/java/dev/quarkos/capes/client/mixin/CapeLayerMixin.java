package dev.quarkos.capes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeDraw;
import dev.quarkos.capes.client.render.CapeLayerWatch;
import dev.quarkos.capes.client.render.PlayerFacts;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
*///?} else {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
//?}

@Mixin(CapeLayer.class)
public class CapeLayerMixin {
    //? if <1.21.11 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quarkosCapes(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        boolean local = client.player == player;
        if (player.isInvisible() || player.isSpectator()) { ci.cancel(); return; }
        Identifier vanilla = player.getSkin() == null ? null : player.getSkin().capeTexture();
        CapeBook.Choice choice = CapeDraw.resolve(player.getUUID(), local, vanilla);
        CapeLayerWatch.textureOverride = null;
        if (local) {
            CapeLayerWatch.local(choice.texture());
        }
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
        }
        // A flat cape is the vanilla cloak. Cancelling here drew a sheet on the body and the skin still had no cape.
        if (choice.kind() == CapeBook.Kind.QUAD && choice.frames() <= 1) {
            CapeLayerWatch.textureOverride = choice.texture();
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, player.isCrouching() ? 0.2F : 0.0F,
                player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() ? 0.0F : 0.0625F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(player.isCrouching() ? 31.0F : 6.0F));
        var consumer = buffers.getBuffer(McBridge.cutout(choice.texture()));
        CapeDraw.emit(pose.last(), consumer, CapeBook.preview ? 15728880 : light, player.getUUID(), choice);
        pose.popPose();
        ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void quarkosCapesClear(CallbackInfo ci) {
        CapeLayerWatch.textureOverride = null;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"))
    private boolean quarkosCapePart(AbstractClientPlayer player, PlayerModelPart part) {
        if (part == PlayerModelPart.CAPE && CapeLayerWatch.textureOverride != null) {
            return true;
        }
        return player.isModelPartShown(part);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/PlayerSkin;capeTexture()Lnet/minecraft/resources/Identifier;"))
    private Identifier quarkosCapeTexture(net.minecraft.client.resources.PlayerSkin skin) {
        Identifier ours = CapeLayerWatch.textureOverride;
        return ours != null ? ours : skin.capeTexture();
    }
    *///?} else {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void quarkosCapes(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        boolean local = client.player != null && client.player.getId() == state.id;
        if (state.isInvisible || state.isSpectator) { ci.cancel(); return; }
        PlayerFacts.Fact fact = PlayerFacts.byEntity(state.id);
        java.util.UUID id = fact != null ? fact.id() : (local ? client.player.getUUID() : null);
        if (id == null) {
            if (local) {
                CapeLayerWatch.local(null);
            }
            return;
        }
        Identifier vanilla = state.skin != null && state.skin.cape() != null ? state.skin.cape().texturePath() : null;
        CapeBook.Choice choice = CapeDraw.resolve(id, local, vanilla);
        if (local) {
            CapeLayerWatch.local(choice.texture());
        }
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
        }
        if (choice.kind() == CapeBook.Kind.QUAD && choice.frames() <= 1 && state.skin != null) {
            state.showCape = true;
            Identifier cape = choice.texture();
            state.skin = new PlayerSkin(
                    state.skin.body(),
                    new ClientAsset.ResourceTexture(cape, cape),
                    state.skin.elytra(),
                    state.skin.model(),
                    state.skin.secure()
            );
            if (CapeBook.preview) {
                CapeBook.draws++;
            }
            return;
        }
        int bright = CapeBook.preview ? 15728880 : light;
        java.util.UUID drawn = id;
        pose.pushPose();
        pose.translate(0.0F, state.isCrouching ? 0.2F : 0.0F, state.chestEquipment.isEmpty() ? 0.0F : 0.0625F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(state.isCrouching ? 31.0F : 6.0F));
        collector.submitCustomGeometry(pose, McBridge.cutout(choice.texture()), (entry, consumer) ->
                CapeDraw.emit(entry, consumer, bright, drawn, choice));
        pose.popPose();
        ci.cancel();
    }
    //?}
}
