package dev.quarkos.capes.client.render;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.sim.WaveCloth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Steps the 16-part cloth after the cape was drawn recently. */
public final class ClothSim {
    private static final class Slot {
        final WaveCloth.State state = new WaveCloth.State();
        WaveCloth.Movement movement = WaveCloth.Movement.SIMULATION_V2;
        int lastDraw;
        int entityId = -1;
        int playerTick = -1;
        float gravX;
        float gravY = -1f;
        float gravZ;
    }

    private static final Map<UUID, Slot> SLOTS = new ConcurrentHashMap<>();
    private static int tick;

    private ClothSim() {
    }

    public static void clientTick() {
        tick++;
        WaveCloth.Settings settings = ClientSettings.get().cloth;
        for (Map.Entry<UUID, Slot> entry : SLOTS.entrySet()) {
            Slot slot = entry.getValue();
            if (tick - slot.lastDraw > 100) {
                SLOTS.remove(entry.getKey(), slot);
                continue;
            }
            PlayerFacts.Fact fact = PlayerFacts.byUuid(entry.getKey());
            if (fact == null) {
                SLOTS.remove(entry.getKey(), slot);
                continue;
            }
            if (slot.entityId != fact.entityId()) {
                slot.entityId = fact.entityId();
                slot.playerTick = -1;
                slot.state.reset();
            }
            if (slot.playerTick == fact.tickAge() && slot.movement == settings.movement()) continue;
            slot.playerTick = fact.tickAge();
            WaveCloth.Motion motion = motion(fact, slot);
            if (slot.movement != settings.movement()) {
                slot.movement = settings.movement();
                WaveCloth.run(slot.state, settings, motion, 5);
            }
            WaveCloth.step(slot.state, settings, motion);
        }
    }

    public static void clear() { SLOTS.clear(); }

    public static WaveCloth.State state(UUID id) {
        return slot(id).state;
    }

    public static void markDrawn(UUID id) {
        slot(id).lastDraw = tick;
    }

    public static void gravity(UUID id, float x, float y, float z) {
        Slot slot = slot(id);
        slot.gravX = x;
        slot.gravY = y;
        slot.gravZ = z;
    }

    public static WaveCloth.Motion motion(PlayerFacts.Fact fact) {
        return motion(fact, slot(fact.id()));
    }

    private static WaveCloth.Motion motion(PlayerFacts.Fact fact, Slot slot) {
        return WaveCloth.playerMotion(
                fact.x() - fact.px(), fact.y() - fact.py(), fact.z() - fact.pz(), fact.bodyYaw(), fact.turn(),
                fact.underwater(),
                fact.swimming(),
                fact.xRot(),
                fact.sneaking(),
                fact.sneakStarted(),
                fact.tickAge() * 50L,
                slot.gravX,
                slot.gravY,
                slot.gravZ
        );
    }

    private static Slot slot(UUID id) {
        return SLOTS.computeIfAbsent(id, key -> new Slot());
    }
}
