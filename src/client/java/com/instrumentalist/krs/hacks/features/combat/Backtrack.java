package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.packet.ServerPositionTracker;
import com.instrumentalist.krs.utils.render.PathLines;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.render.TargetBoxRenderer;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Port of LiquidBounce's Backtrack.
 *
 * <p>Incoming target position packets are queued through a {@link VecDeltaCodec} base that starts
 * at the target's server position, exactly like LiquidBounce's {@code TrackedEntityPosition}.</p>
 */
public class Backtrack extends Module {
    private static final int MAX_STORED_PACKETS = 512;
    private static final int MIN_TARGET_TICKS = 10;
    private static final double MIN_LINE_DISTANCE_SQUARED = 0.05D * 0.05D;
    private static final Object TARGET_LINE = new Object();
    private static final Object SELF_LINE = new Object();

    @Setting
    private final ListValue targetMode = new ListValue(
            "Target Mode",
            new String[]{"Attack", "Range"},
            "Attack"
    ).describe("Attack backtracks the entity you hit, Range the nearest enemy in range.");

    @Setting
    private final BooleanValue render = new BooleanValue(
            "Render",
            true
    ).describe("Draws a theme-coloured box around the backtracked target.");

    @Setting
    private final BooleanValue walls = new BooleanValue(
            "Walls",
            true
    ).describe("Draws filled target-box walls without the glowing outline.");

    @Setting
    private final FloatValue minRange = new FloatValue(
            "Min Range",
            1f,
            0f,
            10f,
            "m"
    ).describe("Closest distance at which the target is backtracked.");

    @Setting
    private final FloatValue maxRange = new FloatValue(
            "Max Range",
            3f,
            0f,
            10f,
            "m"
    ).describe("Furthest distance at which the target is backtracked.");

    @Setting
    private final IntValue minDelay = new IntValue(
            "Min Delay",
            100,
            0,
            1000,
            "ms"
    ).describe("Shortest time incoming positions are held back.");

    @Setting
    private final IntValue maxDelay = new IntValue(
            "Max Delay",
            150,
            0,
            1000,
            "ms"
    ).describe("Longest time incoming positions are held back.");

    @Setting
    private final IntValue minNextBacktrackDelay = new IntValue(
            "Min Next Backtrack Delay",
            0,
            0,
            2000,
            "ms"
    ).describe("Shortest cooldown before backtracking again.");

    @Setting
    private final IntValue maxNextBacktrackDelay = new IntValue(
            "Max Next Backtrack Delay",
            10,
            0,
            2000,
            "ms"
    ).describe("Longest cooldown before backtracking again.");

    @Setting
    private final IntValue trackingBuffer = new IntValue(
            "Tracking Buffer",
            500,
            0,
            2000,
            "ms"
    ).describe("How long a target is still backtracked after leaving the range.");

    @Setting
    private final FloatValue chance = new FloatValue(
            "Chance",
            50f,
            0f,
            100f,
            "%"
    ).describe("Probability that backtracking engages on an attack.");

    @Setting
    private final BooleanValue pauseOnHurtTime = new BooleanValue(
            "Pause On Hurt Time",
            false
    ).describe("Pause backtracking while the target is in its hurt animation.");

    @Setting
    private final IntValue hurtTime = new IntValue(
            "Hurt Time",
            3,
            0,
            10,
            "ticks",
            () -> pauseOnHurtTime.get()
    ).describe("Minimum remaining hurt ticks that pause backtracking.");

    @Setting
    private final IntValue lastAttackTimeToWork = new IntValue(
            "Last Attack Time To Work",
            1000,
            0,
            5000,
            "ms"
    ).describe("How long after attacking backtracking stays active.");

    private final ArrayDeque<QueuedPacket> storage = new ArrayDeque<>();
    private final Object storageLock = new Object();
    private final VecDeltaCodec trackedPositionCodec = new VecDeltaCodec();
    private final MSTimer trackingBufferTimer = new MSTimer();
    private final MSTimer attackTimer = new MSTimer();
    private final ArrayList<Vec3> lineScratch = new ArrayList<>(1);

