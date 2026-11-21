package com.instrumentalist.krs.hacks.features.player.phase.features;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

public class IntavePhase implements PhaseEvent {
    private boolean mining = false;

    @Override
    public String getName() {
        return "Intave";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mc.options.keyAttack.isDown() && player.getXRot() > 80) {
            PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    player.blockPosition().below(),
                    Direction.UP
            ));

            mining = true;
        } else {
            mining = false;
        }

        if (mining)
            player.position().y -= 0.0042;

        if (player.isShiftKeyDown()) {
            float distance = 0.005f;
            double rotation = Math.toRadians(player.getYRot());

            if (mc.options.keyUp.isDown()) move(player, rotation, distance, 1, 1);
            else if (mc.options.keyDown.isDown()) move(player, rotation, -distance, 1, -1);
            else if (mc.options.keyLeft.isDown()) move(player, rotation, distance, -1, 1);
            else if (mc.options.keyRight.isDown()) move(player, rotation, -distance, -1, -1);
        }
    }

    private void move(LocalPlayer player, double rotation, float distance, int xMultiplier, int zMultiplier) {
        double xx = Math.cos(rotation) * distance * xMultiplier;
        double zz = Math.sin(rotation) * distance * zMultiplier;

        player.setPos(player.getX() + xx, player.getY(), player.getZ() + zz);
    }
}
