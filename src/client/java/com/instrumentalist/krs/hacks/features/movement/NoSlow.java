package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class NoSlow extends Module {

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Hypixel NCP", "Watchdog Legacy", "Grim"}, "Vanilla")
            .describe("Bypass used to remove the slowdown. Grim uses LiquidBounce's Grim 2.3.71 packet bypass.");

    @Setting
    private static final BooleanValue sneak = new BooleanValue("Sneak", false, () -> mode.get().equalsIgnoreCase("vanilla"))
            .describe("Also removes the slowdown while sneaking.");

    @Setting
    private static final FloatValue webSpeed = new FloatValue("Web Speed", 0.35f, 0.05f, 1.0f)
            .describe("Speed kept while inside cobwebs.");

    @Setting
    private static final BooleanValue webVertical = new BooleanValue("Web Vertical", true)
            .describe("Lets you move vertically inside cobwebs.");

    private static int lastSneakInputTick = Integer.MIN_VALUE;
    private boolean waitingPacket = false;
    private int grimSequenceTicks;
    private int grimBurstRemaining;

    @Override
    public String description() {
        return "Removes the slowdown from eating, blocking and cobwebs";
    }

    public NoSlow() {
        super("No Slow", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        lastSneakInputTick = Integer.MIN_VALUE;
        waitingPacket = false;
        resetGrimPackets();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("hypixel ncp") && !ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && player.isUsingItem() && player.onGround())
            event.y += player.tickCount % 2 == 0 ? 0.03 : 1E-5;

        if (mode.get().equalsIgnoreCase("watchdog legacy")) {
            event.callInPost = true;

            if (!isBlocking(player)) return;

            if (!event.post) {
                PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                        BlockPos.ZERO,
                        Direction.DOWN
                ));
            } else {
                PacketUtil.sendPacket(new ServerboundUseItemPacket(
                        player.isUsingItem() ? player.getUsedItemHand() : InteractionHand.MAIN_HAND,
                        0,
                        player.getYRot(),
                        player.getXRot()
                ));
            }
        }
    }

    private static boolean isBlocking(LocalPlayer player) {
        return currentUseAnimation(player) == ItemUseAnimation.BLOCK;
    }

    private static ItemUseAnimation currentUseAnimation(LocalPlayer player) {
        if (player.isUsingItem())
            return player.getUseItem().getUseAnimation();

        return KillAura.isServerBlocking() ? ItemUseAnimation.BLOCK : null;
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        var player = mc.player;
        if (player == null) return;

        var packet = event.packet;
        ItemStack mainHandItem = player.getMainHandItem();

        if (mode.get().equalsIgnoreCase("hypixel ncp") && MovementUtil.fallTicks < 2 && isHypixelUseItem(mainHandItem)) {
            if (packet instanceof ServerboundUseItemPacket) {
                event.cancel();

                if (player.onGround()) {
                    player.jumpFromGround();
                }

                waitingPacket = true;
            }

            if (packet instanceof ServerboundUseItemOnPacket)
                event.cancel();
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("grim"))
            updateGrimPackets(player);
        else
            resetGrimPackets();

        if (isInWeb()) {
            if (MovementUtil.isMoving())
                MovementUtil.setSpeed(Math.max(MovementUtil.getSpeed(), webSpeed.get()));

            if (webVertical.get()) {
                if (mc.options.keyJump.isDown())
                    MovementUtil.setVelocityY(webSpeed.get());
                else if (mc.options.keyShift.isDown())
                    MovementUtil.setVelocityY(-webSpeed.get());
            }
        }

        if (mode.get().equalsIgnoreCase("hypixel ncp") && waitingPacket && !player.isUsingItem())
            waitingPacket = false;

        if (mode.get().equalsIgnoreCase("hypixel ncp") && waitingPacket && MovementUtil.fallTicks >= 2 && isHypixelUseItem(player.getMainHandItem())) {
            PacketUtil.sendPacket(new ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND,
                    0,
                    player.getYRot(),
                    player.getXRot()
            ));
            waitingPacket = false;
        }
    }

    public static boolean shouldNoSlowSneak() {
        var player = mc.player;
        if (player == null
                || !ModuleManager.getModuleState(NoSlow.class)
                || !mode.get().equalsIgnoreCase("vanilla")
                || !sneak.get())
            return false;

        if (isSneakInputActive()) {
            lastSneakInputTick = player.tickCount;
            return true;
        }

        int ticksSinceSneak = player.tickCount - lastSneakInputTick;
        return ticksSinceSneak >= 0 && ticksSinceSneak <= 1 && player.isMovingSlowly();
    }

    public static boolean noSlowHook() {
        if (!ModuleManager.getModuleState(NoSlow.class))
            return false;

        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                return shouldNoSlowSneak() || shouldNoSlowUseItem();
            case "hypixel ncp", "watchdog legacy", "grim":
                return shouldNoSlowUseItem();
        }
        return false;
    }

    public static boolean shouldNoSlowUseItem() {
        var player = mc.player;
        if (player == null || !ModuleManager.getModuleState(NoSlow.class))
            return false;

        ItemUseAnimation animation = currentUseAnimation(player);
        if (animation == null)
            return false;

        boolean fastUseItem = isFastUseAnimation(animation);
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                return fastUseItem;
            case "hypixel ncp":
                return !ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && !player.isShiftKeyDown() && fastUseItem;
            case "watchdog legacy": {
                return animation == ItemUseAnimation.EAT
                        || animation == ItemUseAnimation.DRINK
                        || animation == ItemUseAnimation.BLOCK
                        || animation == ItemUseAnimation.BOW;
            }
            case "grim":
                return fastUseItem;
        }

        return false;
    }

    private void updateGrimPackets(LocalPlayer player) {
        if (!shouldNoSlowUseItem() || !isGrimUseAnimation(currentUseAnimation(player))) {
            resetGrimPackets();
            return;
        }

        // LiquidBounce's Grim 2.3.71 bypass sends two sequenced use-item packets
        // one tick apart, then repeats after a short pause.
        if (grimBurstRemaining > 0) {
            sendGrimUseItem(player);
            grimBurstRemaining--;
            return;
        }

        if (++grimSequenceTicks >= 2) {
            sendGrimUseItem(player);
            grimSequenceTicks = 0;
            grimBurstRemaining = 1;
        }
    }

    private void sendGrimUseItem(LocalPlayer player) {
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (level == null || gameMode == null)
            return;

        InteractionHand hand = player.isUsingItem()
                ? player.getUsedItemHand()
                : InteractionHand.MAIN_HAND;
        gameMode.startPrediction(level, sequence -> new ServerboundUseItemPacket(
                hand,
                sequence,
                player.getYRot(),
                player.getXRot()
        ));
    }

    private void resetGrimPackets() {
        grimSequenceTicks = 0;
        grimBurstRemaining = 0;
    }

    private static boolean isInWeb() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return false;

        AABB box = player.getBoundingBox();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBWEB))
                        return true;
                }
            }
        }
        return false;
    }

    private static boolean isSneakInputActive() {
        var player = mc.player;
        if (player == null)
            return false;

        if (player.isShiftKeyDown())
            return true;

        if (player.input == null)
            return false;

        Input input = player.input.keyPresses;
        return input != null && input.shift();
    }

    private static boolean isGrimUseAnimation(ItemUseAnimation animation) {
        return animation == ItemUseAnimation.EAT
                || animation == ItemUseAnimation.DRINK
                || animation == ItemUseAnimation.BLOCK
                || animation == ItemUseAnimation.BOW
                || animation == ItemUseAnimation.CROSSBOW
                || animation == ItemUseAnimation.TRIDENT
                || animation == ItemUseAnimation.SPEAR
                || animation == ItemUseAnimation.BUNDLE;
    }

    private static boolean isFastUseAnimation(ItemUseAnimation animation) {
        return animation == ItemUseAnimation.SPYGLASS
                || animation == ItemUseAnimation.TOOT_HORN
                || animation == ItemUseAnimation.BUNDLE
                || animation == ItemUseAnimation.DRINK
                || animation == ItemUseAnimation.BRUSH
                || animation == ItemUseAnimation.BLOCK
                || animation == ItemUseAnimation.EAT
                || animation == ItemUseAnimation.CROSSBOW
                || animation == ItemUseAnimation.SPEAR
                || animation == ItemUseAnimation.BOW
                || animation == ItemUseAnimation.TRIDENT;
    }

    private static boolean isHypixelUseItem(ItemStack stack) {
        var item = stack.getItem();
        return stack.has(DataComponents.FOOD) || item == Items.POTION || item instanceof BowItem || item == Items.MILK_BUCKET;
    }

}
