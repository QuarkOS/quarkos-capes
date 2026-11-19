package dev.quarkos.capes.client.render;

import dev.quarkos.capes.sim.CapeLift;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerFacts {
    public record Fact(
            UUID id,
            String name,
            int entityId,
            boolean elytra,
            boolean underwater,
            boolean swimming,
            boolean sneaking,
            boolean sneakStarted,
            boolean capePart,
            float xRot,
            float yRot,
            float bodyYaw,
            float turn,
            int tickAge,
            double x,
            double y,
            double z,
            double px,
            double py,
            double pz,
            CapeLift capeLift
    ) {
    }

    private static final Map<UUID, Fact> BY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, UUID> ENTITY = new ConcurrentHashMap<>();

    private static Level previousLevel;

    private PlayerFacts() {
    }

    public static void tick(Minecraft client) {
        if (client.level != previousLevel) {
            BY_ID.clear(); ENTITY.clear();
            ClothSim.clear(); CapeBook.clearPlayers();
            previousLevel = client.level;
        }
        if (client.level == null) {
            return;
        }
        Level level = client.level;
        BY_ID.keySet().removeIf(id -> {
            if (level.getPlayerByUUID(id) != null) return false;
            CapeBook.forget(id);
            return true;
        });
        ENTITY.clear();
        for (Player player : level.players()) {
            Fact previous = BY_ID.get(player.getUUID());
            if (previous != null && previous.entityId() != player.getId()) previous = null;
            boolean sneaking = player.isCrouching();
            double x = player.getX(), y = player.getY(), z = player.getZ();
            CapeLift lift = previous == null ? CapeLift.RESTING : previous.capeLift();
            if (previous != null && player.tickCount != previous.tickAge()) {
                lift = lift.advance(x - previous.x(), z - previous.z());
            }
            Fact fact = new Fact(
                    player.getUUID(),
                    player.getName().getString(),
                    player.getId(),
                    player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA),
                    player.isUnderWater(),
                    player.isSwimming(),
                    sneaking,
                    sneaking && (previous == null || !previous.sneaking()),
                    player.isModelPartShown(PlayerModelPart.CAPE),
                    player.getXRot(),
                    player.getYRot(),
                    player.yBodyRot,
                    previous == null ? 0f : Mth.wrapDegrees(player.getYRot() - previous.yRot()),
                    player.tickCount,
                    x, y, z,
                    previous == null ? x : previous.x(),
                    previous == null ? y : previous.y(),
                    previous == null ? z : previous.z(),
                    lift
            );
            BY_ID.put(player.getUUID(), fact);
            ENTITY.put(player.getId(), player.getUUID());
        }
    }

    public static Fact byUuid(UUID id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Fact byEntity(int entityId) {
        UUID id = ENTITY.get(entityId);
        return id == null ? null : BY_ID.get(id);
    }
}
