package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's HypixelLowHop (Watchdog 25.01.25): a shortened jump followed by
 * air-tick speed corrections, with a glide option near the ground.
 */
public class HypixelLowHopSpeed extends SpeedBHop {

    @Override
    public String getName() {
        return "HypixelLowHop";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        int airTicks = MovementUtil.fallTicks;
        int amplifier = MovementUtil.getSpeedAmplifier();

        if (player.onGround()) {
            if (MovementUtil.isMoving())
                MovementUtil.strafe(MovementUtil.getSpeed());
        } else {
            switch (airTicks) {
                case 1 -> {
                    if (MovementUtil.isMoving())
                        MovementUtil.strafe(MovementUtil.getSpeed());

                    MovementUtil.setVelocityY(player.getDeltaMovement().y + 0.0568);
                }
                case 3 -> {
                    MovementUtil.scaleVelocityXZ(0.95);
                    MovementUtil.setVelocityY(player.getDeltaMovement().y - 0.13);
                }
                case 4 -> MovementUtil.setVelocityY(player.getDeltaMovement().y - 0.2);
                case 7 -> {
                    if (SpeedModule.hypixelLowHopGlide.get() && MovementUtil.isGroundWithin(0.66))
                        MovementUtil.setVelocityY(0.0);
                }
            }

            if (MovementUtil.isGroundWithin(0.66) && player.getDeltaMovement().y < 0)
                MovementUtil.strafe(MovementUtil.getSpeed());

            if (player.hurtTime == 9)
                MovementUtil.strafe(Math.max(MovementUtil.getSpeed(), 0.281f));

            if (amplifier == 2) {
                switch (airTicks) {
                    case 1, 2, 5, 6, 8 -> MovementUtil.scaleVelocityXZ(1.2);
                }
            }
        }

        if (jumped)
            MovementUtil.strafe(Math.max(MovementUtil.getSpeed(), (float) (0.247 + 0.15 * amplifier)));
    }
}
