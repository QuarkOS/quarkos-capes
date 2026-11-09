package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.harness.ShotView;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
/*import net.minecraft.world.level.BlockGetter;
*///?} else {
import net.minecraft.world.level.Level;
//?}

/** Pulls the harness camera back and around the player. A normal view leaves this idle. */
@Mixin(Camera.class)
public abstract class ShotCameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void move(float x, float y, float z);

    //? if <1.21.11 {
    /*@Inject(method = "setup", at = @At("RETURN"))
    private void quarkosShotCamera(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        orbit(entity, partial);
    }
    *///?} else {
    @Inject(method = "setup", at = @At("RETURN"))
    private void quarkosShotCamera(Level level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        orbit(entity, partial);
    }
    //?}

    private void orbit(Entity entity, float partial) {
        if (!Boolean.getBoolean("quarkos_capes.harness") || !ShotView.active() || entity == null) {
            return;
        }
        setRotation(entity.getViewYRot(partial) + ShotView.yaw(), ShotView.pitch());
        Vec3 eye = entity.getEyePosition(partial);
        setPosition(eye.x, eye.y + ShotView.lift(), eye.z);
        move(-ShotView.distance(), 0f, 0f);
    }
}
