package com.instrumentalist.krs.hacks.features.level.anticheat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flags no slow: sprinting while using an item. Vanilla cancels the sprint as soon as the
 * player starts using a sword guard, a shield, food or a potion.
 */
public final class NoSlowCheck extends AntiCheatCheck {

    private static final int MIN_USE_TICKS = 3;
    private static final long SIGNAL_COOLDOWN_TICKS = 20;

    private final Map<UUID, Integer> sprintingUseTicks = new HashMap<>();
    private final Map<UUID, Long> lastSignalTicks = new HashMap<>();

    public NoSlowCheck() {
        super(AntiCheatFlags.NOSLOW);
    }

    @Override
    protected void handle(AbstractClientPlayer player, ClientLevel level, boolean swingStarted) {
        UUID id = player.getUUID();

        if (!player.isUsingItem() || !player.isSprinting() || player.isFallFlying()) {
            sprintingUseTicks.remove(id);
            return;
        }

        int useTicks = sprintingUseTicks.merge(id, 1, Integer::sum);
        if (useTicks < MIN_USE_TICKS)
            return;

        long now = now();
        if (now - lastSignalTicks.getOrDefault(id, Long.MIN_VALUE) < SIGNAL_COOLDOWN_TICKS)
            return;

        lastSignalTicks.put(id, now);
        sprintingUseTicks.put(id, 0);
        AntiCheatFlags.receiveSignal(playerName(player), name());
    }

    @Override
    protected void prune(Set<UUID> present) {
        sprintingUseTicks.keySet().retainAll(present);
        lastSignalTicks.keySet().retainAll(present);
    }

    @Override
    public void reset() {
        sprintingUseTicks.clear();
        lastSignalTicks.clear();
    }
}
