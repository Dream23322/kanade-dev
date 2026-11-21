package com.instrumentalist.krs.hacks.features.player.phase;

import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.features.player.phase.features.AAC4Phase;
import com.instrumentalist.krs.hacks.features.player.phase.features.HypixelPhase;
import com.instrumentalist.krs.hacks.features.player.phase.features.IntavePhase;
import com.instrumentalist.krs.hacks.features.player.phase.features.NCPPhase;
import com.instrumentalist.krs.hacks.features.player.phase.features.VanillaPhase;
import com.instrumentalist.krs.hacks.features.player.phase.features.WatchdogLegacyPhase;
import com.instrumentalist.krs.utils.value.ListValue;

import java.util.Locale;

public final class PhaseModeManager {

    private final ListValue phaseMode;
    public PhaseEvent currentMode;

    public PhaseModeManager(ListValue phaseMode) {
        this.phaseMode = phaseMode;
        updateCurrentMode();
    }

    private void updateCurrentMode() {
        currentMode = switch (phaseMode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla" -> new VanillaPhase();
            case "ncp" -> new NCPPhase();
            case "aac 4" -> new AAC4Phase();
            case "hypixel" -> new HypixelPhase();
            case "watchdog legacy" -> new WatchdogLegacyPhase();
            case "intave" -> new IntavePhase();
            default -> null;
        };
    }

    private boolean isCurrent(PhaseEvent mode) {
        return mode != null && phaseMode.get().equals(mode.getName());
    }

    /** Rebuilds the mode when the setting changed and lets the new mode initialise itself. */
    private PhaseEvent switchMode() {
        PhaseEvent mode = currentMode;
        if (isCurrent(mode))
            return mode;

        if (mode != null)
            mode.onDisable();

        updateCurrentMode();

        if (currentMode != null)
            currentMode.onEnable();

        return currentMode;
    }

    public void onEnable() {
        PhaseEvent mode = currentMode;
        if (isCurrent(mode)) {
            mode.onEnable();
            return;
        }

        switchMode();
    }

    public void onDisable() {
        PhaseEvent mode = currentMode;
        if (mode != null)
            mode.onDisable();
    }

    public void onUpdate(UpdateEvent event) {
        PhaseEvent mode = switchMode();
        if (mode != null)
            mode.onUpdate(event);
    }

    public void onMotion(MotionEvent event) {
        PhaseEvent mode = switchMode();
        if (mode != null)
            mode.onMotion(event);
    }

    public void onBlock(BlockEvent event) {
        PhaseEvent mode = switchMode();
        if (mode != null)
            mode.onBlock(event);
    }

    public void onReceivedPacket(ReceivedPacketEvent event) {
        PhaseEvent mode = switchMode();
        if (mode != null)
            mode.onReceivedPacket(event);
    }

    public void onWorld(WorldEvent event) {
        PhaseEvent mode = switchMode();
        if (mode != null)
            mode.onWorld(event);
    }
}
