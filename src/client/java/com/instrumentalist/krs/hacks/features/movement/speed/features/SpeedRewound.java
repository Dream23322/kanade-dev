package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Hops with the NoCheatPlus allowed distance and keeps a short recovery phase after every setback
 * or wall hit, so the server never sees the boosted speeds back to back.
 */
public class SpeedRewound implements SpeedEvent {

    private static final long SETBACK_WINDOW_MS = 500L;

    private enum Phase {
        /** Base speed for {@code phaseTicks} moves after a setback or wall hit. */
        RECOVER,
        BOOST,
        AIR,
    }

    private Phase phase = Phase.AIR;
    private int phaseTicks = 2;
    private boolean collided;
    private int stairTicks;
    private double hopLoad;
    private boolean slowHop;
    private boolean slowAfterSetback;
    private long lastSetback = System.currentTimeMillis();

    @Override
    public String getName() {
        return "Rewound";
    }

    public void reset() {
        phase = Phase.AIR;
        phaseTicks = 2;
        collided = mc.player != null && mc.player.horizontalCollision;
        stairTicks = 0;
        hopLoad = 0.0;
        slowHop = false;
        slowAfterSetback = false;
        lastSetback = System.currentTimeMillis();
    }

    private boolean isTowering() {
        return ModuleManager.getModuleState(Scaffold.class) && mc.options.keyJump.isDown();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (MovementUtil.isMoving() && !isTowering())
            InputUtil.setJumping(mc.player.input, false);
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || event.type != MoverType.SELF || isTowering()) return;

        LocalPlayer player = mc.player;

        collided = collided || player.horizontalCollision;
        if (collided) {
            recover(1);
        } else if (SpeedModule.rewoundTimer.get()) {
            TimerUtil.timerSpeed = 1.2f;
        }

        trackStep(player);

        stairTicks = Math.max(stairTicks - 1, 0);
        hopLoad = Math.max(hopLoad - (hopLoad > 1.0 ? 0.12 : 0.11), 0.0);

        if (!MovementUtil.isMoving()) return;

        double y = event.movement.y;
        if (!player.isInWater() && !player.isInLava() && MovementUtil.isGroundWithin(0.01)) {
            collided = player.horizontalCollision;
            if (phase != Phase.RECOVER || collided) {
                phase = Phase.BOOST;
                if (stairTicks == 0) {
                    y = 0.407 + EntityExtension.effectLevel(player, MobEffects.JUMP_BOOST) * 0.1;
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x, y, motion.z);
                }

                // Hops less than about 9 ticks apart pile up, every second one of those is 5% slower.
                hopLoad++;
                slowHop = hopLoad > 1.0 && !slowHop;
                hopLoad = Math.min(hopLoad, 1.12);
            }
        }

        if (phase == Phase.BOOST)
            slowAfterSetback = System.currentTimeMillis() - lastSetback < SETBACK_WINDOW_MS;

        Vec3 strafe = MovementUtil.getMovementVector(moveSpeed(player));
        event.movement = new Vec3(strafe.x, y, strafe.z);
        advance();
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            recover(4);
            lastSetback = System.currentTimeMillis();
        }
    }

    /**
     * Stand-in for LiquidBounce's step success event, which Kanade does not have: the step height
     * shows up as the previous tick's vertical displacement.
     */
    private void trackStep(LocalPlayer player) {
        double deltaY = player.getY() - player.yo;

        if (deltaY > 0.7)
            hopLoad = 0.0;

        if (Math.abs(deltaY - 0.5) < 1e-4)
            stairTicks = 3;
    }

    private void recover(int ticks) {
        phase = Phase.RECOVER;
        phaseTicks = ticks;
    }

    private void advance() {
        switch (phase) {
            case RECOVER -> {
                if (--phaseTicks == 0)
                    phase = Phase.BOOST;
            }
            case BOOST -> {
                phase = Phase.AIR;
                phaseTicks = 1;
            }
            case AIR -> phaseTicks++;
        }
    }

    private double moveSpeed(LocalPlayer player) {
        if (player.isInWater() || player.isInLava())
            return 0.12;

        double speed;
        if (phase == Phase.RECOVER) {
            speed = MovementUtil.ncpBaseMoveSpeed();
        } else if (stairTicks > 0) {
            speed = (hopSpeed(player) + 0.0331) * 0.91
                    * (0.7 - EntityExtension.effectLevel(player, MobEffects.SPEED) * 0.1);
        } else {
            speed = (hopSpeed(player) + 0.0331) * 0.91;
        }

        if (slowHop)
            speed *= 0.95;

        return speed;
    }

    private double hopSpeed(LocalPlayer player) {
        if (slowAfterSetback)
            return phase == Phase.BOOST ? 0.0 : 0.2;

        int level = EntityExtension.effectLevel(player, MobEffects.SPEED);
        double floor = MovementUtil.ncpBaseMoveSpeed() + 0.028 * level;

        if (System.currentTimeMillis() - lastSetback < SETBACK_WINDOW_MS || collided)
            return floor;

        double first = 0.4145 + level / 12.5;
        double speed;
        if (phase == Phase.BOOST) {
            speed = 0.64 + (level + 0.028 * level) * 0.134;
        } else if (phaseTicks == 1) {
            speed = first;
        } else {
            speed = first - phaseTicks / 250.0;
        }

        return Math.max(speed, floor);
    }
}
