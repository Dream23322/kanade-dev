package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.render.TargetBoxRenderer;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Port of LiquidBounce's FakeLag.
 *
 * <p>Every outgoing packet is considered for the queue. A packet that LiquidBounce would not
 * queue acts as its default {@code FLUSH} action: held packets are sent first, then the packet
 * itself passes through.</p>
 */
public class FakeLag extends Module {
    private static final int MAX_QUEUED_PACKETS = 1024;

    @Setting
    private final FloatValue minRange = new FloatValue(
            "Min Range",
            2f,
            0f,
            10f,
            "m"
    ).describe("Closest distance at which an enemy starts FakeLag. LiquidBounce's Range starts at 2.");

    @Setting
    private final FloatValue range = new FloatValue(
            "Range",
            5f,
            0f,
            10f,
            "m"
    ).describe("Furthest distance at which an enemy starts FakeLag. LiquidBounce's Range ends at 5.");

    @Setting
    private final BooleanValue render = new BooleanValue(
            "Render",
            true
    ).describe("Draws a theme-coloured box at the position the server sees you at.");

    @Setting
    private final BooleanValue walls = new BooleanValue(
            "Walls",
            true
    ).describe("Draws filled target-box walls without the glowing outline.");

    @Setting
    private final IntValue minDelay = new IntValue(
            "Min Delay",
            300,
            0,
            1000,
            "ms"
    ).describe("Shortest time packets are held back.");

    @Setting
    private final IntValue maxDelay = new IntValue(
            "Max Delay",
            600,
            0,
            1000,
            "ms"
    ).describe("Longest time packets are held back.");

    @Setting
    private final IntValue recoilTime = new IntValue(
            "Recoil Time",
            250,
            0,
            1000,
            "ms"
    ).describe("Cooldown after an interaction flush before lagging again.");

