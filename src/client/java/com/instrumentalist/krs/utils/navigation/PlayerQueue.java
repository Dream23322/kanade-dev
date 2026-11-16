package com.instrumentalist.krs.utils.navigation;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.hacks.features.player.Rewound;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Persistent target queue for the {@code stalk} and {@code dodge} commands.
 *
 * <p>Stalking requeues while the named player is absent from the current game;
 * dodging requeues while that player is present. The queue survives world and
 * game changes, so it keeps looking for (or avoiding) the same player.</p>
 */
public final class PlayerQueue implements EventListener {
    private static final PlayerQueue INSTANCE = new PlayerQueue();
    private static final long REQUEUE_COOLDOWN_NANOS = 5_000_000_000L;

    private enum Mode {
        STALK,
        DODGE
    }

    private Mode mode;
    private String target = "";
    private boolean active;
    private boolean registered;
    private boolean missingModeNotified;
    private long lastRequeueNanos;
    private ClientLevel lastRequeueLevel;

    private PlayerQueue() {
    }

    public static String stalk(String arguments) {
        return start(Mode.STALK, arguments);
    }

    public static String dodge(String arguments) {
        return start(Mode.DODGE, arguments);
    }

    private static String start(Mode mode, String arguments) {
        String name = arguments == null ? "" : arguments.trim();
        if (name.equalsIgnoreCase("stop") || name.equalsIgnoreCase("cancel"))
            return stop();

        if (name.isEmpty())
            return mode == Mode.STALK
                    ? "Usage: stalk <player|stop>"
                    : "Usage: dodge <player|stop>";

        PlayerQueue queue = INSTANCE;
        if (queue.active)
            queue.clear(false);

        queue.mode = mode;
        queue.target = name;
        queue.active = true;
        queue.missingModeNotified = false;
        queue.lastRequeueNanos = 0L;
        queue.lastRequeueLevel = null;
        queue.setRegistered(true);
        queue.evaluate();

        return (mode == Mode.STALK ? "Stalking " : "Dodging ") + name;
    }

    private static String stop() {
        PlayerQueue queue = INSTANCE;
        if (!queue.active)
            return "No player queue is running";

        String action = queue.mode == Mode.STALK ? "Stopped stalking " : "Stopped dodging ";
        String name = queue.target;
        queue.clear(true);
        return action + name;
    }

    private void clear(boolean stopNavigation) {
        active = false;
        target = "";
        missingModeNotified = false;
        lastRequeueLevel = null;
        setRegistered(false);
        if (stopNavigation)
            Navigator.stop();
    }

    private void setRegistered(boolean state) {
        if (registered == state || Client.eventManager == null)
            return;

        registered = state;
        if (state)
            Client.eventManager.register(this);
        else
            Client.eventManager.unregister(this);
    }

    @Override
    public void onTick(TickEvent event) {
        if (active)
            evaluate();
    }

    private void evaluate() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null)
            return;

        Player entity = findEntity();
        boolean inTab = isInTab();

        if (mode == Mode.STALK) {
            if (entity != null) {
                if (!Navigator.isStalking(target))
                    Navigator.stalk(target);
                return;
            }

            Navigator.stop();
            if (!inTab)
                requeue();
            return;
        }

        Navigator.stop();
        if (entity != null || inTab)
            requeue();
    }

    private Player findEntity() {
        if (mc.level == null)
            return null;

        for (Player player : mc.level.players()) {
            if (player != mc.player && player.getName().getString().equalsIgnoreCase(target))
                return player;
        }
        return null;
    }

    private boolean isInTab() {
        if (mc.getConnection() == null)
            return false;

        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (info == null || info.getProfile() == null)
                continue;
            if (target.equalsIgnoreCase(info.getProfile().name()))
                return true;
        }
        return false;
    }

    private void requeue() {
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null)
            return;

        long now = System.nanoTime();
        if (lastRequeueLevel == mc.level && lastRequeueNanos != 0L)
            return;
        if (now - lastRequeueNanos < REQUEUE_COOLDOWN_NANOS)
            return;

        String gameMode = Rewound.detectQueueMode();
        if (gameMode == null || gameMode.isBlank()) {
            if (!missingModeNotified) {
                missingModeNotified = true;
                notify("Could not detect the current gamemode; set Rewound's Game Mode or enable Rewound.");
            }
            return;
        }

        missingModeNotified = false;
        lastRequeueNanos = now;
        lastRequeueLevel = mc.level;
        player.connection.sendCommand("play " + gameMode);
        notify((mode == Mode.STALK ? "Target absent - requeueing " : "Target present - leaving ")
                + gameMode);
    }

    private void notify(String message) {
        if (Client.notificationManager != null)
            Client.notificationManager.addNotification(mode == Mode.STALK ? "Stalk" : "Dodge", message);
    }
}
