package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

/**
 * LiquidBounce's BlocksMC: a small ground/air state machine with per-tick strafe
 * speeds and lowered movement while descending.
 */
public class BlocksMCSpeed extends SpeedBHop {

    private int state;
    private int flagDelay;

    @Override
    public String getName() {
        return "BlocksMC";
    }

    @Override
    protected boolean autoJump() {
        return false;
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (ModuleManager.getModuleState(Scaffold.class) && Scaffold.getWasTowering()) {
            state = 0;
            return;
        }

        if (player.onGround()) {
            state = 1;
            if (ModuleManager.getModuleState(Scaffold.class))
                state = 2;
        }

        double speed = 0.06 + (player.onGround() ? 0.12 : 0.21) + player.getDeltaMovement().y / 20.0;

        if (MovementUtil.getSpeedAmplifier() == 1)
            speed += 0.1;

        if (flagDelay > 0) {
            flagDelay--;
            for (int i = 0; i < flagDelay; i++)
                speed -= 0.007;
        }

        switch (state) {
            case 1 -> {
                if (MovementUtil.fallTicks == 4)
                    MovementUtil.setVelocityY(-0.09800000190734863);
            }
            case 2 -> {
                switch (MovementUtil.fallTicks) {
                    case 1 -> MovementUtil.setVelocityY(player.getDeltaMovement().y + 0.0568);
                    case 3 -> MovementUtil.setVelocityY(player.getDeltaMovement().y - 0.13);
                    case 4 -> MovementUtil.setVelocityY(player.getDeltaMovement().y - 0.2);
                }
            }
        }

        float yaw = MovementUtil.getPlayerDirection();
        if (SpeedModule.blocksMCRoundYaw.get())
            yaw = Math.round(yaw / 45f) * 45f;

        if (!player.onGround() && state != 0)
            MovementUtil.setSpeed(speed, yaw);

        if (MovementUtil.isMoving() && state != 0)
            player.input.makeJump();

        if (MovementUtil.isMoving() && !player.isSprinting())
            player.setSprinting(true);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket)
            flagDelay = 20;
    }
}
