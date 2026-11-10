package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.PlayerFacts;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;

@Mixin(CapeLayer.class)
*///?} else {
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

@Mixin(ModelFeatureRenderer.class)
//?}
public class CapeModelDrawMixin {
    //? if <1.21.11 {
    /*@Inject(method = "render", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/PlayerModel;renderCloak(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V", shift = At.Shift.AFTER))
    private void quarkosDrawn(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                              float swing, float amount, float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        var skin = player.getSkin();
        var texture = dev.quarkos.capes.client.render.CapeLayerWatch.textureOverride;
        CapeBook.drawn(player.getUUID(), texture != null ? texture : skin.capeTexture(), false);
    }
    *///?} else {
    @Inject(method = "renderModel", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0, shift = At.Shift.AFTER))
    private void quarkosDrawn(SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer consumer,
                              OutlineBufferSource outlines, MultiBufferSource.BufferSource crumbling, CallbackInfo ci) {
        if (!(submit.model() instanceof PlayerCapeModel) || !(submit.state() instanceof AvatarRenderState state)) return;
        var client = Minecraft.getInstance();
        var fact = PlayerFacts.byEntity(state.id);
        var id = fact != null ? fact.id() : client.player != null && client.player.getId() == state.id ? client.player.getUUID() : null;
        if (id != null && state.skin != null && state.skin.cape() != null)
            CapeBook.drawn(id, state.skin.cape().texturePath(), state == CapeBook.previewState);
    }
    //?}
}
