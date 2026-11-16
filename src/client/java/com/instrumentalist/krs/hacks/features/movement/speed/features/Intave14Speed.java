package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's Intave-14: a weak strafe blend on the ground and late in the air,
 * plus a tiny acceleration while rising.
 */
public class Intave14Speed extends SpeedBHop {

    private static final double BOOST_CONSTANT = 0.003;

    @Override
    public String getName() {
        return "Intave-14";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (player.isSprinting() && (player.onGround() || MovementUtil.fallTicks == 11))
            MovementUtil.strafe(MovementUtil.getSpeed(), SpeedModule.intaveStrafeStrength.get());

        if (player.getDeltaMovement().y > 0.003 && player.isSprinting())
            MovementUtil.scaleVelocityXZ(1.0 + BOOST_CONSTANT * 0.25);
    }
}
