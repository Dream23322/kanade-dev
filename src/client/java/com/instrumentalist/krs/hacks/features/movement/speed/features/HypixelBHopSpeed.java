package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

/**
 * LiquidBounce's HypixelBHop (Watchdog 12.12.2023): ground strafe, a tiny air
 * acceleration and a damage-based speed refund.
 */
public class HypixelBHopSpeed extends SpeedBHop {

    private static final double AT_LEAST = 0.281;
    private static final double BASH = 0.2857671997172534;
    private static final double SPEED_EFFECT_CONST = 0.008003278196411223;

    private static final double BASE_HORIZONTAL_MODIFIER = 0.0004;
    private static final double HORIZONTAL_SPEED_AMPLIFIER = 0.0007;
    private static final double VERTICAL_SPEED_AMPLIFIER = 0.0004;

    private boolean wasFlagged;
    private int boostTicks = -1;

    @Override
    public String getName() {
        return "HypixelBHop";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (player.onGround()) {
            if (MovementUtil.isMoving())
                MovementUtil.strafe(MovementUtil.getSpeed());
        } else {
            double horizontalMod = SpeedModule.hypixelBHopHorizontal.get()
                    ? BASE_HORIZONTAL_MODIFIER + HORIZONTAL_SPEED_AMPLIFIER * MovementUtil.getSpeedAmplifier()
                    : 0.0;
            double verticalMod = SpeedModule.hypixelBHopVertical.get()
                    && player.getDeltaMovement().y < 0
                    && player.fallDistance < 1
                    ? VERTICAL_SPEED_AMPLIFIER
                    : 0.0;

            var motion = player.getDeltaMovement();
            MovementUtil.setVelocity(
                    motion.x * (1.0 + horizontalMod),
                    motion.y * (1.0 + verticalMod),
                    motion.z * (1.0 + horizontalMod)
            );
        }

        if (jumped) {
            double atLeast = wasFlagged
                    ? 0.0
                    : AT_LEAST + SPEED_EFFECT_CONST * MovementUtil.getSpeedAmplifier();

            MovementUtil.strafe(Math.max(MovementUtil.getSpeed(), (float) atLeast));
        }

        if (boostTicks > 0 && --boostTicks == 0)
            MovementUtil.strafe(MovementUtil.getSpeed());
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (event.packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == player.getId()) {
            if (motion.movement().x == 0.0 && motion.movement().z == 0.0 && isFallDamage(motion.movement().y))
                MovementUtil.strafe((float) Math.max(MovementUtil.getSpeed(), BASH * MovementUtil.getSpeedAmplifier()));

            boostTicks = 1;
        } else if (event.packet instanceof ClientboundPlayerPositionPacket) {
            wasFlagged = true;
        }
    }

    private boolean isFallDamage(double motionY) {
        return Math.abs(motionY + 0.0784000015258789) < 1.0E-7;
    }
}
