package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.BlockEdgeEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.PerspectiveUtil;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A genuine player-controlled scaffold.
 *
 * <p>The module never changes movement input. It watches the direction you are already
 * travelling and places into the next foot block before you step off the edge. Rotation is
 * only used to make the placement face visible to the server.</p>
 */
public class LegitScaffold extends Module {

    private static final int OFFHAND_SLOT = -1;
    private static final float AIM_SPEED = 180f;
    private static final double FACE_AIM_INSET = 0.18D;
    private static final Direction[] SUPPORT_SIDES = {
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.DOWN
    };

    @Setting
    private final ListValue style = new ListValue(
            "Style",
            new String[]{"Breezily", "God", "Sneak"},
            "Sneak"
    ).describe("Placement pacing. Sneak also enables edge sneaking; no style changes your movement.");

    @Setting
    private final BooleanValue autoBlock = new BooleanValue(
            "Auto Block",
            true
    ).describe("Switches to a hotbar block automatically.");

    @Setting
    private final IntValue slotResetDelay = new IntValue(
            "Slot Reset Delay",
            4,
            0,
            20,
            "t",
            autoBlock::get
    ).describe("Ticks before switching back to the original hotbar slot.");

    @Setting
    private final BooleanValue useOffhand = new BooleanValue(
            "Use Offhand",
            true
    ).describe("Uses blocks from the offhand before switching hotbar slots.");

    @Setting
    private final BooleanValue autoSneak = new BooleanValue(
            "Auto Sneak",
            true,
            this::isSneakStyle
    ).describe("Sneaks at the edge only while you are walking off it. Your movement is never changed.");

    @Setting
    private final BooleanValue swing = new BooleanValue(
            "Swing",
            true
    ).describe("Swings the interaction hand after placing.");

    @Setting
    private final BooleanValue autoF5 = new BooleanValue(
            "Auto F5",
            false
    ).describe("Switches to third person while the module is enabled.");

    private int bridgeY;
    private int originalSlot = -1;
    private int blockSlot = -1;
    private int restoreAtTick;
    private int nextPlaceTick;
    private int lastAimTick = -1;
    private BlockPos aimSupport;
    private Direction aimSide;

    private record Placement(BlockPos support, Direction side, Vec3 hitPos) {
    }

    public LegitScaffold() {
        super("Legit Scaffold", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Places blocks under your feet while you bridge without changing your movement";
    }

    @Override
    public String tag() {
        String current = style.get();
        return current == null || current.isBlank() ? "Legit" : current;
    }

    @Override
    public void onEnable() {
        PerspectiveUtil.update(this, autoF5.get());
        var player = mc.player;
        if (player == null || mc.level == null)
            return;

        bridgeY = player.blockPosition().getY() - 1;
        originalSlot = player.getInventory().getSelectedSlot();
        blockSlot = -1;
        restoreAtTick = 0;
        nextPlaceTick = 0;
        clearAim();
        BehaviorUtils.noKillAura = true;
    }

    @Override
    public void onDisable() {
        PerspectiveUtil.release(this);
        restoreOriginalSlot();
        BehaviorUtils.noKillAura = false;
        if (mc.player != null && Client.rotationManager != null)
            Client.rotationManager.stopRotation();
    }

    @Override
    public void onBlockEdge(BlockEdgeEvent event) {
        var player = mc.player;
        if (!isSneakStyle() || !autoSneak.get() || player == null || mc.level == null)
            return;
        if (ModuleManager.getModuleState(Scaffold.class) || !hasUsableBlock(player))
            return;
        if (player.getAbilities().flying
                || player.isFallFlying()
                || player.isPassenger()
                || player.isSwimming()
                || player.isSpectator()
                || !player.onGround())
            return;

        Input keys = player.input == null || player.input.keyPresses == null
                ? Input.EMPTY
                : player.input.keyPresses;
        if (shouldAutoSneak(player, keys))
            event.cancel();
    }

    /**
     * Adds sneak to the vanilla input only when the player is actually walking toward an
     * unsupported block. Movement keys are copied exactly as they are.
     */
    public static void hookSneakInput(ClientInput input) {
        LegitScaffold module = ModuleManager.getModule(LegitScaffold.class);
        if (module == null || !module.tempEnabled || !module.isSneakStyle() || !module.autoSneak.get())
            return;
        if (ModuleManager.getModuleState(Scaffold.class))
            return;
        if (input == null || mc.player == null || mc.level == null)
            return;

        LocalPlayer player = mc.player;
        Input keys = input.keyPresses == null ? Input.EMPTY : input.keyPresses;
        if (keys.shift() || !module.shouldAutoSneak(player, keys))
            return;

        InputUtil.applyInput(input, new Input(
                keys.forward(),
                keys.backward(),
                keys.left(),
                keys.right(),
                keys.jump(),
                true,
                keys.sprint()
        ));
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        PerspectiveUtil.update(this, autoF5.get());

        var player = mc.player;
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null || mc.gui.screen() != null)
            return;

        BehaviorUtils.noKillAura = true;

        if (player.onGround())
            bridgeY = player.blockPosition().getY() - 1;

        if (player.isSpectator()
                || player.isUsingItem()
                || player.getAbilities().flying
                || player.isFallFlying()
                || player.isPassenger()
                || !hasUsableBlock(player)) {
            restoreBlockSlotIfDue(player);
            return;
        }

        // Let the regular Scaffold own placement if both modules are somehow enabled.
        if (ModuleManager.getModuleState(Scaffold.class)) {
            restoreBlockSlotIfDue(player);
            return;
        }

        if (player.tickCount < nextPlaceTick)
            return;

        // Bridging is ground work. Do not silently clutch while jumping or falling.
        if (!player.onGround())
            return;

        // Aim on the approach tick. By the next tick the server has received that
        // rotation, so the interaction is never ordered ahead of the look packet.
        if (placePendingAim(player, level, gameMode))
            return;

        Placement placement = findPlacement(player, level);
        if (placement == null) {
            restoreBlockSlotIfDue(player);
            return;
        }

        float[] rotations = rotationsTo(placement.hitPos());
        Client.rotationManager.startRotation(rotations[0], rotations[1], AIM_SPEED);
        lastAimTick = player.tickCount;
        aimSupport = placement.support();
        aimSide = placement.side();
    }

