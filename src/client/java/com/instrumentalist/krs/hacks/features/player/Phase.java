package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseModeManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;

public class Phase extends Module {

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "NCP", "AAC 4", "Hypixel", "Watchdog Legacy", "Intave"}, "NCP")
            .describe("Bypass used for phasing.");

    private static final PhaseModeManager phaseModeManager = new PhaseModeManager(mode);

    // NCP
    @Setting
    public static final FloatValue ncpStep = new FloatValue("NCP Step", 0.045f, 0.01f, 0.2f, () -> isMode("ncp"))
            .describe("Distance per tick while stepping into the block, kept under the NCP horizontal limit.");

    @Setting
    public static final IntValue ncpWarmup = new IntValue("NCP Warmup", 2, 1, 6, "ticks", () -> isMode("ncp"))
            .describe("Sub-threshold steps taken inside the block before crossing.");

    @Setting
    public static final FloatValue ncpCrossDistance = new FloatValue("NCP Cross Distance", 4.0f, 0.5f, 8.0f, () -> isMode("ncp"))
            .describe("How far past the wall the first collision-free position is searched.");

    @Setting
    public static final FloatValue ncpTimer = new FloatValue("NCP Timer", 0.3f, 0.1f, 1.0f, "x", () -> isMode("ncp"))
            .describe("Client timer speed while phasing, which spaces the move packets out.");

    @Setting
    public static final BooleanValue ncpKeepTimer = new BooleanValue("NCP Keep Timer", false, () -> isMode("ncp"))
            .describe("Leaves the timer slowed after the phase finishes.");

    @Setting
    public static final IntValue ncpCooldown = new IntValue("NCP Cooldown", 8, 1, 40, "ticks", () -> isMode("ncp"))
            .describe("Ticks to wait after crossing or after a setback.");

    @Setting
    public static final ListValue ncpDirection = new ListValue("NCP Direction", new String[]{"Movement", "Camera"}, "Movement", () -> isMode("ncp"))
            .describe("Direction source used for the phase.");

    private static boolean isMode(String name) {
        return mode.get().equalsIgnoreCase(name);
    }

    /** Lets a mode that only works for a moment switch the module off (AAC 4, Watchdog Legacy). */
    public static void requestDisable() {
        Phase phase = ModuleManager.getModule(Phase.class);
        if (phase != null && phase.tempEnabled)
            phase.toggle();
    }

    @Override
    public String description() {
        return "Lets you walk through blocks";
    }

    public Phase() {
        super("Phase", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onEnable() {
        phaseModeManager.onEnable();
    }

    @Override
    public void onDisable() {
        phaseModeManager.onDisable();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        phaseModeManager.onUpdate(event);
    }

    @Override
    public void onMotion(MotionEvent event) {
        phaseModeManager.onMotion(event);
    }

    @Override
    public void onBlock(BlockEvent event) {
        phaseModeManager.onBlock(event);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        phaseModeManager.onReceivedPacket(event);
    }

    @Override
    public void onWorld(WorldEvent event) {
        phaseModeManager.onWorld(event);
    }
}
