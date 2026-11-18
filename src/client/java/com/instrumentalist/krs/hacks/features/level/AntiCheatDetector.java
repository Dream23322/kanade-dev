package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.anticheat.AntiCheatEngine;
import com.instrumentalist.krs.hacks.features.nulling.PluginsDetector;
import com.instrumentalist.krs.hacks.features.player.HackerDetector;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;

import java.util.List;

public class AntiCheatDetector extends Module {

    @Setting
    public static final BooleanValue killAuraCheck = new BooleanValue(
            "KillAura Check",
            true
    ).describe("Flags sustained sub-cooldown swings and hits that land while facing away.");

    @Setting
    public static final BooleanValue autoBlockCheck = new BooleanValue(
            "AutoBlock Check",
            true
    ).describe("Flags sword guards that stay up while the player attacks.");

    @Setting
    public static final BooleanValue noSlowCheck = new BooleanValue(
            "NoSlow Check",
            true
    ).describe("Flags sprinting while using a sword, shield, food or potion.");

    @Setting
    public static final BooleanValue scaffoldCheck = new BooleanValue(
            "Scaffold Check",
            true
    ).describe("Flags repeated downward aim snaps while bridging.");

    @Setting
    public static final FloatValue sensitivity = new FloatValue(
            "Sensitivity",
            1f,
            0.5f,
            2f,
            "x"
    ).describe("Higher values flag sooner; lower values need clearer evidence.");

    @Setting
    public static final BooleanValue chatAlerts = new BooleanValue(
            "Chat Alerts",
            true
    ).describe("Prints flags into the client chat.");

    @Setting
    public static final BooleanValue alertNotifications = new BooleanValue(
            "Alert Notifications",
            true
    ).describe("Shows flags as a notification card.");

    @Setting
    public static final BooleanValue alertSound = new BooleanValue(
            "Alert Sound",
            true
    ).describe("Plays a sound when a flag is raised.");

    private final AntiCheatEngine antiCheatEngine = new AntiCheatEngine();

    @Override
    public String description() {
        return "Detects the server anticheat and flags players that look like they are cheating";
    }

    public AntiCheatDetector() {
        super("Anti Cheat Detector", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, false);
    }

    public static boolean shouldNotify = false;

    private static String getAcStrings() {
        List<String> antiCheats = PluginsDetector.getAntiCheats();

        if (antiCheats == null) {
            if (!ModuleManager.plChecked)
                return "Checking...";
            else if (PluginsDetector.plugins == null || PluginsDetector.plugins.length == 0)
                return "No plugins found";
            else if (PluginsDetector.detectedAcs == null)
                return "No acs found";
        } else if (!antiCheats.isEmpty()) {
            return String.join(", ", antiCheats);
        }

        return "";
    }

    @Override
    public void onDisable() {
        shouldNotify = false;
        antiCheatEngine.reset();
    }

    @Override
    public void onEnable() {
        antiCheatEngine.reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        shouldNotify = false;
        antiCheatEngine.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null)
            return;

        antiCheatEngine.tick(mc.level, mc.player);
    }

    @Override
    public void onAttack(AttackEvent event) {
        // The server never echoes your own swing packet, so feed the local swing for self tests.
        if (HackerDetector.checkSelf.get() && mc.player != null)
            AntiCheatEngine.recordSwing(mc.player.getId());
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) {
            shouldNotify = false;
            return;
        }

        if (shouldNotify && ModuleManager.plChecked) {
            Client.notificationManager.addNotification("Anti Cheat Detector", getAcStrings());
            shouldNotify = false;
        }
    }
}