    private boolean placePendingAim(LocalPlayer player, Level level, MultiPlayerGameMode gameMode) {
        if (Client.rotationManager == null
                || lastAimTick != player.tickCount - 1
                || aimSupport == null
                || aimSide == null)
            return false;

        BlockPos target = aimSupport.relative(aimSide);
        if (!isReplaceable(level.getBlockState(target)) || intersectsPlayer(target, player)) {
            clearAim();
            return false;
        }

        BlockHitResult hit = Client.rotationManager.rayTraceBlocks(
                Client.rotationManager.getRotationYaw(),
                Client.rotationManager.getRotationPitch(),
                player.blockInteractionRange()
        );
        if (hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(aimSupport)
                || hit.getDirection() != aimSide)
            return false;

        placeBlock(player, gameMode, new Placement(aimSupport, aimSide, hit.getLocation()));
        clearAim();
        return true;
    }

    private void clearAim() {
        lastAimTick = -1;
        aimSupport = null;
        aimSide = null;
    }

    /**
     * Finds the first safe placement in the direction the player is already moving, then
     * falls back to the block directly under the player.
     */
    private Placement findPlacement(LocalPlayer player, Level level) {
        BlockPos feet = new BlockPos(
                Mth.floor(player.getX()),
                bridgeY,
                Mth.floor(player.getZ())
        );

        Set<BlockPos> candidates = new LinkedHashSet<>(5);

        // If the block under the player is already gone, save that position first.
        if (isReplaceable(level.getBlockState(feet)))
            candidates.add(feet);

        Vec3 movement = MovementUtil.getMovementVector();
        if (movement.lengthSqr() <= 1.0E-6D) {
            Vec3 motion = player.getDeltaMovement();
            movement = new Vec3(motion.x, 0.0D, motion.z);
        }

        int stepX = Math.abs(movement.x) > 0.25D ? (movement.x > 0.0D ? 1 : -1) : 0;
        int stepZ = Math.abs(movement.z) > 0.25D ? (movement.z > 0.0D ? 1 : -1) : 0;

        if (stepX != 0 || stepZ != 0) {
            if (stepX != 0 && stepZ != 0)
                candidates.add(feet.offset(stepX, 0, stepZ));
            if (stepX != 0)
                candidates.add(feet.offset(stepX, 0, 0));
            if (stepZ != 0)
                candidates.add(feet.offset(0, 0, stepZ));
        }

        candidates.add(feet);

        for (BlockPos target : candidates) {
            if (!isReplaceable(level.getBlockState(target)) || intersectsPlayer(target, player))
                continue;

            Placement placement = findPlacementForTarget(level, player, target);
            if (placement != null)
                return placement;
        }

        return null;
    }

