package com.instrumentalist.krs.hacks.features.movement.speed;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.features.movement.speed.features.*;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.blaze3d.platform.InputConstants;
import com.instrumentalist.krs.utils.input.Keys;

public class SpeedModule extends Module {

    @Override
    public String description() {
        return "Moves you faster than normal";
    }

    public SpeedModule() {
        super("Speed", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final ListValue speedMode = new ListValue(
            "Speed Mode",
            new String[]{"Vanilla", "Smooth Vanilla", "Legit Hop", "YPort", "Verus-b3882", "HypixelBHop", "HypixelLowHop", "Spartan-4.0.4.3", "Spartan-4.0.4.3-FastFall", "CubeCraft-2024-06-30", "Vulcan-2.8.6", "Vulcan-2.8.8", "Vulcan-2.8.6-Ground", "Grim-2.3.73", "NCP-2024-08-31", "Intave-14", "Intave-14-Fast", "HylexLowHop", "HylexGround", "BlocksMC", "Matrix-7", "Rewound", "Modern MMC", "5b5t", "Boost", "Flag Boost", "Miniblox", "Watchdog Legacy"},
            "Vanilla"
    ).describe("Speed bypass to use.");

    private static final SpeedModeManager speedModeManager = new SpeedModeManager(speedMode);
    private static boolean paused;

    public static boolean isPaused() {
        return paused;
    }

    /** Suspends the active mode without touching movement, used while looting. */
    public static void setPaused(boolean state) {
        if (paused == state)
            return;

        paused = state;
        if (state) {
            TimerUtil.reset();
            onDisableFunctions(false);
        } else {
            speedModeManager.reload();
            onEnableFunctions();
        }
    }

    private static boolean isMode(String mode) {
        return speedMode.get().equalsIgnoreCase(mode);
    }

    // Identify
    @Setting
    public static final FloatValue vanillaSpeed = new FloatValue("Speed", 1f, 0.1f, 10f, () -> speedMode.get().equalsIgnoreCase("vanilla") || speedMode.get().equalsIgnoreCase("smooth vanilla") || speedMode.get().equalsIgnoreCase("flag boost"))
            .describe("Speed multiplier for the vanilla modes.");

    // Vanilla
    @Setting
    public static final BooleanValue vanillaAutoBHop = new BooleanValue("Auto BHop", true, () -> speedMode.get().equalsIgnoreCase("vanilla"))
            .describe("Jumps automatically while speeding.");

    // YPort
    @Setting
    public static final FloatValue yportSpeed = new FloatValue("YPort Speed", 0.4f, 0.1f, 1f, () -> isMode("yport"))
            .describe("Speed used right after each YPort jump.");

    // HypixelBHop
    @Setting
    public static final BooleanValue hypixelBHopHorizontal = new BooleanValue("Hypixel BHop Horizontal", true, () -> isMode("hypixelbhop"))
            .describe("Adds a small horizontal acceleration while airborne.");

    @Setting
    public static final BooleanValue hypixelBHopVertical = new BooleanValue("Hypixel BHop Vertical", true, () -> isMode("hypixelbhop"))
            .describe("Slows the start of the fall slightly.");

    // HypixelLowHop
    @Setting
    public static final BooleanValue hypixelLowHopGlide = new BooleanValue("Hypixel Low Hop Glide", false, () -> isMode("hypixellowhop"))
            .describe("Holds your height briefly when near the ground.");

    // CubeCraft
    @Setting
    public static final FloatValue cubeCraftSpeed = new FloatValue("CubeCraft Speed", 0.5f, 0.1f, 5f, () -> isMode("cubecraft-2024-06-30"))
            .describe("Strafe speed applied after taking damage.");

    @Setting
    public static final IntValue cubeCraftReboostTicks = new IntValue("CubeCraft Reboost Ticks", 30, 10, 50, "t", () -> isMode("cubecraft-2024-06-30"))
            .describe("Ticks between damage packet sequences.");

    // Grim
    @Setting
    public static final FloatValue grimBoostSpeed = new FloatValue("Grim Boost Speed", 0.08f, 0.01f, 0.08f, "b/t", () -> isMode("grim-2.3.73"))
            .describe("Speed gained per nearby entity.");

    @Setting
    public static final FloatValue grimShrinkBox = new FloatValue("Grim Shrink Box", 0.5f, 0.1f, 2f, () -> isMode("grim-2.3.73"))
            .describe("How close an entity must be to count.");

    // NCP
    @Setting
    public static final BooleanValue ncpTimer = new BooleanValue("NCP Timer", true, () -> isMode("ncp-2024-08-31"))
            .describe("Runs the game a little faster while the mode is active.");

    @Setting
    public static final BooleanValue ncpLowHop = new BooleanValue("NCP Low Hop", true, () -> isMode("ncp-2024-08-31"))
            .describe("Uses a lower jump.");

    @Setting
    public static final BooleanValue ncpAirStrafe = new BooleanValue("NCP Air Strafe", true, () -> isMode("ncp-2024-08-31"))
            .describe("Lets you steer in the air.");

    // Intave
    @Setting
    public static final FloatValue intaveStrafeStrength = new FloatValue("Intave Strafe Strength", 0.27f, 0.01f, 0.27f, () -> isMode("intave-14"))
            .describe("How strongly the strafe is blended in.");

    @Setting
    public static final BooleanValue intaveFastTimer = new BooleanValue("Intave Fast Timer", true, () -> isMode("intave-14-fast"))
            .describe("Runs the game a tiny bit faster.");

    // BlocksMC
    @Setting
    public static final BooleanValue blocksMCRoundYaw = new BooleanValue("BlocksMC Round Yaw", false, () -> isMode("blocksmc"))
            .describe("Rounds the strafe yaw to 45 degree steps.");

    // Rewound
    @Setting
    public static final BooleanValue rewoundTimer = new BooleanValue("Rewound Timer", true, () -> speedMode.get().equalsIgnoreCase("rewound"))
            .describe("Uses a slight timer boost while speeding.");

    public static void onEnableFunctions() {
        if (speedModeManager.currentMode instanceof SpeedRewound speedRewound)
            speedRewound.reset();
    }

    public static void onDisableFunctions() {
        onDisableFunctions(true);
    }

    /**
     * Resets the active mode. Pausing passes {@code false} so a loot session can
     * suspend Speed without taking away the player's movement.
     */
    public static void onDisableFunctions(boolean stopMovement) {
        if (speedModeManager.currentMode instanceof VanillaSpeed) {
            if (stopMovement && mc.player != null)
                MovementUtil.stopMoving();
            VanillaSpeed.cancelledOnce = false;
        } else if (speedModeManager.currentMode instanceof FlagBoostSpeed) {
            FlagBoostSpeed.tick1 = 0;
            if (stopMovement && mc.player != null)
                MovementUtil.stopXZ();
        } else if (speedModeManager.currentMode instanceof MinibloxSpeed) {
            if (stopMovement && mc.player != null)
                MovementUtil.stopMoving();
        } else if (speedModeManager.currentMode instanceof SpartanV4043FastFallSpeed || speedModeManager.currentMode instanceof CubeCraft20240630Speed) {
            if (stopMovement && mc.player != null)
                MovementUtil.stopMoving();
            TimerUtil.reset();
        } else if (speedModeManager.currentMode instanceof VerusB3882Speed
                || speedModeManager.currentMode instanceof NCP2024Speed
                || speedModeManager.currentMode instanceof Intave14FastSpeed) {
            TimerUtil.reset();
        } else if (speedModeManager.currentMode instanceof BlocksMCSpeed) {
            if (stopMovement && mc.player != null)
                MovementUtil.stopMoving();
        } else if (speedModeManager.currentMode instanceof FiveBFiveTSpeed) {
            if (stopMovement && mc.player != null)
                MovementUtil.stopMoving();
            TimerUtil.reset();
        } else if (speedModeManager.currentMode instanceof SpeedRewound) {
            if (stopMovement && mc.player != null)
                MovementUtil.stopMoving();
            TimerUtil.reset();
        }

        if (mc.player != null) {
            mc.options.keyJump.setDown(InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyJump.saveString()).getValue()));
        }
    }

    @Override
    public String tag() {
        return speedMode.get();
    }

    @Override
    public void onDisable() {
        onDisableFunctions();
    }

    @Override
    public void onEnable() {
        speedModeManager.reload();
        onEnableFunctions();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        speedModeManager.onUpdate(event);
    }

    @Override
    public void onMotion(MotionEvent event) {
        speedModeManager.onMotion(event);
    }

    @Override
    public void onTick(TickEvent event) {
        speedModeManager.onTick(event);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        speedModeManager.onSendPacket(event);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        speedModeManager.onReceivedPacket(event);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        speedModeManager.onPlayerMove(event);
    }
}
