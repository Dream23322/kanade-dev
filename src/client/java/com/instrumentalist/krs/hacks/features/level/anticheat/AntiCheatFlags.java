package com.instrumentalist.krs.hacks.features.level.anticheat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.features.level.AntiCheatDetector;
import com.instrumentalist.krs.hacks.features.player.HackerDetector;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.NotificationManager.Severity;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Collects the signals sent by the individual anti cheat checks and turns them into a
 * single alert once the same player repeats a violation often enough inside the flag window.
 */
public final class AntiCheatFlags {

    public static final String AUTOBLOCK = "AutoBlock";
    public static final String NOSLOW = "NoSlow";
    public static final String KILLAURA = "KillAura";
    public static final String SCAFFOLD = "Scaffold";

    private static final long FLAG_WINDOW_MS = 5_000L;
    private static final long ALERT_COOLDOWN_MS = 5_000L;
    private static final long MIN_SIGNAL_GAP_MS = 250L;

    private static final Minecraft mc = Minecraft.getInstance();

    private static final Map<String, FlagData> flags = new HashMap<>();
    private static final Map<String, Long> alertCooldowns = new HashMap<>();
    private static final int MAX_DEBUG_ENTRIES = 32;
    private static final long DEBUG_TTL_MS = 60_000L;
    private static final Map<String, FlagDebug> debugEntries = new LinkedHashMap<>();

    /** Details shown in the chat hover tooltip for a flag line. */
    public record FlagDebug(String player, String check, int violations, int signals, int threshold,
                            long timeMs) {
    }

    private AntiCheatFlags() {
    }

    public static void receiveSignal(String playerName, String checkName) {
        receiveSignal(playerName, checkName, 0);
    }

    public static void receiveSignal(String playerName, String checkName, int violationLevel) {
        if (!isKnownCheck(checkName) || playerName == null || playerName.isEmpty())
            return;
        if (mc.player == null || mc.level == null)
            return;
        if (!HackerDetector.checkSelf.get() && playerName.equalsIgnoreCase(mc.player.getName().getString()))
            return;

        long now = System.currentTimeMillis();
        String key = getFlagKey(playerName, checkName);

        FlagData data = flags.computeIfAbsent(key, ignored -> new FlagData());
        if (now - data.lastSignalMs > FLAG_WINDOW_MS)
            data.count = 0;
        if (now - data.lastCountedMs < MIN_SIGNAL_GAP_MS)
            return;

        data.count++;
        data.lastCountedMs = now;
        data.lastSignalMs = now;

        int maxFlagCount = maxFlagsFor(checkName);
        long lastAlert = alertCooldowns.getOrDefault(key, 0L);

        if (data.count < maxFlagCount || now - lastAlert < ALERT_COOLDOWN_MS)
            return;

        alert(playerName, checkName, Math.max(violationLevel, data.count), data.count, maxFlagCount);
        alertCooldowns.put(key, now);
        flags.remove(key);
    }

    /** Drops expired flag entries and alert cooldowns; called once per client tick. */
    public static void tick() {
        long now = System.currentTimeMillis();
        flags.values().removeIf(data -> now - data.lastSignalMs > FLAG_WINDOW_MS);
        alertCooldowns.values().removeIf(time -> now - time > ALERT_COOLDOWN_MS);
        synchronized (debugEntries) {
            debugEntries.values().removeIf(debug -> now - debug.timeMs() > DEBUG_TTL_MS);
        }
    }

    public static void reset() {
        flags.clear();
        alertCooldowns.clear();
        synchronized (debugEntries) {
            debugEntries.clear();
        }
    }

    /** Debug details for a rendered flag line, or null when the message is not a flag. */
    public static FlagDebug debugFor(String message) {
        synchronized (debugEntries) {
            return debugEntries.get(message);
        }
    }

    private static void rememberDebug(String message, FlagDebug debug) {
        synchronized (debugEntries) {
            debugEntries.put(message, debug);
            while (debugEntries.size() > MAX_DEBUG_ENTRIES) {
                Iterator<String> iterator = debugEntries.keySet().iterator();
                if (!iterator.hasNext())
                    break;
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static void alert(String playerName, String checkName, int violationLevel, int signals, int threshold) {
        String message = playerName + " failed " + checkName
                + (violationLevel > 0 ? " [x" + violationLevel + "]" : "");
        rememberDebug(message, new FlagDebug(playerName, checkName, violationLevel, signals, threshold,
                System.currentTimeMillis()));

        if (AntiCheatDetector.chatAlerts.get())
            ChatUtil.printFlag(message);

        if (AntiCheatDetector.alertNotifications.get() && Client.notificationManager != null)
            Client.notificationManager.addChatNotification(message, Severity.DANGER);

        if (AntiCheatDetector.alertSound.get() && mc.player != null)
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3f, 1.0f);
    }

    /** Prints a sample flag through the normal chat and notification path, used by {@code .flag}. */
    public static void printSample() {
        String message = "diep_io failed KillAura [x4]";
        rememberDebug(message, new FlagDebug("diep_io", KILLAURA, 4, 4, maxFlagsFor(KILLAURA),
                System.currentTimeMillis()));
        ChatUtil.printFlag(message);

        if (Client.notificationManager != null)
            Client.notificationManager.addChatNotification(message, Severity.DANGER);
    }

    private static boolean isKnownCheck(String checkName) {
        return AUTOBLOCK.equals(checkName)
                || NOSLOW.equals(checkName)
                || KILLAURA.equals(checkName)
                || SCAFFOLD.equals(checkName);
    }

    private static int maxFlagsFor(String checkName) {
        int base;
        if (AUTOBLOCK.equals(checkName)) base = 5;
        else if (NOSLOW.equals(checkName)) base = 3;
        else if (KILLAURA.equals(checkName)) base = 4;
        else if (SCAFFOLD.equals(checkName)) base = 4;
        else base = 2;

        double sensitivity = AntiCheatDetector.sensitivity.get();
        return Math.max(2, (int) Math.round(base / Math.max(0.1, sensitivity)));
    }

    private static String getFlagKey(String playerName, String checkName) {
        return playerName.toLowerCase(Locale.ROOT) + ":" + checkName;
    }

    private static final class FlagData {
        private int count;
        private long lastSignalMs;
        private long lastCountedMs;
    }
}
