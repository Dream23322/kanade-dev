package com.instrumentalist.krs.hacks.features.level.anticheat;

import com.instrumentalist.krs.hacks.features.level.AntiCheatDetector;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flags aura style attacking from the swing packet stream:
 * <ul>
 *     <li>sustained sub-cooldown swing intervals while a player is in range,</li>
 *     <li>attacks landing while the attacker is not facing its nearest target.</li>
 * </ul>
 */
public final class KillAuraCheck extends AntiCheatCheck {

    private static final double TARGET_RANGE = 6.0;
    private static final int INTERVAL_HISTORY = 10;
    private static final double BASE_FAST_TICKS = 2.0;
    private static final double BASE_REQUIRED_FAST = 8.0;
    private static final float BASE_MAX_AIM_OFFSET = 80f;

    private final Map<UUID, Long> lastSwingTicks = new HashMap<>();
    private final Map<UUID, ArrayDeque<Long>> intervals = new HashMap<>();

    public KillAuraCheck() {
        super(AntiCheatFlags.KILLAURA);
    }

    @Override
    protected void handle(AbstractClientPlayer player, ClientLevel level, boolean swingStarted) {
        UUID id = player.getUUID();
        AbstractClientPlayer target = nearestTarget(player, level);

        if (target == null) {
            lastSwingTicks.remove(id);
            intervals.remove(id);
            return;
        }

        if (!swingStarted)
            return;

        long now = now();
        Long previous = lastSwingTicks.put(id, now);
        if (previous == null)
            return;

        long gap = now - previous;
        if (gap <= 0)
            return;

        double sensitivity = AntiCheatDetector.sensitivity.get();
        long fastTicks = Math.max(1L, Math.round(BASE_FAST_TICKS * sensitivity));
        int requiredFast = Math.clamp((int) Math.round(BASE_REQUIRED_FAST / sensitivity), 3, INTERVAL_HISTORY);

        ArrayDeque<Long> recent = intervals.computeIfAbsent(id, ignored -> new ArrayDeque<>(INTERVAL_HISTORY));
        recent.addLast(gap);
        while (recent.size() > INTERVAL_HISTORY)
            recent.removeFirst();

        long fastCount = recent.stream().filter(value -> value <= fastTicks).count();
        if (fastCount >= requiredFast) {
            AntiCheatFlags.receiveSignal(playerName(player), name());
            recent.clear();
        }

        // A hit that lands while the attacker faces away from its target is an aura rotation.
        float aimOffset = Math.abs(wrapDegrees(player.getYRot() - yawTo(player, target)));
        float maxAimOffset = (float) Math.clamp(BASE_MAX_AIM_OFFSET / sensitivity, 45.0, 110.0);
        if (aimOffset > maxAimOffset && player.distanceTo(target) > 2.5f)
            AntiCheatFlags.receiveSignal(playerName(player), name());
    }

    @Override
    protected void prune(Set<UUID> present) {
        lastSwingTicks.keySet().retainAll(present);
        intervals.keySet().retainAll(present);
    }

    @Override
    public void reset() {
        lastSwingTicks.clear();
        intervals.clear();
    }

    private static float yawTo(AbstractClientPlayer player, AbstractClientPlayer target) {
        Vec3 direction = target.position().subtract(player.position());
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    private static AbstractClientPlayer nearestTarget(AbstractClientPlayer player, ClientLevel level) {
        AbstractClientPlayer closest = null;
        double bestDistance = TARGET_RANGE * TARGET_RANGE;

        for (AbstractClientPlayer candidate : level.players()) {
            if (candidate == player || candidate.isDeadOrDying() || candidate.isSpectator())
                continue;

            double distance = player.distanceToSqr(candidate);
            if (distance < bestDistance && distance > 0.01) {
                bestDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }
}
