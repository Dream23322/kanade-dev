package com.instrumentalist.krs.hacks.features.movement.fly.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Hovers a bit over a block up at a decaying speed, re-boosting with a self damage burst once you
 * touch the ground again.
 */
public class FlyRewound implements FlyEvent {

    private static final double SELF_DAMAGE_OFFSET = 0.425;
    private static final double SELF_DAMAGE_HEIGHT = 3.0;

    private int stage;
    private double moveSpeed;
    private Double motionY;
    private int boostTick = Integer.MAX_VALUE;

    @Override
    public String getName() {
        return "Rewound";
    }

    public void reset() {
        stage = 0;
        moveSpeed = 0.0;
        motionY = null;
        boostTick = Integer.MAX_VALUE;
    }

    public void disable() {
        MovementUtil.stopXZ();
        TimerUtil.reset();
        reset();
    }

    private double currentSpeed() {
        if (mc.player == null || mc.player.horizontalCollision || !MovementUtil.isMoving())
            return MovementUtil.ncpBaseMoveSpeed();

        return moveSpeed;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        // Stand-in for LiquidBounce's tick coroutine: stage 0..3 are the one tick waits of the
        // launch sequence, stage 4 is its endless hover loop.
        switch (stage) {
            case 0 -> {
                if (!mc.player.onGround()) return;

                selfDamage();
                stage = 1;
            }
            case 1 -> {
                moveSpeed = MovementUtil.ncpBaseMoveSpeed(0.3);
                stage = 2;
            }
            case 2 -> {
                moveSpeed = currentSpeed() * 2.149;
                motionY = 0.3999;
                stage = 3;
            }
            case 3 -> {
                moveSpeed = MovementUtil.ncpBaseMoveSpeed() * FlyModule.rewoundSpeed.get();
                boostTick = 0;
                stage = 4;
                requestTimer();
            }
            default -> {
                requestTimer();
                moveSpeed = currentSpeed() - currentSpeed() / FlyModule.rewoundDecay.get();
                motionY = 0.0;
                if (!MovementUtil.isGroundWithin(1e-4))
                    mc.player.setPos(mc.player.getX(), mc.player.getY() - 1e-14, mc.player.getZ());
            }
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || event.type != MoverType.SELF) return;

        double y = event.movement.y;
        if (motionY != null) {
            y = motionY;
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motion.x, motionY, motion.z);
            motionY = null;
        }

        double speed = Math.max(moveSpeed, MovementUtil.ncpBaseMoveSpeed());
        Vec3 strafe = MovementUtil.getMovementVector(speed);
        event.movement = new Vec3(strafe.x, y, strafe.z);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket && motionPacket.id() == mc.player.getId()) {
            event.cancel();
            return;
        }

        if (packet instanceof ClientboundPlayerPositionPacket) {
            moveSpeed = MovementUtil.ncpBaseMoveSpeed();
            boostTick = Integer.MAX_VALUE;
        }
    }

    @Override
    public void onBlock(BlockEvent event) {
    }

    private void requestTimer() {
        float normal = FlyModule.rewoundTimer.get() ? 1.0766f : 1f;
        int boostTicks = FlyModule.rewoundBoostTicks.get();

        float speed = normal;
        if (boostTick < boostTicks && FlyModule.rewoundBoostTimer.get() > normal) {
            speed = FlyModule.rewoundBoostTimer.get()
                    - (FlyModule.rewoundBoostTimer.get() - normal) * boostTick / boostTicks;
        }

        if (boostTick < Integer.MAX_VALUE)
            boostTick++;

        TimerUtil.timerSpeed = speed;
    }

    private void selfDamage() {
        if (mc.player == null) return;

        FlyModule.noteSelfDamage();
        int iterations = (int) Math.ceil(SELF_DAMAGE_HEIGHT / SELF_DAMAGE_OFFSET);
        Vec3 pos = mc.player.position();
        boolean collision = mc.player.horizontalCollision;

        for (int i = 0; i <= iterations; i++) {
            PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + SELF_DAMAGE_OFFSET, pos.z, false, collision));
            PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, i == iterations, collision));
        }
    }
}
