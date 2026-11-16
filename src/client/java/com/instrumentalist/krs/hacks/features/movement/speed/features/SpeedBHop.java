package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * Java counterpart of LiquidBounce's SpeedBHopBase: keeps jumping while you are on the
 * ground and moving, and lets a mode override the jump height or react to the jump tick.
 */
public abstract class SpeedBHop implements SpeedEvent {

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean jumped = false;
        if (autoJump() && player.onGround() && MovementUtil.isMoving()) {
            float motionY = jumpMotionY();

            if (Float.isNaN(motionY)) {
                player.input.makeJump();
            } else {
                player.jumpFromGround();
                MovementUtil.setVelocityY(motionY);
            }

            jumped = true;
        }

        onSpeedUpdate(player, jumped);
    }

    protected boolean autoJump() {
        return true;
    }

    /**
     * Vertical motion applied right after the jump, or {@link Float#NaN} to leave vanilla jump height alone.
     */
    protected float jumpMotionY() {
        return Float.NaN;
    }

    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
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
}
