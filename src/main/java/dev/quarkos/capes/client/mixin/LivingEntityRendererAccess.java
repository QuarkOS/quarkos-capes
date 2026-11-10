package dev.quarkos.capes.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Reads the feature list and adds a layer. {@code addLayer} is protected on the living renderer. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccess {
    @Accessor("layers")
    List<?> quarkos$layers();

    @Invoker("addLayer")
    boolean quarkos$addLayer(RenderLayer<?, ?> layer);
}
