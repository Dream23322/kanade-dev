package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
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
import com.instrumentalist.krs.utils.packet.ServerPositionTracker;
import com.instrumentalist.krs.utils.render.TargetBoxRenderer;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    private final Consumer<Packet<?>> queuedPacketSender;
    private final FakeLagQueue<Packet<?>> queue;
    private final MSTimer recoilTimer = new MSTimer();

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
        this(PacketUtil::sendPacketAsSilent);
    }

    /** Test seam for driving the real outgoing-packet handler without a live connection. */
    FakeLag(Consumer<Packet<?>> queuedPacketSender) {
        super("Fake Lag", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
        this.queuedPacketSender = queuedPacketSender;
        this.queue = new FakeLagQueue<>(MAX_QUEUED_PACKETS, this::sendQueued);
    }

    @Override
    public void onEnable() {
        nextDelay = rollDelay();
        isEnemyNearby = false;
        ServerPositionTracker.reset();
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
        ServerPositionTracker.reset();
        serverPosition = mc.player == null ? null : mc.player.position();
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event == null || event.packet == null || mc.player == null)
            return;

        Packet<?> packet = event.packet;
        if (packet instanceof ClientboundPlayerPositionPacket) {
            // Queued positions are from before the teleport and must not be replayed after it.
            dropQueue();
            serverPosition = mc.player.position();
            ServerPositionTracker.reset();
            return;
        }

        // LiquidBounce flushes on damage, knockback, and explosion so the held position is not
        // sent after the server has already moved or damaged the player.
        if (packet instanceof ClientboundSetHealthPacket) {
            flush();
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.id() == mc.player.getId()
                && motion.movement().lengthSqr() > 0.0d) {
            flush();
            return;
        }

        if (packet instanceof ClientboundExplodePacket explosion
                && explosion.playerKnockback().filter(knockback -> knockback.lengthSqr() > 0.0d).isPresent()) {
            flush();
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (event == null || event.packet == null || event.isCancelled() || queue.isFlushing())
            return;
        if (!isReady()) {
            dropQueue();
            return;
        }

        Packet<?> packet = event.packet;

        FakeLagQueue.ControlAction controlAction = controlAction(packet);
        if (controlAction != FakeLagQueue.ControlAction.NONE) {
            // Teleport acknowledgements drop the queue so old movement is not replayed after
            // the server has already moved us. The packet itself still passes immediately.
            queue.handleControl(controlAction, packet, ignored -> { });
            return;
        }

        // LiquidBounce does not exempt client tick ends from the queue. Holding the tick end
        // together with its movement keeps the server's client tick count aligned with the
        // delayed positions; passing every tick end while holding movement makes the server
        // process empty ticks and then a position burst, which Grim reports as Simulation.

        // BlinkManager still fires the module for these, then lets them through without queueing.
        boolean alwaysPassed = isAlwaysPassed(packet);

        if (shouldFlushOn(packet)) {
            recoilTimer.reset();
            // LiquidBounce's BlinkManager default action flushes the held movement before the
            // attack packet. The current tick's movement and tick end then enter the queue like
            // any other held tick, matching BlinkManager's ordering.
            flush();
            updateServerPosition(packet);
            return;
        }

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

        if (!queue.hasQueued())
            serverPosition = mc == null || mc.player == null ? null : mc.player.position();
        boolean overflow = queue.enqueue(packet, System.nanoTime());

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
        if (!render.get() || player == null || Client.nanoVgManager == null
                || mc.options.getCameraType().isFirstPerson())
            return;

        Vec3 position = serverPosition;
        if (position == null || !hasQueuedPackets())
            return;

        Client.nanoVgManager.loadWorldAnchored(vg ->
                TargetBoxRenderer.render(player, position, vg, walls.get()));
    }

    boolean isReady() {
        return mc.player != null && mc.level != null;
    }

    boolean canLag() {
        if (BlinkUtil.INSTANCE.getBlinking() || ModuleManager.getModuleState(Freecam.class))
            return false;

        if (mc.player.isDeadOrDying() || mc.player.isInWater() || mc.gui.screen() != null)
            return false;

        return recoilTimer.hasTimePassed(recoilTime.get());
    }

    boolean shouldQueue() {
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

    static FakeLagQueue.ControlAction controlAction(Packet<?> packet) {
        if (packet instanceof ServerboundAcceptTeleportationPacket)
            return FakeLagQueue.ControlAction.DROP_QUEUE;

        return FakeLagQueue.ControlAction.NONE;
    }

    private static boolean isAlwaysPassed(Packet<?> packet) {
        return packet instanceof ClientIntentionPacket
                || packet instanceof ServerboundStatusRequestPacket
                || packet instanceof ServerboundPingRequestPacket
                || packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket;
    }

    private boolean isConsuming() {
        return mc != null
                && mc.player != null
                && mc.player.isUsingItem()
                && mc.player.getUseItem().get(DataComponents.CONSUMABLE) != null;
    }

    private boolean isAboveTime() {
        long first = queue.firstTimestampNanos();
        if (first < 0L)
            return false;

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - first) >= nextDelay;
    }

    private void flush() {
        queue.flush();
    }

    private void sendQueued(Packet<?> packet) {
        // BlinkManager flushes silently, bypassing packet handlers.
        queuedPacketSender.accept(packet);
        Vec3 position = queuedPosition(packet);
        if (position != null)
            serverPosition = position;
    }

    private void dropQueue() {
        queue.drop();
    }

    private boolean hasQueuedPackets() {
        return queue.hasQueued();
    }

    private Vec3 firstQueuedPosition() {
        Packet<?> packet = queue.firstMatching(candidate -> queuedPosition(candidate) != null);
        return packet == null ? null : queuedPosition(packet);
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

}
