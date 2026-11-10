package dev.quarkos.capes.client.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Counts successful applications only for explicitly enabled harness runs. */
public final class CapeMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean("quarkos_capes.harness") || !(mixin.endsWith(".ShotCameraMixin") || mixin.endsWith(".ShotSkinMixin"));
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (Boolean.getBoolean("quarkos_capes.harness")) dev.quarkos.capes.client.harness.HarnessMixins.applied(mixin);
        if (mixin.endsWith(".CapeModelDrawMixin")) {
            boolean hooked = false;
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.contains("quarkosDrawn")) hooked = true;
            }
            if (!hooked) org.slf4j.LoggerFactory.getLogger("quarkos_capes").warn(
                    "Cape draw observation hook unavailable; cape rendering continues without vanilla draw counters.");
        }
    }
}
