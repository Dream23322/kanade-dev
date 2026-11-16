package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.entity.FakePlayerEntity;
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
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Port of LiquidBounce's Backtrack: incoming position packets of the tracked target are held back
 * so the target is shown (and hit) where it was a moment ago, buying extra reach.
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
    private final VecDeltaCodec simulatedPositionCodec = new VecDeltaCodec();
    private final MSTimer trackingBufferTimer = new MSTimer();
    private final MSTimer attackTimer = new MSTimer();
    private final ArrayList<Vec3> lineScratch = new ArrayList<>(1);

    private Entity target;
    private Vec3 trackedPosition;
    private Vec3 simulatedPosition;
    private Vec3 simulatedDeltaMovement = Vec3.ZERO;
    private float simulatedYaw;
    private float simulatedPitch;
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
        chancePassed = true;
        currentDelay = rollDelay();
        nextBacktrackReadyNanos = 0L;
        clear(false, false);
    }

    @Override
    public void onDisable() {
        clear(true, true);
        resetSimulation();
        ServerPositionTracker.reset();
        PathLines.hide(this, TARGET_LINE);
        PathLines.hide(this, SELF_LINE);
    }

    @Override
    public void onWorld(WorldEvent event) {
        dropQueue();
        clear(false, false);
        resetSimulation();
        ServerPositionTracker.reset();
        PathLines.hide(this, TARGET_LINE);
        PathLines.hide(this, SELF_LINE);
    }

    @Override
    public void onAttack(AttackEvent event) {
        Entity attacked = event.entity;
        if (attacked == null || mc.player == null || attacked == mc.player)
            return;
        if (!(attacked instanceof LivingEntity) || attacked instanceof FakePlayerEntity)
            return;

        attackTimer.reset();
        chancePassed = RandomUtil.nextFloat(0f, 100f) < chance.get();

        if (!isRangeMode())
            processTarget(attacked);
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) {
            dropQueue();
            clear(false, false);
            return;
        }

        if (isRangeMode()) {
            Entity enemy = BehaviorUtils.findEnemy(effectiveMaxRange());
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
        if (event == null || event.packet == null || mc.player == null || mc.level == null)
            return;

        Packet<?> packet = event.packet;

        // Teleports, disconnects and our own death invalidate the held positions.
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0F) {
            clear(true, true);
            return;
        }

        if (!queueing() && !hasQueuedPackets())
            return;

        Entity current = target;
        if (current == null)
            return;

        Vec3 position;
        try {
            if (packet instanceof ClientboundMoveEntityPacket movePacket) {
                if (movePacket.getEntity(mc.level) != current)
                    return;
                ensureSimulationInitialized(current);
                position = simulateMovePacket(current, movePacket);
            } else if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
                if (mc.level.getEntity(teleportPacket.id()) != current)
                    return;
                ensureSimulationInitialized(current);
                PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(
                        new PositionMoveRotation(simulatedPosition, simulatedDeltaMovement, simulatedYaw, simulatedPitch),
                        teleportPacket.change(),
                        teleportPacket.relatives());
                updateSimulation(absolute.position(), absolute.deltaMovement(), absolute.yRot(), absolute.xRot());
                position = absolute.position();
            } else if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket) {
                if (mc.level.getEntity(syncPacket.id()) != current)
                    return;
                // Position sync packets no longer carry a velocity since 26.3, so keep the simulated one.
                position = syncPacket.position().endPosition();
                updateSimulation(position, simulatedDeltaMovement, syncPacket.yRot(), syncPacket.xRot());
            } else {
                return;
            }
        } catch (RuntimeException ignored) {
            clear(true, true);
            return;
        }

        trackedPosition = position;

        // We want to be able to hit the enemy, not the opposite.
        if (!queueing() || isTrackedPositionCloser(current, position)) {
            releaseAll();
            return;
        }

        boolean overflow;
        synchronized (storageLock) {
            overflow = storage.size() >= MAX_STORED_PACKETS;
            if (!overflow) {
                if (storage.isEmpty())
                    currentDelay = rollDelay();
                event.cancel();
                storage.addLast(new QueuedPacket(packet, System.nanoTime()));
            }
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

        Client.nanoVgManager.loadWorldAnchored(vg -> TargetBoxRenderer.render(current, position, vg));
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
        if (!enemy.isAlive() || enemy.isRemoved() || !enemy.isAttackable() || enemy instanceof FakePlayerEntity)
            return;

        // Reset on enemy change, so the tracked position starts from the new target.
        if (enemy != target) {
            releaseAll();
            clear(false, false);
            initializeSimulation(enemy);
        }

        target = enemy;
    }

    private boolean queueing() {
        Entity current = target;
        return tempEnabled && current != null && shouldBacktrack(current);
    }

    private boolean shouldBacktrack(Entity candidate) {
        if (candidate == null || mc.player == null || mc.level == null)
            return false;
        if (!candidate.isAlive() || candidate.isRemoved() || !candidate.isAttackable())
            return false;

        double distance = EntityExtension.boxedDistanceTo(candidate, mc.player);
        double min = Math.min(minRange.get(), maxRange.get());
        double max = Math.max(minRange.get(), maxRange.get());
        boolean inRange = distance >= min && distance <= max;

        if (inRange)
            trackingBufferTimer.reset();

        if (!inRange && trackingBufferTimer.hasTimePassed(trackingBuffer.get()))
            return false;

        return mc.player.tickCount > MIN_TARGET_TICKS
                && chancePassed
                && System.nanoTime() >= nextBacktrackReadyNanos
                && !isPaused(candidate)
                && !attackTimer.hasTimePassed(lastAttackTimeToWork.get());
    }

    private boolean isPaused(Entity candidate) {
        if (!pauseOnHurtTime.get())
            return false;

        return candidate instanceof LivingEntity living && living.hurtTime >= hurtTime.get();
    }

    private void tickQueue() {
        boolean hadQueuedPackets = hasQueuedPackets();

        if (queueing()) {
            flushExpired();
        } else if (hadQueuedPackets) {
            releaseAll();
            clear(true, true);
        }
    }

    private boolean isTrackedPositionCloser(Entity entity, Vec3 position) {
        Vec3 eye = mc.player.getEyePosition();
        double trackedDistance = makeBoundingBox(entity, position).distanceToSqr(eye);
        double shownDistance = entity.getBoundingBox().distanceToSqr(eye);
        return trackedDistance < shownDistance;
    }

    private boolean hasQueuedPackets() {
        synchronized (storageLock) {
            return !storage.isEmpty();
        }
    }

    private void flushExpired() {
        long cutoff = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(Math.clamp(currentDelay, 0, 2000));
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

    private void clear(boolean flushPackets, boolean resetCooldown) {
        if (flushPackets)
            releaseAll();

        if (target != null && resetCooldown)
            nextBacktrackReadyNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(rollNextBacktrackDelay());

        target = null;
        trackedPosition = null;
    }

    private void resetSimulation() {
        simulatedPosition = null;
        simulatedDeltaMovement = Vec3.ZERO;
        simulatedYaw = 0.0F;
        simulatedPitch = 0.0F;
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

    private void ensureSimulationInitialized(Entity entity) {
        if (simulatedPosition == null)
            initializeSimulation(entity);
    }

    private void initializeSimulation(Entity entity) {
        Vec3 base = entity.getPositionCodec().getBase();
        if (base == null)
            base = entity.position();

        simulatedPositionCodec.setBase(base);
        simulatedPosition = entity.position();
        simulatedDeltaMovement = entity.getDeltaMovement();
        simulatedYaw = entity.getYRot();
        simulatedPitch = entity.getXRot();
        trackedPosition = simulatedPosition;
    }

    private Vec3 simulateMovePacket(Entity entity, ClientboundMoveEntityPacket packet) {
        Vec3 position = simulatedPosition != null ? simulatedPosition : entity.position();

        if (packet.hasPosition()) {
            position = packet.getPositionDelta().decode(simulatedPositionCodec).endPosition();
            simulatedPositionCodec.setBase(position);
            simulatedPosition = position;
        }

        if (packet.hasRotation()) {
            simulatedYaw = packet.getYRot();
            simulatedPitch = packet.getXRot();
        }

        return position;
    }

    private void updateSimulation(Vec3 position, Vec3 deltaMovement, float yaw, float pitch) {
        simulatedPosition = position;
        simulatedDeltaMovement = deltaMovement;
        simulatedYaw = yaw;
        simulatedPitch = pitch;
        simulatedPositionCodec.setBase(position);
    }

    private AABB makeBoundingBox(Entity entity, Vec3 position) {
        double halfWidth = entity.getBbWidth() / 2.0D;
        return new AABB(
                position.x - halfWidth,
                position.y - 0.1D,
                position.z - halfWidth,
                position.x + halfWidth,
                position.y + entity.getBbHeight() + 0.1D,
                position.z + halfWidth
        );
    }

    private record QueuedPacket(Packet<?> packet, long timestampNanos) {
    }
}
