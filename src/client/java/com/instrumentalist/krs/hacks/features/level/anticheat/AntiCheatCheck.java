package com.instrumentalist.krs.hacks.features.level.anticheat;

import com.instrumentalist.krs.hacks.features.player.HackerDetector;
import com.instrumentalist.krs.utils.math.ToolUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared plumbing for the anti cheat checks: player iteration and a private tick counter.
 * Swings are fed from {@code ClientboundSwingAnimationPacket}, not from the animation state,
 * because a new swing during an ongoing animation does not reliably produce a false/true edge
 * on {@link AbstractClientPlayer#isSwinging()}.
 */
public abstract class AntiCheatCheck {

    private final String name;
    private long tick;

    protected AntiCheatCheck(String name) {
        this.name = name;
    }

    public final String name() {
        return name;
    }

    public final void tick(ClientLevel level, LocalPlayer self, Set<UUID> swingers) {
        tick++;

        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            present.add(player.getUUID());
            boolean skipSelf = player == self && !HackerDetector.checkSelf.get();
            if (skipSelf || player.isDeadOrDying() || player.isSpectator())
                continue;

            handle(player, level, swingers.contains(player.getUUID()));
        }

        prune(present);
    }

    protected abstract void handle(AbstractClientPlayer player, ClientLevel level, boolean swingStarted);

    public abstract void reset();

    /** Called once per tick with the ids of every player currently in the level. */
    protected void prune(Set<UUID> present) {
    }

    protected final long now() {
        return tick;
    }

    protected static boolean holdingSword(AbstractClientPlayer player) {
        return ToolUtil.INSTANCE.isSword(player.getMainHandItem());
    }

    protected static String playerName(AbstractClientPlayer player) {
        return player.getName().getString();
    }

    protected static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f)
            wrapped -= 360.0f;
        if (wrapped < -180.0f)
            wrapped += 360.0f;
        return wrapped;
    }
}
