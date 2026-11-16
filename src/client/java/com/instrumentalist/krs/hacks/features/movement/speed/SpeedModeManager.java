package com.instrumentalist.krs.hacks.features.movement.speed;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.features.*;
import com.instrumentalist.krs.utils.value.ListValue;

import java.util.Locale;

public class SpeedModeManager {

    private final ListValue speedMode;
    public SpeedEvent currentMode;

    public SpeedModeManager(ListValue speedMode) {
        this.speedMode = speedMode;
        updateCurrentMode();
    }

    private void updateCurrentMode() {
        switch (speedMode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                currentMode = new VanillaSpeed();
                break;
            case "smooth vanilla":
                currentMode = new SmoothVanillaSpeed();
                break;
            case "legit hop":
                currentMode = new LegitHopSpeed();
                break;
            case "yport":
                currentMode = new YPortSpeed();
                break;
            case "verus-b3882":
                currentMode = new VerusB3882Speed();
                break;
            case "hypixelbhop":
                currentMode = new HypixelBHopSpeed();
                break;
            case "hypixellowhop":
                currentMode = new HypixelLowHopSpeed();
                break;
            case "spartan-4.0.4.3":
                currentMode = new SpartanV4043Speed();
                break;
            case "spartan-4.0.4.3-fastfall":
                currentMode = new SpartanV4043FastFallSpeed();
                break;
            case "cubecraft-2024-06-30":
                currentMode = new CubeCraft20240630Speed();
                break;
            case "vulcan-2.8.6":
                currentMode = new Vulcan286Speed();
                break;
            case "vulcan-2.8.8":
                currentMode = new Vulcan288Speed();
                break;
            case "vulcan-2.8.6-ground":
                currentMode = new Vulcan286GroundSpeed();
                break;
            case "grim-2.3.73":
                currentMode = new Grim2373Speed();
                break;
            case "ncp-2024-08-31":
                currentMode = new NCP2024Speed();
                break;
            case "intave-14":
                currentMode = new Intave14Speed();
                break;
            case "intave-14-fast":
                currentMode = new Intave14FastSpeed();
                break;
            case "hylexlowhop":
                currentMode = new HylexLowHopSpeed();
                break;
            case "hylexground":
                currentMode = new HylexGroundSpeed();
                break;
            case "blocksmc":
                currentMode = new BlocksMCSpeed();
                break;
            case "matrix-7":
                currentMode = new Matrix7Speed();
                break;
            case "rewound":
                currentMode = new SpeedRewound();
                break;
            case "modern mmc":
                currentMode = new ModernMMCSpeed();
                break;
            case "5b5t":
                currentMode = new FiveBFiveTSpeed();
                break;
            case "boost":
                currentMode = new BoostSpeed();
                break;
            case "flag boost":
                currentMode = new FlagBoostSpeed();
                break;
            case "miniblox":
                currentMode = new MinibloxSpeed();
                break;
            case "watchdog legacy":
                currentMode = new WatchdogLegacySpeed();
                break;
            default:
                currentMode = null;
        }
    }

    public void syncMode() {
        SpeedEvent mode = currentMode;
        if (mode == null || !speedMode.get().equals(mode.getName()))
            updateCurrentMode();
    }

    /**
     * Rebuild the selected mode so every stateful counter starts from scratch.
     */
    public void reload() {
        updateCurrentMode();
    }

    public void onUpdate(UpdateEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onUpdate(event);
    }

    public void onMotion(MotionEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onMotion(event);
    }

    public void onTick(TickEvent event) {
        SpeedEvent mode = currentMode;
        if (mode == null || !speedMode.get().equals(mode.getName())) {
            SpeedModule.onDisableFunctions();
            updateCurrentMode();
            SpeedModule.onEnableFunctions();
            mode = currentMode;
        }

        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onTick(event);
    }

    public void onSendPacket(SendPacketEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onSendPacket(event);
    }

    public void onReceivedPacket(ReceivedPacketEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onReceivedPacket(event);
    }

    public void onPlayerMove(PlayerMoveEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onPlayerMove(event);
    }
}
