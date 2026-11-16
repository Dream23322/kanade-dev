package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * LiquidBounce's Grim-2.3.73: every nearby living entity counted as a collision
 * grants a small push, exploiting Grim's collision lenience.
 */
public class Grim2373Speed extends SpeedBHop {

    @Override
    public String getName() {
        return "Grim-2.3.73";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (mc.level == null || !MovementUtil.isMoving()) return;

        int collisions = mc.level.getEntities(
                player,
                player.getBoundingBox().inflate(SpeedModule.grimShrinkBox.get()),
                entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStand)
        ).size();

        if (collisions == 0) return;

        double yaw = Math.toRadians(player.getYRot());
        double boost = SpeedModule.grimBoostSpeed.get() * collisions;
        player.push(-Math.sin(yaw) * boost, 0.0, Math.cos(yaw) * boost);
    }
}