    private Placement findPlacementForTarget(Level level, LocalPlayer player, BlockPos target) {
        if (Client.rotationManager == null)
            return null;

        Vec3 eyes = player.getEyePosition();
        double reach = player.blockInteractionRange();
        double reachSqr = reach * reach;
        Placement best = null;
        double bestScore = Double.MAX_VALUE;

        for (Direction side : SUPPORT_SIDES) {
            BlockPos support = target.relative(side.getOpposite());
            BlockState state = level.getBlockState(support);
            if (!isSupport(state, level, support) || Scaffold.isGuiBlock(state, level, support))
                continue;

            AABB bounds = state.getCollisionShape(level, support).isEmpty()
                    ? new AABB(support)
                    : state.getCollisionShape(level, support).bounds().move(support);

            for (int u = -1; u <= 1; u++) {
                for (int v = -1; v <= 1; v++) {
                    Vec3 point = facePoint(bounds, side, u, v);
                    if (eyes.distanceToSqr(point) > reachSqr)
                        continue;

                    float[] rotations = rotationsTo(point);
                    BlockHitResult hit = Client.rotationManager.rayTraceBlocks(
                            rotations[0],
                            rotations[1],
                            reach
                    );
                    if (hit.getType() != HitResult.Type.BLOCK
                            || !hit.getBlockPos().equals(support)
                            || hit.getDirection() != side)
                        continue;

                    // Prefer the center of a face: it stays stable while the player
                    // moves and is the least sensitive to mouse rounding.
                    double centerBonus = u == 0 && v == 0 ? 1.0D : 0.0D;
                    double score = eyes.distanceToSqr(hit.getLocation()) - centerBonus;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Placement(support.immutable(), side, hit.getLocation());
                    }
                }
            }
        }

