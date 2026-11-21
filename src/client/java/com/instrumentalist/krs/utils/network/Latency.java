package com.instrumentalist.krs.utils.network;

import com.instrumentalist.krs.utils.IMinecraft;

/**
 * Latency helper for timing-sensitive combat code.
 *
 * <p>The tab list entry carries the round trip time in milliseconds. Everything that has to
 * compensate for the delay between a sent packet and its effect on the server works in ticks,
 * so {@link #ticks()} converts and clamps the value.</p>
 */
public final class Latency implements IMinecraft {
    private static final int MAX_TICKS = 20;

    private Latency() {
    }

    /** Round trip time in milliseconds, or 0 while the player or connection is missing. */
    public static int millis() {
        var connection = mc.getConnection();
        var player = mc.player;
        if (connection == null || player == null)
            return 0;

        var info = connection.getPlayerInfo(player.getUUID());
        if (info == null)
            return 0;

        return Math.max(0, info.getLatency());
    }

    /** Round trip time in ticks, rounded and clamped. */
    public static int ticks() {
        return Math.min(MAX_TICKS, (millis() + 25) / 50);
    }

    /** Half of the round trip in ticks: how far behind the server a client-side reading is. */
    public static int halfTicks() {
        return Math.min(MAX_TICKS, ticks() / 2);
    }
}
