package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's Intave-14-Fast: early-air accelerations with an optional tiny timer boost.
 */
public class Intave14FastSpeed extends SpeedBHop {

    @Override
    public String getName() {
        return "Intave-14-Fast";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        switch (MovementUtil.fallTicks) {
            case 1 -> MovementUtil.scaleVelocityXZ(1.04);
            case 2, 3, 4 -> MovementUtil.scaleVelocityXZ(1.02);
        }

        if (SpeedModule.intaveFastTimer.get())
            TimerUtil.timerSpeed = 1.002f;
    }
}