    private Entity target;
    private Vec3 trackedPosition;
    private boolean shouldPause;
    private boolean chancePassed = true;
    private int currentDelay = 100;
    private long nextBacktrackReadyNanos;

    @Override
    public String description() {
        return "Delays incoming hit packets so you can reach further";
    }

    @Override
    public String tag() {
        return targetMode.get() + " " + currentDelay + "ms";
    }

    public Backtrack() {
        super("Backtrack", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    public boolean isBacktracking() {
        return tempEnabled && hasQueuedPackets();
    }

    @Override
    public void onEnable() {
        currentDelay = rollDelay();
        nextBacktrackReadyNanos = 0L;
        clear(false, false);
    }

    @Override
    public void onDisable() {
        clear(true, true);
        shouldPause = false;
        ServerPositionTracker.reset();
        PathLines.hide(this, TARGET_LINE);
        PathLines.hide(this, SELF_LINE);
    }

    @Override
    public void onWorld(WorldEvent event) {
        dropQueue();
        clear(false, false);
        ServerPositionTracker.reset();
        PathLines.hide(this, TARGET_LINE);
        PathLines.hide(this, SELF_LINE);
    }

    @Override
    public void onAttack(AttackEvent event) {
        // LiquidBounce updates the last-attack timer and rolls the chance for every attack.
        attackTimer.reset();
        chancePassed = RandomUtil.nextFloat(0f, 100f) < chance.get();

        if (!isRangeMode())
            processTarget(event.entity);
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) {
            dropQueue();
            clear(false, false);
            return;
        }

        if (isRangeMode()) {
            Entity enemy = BehaviorUtils.findEnemy(minRange.get(), effectiveMaxRange());
            if (enemy == null) {
                if (target != null)
                    clear(true, true);
            } else {
                processTarget(enemy);
            }
        }

        tickQueue();
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event == null || event.packet == null || event.isCancelled() || mc.player == null || mc.level == null)
            return;

        boolean shouldCancel = shouldCancelPackets();
        boolean hadQueuedIncoming = hasQueuedPackets();

        // If we should not lag and there is nothing queued, BlinkManager is not involved at all.
        if (!hadQueuedIncoming && !shouldCancel)
            return;

        Packet<?> packet = event.packet;

        // Ignore message-related packets.
        if (packet instanceof ServerboundChatPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundDisguisedChatPacket
                || packet instanceof ServerboundChatCommandPacket)
            return;

        // Flush on teleport or disconnect.
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundDisconnectPacket) {
            clear(true, true);
            return;
        }

        // BlinkManager flushes on reconnect/respawn, but keeps the current target.
        if (packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket) {
            releaseAll();
            return;
        }

        // Ignore own hurt sounds.
        if (packet instanceof ClientboundSoundPacket soundPacket
                && soundPacket.getSound().value() == SoundEvents.PLAYER_HURT)
            return;

        // Flush on own death.
        if (packet instanceof ClientboundSetHealthPacket healthPacket && healthPacket.getHealth() <= 0.0F) {
            clear(true, true);
            return;
        }

        Entity current = target;
        if (current == null)
            return;

        Vec3 position = handleTrackedPacket(packet, current);
        if (position != null) {
            // Is the target's actual position closer than its tracked position?
            if (squareBoxedDistanceTo(current, position, mc.player) < boxedDistanceSquared(current, mc.player)) {
                // Process all packets. We want to hit the enemy, not the opposite.
                releaseAll();
                return;
            }
        }

        // There was already a queue or backtracking just became active.
        event.cancel();

        boolean overflow;
        synchronized (storageLock) {
            if (storage.isEmpty())
                currentDelay = rollDelay();
            storage.addLast(new QueuedPacket(packet, System.nanoTime()));
            overflow = storage.size() >= MAX_STORED_PACKETS;
        }

