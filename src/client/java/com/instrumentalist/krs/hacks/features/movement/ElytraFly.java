package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class ElytraFly extends Module {

    @Setting
    private final FloatValue horizontalSpeed = new FloatValue("Horizontal Speed", 1.8f, 0.1f, 4f)
            .describe("Horizontal elytra speed.");

    @Setting
    private final FloatValue verticalSpeed = new FloatValue("Vertical Speed", 1.2f, 0.1f, 4f)
            .describe("Vertical elytra speed.");

    @Setting
    private static final BooleanValue autoRocket = new BooleanValue("Auto Rocket", true)
            .describe("Fires fireworks automatically while elytra flying.");

    @Setting
    private static final ListValue rocketMode = new ListValue("Rocket Mode", new String[]{"Low Speed", "Jump", "Always"}, "Low Speed", autoRocket::get)
            .describe("When rockets are used.");

    @Setting
    private static final FloatValue rocketSpeed = new FloatValue("Rocket Speed", 0.7f, 0.1f, 3.0f, () -> autoRocket.get() && rocketMode.get().equalsIgnoreCase("low speed"))
            .describe("Speed kept before a rocket is used.");

    @Setting
    private static final IntValue rocketDelay = new IntValue("Rocket Delay", 1500, 100, 5000, "ms", autoRocket::get)
            .describe("Milliseconds between rockets.");

    private final MSTimer rocketTimer = new MSTimer();

    @Override
    public String description() {
        return "Flies with an elytra without fireworks and auto-fires rockets";
    }

    public ElytraFly() {
        super("Elytra Fly", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    public static Vec3 hookFallFlyingMovement(Vec3 original, LivingEntity entity) {
        if (!(entity instanceof LocalPlayer player)) return original;

        ElytraFly module = ModuleManager.getModule(ElytraFly.class);
        if (module == null || !module.tempEnabled)
            return original;

        return module.getControlledMotion(original, player);
    }

    private Vec3 getControlledMotion(Vec3 original, LocalPlayer player) {
        if (!player.isFallFlying() || player.isPassenger() || !InventoryMove.canMoveFreely())
            return original;

        double left = 0.0;
        double forward = 0.0;

        if (isKeyDown(mc.options.keyLeft)) left += 1.0;
        if (isKeyDown(mc.options.keyRight)) left -= 1.0;
        if (isKeyDown(mc.options.keyUp)) forward += 1.0;
        if (isKeyDown(mc.options.keyDown)) forward -= 1.0;

        double length = Math.sqrt(left * left + forward * forward);
        if (length > 1.0) {
            left /= length;
            forward /= length;
        }

        double yaw = Math.toRadians(player.getYRot());
        double x = left * Math.cos(yaw) - forward * Math.sin(yaw);
        double z = left * Math.sin(yaw) + forward * Math.cos(yaw);
        double y = 0.0;

        if (isKeyDown(mc.options.keyJump)) y += verticalSpeed.get();
        if (isKeyDown(mc.options.keyShift)) y -= verticalSpeed.get();

        return new Vec3(x * horizontalSpeed.get(), y, z * horizontalSpeed.get());
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (!autoRocket.get() || player == null || gameMode == null || !player.isFallFlying()
                || mc.gui.screen() != null || !rocketTimer.hasTimePassed(rocketDelay.get()) || !shouldUseRocket())
            return;

        int slot = findRocketSlot();
        if (slot == -1)
            return;

        int oldSlot = player.getInventory().getSelectedSlot();
        if (slot != oldSlot) {
            player.getInventory().setSelectedSlot(slot);
            PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(slot));
        }

        // The server broadcasts the swing for successful item use on its own since 26.3.
        gameMode.useItem(player, InteractionHand.MAIN_HAND);

        if (slot != oldSlot) {
            player.getInventory().setSelectedSlot(oldSlot);
            PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot));
        }

        rocketTimer.reset();
    }

    private boolean shouldUseRocket() {
        return switch (rocketMode.get().toLowerCase(Locale.ROOT)) {
            case "jump" -> mc.options.keyJump.isDown();
            case "always" -> true;
            default -> MovementUtil.getSpeed() <= rocketSpeed.get();
        };
    }

    private int findRocketSlot() {
        var player = mc.player;
        if (player == null)
            return -1;

        for (int i = 0; i <= 8; i++) {
            if (player.getInventory().getItem(i).getItem() == Items.FIREWORK_ROCKET)
                return i;
        }
        return -1;
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player != null && mc.player.isFallFlying() && InventoryMove.canMoveFreely())
            mc.options.keyShift.setDown(false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
        rocketTimer.reset();
    }

    private boolean isKeyDown(KeyMapping key) {
        return InputConstants.isKeyDown(InputConstants.getKey(key.saveString()).getValue()
        );
    }
}