    @Setting
    private final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Constant", "Dynamic"},
            "Dynamic"
    ).describe("Dynamic only lags near enemies, Constant always lags.");

    @Setting
    private final BooleanValue flushOnEntityInteract = new BooleanValue(
            "Flush On Entity Interact",
            true
    ).describe("Flush held packets before attacking or interacting with an entity.");

    @Setting
    private final BooleanValue flushOnBlockInteract = new BooleanValue(
            "Flush On Block Interact",
            true
    ).describe("Flush held packets before interacting with a block or signing.");

    @Setting
    private final BooleanValue flushOnAction = new BooleanValue(
            "Flush On Action",
            true
    ).describe("Flush held packets before block breaking and similar actions.");

    private final ArrayDeque<QueuedPacket> queue = new ArrayDeque<>();
    private final Object queueLock = new Object();
    private final MSTimer recoilTimer = new MSTimer();

    private volatile boolean flushing;
    private volatile Vec3 serverPosition;
    private int nextDelay = 300;
    private boolean isEnemyNearby;

    @Override
    public String description() {
        return "Holds back your outgoing packets to fake lag";
    }

    @Override
    public String tag() {
        return mode.get() + " " + nextDelay + "ms";
    }

    public FakeLag() {
        super("Fake Lag", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        nextDelay = rollDelay();
        isEnemyNearby = false;
        serverPosition = mc.player == null ? null : mc.player.position();
    }

    @Override
    public void onDisable() {
        flush();
        isEnemyNearby = false;
        serverPosition = null;
    }

    @Override
    public void onWorld(WorldEvent event) {
        dropQueue();
        nextDelay = rollDelay();
        isEnemyNearby = false;
        serverPosition = mc.player == null ? null : mc.player.position();
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (event == null || event.packet == null || event.isCancelled() || flushing)
            return;
        if (mc.player == null || mc.level == null) {
            dropQueue();
            return;
        }

        Packet<?> packet = event.packet;

        // BlinkManager still fires the module for these, then lets them through without queueing.
        boolean alwaysPassed = isAlwaysPassed(packet);

        // Returning without QUEUE means BlinkManager's default action, FLUSH.
        if (!canLag()) {
            flush();
            updateServerPosition(packet);
            return;
        }

        if (isAboveTime()) {
            nextDelay = rollDelay();
            flush();
            updateServerPosition(packet);
            return;
        }

        if (shouldFlushOn(packet)) {
            recoilTimer.reset();
            flush();
            updateServerPosition(packet);
            return;
        }

        if (packet instanceof ServerboundResourcePackPacket) {
            recoilTimer.reset();
            flush();
            updateServerPosition(packet);
            return;
        }

        if (isConsuming()) {
            flush();
            updateServerPosition(packet);
            return;
        }

        if (!shouldQueue()) {
            flush();
            updateServerPosition(packet);
            return;
        }

        // Chat and connection packets are never queued, even when the lag action is QUEUE.
        if (alwaysPassed)
            return;

        event.cancel();

        boolean overflow;
        synchronized (queueLock) {
            if (queue.isEmpty())
                serverPosition = mc.player == null ? null : mc.player.position();
            queue.addLast(new QueuedPacket(packet, System.nanoTime(), queuedPosition(packet)));
            overflow = queue.size() >= MAX_QUEUED_PACKETS;
        }

        if (overflow)
            flush();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) {
            dropQueue();
            isEnemyNearby = false;
            return;
        }

        isEnemyNearby = BehaviorUtils.findEnemy(minRange.get(), maxRange()) != null;

        // Blink and Freecam own the outgoing queue while they are active.
        if (BlinkUtil.INSTANCE.getBlinking() || ModuleManager.getModuleState(Freecam.class)) {
            flush();
            return;
        }

        if (!hasQueuedPackets())
            return;

        // BlinkManager fires the module once per render task with a null packet. With no packet,
        // every non-queue result is still FLUSH, so the same conditions empty the queue here.
        if (!canLag()) {
            flush();
            return;
        }

        if (isAboveTime()) {
            nextDelay = rollDelay();
            flush();
            return;
        }

        if (isConsuming() || !shouldQueue())
            flush();
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        var player = mc.player;
        if (!render.get() || player == null || Client.nanoVgManager == null)
            return;

        Vec3 position = serverPosition;
        if (position == null || !hasQueuedPackets())
            return;

        Client.nanoVgManager.loadWorldAnchored(vg ->
                TargetBoxRenderer.render(player, position, vg, walls.get()));
    }

    private boolean canLag() {
        if (BlinkUtil.INSTANCE.getBlinking() || ModuleManager.getModuleState(Freecam.class))
            return false;

        if (mc.player.isDeadOrDying() || mc.player.isInWater() || mc.gui.screen() != null)
            return false;

        return recoilTimer.hasTimePassed(recoilTime.get());
    }

    private boolean shouldQueue() {
        if (!mode.get().equalsIgnoreCase("Dynamic"))
            return true;

        // If there is an enemy in range, we want to lag.
        if (!isEnemyNearby)
            return false;

        Vec3 position = firstQueuedPosition();
        if (position == null) {
            // No position has been queued yet, so start the queue from here.
            return true;
        }

        AABB playerBox = mc.player.getDimensions(mc.player.getPose()).makeBoundingBox(position);
        List<Entity> entities = entitiesNear(position);
        if (entities.isEmpty())
            return false;

        boolean intersects = false;
        double serverDistance = Double.MAX_VALUE;
        double clientDistance = Double.MAX_VALUE;
        for (Entity entity : entities) {
            // LiquidBounce's Entity.box includes the pick radius.
            if (entity.getBoundingBox().inflate(entity.getPickRadius()).intersects(playerBox))
                intersects = true;

            double toServer = entity.position().distanceTo(position);
            if (toServer < serverDistance)
                serverDistance = toServer;

            double toClient = entity.position().distanceTo(mc.player.position());
            if (toClient < clientDistance)
                clientDistance = toClient;
        }

        // If the server position is not closer than the client position, we keep lagging.
        // Also, do not lag while the old position intersects an entity.
        return serverDistance >= clientDistance && !intersects;
    }

    private List<Entity> entitiesNear(Vec3 position) {
        List<Entity> entities = new ArrayList<>(4);
        if (mc.player == null || mc.level == null)
            return entities;

        double maxRangeSquared = maxRange() * maxRange();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!BehaviorUtils.isValidCombatTarget(entity))
                continue;

            // LiquidBounce checks every attackable entity in the max range of the old server
            // position; the min range only applies to the initial findEnemy call.
            if (entity.getBoundingBox()
                    .inflate(entity.getPickRadius())
                    .distanceToSqr(position) > maxRangeSquared)
                continue;

            entities.add(entity);
        }

        return entities;
    }

    private boolean shouldFlushOn(Packet<?> packet) {
        if (flushOnEntityInteract.get()
                && (packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundAttackPacket
                || packet instanceof ServerboundSpectatorActionPacket
                || packet instanceof ServerboundPunchPacket))
            return true;

        if (flushOnBlockInteract.get()
                && (packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundSignUpdatePacket))
            return true;

        return flushOnAction.get() && packet instanceof ServerboundPlayerActionPacket;
    }

    private static boolean isAlwaysPassed(Packet<?> packet) {
        return packet instanceof ClientIntentionPacket
                || packet instanceof ServerboundStatusRequestPacket
                || packet instanceof ServerboundPingRequestPacket
                || packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket;
    }

    private boolean isConsuming() {
        return mc.player != null
                && mc.player.isUsingItem()
                && mc.player.getUseItem().get(DataComponents.CONSUMABLE) != null;
    }

    private boolean isAboveTime() {
        long first;
        synchronized (queueLock) {
            QueuedPacket head = queue.peekFirst();
            if (head == null)
                return false;
            first = head.timestampNanos();
        }

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - first) >= nextDelay;
    }

    private void flush() {
        ArrayDeque<QueuedPacket> pending;
        synchronized (queueLock) {
            if (queue.isEmpty())
                return;
            pending = new ArrayDeque<>(queue);
            queue.clear();
        }

        flushing = true;
        try {
            while (!pending.isEmpty()) {
                QueuedPacket queued = pending.removeFirst();
                try {
                    // BlinkManager flushes silently, bypassing packet handlers.
                    PacketUtil.sendPacketAsSilent(queued.packet());
                    if (queued.position() != null)
                        serverPosition = queued.position();
                } catch (RuntimeException ignored) {
                    pending.clear();
                }
            }
        } finally {
            flushing = false;
        }
    }

    private void dropQueue() {
        synchronized (queueLock) {
            queue.clear();
        }
    }

    private boolean hasQueuedPackets() {
        synchronized (queueLock) {
            return !queue.isEmpty();
        }
    }

    private Vec3 firstQueuedPosition() {
        synchronized (queueLock) {
            for (QueuedPacket queued : queue) {
                if (queued.position() != null)
                    return queued.position();
            }
        }

        return null;
    }

    private void updateServerPosition(Packet<?> packet) {
        Vec3 position = queuedPosition(packet);
        if (position != null)
            serverPosition = position;
    }

    private Vec3 queuedPosition(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.hasPosition())
            return new Vec3(movePacket.getX(0.0D), movePacket.getY(0.0D), movePacket.getZ(0.0D));

        return null;
    }

    private float maxRange() {
        return Math.max(minRange.get(), range.get());
    }

    private int rollDelay() {
        int min = Math.min(minDelay.get(), maxDelay.get());
        int max = Math.max(minDelay.get(), maxDelay.get());
        return RandomUtil.nextInt(min, max + 1);
    }

    private record QueuedPacket(Packet<?> packet, long timestampNanos, Vec3 position) {
    }
}