        // Applying the backlog first keeps the target's position sequence in order.
        if (overflow)
            releaseAll();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null)
            return;

        Entity current = target;
        Vec3 serverTarget = current != null && hasQueuedPackets() ? trackedPosition : null;
        if (serverTarget != null && serverTarget.distanceToSqr(current.position()) > MIN_LINE_DISTANCE_SQUARED)
            showLine(TARGET_LINE, RenderUtil.INSTANCE.getLerpedPos(current, event.partialTicks), serverTarget);
        else
            PathLines.hide(this, TARGET_LINE);

        Vec3 serverSelf = ServerPositionTracker.lastSentPosition();
        if (serverSelf != null && serverSelf.distanceToSqr(mc.player.position()) > MIN_LINE_DISTANCE_SQUARED)
            showLine(SELF_LINE, RenderUtil.INSTANCE.getLerpedPos(mc.player, event.partialTicks), serverSelf);
        else
            PathLines.hide(this, SELF_LINE);
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        Entity current = target;
        if (!render.get() || current == null || current.isRemoved() || Client.nanoVgManager == null)
            return;

        Vec3 position = isBacktracking() && trackedPosition != null
                ? trackedPosition
                : RenderUtil.INSTANCE.getLerpedPos(current, event.tickDelta);
        if (position == null)
            return;

        Client.nanoVgManager.loadWorldAnchored(vg -> TargetBoxRenderer.render(current, position, vg, walls.get()));
    }

    private void showLine(Object key, Vec3 from, Vec3 to) {
        lineScratch.clear();
        lineScratch.add(to);
        PathLines.show(this, key, from, lineScratch, key == SELF_LINE ? RenderColors.BACKTRACK_SELF : RenderColors.BACKTRACK_TARGET);
    }

    private boolean isRangeMode() {
        return targetMode.get().equalsIgnoreCase("Range");
    }

    private double effectiveMaxRange() {
        return Math.max(minRange.get(), maxRange.get());
    }

    private void processTarget(Entity enemy) {
        if (enemy == null || mc.player == null || enemy == mc.player)
            return;

        // LiquidBounce stores this only while processing a target, not on every predicate call.
        shouldPause = enemy instanceof LivingEntity living && living.hurtTime >= hurtTime.get();

        if (!shouldBacktrack(enemy))
            return;

        // Reset on enemy change.
        if (enemy != target) {
            releaseAll();
            clear(false, false);

            // Instantly set the new position, so the box is not created with delay.
            trackedPositionCodec.setBase(enemy.getPositionCodec().getBase());
            trackedPosition = trackedPositionCodec.getBase();
        }

        target = enemy;
    }

    private boolean shouldCancelPackets() {
        Entity current = target;
        return tempEnabled && current != null && current.isAlive() && shouldBacktrack(current);
    }

    private boolean shouldBacktrack(Entity candidate) {
        if (candidate == null || mc.player == null || mc.level == null)
            return false;
        if (!candidate.isAlive() || candidate.isRemoved() || !candidate.isAttackable())
            return false;
        if (!mc.level.getWorldBorder().isWithinBounds(candidate.blockPosition()))
            return false;

        double distance = Math.sqrt(boxedDistanceSquared(candidate, mc.player));
        double min = Math.min(minRange.get(), maxRange.get());
        double max = Math.max(minRange.get(), maxRange.get());
        boolean inRange = distance >= min && distance <= max;

        if (inRange)
            trackingBufferTimer.reset();

        return (inRange || !trackingBufferTimer.hasTimePassed(trackingBuffer.get()))
                && mc.player.tickCount > MIN_TARGET_TICKS
                && chancePassed
                && System.nanoTime() >= nextBacktrackReadyNanos
                && !isPaused()
                && !attackTimer.hasTimePassed(lastAttackTimeToWork.get());
    }

    private boolean isPaused() {
        return pauseOnHurtTime.get() && shouldPause;
    }

    /**
     * Mirrors LiquidBounce's TrackedEntityPosition: movement deltas are decoded against a codec
     * whose base starts at the target's server position.
     */
    private Vec3 handleTrackedPacket(Packet<?> packet, Entity current) {
        Vec3 trackedPos;

        if (packet instanceof ClientboundMoveEntityPacket movePacket) {
            if (mc.level == null || movePacket.getEntity(mc.level) != current)
                return null;
            trackedPos = movePacket.getPositionDelta().decode(trackedPositionCodec).endPosition();
        } else if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
            if (teleportPacket.id() != current.getId())
                return null;
            trackedPos = teleportPacket.change().position();
        } else if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket) {
            if (syncPacket.id() != current.getId())
                return null;
            trackedPos = syncPacket.position().endPosition();
        } else {
            return null;
        }

        trackedPositionCodec.setBase(trackedPos);
        trackedPosition = trackedPos;
        return trackedPos;
    }

    private void tickQueue() {
        boolean hadQueuedIncoming = hasQueuedPackets();

        if (shouldCancelPackets()) {
            flushExpired();
        } else if (hadQueuedIncoming) {
            releaseAll();
            clear(false, true);
        }

        if (!hasQueuedPackets())
            currentDelay = rollDelay();
    }

    private boolean hasQueuedPackets() {
        synchronized (storageLock) {
            return !storage.isEmpty();
        }
    }

    private void flushExpired() {
        long now = System.nanoTime();
        long cutoff = now - TimeUnit.MILLISECONDS.toNanos(Math.clamp(currentDelay, 0, 2000));
        ArrayDeque<Packet<?>> due = null;

        synchronized (storageLock) {
            while (!storage.isEmpty() && storage.peekFirst().timestampNanos() <= cutoff) {
                if (due == null)
                    due = new ArrayDeque<>();
                due.addLast(storage.removeFirst().packet());
            }
        }

        if (due == null)
            return;

        while (!due.isEmpty())
            PacketUtil.handlePacket(due.removeFirst());
    }

    private void releaseAll() {
        ArrayDeque<QueuedPacket> pending;
        synchronized (storageLock) {
            if (storage.isEmpty())
                return;
            pending = new ArrayDeque<>(storage);
            storage.clear();
        }

        while (!pending.isEmpty())
            PacketUtil.handlePacket(pending.removeFirst().packet());
    }

    private void dropQueue() {
        synchronized (storageLock) {
            storage.clear();
        }
    }

    private void clear(boolean releasePackets, boolean resetCooldown) {
        if (releasePackets)
            releaseAll();

        if (target != null && resetCooldown)
            nextBacktrackReadyNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(rollNextBacktrackDelay());

        target = null;
        trackedPosition = null;
    }

    /**
     * Mirrors LiquidBounce's {@code Entity.boxedDistanceTo}: the target's pick box against the
     * player's eye position.
     */
    private double boxedDistanceSquared(Entity entity, Entity other) {
        return pickBoxAt(entity, entity.position()).distanceToSqr(other.getEyePosition());
    }

    /**
     * Mirrors LiquidBounce's {@code squareBoxedDistanceTo}: the target's pick box moved to a
     * different position, against the player's eye position.
     */
    private double squareBoxedDistanceTo(Entity entity, Vec3 position, Entity other) {
        return pickBoxAt(entity, position).distanceToSqr(other.getEyePosition());
    }

    private AABB pickBoxAt(Entity entity, Vec3 position) {
        return entity.getBoundingBox()
                .inflate(entity.getPickRadius())
                .move(position.subtract(entity.position()));
    }

    private int rollDelay() {
        int min = Math.min(minDelay.get(), maxDelay.get());
        int max = Math.max(minDelay.get(), maxDelay.get());
        return RandomUtil.nextInt(min, max + 1);
    }

    private int rollNextBacktrackDelay() {
        int min = Math.min(minNextBacktrackDelay.get(), maxNextBacktrackDelay.get());
        int max = Math.max(minNextBacktrackDelay.get(), maxNextBacktrackDelay.get());
        return RandomUtil.nextInt(min, max + 1);
    }

    private record QueuedPacket(Packet<?> packet, long timestampNanos) {
    }
}
