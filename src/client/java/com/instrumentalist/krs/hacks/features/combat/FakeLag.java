package com.instrumentalist.krs.hacks.features.combat;

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
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/**
 * Port of LiquidBounce's FakeLag: your own outgoing packets are held back so the server sees your
 * old position, which keeps you out of the enemy's reach for a moment.
 */
public class FakeLag extends Module {
    private static final int MAX_QUEUED_PACKETS = 1024;

    @Setting
    private final FloatValue range = new FloatValue(
            "Range",
            5f,
            0f,
            10f,
            "m"
    ).describe("Distance at which an enemy is close enough to start lagging.");

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
    ).describe("Cooldown after a flush before lagging again.");

    @Setting
    private final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Dynamic", "Constant"},
            "Dynamic"
    ).describe("Dynamic only lags near enemies, Constant always lags.");

    @Setting
    private final BooleanValue flushOnEntityInteract = new BooleanValue(
            "Flush On Entity Interact",
            true
    ).describe("Send held packets before attacking an entity.");

    @Setting
    private final BooleanValue flushOnBlockInteract = new BooleanValue(
            "Flush On Block Interact",
            true
    ).describe("Send held packets before interacting with a block.");

    @Setting
    private final BooleanValue flushOnAction = new BooleanValue(
            "Flush On Action",
            true
    ).describe("Send held packets before block breaking and similar actions.");

    private final ArrayDeque<QueuedPacket> queue = new ArrayDeque<>();
    private final Object queueLock = new Object();
    private final MSTimer recoilTimer = new MSTimer();

    private volatile boolean flushing;
    private int currentDelay = 300;

    @Override
    public String description() {
        return "Holds back your outgoing packets to fake lag";
    }

    @Override
    public String tag() {
        return mode.get() + " " + currentDelay + "ms";
    }

    public FakeLag() {
        super("Fake Lag", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        currentDelay = rollDelay();
    }

    @Override
    public void onDisable() {
        flush();
    }

    @Override
    public void onWorld(WorldEvent event) {
        dropQueue();
        recoilTimer.reset();
        currentDelay = rollDelay();
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (event == null || event.packet == null || flushing)
            return;
        if (mc.player == null || mc.level == null)
            return;

        Packet<?> packet = event.packet;
        if (!isQueueable(packet))
            return;

        if (!canLag()) {
            // Never let a newer packet overtake packets that are still held back.
            if (hasQueuedPackets())
                flush();
            return;
        }

        if (shouldFlushOn(packet)) {
            flush();
            return;
        }

        if (!shouldQueue())
            return;

        event.cancel();

        boolean overflow;
        synchronized (queueLock) {
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
            return;
        }

        // Blink and Freecam own the outgoing queue while they are active.
        if (BlinkUtil.INSTANCE.getBlinking() || ModuleManager.getModuleState(Freecam.class)) {
            flush();
            return;
        }

        if (!hasQueuedPackets())
            return;

        if (recoilTimer.hasTimePassed(recoilTime.get()) && queueAgeMillis() >= currentDelay)
            flush();
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

        Entity enemy = BehaviorUtils.findEnemy(range.get());
        if (enemy == null)
            return false;

        Vec3 serverPosition = firstQueuedPosition();
        if (serverPosition == null)
            return true;

        AABB serverBox = mc.player.getBoundingBox().move(serverPosition.subtract(mc.player.position()));
        if (serverBox.intersects(enemy.getBoundingBox()))
            return false;

        double serverDistance = enemy.position().distanceTo(serverPosition);
        double clientDistance = enemy.position().distanceTo(mc.player.position());

        // Only lag while it keeps us further away from the enemy than our real position.
        return serverDistance >= clientDistance;
    }

    private boolean shouldFlushOn(Packet<?> packet) {
        if (flushOnEntityInteract.get() && packet instanceof ServerboundInteractPacket)
            return true;
        if (flushOnBlockInteract.get() && packet instanceof ServerboundUseItemOnPacket)
            return true;
        return flushOnAction.get() && packet instanceof ServerboundPlayerActionPacket;
    }

    private boolean isQueueable(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket
                || packet instanceof ServerboundPlayerCommandPacket
                || packet instanceof ServerboundSetCarriedItemPacket
                || packet instanceof ServerboundPlayerActionPacket
                || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundInteractPacket;
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
                try {
                    PacketUtil.sendPacket(pending.removeFirst().packet());
                } catch (RuntimeException ignored) {
                    pending.clear();
                }
            }
        } finally {
            flushing = false;
        }

        recoilTimer.reset();
        currentDelay = rollDelay();
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

    private long queueAgeMillis() {
        long oldest;
        synchronized (queueLock) {
            QueuedPacket head = queue.peekFirst();
            if (head == null)
                return 0L;
            oldest = head.timestampNanos();
        }

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - oldest);
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

    private Vec3 queuedPosition(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.hasPosition())
            return new Vec3(movePacket.getX(0.0D), movePacket.getY(0.0D), movePacket.getZ(0.0D));

        return null;
    }

    private int rollDelay() {
        int min = Math.min(minDelay.get(), maxDelay.get());
        int max = Math.max(minDelay.get(), maxDelay.get());
        return RandomUtil.nextInt(min, max + 1);
    }

    private record QueuedPacket(Packet<?> packet, long timestampNanos, Vec3 position) {
    }
}
