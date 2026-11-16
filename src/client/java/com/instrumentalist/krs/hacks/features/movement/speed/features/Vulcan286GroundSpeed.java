package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * LiquidBounce's Vulcan-2.8.6-Ground: no hopping, just a small ground strafe with a
 * matching vertical offset in the outgoing movement packets.
 */
public class Vulcan286GroundSpeed extends SpeedBHop {

    @Override
    public String getName() {
        return "Vulcan-2.8.6-Ground";
    }

    @Override
    protected boolean autoJump() {
        return false;
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (!MovementUtil.isMoving() || !collidesBelow() || mc.options.keyJump.isDown()) return;

        boolean hasSpeed = MovementUtil.getSpeedAmplifier() > 0;
        boolean goingSideways = InputUtil.leftImpulse(player.input) != 0f;

        double speed;
        if (hasSpeed) speed = 0.59;
        else if (goingSideways) speed = 0.41;
        else speed = 0.42;

        MovementUtil.strafe((float) speed);
        MovementUtil.setVelocityY(0.005);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (event.packet instanceof ServerboundMovePlayerPacket packet
                && packet.hasPos
                && collidesBelow()
                && !mc.options.keyJump.isDown())
            packet.y += 0.005;
    }

    private boolean collidesBelow() {
        LocalPlayer player = mc.player;
        return player != null && MovementUtil.isGroundWithin(0.005);
    }
}
