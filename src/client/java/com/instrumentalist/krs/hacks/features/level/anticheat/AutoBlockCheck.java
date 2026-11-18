package com.instrumentalist.krs.hacks.features.level.anticheat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flags auto block: a sword guard that is still up while the player swings. Vanilla has to
 * release the guard to attack, so a block that survives the swing comes from the client.
 */
public final class AutoBlockCheck extends AntiCheatCheck {

    private static final long SIGNAL_COOLDOWN_TICKS = 10;

    private final Map<UUID, Long> lastSignalTicks = new HashMap<>();

    public AutoBlockCheck() {
        super(AntiCheatFlags.AUTOBLOCK);
    }

    @Override
    protected void handle(AbstractClientPlayer player, ClientLevel level, boolean swingStarted) {
        UUID id = player.getUUID();

        if (!swingStarted || !guarding(player))
            return;

        long now = now();
        if (now - lastSignalTicks.getOrDefault(id, Long.MIN_VALUE) < SIGNAL_COOLDOWN_TICKS)
            return;

        lastSignalTicks.put(id, now);
        AntiCheatFlags.receiveSignal(playerName(player), name());
    }

    @Override
    protected void prune(Set<UUID> present) {
        lastSignalTicks.keySet().retainAll(present);
    }

    @Override
    public void reset() {
        lastSignalTicks.clear();
    }

    private static boolean guarding(AbstractClientPlayer player) {
        return holdingSword(player) && player.isUsingItem() && player.isBlocking();
    }
}