        return best;
    }

    private void placeBlock(LocalPlayer player, MultiPlayerGameMode gameMode, Placement placement) {
        int slot = findBlockSlot(player);
        if (slot == Integer.MIN_VALUE)
            return;

        InteractionHand hand = slot == OFFHAND_SLOT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean changedSlot = false;
        if (hand == InteractionHand.MAIN_HAND && player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            changedSlot = true;
        }

        if (hand == InteractionHand.MAIN_HAND && changedSlot) {
            blockSlot = slot;
            restoreAtTick = player.tickCount + Math.max(0, slotResetDelay.get());
        }

        BlockHitResult hitResult = new BlockHitResult(
                placement.hitPos(),
                placement.side(),
                placement.support(),
                false
        );

        // Try once per placement interval. The server receives the exact face we traced,
        // so a slow cosmetic rotation cannot make the module miss the block.
        nextPlaceTick = player.tickCount + placementInterval();

        if (!gameMode.useItemOn(player, hand, hitResult).consumesAction())
            return;

        if (swing.get())
            PlayerUtil.INSTANCE.swingInteractHand(hand);
    }

    private int placementInterval() {
        return switch (style.get().toLowerCase(Locale.ROOT)) {
            case "god" -> 1;
            case "breezily" -> 2;
            default -> 2;
        };
    }

    private boolean isSneakStyle() {
        return "sneak".equalsIgnoreCase(style.get());
    }

    private static float[] rotationsTo(Vec3 hitPos) {
        var player = mc.player;
        if (player == null)
            return new float[]{0f, 0f};

        Vec3 diff = hitPos.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        float yaw = (float) (Mth.atan2(-diff.x, diff.z) * (180.0 / Math.PI));
        float pitch = (float) -(Mth.atan2(diff.y, horizontal) * (180.0 / Math.PI));
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
    }

    private static Vec3 facePoint(AABB bounds, Direction side, int u, int v) {
        Vec3 center = faceCenter(bounds, side);
        double halfA;
        double halfB;
        Vec3 axisA;
        Vec3 axisB;

        switch (side.getAxis()) {
            case X -> {
                halfA = Math.max(0.0D, (bounds.maxY - bounds.minY) * 0.5D - FACE_AIM_INSET);
                halfB = Math.max(0.0D, (bounds.maxZ - bounds.minZ) * 0.5D - FACE_AIM_INSET);
                axisA = new Vec3(0.0D, 1.0D, 0.0D);
                axisB = new Vec3(0.0D, 0.0D, 1.0D);
            }
            case Y -> {
                halfA = Math.max(0.0D, (bounds.maxX - bounds.minX) * 0.5D - FACE_AIM_INSET);
                halfB = Math.max(0.0D, (bounds.maxZ - bounds.minZ) * 0.5D - FACE_AIM_INSET);
                axisA = new Vec3(1.0D, 0.0D, 0.0D);
                axisB = new Vec3(0.0D, 0.0D, 1.0D);
            }
            default -> {
                halfA = Math.max(0.0D, (bounds.maxX - bounds.minX) * 0.5D - FACE_AIM_INSET);
                halfB = Math.max(0.0D, (bounds.maxY - bounds.minY) * 0.5D - FACE_AIM_INSET);
                axisA = new Vec3(1.0D, 0.0D, 0.0D);
                axisB = new Vec3(0.0D, 1.0D, 0.0D);
            }
        }

        return center.add(axisA.scale(halfA * u)).add(axisB.scale(halfB * v));
    }

    private static Vec3 faceCenter(AABB bounds, Direction side) {
        return bounds.getCenter().add(
                side.getStepX() * (bounds.maxX - bounds.minX) * 0.5D,
                side.getStepY() * (bounds.maxY - bounds.minY) * 0.5D,
                side.getStepZ() * (bounds.maxZ - bounds.minZ) * 0.5D
        );
    }

    private static boolean isSupport(BlockState state, Level level, BlockPos pos) {
        if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty())
            return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isReplaceable(BlockState state) {
        return state.canBeReplaced();
    }

    private static boolean intersectsPlayer(BlockPos pos, LocalPlayer player) {
        return new AABB(pos).intersects(player.getBoundingBox().deflate(1.0E-6));
    }

    private int findBlockSlot(LocalPlayer player) {
        if (useOffhand.get() && isUsableBlock(player.getOffhandItem()))
            return OFFHAND_SLOT;

        int selected = player.getInventory().getSelectedSlot();
        if (selected >= 0 && selected < 9 && isUsableBlock(player.getInventory().getItem(selected)))
            return selected;

        if (!autoBlock.get())
            return Integer.MIN_VALUE;

        int bestSlot = Integer.MIN_VALUE;
        int bestCount = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isUsableBlock(stack))
                continue;
            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private static boolean isUsableBlock(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && Scaffold.isScaffoldBlock(blockItem.getBlock());
    }

    private boolean hasUsableBlock(LocalPlayer player) {
        return player != null && findBlockSlot(player) != Integer.MIN_VALUE;
    }

    private void restoreBlockSlotIfDue(LocalPlayer player) {
        if (blockSlot < 0 || originalSlot < 0 || player.tickCount < restoreAtTick)
            return;

        if (player.getInventory().getSelectedSlot() == blockSlot)
            player.getInventory().setSelectedSlot(originalSlot);
        blockSlot = -1;
    }

    private void restoreOriginalSlot() {
        var player = mc.player;
        if (player == null) {
            blockSlot = -1;
            return;
        }

        if (blockSlot >= 0
                && originalSlot >= 0
                && player.getInventory().getSelectedSlot() == blockSlot)
            player.getInventory().setSelectedSlot(originalSlot);
        blockSlot = -1;
    }

    private boolean shouldAutoSneak(LocalPlayer player, Input keys) {
        if (player == null || mc.level == null
                || !hasUsableBlock(player)
                || !player.onGround()
                || player.getAbilities().flying
                || player.isFallFlying()
                || player.isPassenger()
                || player.isSwimming()
                || player.isSpectator())
            return false;

        keys = keys == null ? Input.EMPTY : keys;
        if (keys.shift())
            return false;

        float forward = KeyboardInput.calculateImpulse(keys.forward(), keys.backward());
        float left = KeyboardInput.calculateImpulse(keys.left(), keys.right());
        if (forward == 0f && left == 0f)
            return false;

        Vec3 forwardVector = MovementUtil.getHorizontalDirectionVector(player.getYRot());
        Vec3 leftVector = new Vec3(forwardVector.z, 0.0, -forwardVector.x);
        Vec3 direction = forwardVector.scale(forward).add(leftVector.scale(left));
        if (direction.lengthSqr() <= 1.0E-6)
            return false;

        direction = direction.normalize();
        Vec3 position = player.position();
        return isUnsupported(player, position)
                || isUnsupported(player, position.add(direction.scale(0.45)));
    }

    private static boolean isUnsupported(LocalPlayer player, Vec3 position) {
        var level = mc.level;
        if (level == null)
            return false;

        AABB hitbox = player.getDimensions(Pose.STANDING)
                .makeBoundingBox(position)
                .inflate(-0.08, 0.0, -0.08)
                .move(0.0, player.fallDistance - player.maxUpStep(), 0.0);
        return level.noCollision(player, hitbox);
    }
}
