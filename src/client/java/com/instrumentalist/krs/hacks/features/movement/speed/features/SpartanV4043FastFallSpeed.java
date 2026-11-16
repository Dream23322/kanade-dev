package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * LiquidBounce's Spartan-4.0.4.3-FastFall: a bigger jump burst on the ground and a
 * slowed-down, fake on-ground first air tick.
 */
public class SpartanV4043FastFallSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "Spartan-4.0.4.3-FastFall";
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
        double horizontalMove = leatherBoots ? 1.2 : 1.05;
        int jumps = leatherBoots ? 7 : 3;

        if (player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            event.movement = new Vec3(motion.x * horizontalMove, event.movement.y, motion.z * horizontalMove);

            for (int i = 0; i < jumps; i++)
                player.jumpFromGround();

            event.movement = new Vec3(event.movement.x, 0.42, event.movement.z);
        } else if (com.instrumentalist.krs.utils.move.MovementUtil.fallTicks == 1) {
            TimerUtil.timerSpeed = 0.5f;

            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                    player.getX(), player.getY(), player.getZ(), true, player.horizontalCollision
            ));

            event.movement = new Vec3(event.movement.x, -0.0784, event.movement.z);
        }
    }
}
