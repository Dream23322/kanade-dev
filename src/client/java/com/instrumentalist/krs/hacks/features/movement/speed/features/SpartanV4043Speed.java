package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * LiquidBounce's Spartan-4.0.4.3: on the ground, forward movement is multiplied and
 * the jump is issued several times to get a higher jump power.
 */
public class SpartanV4043Speed implements SpeedEvent {

    @Override
    public String getName() {
        return "Spartan-4.0.4.3";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || !player.input.keyPresses.forward()) return;

        boolean leatherBoots = player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS);
        double horizontalMove = leatherBoots ? 1.8 : 1.3;

        if (!player.onGround()) return;

        Vec3 motion = player.getDeltaMovement();
        event.movement = new Vec3(motion.x * horizontalMove, event.movement.y, motion.z * horizontalMove);

        for (int i = 0; i < 4; i++)
            player.jumpFromGround();

        event.movement = new Vec3(event.movement.x, player.getDeltaMovement().y, event.movement.z);
    }
}
