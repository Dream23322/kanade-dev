package com.instrumentalist.krs.hacks.features.level.anticheat;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.AntiCheatDetector;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the enabled anti cheat checks once per client tick. Swing packets are collected
 * between ticks so checks see every attack, including swings that restart an animation.
 */
public final class AntiCheatEngine {

    private record Entry(BooleanValue toggle, AntiCheatCheck check) {
    }

    private static final Set<Integer> pendingSwingEntityIds = new HashSet<>();

    private final List<Entry> checks = List.of(
            new Entry(AntiCheatDetector.killAuraCheck, new KillAuraCheck()),
            new Entry(AntiCheatDetector.autoBlockCheck, new AutoBlockCheck()),
            new Entry(AntiCheatDetector.noSlowCheck, new NoSlowCheck()),
            new Entry(AntiCheatDetector.scaffoldCheck, new ScaffoldCheck())
    );
    private final Set<UUID> swingsThisTick = new HashSet<>();

    /** Called from the client packet listener for every {@code ClientboundSwingAnimationPacket}. */
    public static void recordSwing(int entityId) {
        if (!ModuleManager.getModuleState(AntiCheatDetector.class))
            return;

        synchronized (pendingSwingEntityIds) {
            pendingSwingEntityIds.add(entityId);
        }
    }

    public void tick(ClientLevel level, LocalPlayer self) {
        swingsThisTick.clear();
        synchronized (pendingSwingEntityIds) {
            for (int entityId : pendingSwingEntityIds) {
                Entity entity = level.getEntity(entityId);
                if (entity != null)
                    swingsThisTick.add(entity.getUUID());
            }
            pendingSwingEntityIds.clear();
        }

        for (Entry entry : checks) {
            if (entry.toggle().get())
                entry.check().tick(level, self, swingsThisTick);
        }

        AntiCheatFlags.tick();
    }

    public void reset() {
        for (Entry entry : checks)
            entry.check().reset();

        synchronized (pendingSwingEntityIds) {
            pendingSwingEntityIds.clear();
        }
        swingsThisTick.clear();
        AntiCheatFlags.reset();
    }
}
