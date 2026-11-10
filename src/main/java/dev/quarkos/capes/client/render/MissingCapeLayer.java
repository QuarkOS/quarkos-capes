package dev.quarkos.capes.client.render;

import dev.quarkos.capes.client.mixin.CapeLayersAccessor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
//? if <1.21.11 {
/*import net.minecraft.client.renderer.entity.player.PlayerRenderer;
*///?} else {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
//?}

/** Inherits CapeLayerMixin's selection, visibility, animation and preview path. */
public final class MissingCapeLayer extends CapeLayer {
    //? if <1.21.11 {
    /*private MissingCapeLayer(PlayerRenderer renderer, EntityRendererProvider.Context context) {
        super(renderer);
    }
    *///?} else {
    private MissingCapeLayer(AvatarRenderer<?> renderer, EntityRendererProvider.Context context) {
        super(renderer, context.getModelSet(), context.getEquipmentAssets());
    }
    //?}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addIfMissing(Object renderer, EntityRendererProvider.Context context) {
        //? if <1.21.11 {
        /*if (!(renderer instanceof PlayerRenderer player)) return;
        *///?} else {
        if (!(renderer instanceof AvatarRenderer<?> player)) return;
        //?}
        var layers = ((CapeLayersAccessor) player).quarkosCapeLayers();
        if (CapeCompatibility.needsFallback(layers, layer -> layer instanceof CapeLayer)) {
            layers.add(new MissingCapeLayer(player, context));
        }
    }

    public static void register() {
        //? if fabric {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (type, renderer, helper, context) -> {
                    if (type == net.minecraft.world.entity.EntityType.PLAYER) addIfMissing(renderer, context);
                });
        //?}
    }
}
