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
import com.instrumentalist.krs.hacks.ModuleManager;
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
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Two backtrack implementations:
 *
 * <ul>
 *     <li>{@code Liquid} - port of LiquidBounce's Backtrack. Incoming target position packets are
 *     queued through a {@link VecDeltaCodec} base that starts at the target's server position.</li>
 *     <li>{@code Myau} - port of OpenMyau-Plus's BackTrack. Positions are tracked per packet, the
 *     queue is cancelled while the tracked position is farther than the live one, and the combat
 *     lock keeps the last hit target.</li>
 * </ul>
 */
public class Backtrack extends Module {
    private static final int MAX_STORED_PACKETS = 512;
    private static final int MIN_TARGET_TICKS = 10;
    private static final long MYAU_COMBAT_LOCK_MS = 3000L;
    private static final double MIN_LINE_DISTANCE_SQUARED = 0.05D * 0.05D;
    private static final Object TARGET_LINE = new Object();
    private static final Object SELF_LINE = new Object();

    @Setting
    private final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Liquid", "Myau"},
            "Liquid"
    ).describe("Liquid uses the LiquidBounce port, Myau the OpenMyau-Plus backtrack.");

    // ==================== Liquid ====================

    @Setting
    private final ListValue targetMode = new ListValue(
            "Target Mode",
            new String[]{"Attack", "Range"},
            "Attack",
            this::isLiquidMode
    ).describe("Attack backtracks the entity you hit, Range the nearest enemy in range.");

    @Setting
    private final BooleanValue render = new BooleanValue(
            "Render",
            true,
            this::isLiquidMode
    ).describe("Draws a theme-coloured box around the backtracked target.");

    @Setting
    private final BooleanValue walls = new BooleanValue(
            "Walls",
            true,
            this::isLiquidMode
    ).describe("Draws filled target-box walls without the glowing outline.");

    @Setting
    private final FloatValue minRange = new FloatValue(
            "Min Range",
            1f,
            0f,
            10f,
            "m",
            this::isLiquidMode
    ).describe("Closest distance at which the target is backtracked.");

    @Setting
    private final FloatValue maxRange = new FloatValue(
            "Max Range",
            3f,
            0f,
            10f,
            "m",
            this::isLiquidMode
    ).describe("Furthest distance at which the target is backtracked.");

    @Setting
    private final IntValue minDelay = new IntValue(
            "Min Delay",
            100,
            0,
            1000,
            "ms",
            this::isLiquidMode
    ).describe("Shortest time incoming positions are held back.");

    @Setting
    private final IntValue maxDelay = new IntValue(
            "Max Delay",
            150,
            0,
            1000,
            "ms",
            this::isLiquidMode
    ).describe("Longest time incoming positions are held back.");

    @Setting
    private final IntValue minNextBacktrackDelay = new IntValue(
            "Next Min Delay",
            0,
            0,
            2000,
            "ms",
            this::isLiquidMode
    ).describe("Shortest cooldown before backtracking again.");

    @Setting
    private final IntValue maxNextBacktrackDelay = new IntValue(
            "Next Max Delay",
            10,
            0,
            2000,
            "ms",
            this::isLiquidMode
    ).describe("Longest cooldown before backtracking again.");

    @Setting
    private final IntValue trackingBuffer = new IntValue(
            "Track Buffer",
            500,
            0,
            2000,
            "ms",
            this::isLiquidMode
    ).describe("How long a target is still backtracked after leaving the range.");

    @Setting
    private final FloatValue chance = new FloatValue(
            "Chance",
            50f,
            0f,
            100f,
            "%",
            this::isLiquidMode
    ).describe("Probability that backtracking engages on an attack.");

    @Setting
    private final BooleanValue pauseOnHurtTime = new BooleanValue(
            "Pause On Hurt",
            false,
            this::isLiquidMode
    ).describe("Pause backtracking while the target is in its hurt animation.");

    @Setting
    private final IntValue hurtTime = new IntValue(
            "Hurt Time",
            3,
            0,
            10,
            "ticks",
            () -> isLiquidMode() && pauseOnHurtTime.get()
    ).describe("Minimum remaining hurt ticks that pause backtracking.");

    @Setting
    private final IntValue lastAttackTimeToWork = new IntValue(
            "Attack Window",
            1000,
            0,
            5000,
            "ms",
            this::isLiquidMode
    ).describe("How long after attacking backtracking stays active.");

    // ==================== Myau ====================

    @Setting
    private final FloatValue myauRange = new FloatValue(
            "Myau Range",
            3.5f,
            1f,
            8f,
            "m",
            this::isMyauMode
    ).describe("Distance the Myau backtrack keeps a target at.");

    @Setting
    private final BooleanValue myauAdaptive = new BooleanValue(
            "Myau Adaptive",
            true,
            this::isMyauMode
    ).describe("Only holds positions while the tracked position is farther than the live one.");

    @Setting
    private final IntValue myauNormalDelay = new IntValue(
            "Myau Normal Delay",
            100,
            50,
            1000,
            "ms",
            () -> isMyauMode() && !myauAdaptive.get()
    ).describe("How long incoming positions are held back when adaptive is off.");

    @Setting
    private final IntValue myauAdaptiveDelay = new IntValue(
            "Myau Adapt Delay",
            100,
            50,
            1000,
            "ms",
            () -> isMyauMode() && myauAdaptive.get()
    ).describe("Maximum time incoming positions are held back in adaptive mode.");

    @Setting
    private final BooleanValue myauReleaseOnHit = new BooleanValue(
            "Myau Release Hit",
            true,
            this::isMyauMode
    ).describe("Releases the queue as soon as the target starts its hurt animation.");

    @Setting
    private final IntValue myauLastAttackTimeToWork = new IntValue(
            "Myau Hit Window",
            1000,
            0,
            5000,
            "ms",
            this::isMyauMode
    ).describe("How long after attacking the Myau backtrack engages.");

    @Setting
    private final BooleanValue myauPlayers = new BooleanValue(
            "Myau Players",
            true,
            this::isMyauMode
    ).describe("Only backtrack players.");

    @Setting
    private final BooleanValue myauTeams = new BooleanValue(
            "Myau Teams",
            true,
            this::isMyauMode
    ).describe("Ignores players on your own team.");

    @Setting
    private final BooleanValue myauBotCheck = new BooleanValue(
            "Myau Bot Check",
            true,
            this::isMyauMode
    ).describe("Ignores entities the Anti Bot module marked as bots.");

    @Setting
    private final BooleanValue myauEsp = new BooleanValue(
            "Myau ESP",
            true,
            this::isMyauMode
    ).describe("Draws a box at the tracked (server) position.");

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

    private final VecDeltaCodec myauPositionCodec = new VecDeltaCodec();
    private final MSTimer myauAttackTimer = new MSTimer();
    private Entity myauTarget;
    private LivingEntity myauLastAttacked;
    private Vec3 myauTrackedPosition;
    private long myauLastAttackMs;
    private long myauBacktrackStartMs;
    private int myauLastHurtTime;
    private String activeMode = "Liquid";

    @Override
    public String migrateSettingName(String name) {
        return switch (name) {
            case "Min Next Backtrack Delay" -> "Next Min Delay";
            case "Max Next Backtrack Delay" -> "Next Max Delay";
            case "Tracking Buffer" -> "Track Buffer";
            case "Pause On Hurt Time" -> "Pause On Hurt";
            case "Last Attack Time To Work" -> "Attack Window";
            case "Myau Last Attack Time", "Myau Attack Window" -> "Myau Hit Window";
            case "Myau Adaptive Delay" -> "Myau Adapt Delay";
            case "Myau Release On Hit" -> "Myau Release Hit";
            default -> name;
        };
    }

    @Override
    public String description() {
        return "Delays incoming hit packets so you can reach further";
    }

    @Override
    public String tag() {
        return isMyauMode()
                ? "Myau " + myauMaxDelay() + "ms"
                : targetMode.get() + " " + currentDelay + "ms";
    }

    public Backtrack() {
        super("Backtrack", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    public boolean isBacktracking() {
        return tempEnabled && hasQueuedPackets();
    }

    /**
     * Returns the latest position the server knows for the entity. While positions are queued,
     * the client entity still renders at an older position, so reach checks must use this one.
     */
    public Vec3 getServerPosition(Entity entity) {
        if (entity == null)
            return null;

        if (isMyauMode()) {
            if (entity == myauTarget && myauTrackedPosition != null)
                return myauTrackedPosition;
        } else if (entity == target && trackedPosition != null) {
            return trackedPosition;
        }

        return entity.position();
    }

    @Override
    public void onEnable() {
        currentDelay = rollDelay();
        nextBacktrackReadyNanos = 0L;
        clear(false, false);
        activeMode = mode.get();
        resetMyauState(false);
    }

    @Override
    public void onDisable() {
        clear(true, true);
        shouldPause = false;
        ServerPositionTracker.reset();
        resetMyauState(false);
        PathLines.hide(this, TARGET_LINE);
        PathLines.hide(this, SELF_LINE);
    }

    @Override
    public void onWorld(WorldEvent event) {
        dropQueue();
        clear(false, false);
        ServerPositionTracker.reset();
        resetMyauState(false);
        PathLines.hide(this, TARGET_LINE);
        PathLines.hide(this, SELF_LINE);
    }

    @Override
    public void onAttack(AttackEvent event) {
        // LiquidBounce updates the last-attack timer and rolls the chance for every attack.
        attackTimer.reset();
        chancePassed = RandomUtil.nextFloat(0f, 100f) < chance.get();

        if (isMyauMode()) {
            myauAttackTimer.reset();
            if (event.entity instanceof LivingEntity living) {
                myauLastAttacked = living;
                myauLastAttackMs = System.currentTimeMillis();
            }
            return;
        }

        if (!isRangeMode())
            processTarget(event.entity);
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        handleModeChange();

        if (mc.player == null || mc.level == null) {
            dropQueue();
            clear(false, false);
            resetMyauState(false);
            return;
        }

        if (isMyauMode()) {
            myauTick();
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
        if (isMyauMode()) {
            handleMyauPacket(event);
            return;
        }

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

        // Only delay packets that carry the tracked target's position. Queuing unrelated
        // clientbound packets (especially transactions) disturbs Post's tick ordering.
        if (!shouldQueueTrackedPacket(packet, current))
            return;

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
        if (isMyauMode())
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
        if (isMyauMode()) {
            renderMyauHud(event);
            return;
        }

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

    private void renderMyauHud(RenderHudEvent event) {
        Entity current = myauTarget;
        if (!myauEsp.get() || current == null || current.isRemoved() || Client.nanoVgManager == null)
            return;

        Vec3 position = isBacktracking() && myauTrackedPosition != null
                ? myauTrackedPosition
                : RenderUtil.INSTANCE.getLerpedPos(current, event.tickDelta);
        if (position == null)
            return;

        Client.nanoVgManager.loadWorldAnchored(vg -> TargetBoxRenderer.render(current, position, vg, true));
    }

    // ==================== Mode handling ====================

    private boolean isMyauMode() {
        return "Myau".equalsIgnoreCase(mode.get());
    }

    private boolean isLiquidMode() {
        return !isMyauMode();
    }

    private void handleModeChange() {
        String current = mode.get();
        if (current.equalsIgnoreCase(activeMode))
            return;

        activeMode = current;
        releaseAll();
        clear(false, false);
        resetMyauState(false);
    }

    // ==================== Myau ====================

    private void myauTick() {
        Entity newTarget = resolveMyauTarget();

        if (newTarget != myauTarget) {
            releaseAll();
            myauTrackedPosition = null;
            myauBacktrackStartMs = 0L;
        }
        myauTarget = newTarget;

        if (myauTarget == null)
            return;

        if (myauTrackedPosition == null) {
            Vec3 position = myauTarget.position();
            myauTrackedPosition = new Vec3(
                    Math.floor(position.x * 32.0) / 32.0,
                    Math.floor(position.y * 32.0) / 32.0,
                    Math.floor(position.z * 32.0) / 32.0
            );
            myauPositionCodec.setBase(myauTrackedPosition);
        }

        int hurtTime = mc.player.hurtTime;
        boolean selfHurt = hurtTime > myauLastHurtTime && hurtTime > 0;
        myauLastHurtTime = hurtTime;

        boolean shouldRelease = selfHurt
                || myauTrackedPosition == null
                || myauDistanceTo(myauTrackedPosition) > myauRange.get()
                || myauTimedOut()
                || myauReleaseOnHit.get() && myauTarget instanceof LivingEntity living && living.hurtTime == 1;

        if (shouldRelease || hasQueuedPackets() && !myauCanBacktrack()) {
            releaseAll();
            myauBacktrackStartMs = 0L;
        }
    }

    private void handleMyauPacket(ReceivedPacketEvent event) {
        if (event == null || event.packet == null || event.isCancelled() || mc.player == null || mc.level == null)
            return;

        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundDisconnectPacket) {
            clear(true, true);
            resetMyauState(false);
            return;
        }

        if (packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket) {
            releaseAll();
            myauBacktrackStartMs = 0L;
            return;
        }

        Entity current = myauTarget;
        if (current == null)
            return;

        Vec3 position = trackMyauPosition(packet, current);
        if (position != null
                && squareBoxedDistanceTo(current, position, mc.player) < boxedDistanceSquared(current, mc.player)) {
            releaseAll();
            myauBacktrackStartMs = 0L;
            return;
        }

        boolean canBacktrack = myauCanBacktrack();
        if (!canBacktrack || !shouldQueueMyauPacket(packet, current)) {
            if (!canBacktrack && hasQueuedPackets()) {
                releaseAll();
                myauBacktrackStartMs = 0L;
            }
            return;
        }

        if (myauBacktrackStartMs == 0L)
            myauBacktrackStartMs = System.currentTimeMillis();

        event.cancel();

        boolean overflow;
        synchronized (storageLock) {
            storage.addLast(new QueuedPacket(packet, System.nanoTime()));
            overflow = storage.size() >= MAX_STORED_PACKETS;
        }

        if (overflow) {
            releaseAll();
            myauBacktrackStartMs = 0L;
        }
    }

    private Entity resolveMyauTarget() {
        if (ModuleManager.getModuleState(KillAura.class)
                && KillAura.closestEntity instanceof LivingEntity auraTarget
                && auraTarget.isAlive())
            return auraTarget;

        if (myauLastAttacked != null
                && myauLastAttacked.isAlive()
                && System.currentTimeMillis() - myauLastAttackMs <= MYAU_COMBAT_LOCK_MS
                && mc.player.distanceTo(myauLastAttacked) <= myauRange.get() * 2.0f)
            return myauLastAttacked;

        Entity best = null;
        float bestAngle = Float.MAX_VALUE;
        for (Entity entity : mc.level.players()) {
            if (!isValidMyauTarget(entity))
                continue;
            if (mc.player.distanceTo(entity) > myauRange.get())
                continue;

            float angle = myauAngleTo(entity);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    private boolean isValidMyauTarget(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.player.getVehicle())
            return false;
        if (entity == mc.getCameraEntity())
            return false;
        if (!(entity instanceof Player player))
            return false;
        if (!myauPlayers.get())
            return false;
        if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator())
            return false;
        if (myauTeams.get() && BehaviorUtils.isTeammate(player, true))
            return false;

        return !myauBotCheck.get() || !BehaviorUtils.isBot(player, true);
    }

    private boolean myauCanBacktrack() {
        Entity current = myauTarget;
        if (current == null || !current.isAlive() || myauTrackedPosition == null)
            return false;
        if (mc.player.tickCount <= MIN_TARGET_TICKS)
            return false;
        if (myauAttackTimer.hasTimePassed(myauLastAttackTimeToWork.get()))
            return false;
        if (myauDistanceTo(myauTrackedPosition) > myauRange.get() || myauTimedOut())
            return false;

        double realDistance = myauDistanceTo(myauTrackedPosition);
        double currentDistance = mc.player.distanceTo(current);
        return myauAdaptive.get() ? realDistance > currentDistance : realDistance > currentDistance + 0.1;
    }

    private boolean myauTimedOut() {
        return myauBacktrackStartMs > 0L
                && System.currentTimeMillis() - myauBacktrackStartMs > myauMaxDelay();
    }

    private int myauMaxDelay() {
        return myauAdaptive.get() ? myauAdaptiveDelay.get() : myauNormalDelay.get();
    }

    private Vec3 trackMyauPosition(Packet<?> packet, Entity current) {
        Vec3 trackedPos;

        if (packet instanceof ClientboundMoveEntityPacket movePacket) {
            if (mc.level == null || movePacket.getEntity(mc.level) != current)
                return null;
            trackedPos = movePacket.getPositionDelta().decode(myauPositionCodec).endPosition();
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

        myauPositionCodec.setBase(trackedPos);
        myauTrackedPosition = trackedPos;
        return trackedPos;
    }

    private boolean shouldQueueMyauPacket(Packet<?> packet, Entity current) {
        if (packet instanceof ClientboundMoveEntityPacket movePacket)
            return mc.level != null && movePacket.getEntity(mc.level) == current;
        if (packet instanceof ClientboundTeleportEntityPacket teleportPacket)
            return teleportPacket.id() == current.getId();
        if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket)
            return syncPacket.id() == current.getId();
        if (packet instanceof ClientboundRotateHeadPacket headPacket)
            return mc.level != null && headPacket.getEntity(mc.level) == current;

        return false;
    }

    private double myauDistanceTo(Vec3 position) {
        return mc.player.position().distanceTo(position);
    }

    private float myauAngleTo(Entity entity) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 delta = entity.getBoundingBox().getCenter().subtract(eye);
        if (delta.lengthSqr() < 1.0E-6)
            return 0.0f;

        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG);
        float yawDiff = Math.abs(Mth.wrapDegrees(yaw - mc.player.getYRot()));
        float pitchDiff = Math.abs(pitch - mc.player.getXRot());
        return yawDiff + pitchDiff;
    }

    private void resetMyauState(boolean releasePackets) {
        if (releasePackets)
            releaseAll();

        myauTarget = null;
        myauLastAttacked = null;
        myauTrackedPosition = null;
        myauLastAttackMs = 0L;
        myauBacktrackStartMs = 0L;
        myauLastHurtTime = 0;
    }

    // ==================== Liquid ====================

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

    private boolean shouldQueueTrackedPacket(Packet<?> packet, Entity current) {
        if (packet instanceof ClientboundMoveEntityPacket movePacket)
            return mc.level != null && movePacket.getEntity(mc.level) == current;
        if (packet instanceof ClientboundTeleportEntityPacket teleportPacket)
            return teleportPacket.id() == current.getId();
        if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket)
            return syncPacket.id() == current.getId();
        if (packet instanceof ClientboundRotateHeadPacket headPacket)
            return mc.level != null && headPacket.getEntity(mc.level) == current;

        return false;
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
