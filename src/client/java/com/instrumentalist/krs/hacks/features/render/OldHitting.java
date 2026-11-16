package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.mixin.oringo.ILegacySwing;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class OldHitting extends Module {

    @Setting
    public static final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Push", "Dash", "Swang", "Swank", "Swonk", "Ethr", "Spin"}, "Vanilla")
            .describe("Swing animation played when you hit.");

    @Setting
    private static final ListValue thirdPersonMode = new ListValue("Third Person Mode", new String[]{"Vanilla", "Legacy Vanilla"}, "Legacy Vanilla")
            .describe("Animation used while in third person.");

    @Setting
    public static final FloatValue itemSize = new FloatValue("Item Size", 0f, -2f, 2f)
            .describe("Scales the held item while swinging.");

    @Setting
    public static final FloatValue itemPositionX = new FloatValue("Item Position X", 0f, -2f, 2f)
            .describe("Horizontal offset of the held item.");

    @Setting
    public static final FloatValue itemPositionY = new FloatValue("Item Position Y", 0f, -2f, 2f)
            .describe("Vertical offset of the held item.");

    @Setting
    public static final FloatValue itemPositionZ = new FloatValue("Item Position Z", 0f, -2f, 2f)
            .describe("Forward offset of the held item.");

    @Setting
    private static final BooleanValue noEquipAnimation = new BooleanValue("No Equip Animation", true)
            .describe("Skips the equip animation when switching items.");

    @Setting
    private static final BooleanValue smoothBlock = new BooleanValue("Smooth Block", true)
            .describe("Fades the manual block pose in and out instead of snapping between poses.");

    @Setting
    private static final FloatValue blockSmoothSpeed = new FloatValue("Block Smooth Speed", 0.5f, 0.1f, 1f, smoothBlock::get)
            .describe("How quickly the block pose fades in and out.");

    private static final int BLOCK_RELEASE_GRACE_TICKS = 2;

    private static boolean canBlock = false;

    private static boolean blocking = false;

    private static boolean blockManual = false;

    private static int blockGraceTicks;

    private static float blockWeight;

    private static long blockWeightNanos;

    @Override
    public String description() {
        return "Replaces the modern swing animation with old style hitting";
    }

    public OldHitting() {
        super("Old Hitting", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    private boolean noBlockOnSelectedBlock() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return true;

        var hitResult = player.pick(5.0, 0.0f, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            var blockPos = ((BlockHitResult) hitResult).getBlockPos();
            Block block = level.getBlockState(blockPos).getBlock();
            if ((!ModuleManager.getModuleState(KillAura.class) || !KillAura.isBlocking)
                    && (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock || block instanceof FurnaceBlock
                    || block instanceof CraftingTableBlock || block instanceof CrafterBlock || block instanceof SmokerBlock || block instanceof BlastFurnaceBlock
                    || block instanceof CartographyTableBlock || block instanceof AnvilBlock || block instanceof BellBlock || block instanceof BeaconBlock
                    || block instanceof DragonEggBlock || block instanceof LeverBlock || block instanceof EnchantingTableBlock || block instanceof ButtonBlock
                    || block instanceof GrindstoneBlock || block instanceof LoomBlock || block instanceof NoteBlock || block instanceof FenceGateBlock
                    || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof StonecutterBlock || block instanceof StandingSignBlock
                    || block instanceof WallSignBlock || block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock || block instanceof RepeaterBlock
                    || block instanceof ComparatorBlock || block instanceof DispenserBlock || block instanceof JigsawBlock || block instanceof CommandBlock
                    || block instanceof StructureBlock || block instanceof HopperBlock || block instanceof BedBlock || block instanceof BarrelBlock
                    || block instanceof CakeBlock || block instanceof CandleCakeBlock || block instanceof BrewingStandBlock || block instanceof DaylightDetectorBlock))
                return false;
        }
        return true;
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        canBlock = false;
        resetBlockPose();
    }

    @Override
    public void onEnable() {
        resetBlockPose();
    }

    @Override
    public void onWorld(WorldEvent event) {
        canBlock = false;
        resetBlockPose();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!smoothBlock.get()) {
            blocking = rawBlocking();
            blockGraceTicks = 0;
            return;
        }

        if (rawBlocking()) {
            if (!blocking)
                blockManual = isManualBlockRequested();
            blocking = true;
            blockGraceTicks = BLOCK_RELEASE_GRACE_TICKS;
        } else if (blockGraceTicks > 0) {
            blockGraceTicks--;
        } else {
            blocking = false;
            blockManual = false;
        }
    }

    @Override
    public void onMouseClick(MouseClickEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (event.button == Keys.MOUSE_BUTTON_RIGHT) {
            if (event.action == Keys.PRESS && noBlockOnSelectedBlock())
                canBlock = true;

            if (event.action == Keys.RELEASE)
                canBlock = false;
        }
    }

    public static void thirdPersonAnimation(ModelPart arm, HumanoidArm renderArm) {
        int direction = renderArm == HumanoidArm.RIGHT ? 1 : -1;
        switch (thirdPersonMode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla" -> {
                arm.xRot = arm.xRot * 0.5f - 0.9424779f;
                arm.yRot = -0.5f * direction;
                arm.zRot = 0.1f * direction;
            }

            case "legacy vanilla" -> {
                arm.xRot = arm.xRot * 0.5f - 0.9424779f;
                arm.yRot = 0.0f;
                arm.zRot = 0.0f;
            }
        }
    }

    public static boolean shouldSkipEquipAnimation() {
        return noEquipAnimation.get() && ModuleManager.getModuleState(OldHitting.class);
    }

    public static boolean shouldUseLegacyVanillaThirdPerson() {
        return thirdPersonMode.get().equalsIgnoreCase("legacy vanilla") && shouldBlock();
    }

    public static boolean shouldBlock() {
        if (!ModuleManager.getModuleState(OldHitting.class)) {
            resetBlockPose();
            return false;
        }

        if (!smoothBlock.get())
            return rawBlocking();

        updateBlockState();
        return blocking;
    }

    public static float blockPoseWeight() {
        if (!ModuleManager.getModuleState(OldHitting.class) || mc.player == null || mc.level == null) {
            resetBlockPose();
            return 0f;
        }

        if (!smoothBlock.get()) {
            blockWeightNanos = 0L;
            blockWeight = rawBlocking() ? 1f : 0f;
            return blockWeight;
        }

        updateBlockState();

        // Only the pose started by the user holding right click gets the fade; automated blocks snap into place.
        if (!blockManual) {
            blockWeightNanos = 0L;
            blockWeight = blocking ? 1f : 0f;
            return blockWeight;
        }

        float target = blocking ? 1f : 0f;
        long now = System.nanoTime();

        if (blockWeightNanos == 0L)
            blockWeightNanos = now;

        float deltaSeconds = Math.min((now - blockWeightNanos) / 1.0E9F, 0.25F);
        blockWeightNanos = now;

        if (deltaSeconds > 0f) {
            float speed = Mth.clamp(blockSmoothSpeed.get(), 0.1F, 1.0F);
            float tau = 0.03F + (1.0F - speed) * 0.2F;
            blockWeight += (target - blockWeight) * (1.0F - (float) Math.exp(-deltaSeconds / tau));
        }

        if (target == 0f && blockWeight < 0.01F)
            blockWeight = 0f;
        else if (target == 1f && blockWeight > 0.99F)
            blockWeight = 1f;

        return blockWeight;
    }

    public static float legacySwingProgress(float partialTicks) {
        var player = mc.player;
        if (player == null || !ModuleManager.getModuleState(OldHitting.class))
            return 0f;

        return ((ILegacySwing) player).krs$getLegacySwing(partialTicks);
    }

    public static boolean shouldSuppressUseSwing() {
        var player = mc.player;
        return player != null
                && ModuleManager.getModuleState(OldHitting.class)
                && canBlock
                && mc.gui.screen() == null
                && ToolUtil.INSTANCE.isSword(player.getMainHandItem());
    }

    private static void updateBlockState() {
        if (!rawBlocking()) return;

        if (!blocking)
            blockManual = isManualBlockRequested();

        blocking = true;
        blockGraceTicks = BLOCK_RELEASE_GRACE_TICKS;
    }

    private static boolean isManualBlockRequested() {
        return canBlock && mc.gui.screen() == null;
    }

    private static boolean rawBlocking() {
        var player = mc.player;
        if (player == null) return false;
        return mc.level != null
                && ModuleManager.getModuleState(OldHitting.class)
                && ToolUtil.INSTANCE.isSword(player.getMainHandItem())
                && (canBlock && mc.gui.screen() == null || ModuleManager.getModuleState(KillAura.class) && KillAura.isBlocking);
    }

    private static void resetBlockPose() {
        blocking = false;
        blockManual = false;
        blockGraceTicks = 0;
        blockWeight = 0f;
        blockWeightNanos = 0L;
    }

}
