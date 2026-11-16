package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's NCP-2024-08-31: low-hop bhop with a timer boost, ground and air
 * movement floors, a damage refund and a periodic pull-down.
 */
public class NCP2024Speed extends SpeedBHop {

    private static final double SPEED_CONSTANT = 0.199999999;
    private static final double GROUND_CONSTANT = 0.281;
    private static final double AIR_CONSTANT = 0.2;
    private static final double BOOST_CONSTANT = 0.00718;

    private int ticksInAir;

    @Override
    public String getName() {
        return "NCP-2024-08-31";
    }

    @Override
    protected float jumpMotionY() {
        return SpeedModule.ncpLowHop.get() ? 0.4f : Float.NaN;
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        int speedLevel = MovementUtil.getSpeedAmplifier();

        if (player.onGround()) {
            ticksInAir = 0;
        } else {
            ticksInAir++;
            if (ticksInAir == 5) {
                MovementUtil.strafe(MovementUtil.getSpeed());
                MovementUtil.setVelocityY(player.getDeltaMovement().y - 0.1523351824467155);
            }
        }

        if (player.hurtTime >= 5 && player.getDeltaMovement().y >= 0)
            MovementUtil.setVelocityY(player.getDeltaMovement().y - 0.1);

        if (MovementUtil.isMoving())
            MovementUtil.scaleVelocityXZ(1.0 + BOOST_CONSTANT);

        if (MovementUtil.isMoving()) {
            if (player.onGround()) {
                MovementUtil.strafe((float) Math.max(
                        MovementUtil.getSpeed(),
                        GROUND_CONSTANT + SPEED_CONSTANT * speedLevel
                ));
            } else if (SpeedModule.ncpAirStrafe.get()) {
                MovementUtil.strafe(Math.max(
                        MovementUtil.getSpeed(),
                        (float) (AIR_CONSTANT + SPEED_CONSTANT * speedLevel)
                ), 0.7);
            }
        }

        if (SpeedModule.ncpTimer.get())
            TimerUtil.timerSpeed = 1.08f;

        if (player.hurtTime >= 1)
            MovementUtil.strafe(Math.max(MovementUtil.getSpeed(), 0.5f));
    }
}
