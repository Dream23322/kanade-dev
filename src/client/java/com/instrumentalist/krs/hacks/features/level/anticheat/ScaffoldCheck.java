package com.instrumentalist.krs.hacks.features.level.anticheat;

import com.instrumentalist.krs.hacks.features.level.AntiCheatDetector;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Heuristic scaffold check. Scaffold clients aim at the placement spot with instant pitch
 * snaps while bridging, so repeated large downward pitch jumps while airborne and moving are
 * buffered. The previous version only looked at a block under the feet, which is true for any
 * normal jump and never actually indicated scaffolding.
 */
public final class ScaffoldCheck extends AntiCheatCheck {

    private static final float BASE_SNAP_PITCH = 55f;
    private static final float DOWN_PITCH = 60f;
    private static final double FAST_HORIZONTAL_SPEED = 0.15;
    private static final int VIOLATION_THRESHOLD = 3;
    private static final long SIGNAL_COOLDOWN_TICKS = 40;

    private final Map<UUID, Float> lastPitch = new HashMap<>();
    private final Map<UUID, Integer> snaps = new HashMap<>();
    private final Map<UUID, Long> lastSignalTicks = new HashMap<>();

    public ScaffoldCheck() {
        super(AntiCheatFlags.SCAFFOLD);
    }

    @Override
    protected void handle(AbstractClientPlayer player, ClientLevel level, boolean swingStarted) {
        UUID id = player.getUUID();

        float pitch = player.getXRot();
        float previousPitch = lastPitch.getOrDefault(id, pitch);
        lastPitch.put(id, pitch);
        float pitchDelta = Math.abs(pitch - previousPitch);

        double speedX = player.getDeltaMovement().x;
        double speedZ = player.getDeltaMovement().z;
        double horizontalSpeed = Math.sqrt(speedX * speedX + speedZ * speedZ);
        boolean airborne = !player.onGround();
        boolean lookingDown = pitch > DOWN_PITCH;
        double sensitivity = AntiCheatDetector.sensitivity.get();
        float snapPitch = (float) Math.clamp(BASE_SNAP_PITCH / sensitivity, 30.0, 80.0);

        if (airborne && lookingDown && horizontalSpeed > FAST_HORIZONTAL_SPEED && pitchDelta > snapPitch) {
            int count = snaps.merge(id, 1, Integer::sum);
            if (count < VIOLATION_THRESHOLD)
                return;

            long now = now();
            if (now - lastSignalTicks.getOrDefault(id, Long.MIN_VALUE) < SIGNAL_COOLDOWN_TICKS)
                return;

            lastSignalTicks.put(id, now);
            snaps.put(id, 0);
            AntiCheatFlags.receiveSignal(playerName(player), name(), count);
        } else {
            snaps.computeIfPresent(id, (ignored, value) -> value > 0 ? value - 1 : 0);
        }
    }

    @Override
    protected void prune(Set<UUID> present) {
        lastPitch.keySet().retainAll(present);
        snaps.keySet().retainAll(present);
        lastSignalTicks.keySet().retainAll(present);
    }

    @Override
    public void reset() {
        lastPitch.clear();
        snaps.clear();
        lastSignalTicks.clear();
    }
}
