package com.instrumentalist.krs.hacks.features.movement.fly;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.features.movement.fly.features.*;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.input.Keys;

import net.minecraft.world.phys.Vec3;

public class FlyModule extends Module {

    @Override
    public String description() {
        return "Lets you fly";
    }

    public FlyModule() {
        super("Fly", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final ListValue flyMode = new ListValue(
            "Fly Mode",
            new String[]{"Vanilla", "Creative", "Airwalk", "Jetpack", "Float", "NCP Boost", "Watchdog Legacy", "Negative Packet", "2b2tJP Control Fast", "Modern MMC", "Vulcan Glide", "Verus Jetpack", "Matrix", "Grim 1.9-1.18.1", "Rewound", "Rewound Setback"},
            "Vanilla"
    ).describe("Fly bypass to use.");

    private static final FlyModeManager flyModeManager = new FlyModeManager(flyMode);

    @Setting
    public static final BooleanValue airViewBobbing = new BooleanValue("Air View Bobbing", false)
            .describe("Keeps view bobbing while flying.");

    // Vanilla
    @Setting
    public static final FloatValue vanillaHSpeed = new FloatValue("Horizontal Speed", 2f, 0.1f, 10f, () -> flyMode.get().equalsIgnoreCase("vanilla"))
            .describe("Horizontal speed in vanilla mode.");

    @Setting
    public static final FloatValue vanillaVSpeed = new FloatValue("Vertical Speed", 1.2f, 0.1f, 10f, () -> flyMode.get().equalsIgnoreCase("vanilla"))
            .describe("Vertical speed in vanilla mode.");

    // Matrix
    @Setting
    public static final ListValue matrixMode = new ListValue("Matrix Mode", new String[]{"Normal", "High", "Damage"}, "Normal", () -> flyMode.get().equalsIgnoreCase("matrix"))
            .describe("Variant used by the Matrix fly.");

    @Setting
    public static final BooleanValue matrixHighSelfDamage = new BooleanValue("Matrix High Self Damage", false, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("high"))
            .describe("Damages yourself to keep the high flight stable.");

    @Setting
    public static final FloatValue matrixHighHeight = new FloatValue("Matrix Height", 1f, 0.42f, 7f, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("high"))
            .describe("Height kept above the ground in Matrix high mode.");

    @Setting
    public static final FloatValue matrixDamageTimerSpeed = new FloatValue("Matrix Timer Speed", 30f, 1f, 100f, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Timer speed used by Matrix damage mode.");

    @Setting
    public static final FloatValue matrixDamageSpeed = new FloatValue("Matrix Speed", 0.07f, 0f, 1f, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Horizontal speed used by Matrix damage mode.");

    @Setting
    public static final BooleanValue matrixDamageNoSpeed = new BooleanValue("Matrix No Speed", false, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Stops Matrix damage mode from adding speed.");

    @Setting
    public static final BooleanValue matrixDamageDetectDamage = new BooleanValue("Matrix Detect Damage", true, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Detects when you take damage to refresh the flight.");

    @Setting
    public static final BooleanValue matrixDamageAutoDisable = new BooleanValue("Matrix Auto Disable", true, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Disables fly when the damage trick stops working.");

    @Setting
    public static final IntValue matrixDamageFlyTicks = new IntValue("Matrix Fly Ticks", 1000, 0, 1600, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage") && matrixDamageDetectDamage.get() && !matrixDamageAutoDisable.get())
            .describe("Ticks to fly before damaging yourself again.");

    @Setting
    public static final BooleanValue matrixDamageSelfDamage = new BooleanValue("Matrix Damage Self Damage", true, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Damages yourself to stay airborne.");

    @Setting
    public static final BooleanValue matrixDamageNewSelfDamage = new BooleanValue("Matrix New Self Damage", false, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Uses the newer self damage method.");

    @Setting
    public static final BooleanValue matrixDamagePacket = new BooleanValue("Matrix Packet", true, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("Sends an extra packet for the damage trick.");

    @Setting
    public static final ListValue matrixDamageMotionY = new ListValue("Matrix Motion Y", new String[]{"None", "Simple", "Multiply"}, "None", () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage"))
            .describe("How the vertical motion is modified.");

    @Setting
    public static final FloatValue matrixDamageMotion = new FloatValue("Matrix Motion", -0.01f, -0.3f, 0.3f, () -> flyMode.get().equalsIgnoreCase("matrix") && matrixMode.get().equalsIgnoreCase("damage") && !matrixDamageMotionY.get().equalsIgnoreCase("none"))
            .describe("Vertical motion value that is applied.");

    // Grim 1.9-1.18.1
    @Setting
    public static final FloatValue grimSpeed = new FloatValue("Grim Speed", 0.275f, 0f, 0.32f, () -> flyMode.get().equalsIgnoreCase("grim 1.9-1.18.1"))
            .describe("Horizontal speed used by the Grim bypass.");

    @Setting
    public static final FloatValue grimTimerSpeed = new FloatValue("Grim Timer Speed", 1f, 0.1f, 10f, "x", () -> flyMode.get().equalsIgnoreCase("grim 1.9-1.18.1"))
            .describe("Timer speed used by the Grim bypass.");

    @Setting
    public static final BooleanValue grimSlowFall = new BooleanValue("Grim Slow Fall", false, () -> flyMode.get().equalsIgnoreCase("grim 1.9-1.18.1"))
            .describe("Slows your fall while flying with Grim.");

    // Rewound
    @Setting
    public static final FloatValue rewoundSpeed = new FloatValue("Rewound Speed", 4.2f, 3.5f, 5.5f, () -> flyMode.get().equalsIgnoreCase("rewound"))
            .describe("Horizontal speed used by the Rewound fly.");

    @Setting
    public static final FloatValue rewoundDecay = new FloatValue("Rewound Decay", 159f, 159f, 600f, () -> flyMode.get().equalsIgnoreCase("rewound"))
            .describe("How slowly the Rewound fly speed decays.");

    @Setting
    public static final FloatValue rewoundBoostTimer = new FloatValue("Rewound Boost Timer", 2f, 1f, 3.5f, "x", () -> flyMode.get().equalsIgnoreCase("rewound"))
            .describe("Timer speed right after the boost.");

    @Setting
    public static final IntValue rewoundBoostTicks = new IntValue("Rewound Boost Ticks", 15, 1, 40, "ticks", () -> flyMode.get().equalsIgnoreCase("rewound"))
            .describe("Ticks the boost timer takes to fade out.");

    @Setting
    public static final BooleanValue rewoundTimer = new BooleanValue("Rewound Timer", false, () -> flyMode.get().equalsIgnoreCase("rewound"))
            .describe("Keeps a slight timer boost while flying.");

    // Rewound Setback
    @Setting
    public static final FloatValue rewoundSetbackSpeed = new FloatValue("Rewound Setback Speed", 1f, 0.1f, 5f, () -> flyMode.get().equalsIgnoreCase("rewound setback"))
            .describe("Speed used once the server sets you back.");

    @Setting
    public static final IntValue rewoundSetbackDuration = new IntValue("Rewound Setback Duration", 3000, 500, 10000, "ms", () -> flyMode.get().equalsIgnoreCase("rewound setback"))
            .describe("How long you fly after the setback.");

    @Setting
    public static final FloatValue rewoundSetbackTimer = new FloatValue("Rewound Setback Timer", 2f, 1f, 5f, "x", () -> flyMode.get().equalsIgnoreCase("rewound setback"))
            .describe("Timer speed used while flying after the setback.");

    // Verus Jetpack
    @Setting
    public static final BooleanValue verusJetpackJumpKeyOnly = new BooleanValue("Verus Jump Key Only", false, () -> flyMode.get().equalsIgnoreCase("verus jetpack"))
            .describe("Only lifts you while the jump key is held.");

    // NCP Boost
    @Setting
    public static final BooleanValue ncpDamageBoost = new BooleanValue("NCP Damage Boost", true, () -> flyMode.get().equalsIgnoreCase("ncp boost"))
            .describe("Uses the NCP damage boost.");

    public static void onEnableFunctions() {
        if (flyModeManager.currentMode instanceof MatrixFly matrixFly) {
            matrixFly.reset();
        } else if (flyModeManager.currentMode instanceof Grim19To1181Fly grim19To1181Fly) {
            grim19To1181Fly.reset();
        } else if (flyModeManager.currentMode instanceof WatchdogLegacyFly) {
            WatchdogLegacyFly.enable();
        } else if (flyModeManager.currentMode instanceof FlyRewound flyRewound) {
            flyRewound.reset();
        } else if (flyModeManager.currentMode instanceof FlyRewoundSetback flyRewoundSetback) {
            flyRewoundSetback.reset();
        }
    }

    public static void onDisableFunctions() {
        if (flyModeManager.currentMode instanceof VanillaFly) {
            if (mc.player != null) {
                MovementUtil.stopMoving();
                MovementUtil.setVelocityY(0.0);
            }
        } else if (flyModeManager.currentMode instanceof TwoBTwoTJPControlFastFly) {
            if (mc.player != null) {
                MovementUtil.stopMoving();
                MovementUtil.setVelocityY(0.0);
            }
            TimerUtil.reset();
            TwoBTwoTJPControlFastFly.tick = 0;
            TwoBTwoTJPControlFastFly.phasing = false;
            TwoBTwoTJPControlFastFly.boostSpeed = 1f;
        } else if (flyModeManager.currentMode instanceof ModernMMCFly) {
            MovementUtil.stopMoving();
            MovementUtil.setVelocityY(0.0);
            ModernMMCFly.tick = 0;
        } else if (flyModeManager.currentMode instanceof CreativeFly) {
            if (mc.player != null) {
                if (mc.player.isSpectator() || !mc.player.isCreative())
                    mc.player.getAbilities().mayfly = false;
                if (!mc.player.isCreative())
                    mc.player.getAbilities().flying = false;
            }
        } else if (flyModeManager.currentMode instanceof NegativePacketFly) {
            NegativePacketFly.motion = Vec3.ZERO;
        } else if (flyModeManager.currentMode instanceof MatrixFly matrixFly) {
            matrixFly.disable();
        } else if (flyModeManager.currentMode instanceof Grim19To1181Fly grim19To1181Fly) {
            grim19To1181Fly.disable();
        } else if (flyModeManager.currentMode instanceof WatchdogLegacyFly) {
            WatchdogLegacyFly.disable();
        } else if (flyModeManager.currentMode instanceof VulcanGlideFly) {
            VulcanGlideFly.ticks = 0;
        } else if (flyModeManager.currentMode instanceof NCPBoostFly) {
            NCPBoostFly.moveSpeed = 0;
            if (NCPBoostFly.timer) {
                TimerUtil.reset();
                NCPBoostFly.timer = false;
            }
            if (mc.player != null) {
                MovementUtil.stopMoving();
            }
        } else if (flyModeManager.currentMode instanceof FlyRewound flyRewound) {
            flyRewound.disable();
        } else if (flyModeManager.currentMode instanceof FlyRewoundSetback flyRewoundSetback) {
            flyRewoundSetback.disable();
        }
    }

    @Override
    public String tag() {
        return flyMode.get();
    }

    @Override
    public void onDisable() {
        onDisableFunctions();
    }

    @Override
    public void onEnable() {
        flyModeManager.syncMode();
        onEnableFunctions();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        flyModeManager.onUpdate(event);
    }

    @Override
    public void onMotion(MotionEvent event) {
        flyModeManager.onMotion(event);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        flyModeManager.onSendPacket(event);
    }

    @Override
    public void onTick(TickEvent event) {
        flyModeManager.onTick(event);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        flyModeManager.onReceivedPacket(event);
    }

    @Override
    public void onBlock(BlockEvent event) {
        flyModeManager.onBlock(event);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        flyModeManager.onPlayerMove(event);
    }
}
