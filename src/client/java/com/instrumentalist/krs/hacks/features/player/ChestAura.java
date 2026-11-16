package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.rotation.RotationManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ChestAura extends Module {
    private static final long COMBAT_WINDOW_MILLIS = 2_000L;
    private static final long CHEST_COOLDOWN_MILLIS = 5_000L;
    private static final long ROTATION_OWN_WINDOW_NANOS = 1_000_000_000L;
    private static final double LOOTED_FORGET_MARGIN = 8.0D;
    private static final int OPEN_WAIT_TICKS = 20;
    private static final float ROTATION_SPEED = 180f;

    @Setting
    private static final FloatValue radius = new FloatValue("Radius", 4f, 1f, 6f, "m")
            .describe("How far away chests are opened from.");

    @Setting
    private static final BooleanValue onlyChestStealer = new BooleanValue("Only Chest Stealer", true)
            .describe("Only opens chests while Chest Stealer is enabled.");

    @Setting
    private static final BooleanValue noCombat = new BooleanValue("No Combat", true)
            .describe("Pauses while you are fighting or taking damage.");

    @Setting
    private static final FloatValue minPlayerDistance = new FloatValue("Min Player Distance", 6f, 0f, 32f, "m")
            .describe("Skips chests with another player this close to them.");

    @Setting
    private static final BooleanValue ignoreOpened = new BooleanValue("Ignore Opened", true)
            .describe("Skips chests that are already open with the lid up.");

    private final Map<BlockPos, Long> cooldowns = new HashMap<>();
    private final Set<BlockPos> looted = new HashSet<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private BlockPos pendingTarget;
    private boolean pendingOpened;
    private long lastRotationRequest;
    private int openWait;
    private long lastCombatMillis;

    @Override
    public String description() {
        return "Opens and loots nearby chests";
    }

    public ChestAura() {
        super("Chest Aura", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetState();
    }

    @Override
    public void onAttack(AttackEvent event) {
        lastCombatMillis = System.currentTimeMillis();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null || player.isSpectator() || player.isDeadOrDying()) {
            halt();
            return;
        }

        boolean containerOpen = player.containerMenu instanceof ChestMenu
                || player.containerMenu instanceof ShulkerBoxMenu;

        if (containerOpen) {
            // Keep the pending chest until its loot session ends, so the cooldown starts after looting.
            if (pendingTarget != null) {
                pendingOpened = true;
                openWait = 0;
            }
            return;
        }

        if (pendingTarget != null) {
            if (openWait > 0) {
                openWait--;
                return;
            }

            // A chest that really opened is remembered until you leave the area;
            // a chest that never opened is only retried after a short cooldown.
            if (pendingOpened)
                looted.add(pendingTarget);
            else
                cooldowns.put(pendingTarget, System.currentTimeMillis());

            pendingTarget = null;
            pendingOpened = false;
        }

        if (mc.gui.screen() != null) {
            halt();
            return;
        }

        if (onlyChestStealer.get() && !ModuleManager.getModuleState(ChestStealer.class)) {
            halt();
            return;
        }

        if (noCombat.get() && inCombat(player)) {
            halt();
            return;
        }

        if (!hasInventoryRoom(player)) {
            halt();
            return;
        }

        // Kill Aura owns the server rotation while it has a target; do not fight it.
        if (ModuleManager.getModuleState(KillAura.class) && KillAura.closestEntity != null) {
            halt();
            return;
        }

        BlockPos target = findTarget(player, level, reach(player));
        if (target == null) {
            halt();
            return;
        }

        RotationManager rotation = Client.rotationManager;
        if (rotation == null) {
            halt();
            return;
        }

        // Leave a rotation that belongs to another module alone; only take over when our
        // own last request has expired (the rotation manager returns to the player by then).
        long now = System.nanoTime();
        if (now - lastRotationRequest > ROTATION_OWN_WINDOW_NANOS && rotation.isRotating())
            return;

        lastRotationRequest = now;
        boolean facing = rotation.faceBlock(target, ROTATION_SPEED);
        if (!facing || !rotation.isRotating() || ModuleManager.rotTick <= 1)
            return;

        BlockHitResult hit = PlayerUtil.INSTANCE.blockHitResult(target);
        if (!gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
            // Do not hammer a chest the server keeps rejecting.
            cooldowns.put(target, System.currentTimeMillis());
            return;
        }

        PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        ChestStealer.noteLootTarget(target);
        pendingTarget = target;
        openWait = OPEN_WAIT_TICKS;
    }

    private BlockPos findTarget(LocalPlayer player, ClientLevel level, double reach) {
        int steps = Mth.ceil(reach);
        BlockPos center = BlockPos.containing(player.getEyePosition());
        Vec3 eyePos = player.getEyePosition();
        double reachSqr = reach * reach;
        double playerLimit = minPlayerDistance.get();
        long now = System.currentTimeMillis();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        pruneCooldowns(now);
        forgetLootedChests(player, reach);

        for (int dx = -steps; dx <= steps; dx++) {
            for (int dy = -steps; dy <= steps; dy++) {
                for (int dz = -steps; dz <= steps; dz++) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!isContainerBlock(state.getBlock()))
                        continue;

                    if (ignoreOpened.get() && isAlreadyOpen(level, cursor, state))
                        continue;

                    Vec3 blockCenter = Vec3.atCenterOf(cursor);
                    double distance = eyePos.distanceToSqr(blockCenter);
                    if (distance > reachSqr || distance >= bestDistance)
                        continue;

                    if (cooldowns.containsKey(cursor) || looted.contains(cursor))
                        continue;

                    if (playerLimit > 0D && hasPlayerNearby(level, player, blockCenter, playerLimit))
                        continue;

                    best = cursor.immutable();
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private static boolean isContainerBlock(Block block) {
        return block instanceof ChestBlock || block instanceof BarrelBlock;
    }

    private static boolean isAlreadyOpen(ClientLevel level, BlockPos pos, BlockState state) {
        // Barrels expose the open state on the block; chests only animate their lid.
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN))
            return true;

        return level.getBlockEntity(pos) instanceof LidBlockEntity lid && lid.getOpenNess(1f) > 0.05f;
    }

    private static boolean hasInventoryRoom(LocalPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < Math.min(36, inventory.getContainerSize()); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize())
                return true;
        }

        return false;
    }

    private static boolean hasPlayerNearby(ClientLevel level, LocalPlayer self, Vec3 position, double limit) {
        double limitSqr = limit * limit;
        for (Player other : level.players()) {
            if (other == self || other.isSpectator())
                continue;

            if (other.distanceToSqr(position) <= limitSqr)
                return true;
        }

        return false;
    }

    private void forgetLootedChests(LocalPlayer player, double reach) {
        if (looted.isEmpty())
            return;

        double forgetDistance = reach + LOOTED_FORGET_MARGIN;
        double forgetSqr = forgetDistance * forgetDistance;
        Vec3 playerPos = player.position();
        looted.removeIf(pos -> Vec3.atCenterOf(pos).distanceToSqr(playerPos) > forgetSqr);
    }

    private void pruneCooldowns(long now) {
        Iterator<Map.Entry<BlockPos, Long>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() >= CHEST_COOLDOWN_MILLIS)
                iterator.remove();
        }
    }

    private static double reach(LocalPlayer player) {
        return Math.min(radius.get(), player.blockInteractionRange());
    }

    private boolean inCombat(LocalPlayer player) {
        long now = System.currentTimeMillis();
        if (player.hurtTime > 0)
            lastCombatMillis = now;

        if (ModuleManager.getModuleState(KillAura.class) && KillAura.closestEntity != null)
            return true;

        return now - lastCombatMillis < COMBAT_WINDOW_MILLIS;
    }

    private void halt() {
        pendingTarget = null;
        pendingOpened = false;
        openWait = 0;

        // Only end a rotation we requested recently; anything older already returned to the player
        // and a newer one belongs to another module.
        if (lastRotationRequest == 0L)
            return;

        boolean recent = System.nanoTime() - lastRotationRequest <= ROTATION_OWN_WINDOW_NANOS;
        lastRotationRequest = 0L;
        if (recent && Client.rotationManager != null)
            Client.rotationManager.stopRotation();
    }

    private void resetState() {
        halt();
        cooldowns.clear();
        looted.clear();
        lastCombatMillis = 0L;
    }
}
