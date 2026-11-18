package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.mixin.injector.MultiPlayerGameModeAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.instrumentalist.krs.utils.input.Keys;

public class AutoTool extends Module {
    private static final long NOTIFY_COOLDOWN_MILLIS = 4_000L;

    @Setting
    private static final IntValue durability = new IntValue("Durability", 5, 1, 100)
            .describe("Durability left before the tool is swapped out.");

    @Setting
    private final BooleanValue switchTool = new BooleanValue("Switch Tool", true)
            .describe("Switches to another tool of the same type.");

    @Setting
    private final BooleanValue notify = new BooleanValue("Notify", true)
            .describe("Notifies you when a tool is saved.");

    private BlockPos breakingPos = null;
    private int toolOriginalSlot = -1;
    private int toolSelectedSlot = -1;
    private boolean toolSpoofing = false;
    private int restoreDelayTicks = 0;
    private long lastNotifyMillis;

    @Override
    public String description() {
        return "Switches to the best tool and protects it from breaking";
    }

    @Override
    public String tag() {
        return String.valueOf(durability.get());
    }

    public AutoTool() {
        super("Auto Tool", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) {
            resetState();
            return;
        }

        if (breakingPos != null)
            updateBreakingTool();
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || player.getAbilities().instabuild) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!isAboutToBreak(held)) {
            return;
        }

        boolean attacking = mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) mc.gameMode;
        if (!attacking && !accessor.krs$isDestroying()) {
            return;
        }

        if (switchTool.get()) {
            int replacement = findReplacement(player.getInventory(), held);
            if (replacement != -1) {
                player.getInventory().setSelectedSlot(replacement);
                return;
            }
        }

        if (accessor.krs$isDestroying())
            mc.gameMode.stopDestroyBlock();

        long now = System.currentTimeMillis();
        if (notify.get() && now - lastNotifyMillis > NOTIFY_COOLDOWN_MILLIS) {
            lastNotifyMillis = now;
            int left = held.getMaxDamage() - held.getDamageValue();
            Client.notificationManager.addNotification("Tool Saver", held.getHoverName().getString() + " has " + left + " durability left");
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (event.packet instanceof ServerboundPlayerActionPacket actionPacket && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            breakingPos = actionPacket.getPos();
            restoreDelayTicks = 4;
        }
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetState();
    }

    public void resetState() {
        resetToolSlot();
    }

    public static boolean isAboutToBreak(ItemStack stack) {
        return ModuleManager.getModuleState(AutoTool.class)
                && stack != null
                && !stack.isEmpty()
                && stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() <= durability.get();
    }

    public static boolean shouldBlockMining(HitResult hitResult) {
        LocalPlayer player = mc.player;
        return player != null
                && !player.getAbilities().instabuild
                && hitResult instanceof BlockHitResult
                && hitResult.getType() == HitResult.Type.BLOCK
                && isAboutToBreak(player.getMainHandItem());
    }

    private static int findReplacement(Inventory inventory, ItemStack held) {
        int category = ToolUtil.INSTANCE.getToolCategory(held.getItem());
        int fallback = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == inventory.getSelectedSlot())
                continue;

            ItemStack stack = inventory.getItem(slot);
            if (isAboutToBreak(stack))
                continue;

            if (category != -1 && ToolUtil.INSTANCE.getToolCategory(stack.getItem()) == category)
                return slot;
            if (fallback == -1 && stack.isEmpty())
                fallback = slot;
        }
        return fallback;
    }

    private void updateBreakingTool() {
        if (!isBreakingInputHeld()) {
            resetToolSlot();
            return;
        }

        BlockPos targetPos = getTargetBlockPos();
        if (targetPos != null && mc.level != null && !mc.level.getBlockState(targetPos).isAir()) {
            breakingPos = targetPos;
            if (switchToBestTool(targetPos))
                restoreDelayTicks = 4;
            else
                resetToolSlot();
            return;
        }

        if (restoreDelayTicks > 0) {
            restoreDelayTicks--;
            return;
        }

        resetToolSlot();
    }

    private boolean isBreakingInputHeld() {
        return mc.options.keyAttack.isDown() && !mc.options.keyUse.isDown();
    }

    private BlockPos getTargetBlockPos() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)
            return null;

        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    private boolean switchToBestTool(BlockPos pos) {
        var player = mc.player;
        if (player == null) return false;
        int bestToolSlot = ToolUtil.INSTANCE.findBestTool(pos);

        if (bestToolSlot == -1)
            return false;

        if (isAboutToBreak(player.getInventory().getItem(bestToolSlot)))
            return false;

        if (toolOriginalSlot == -1 && player.getInventory().getSelectedSlot() == bestToolSlot)
            return true;

        if (toolOriginalSlot == -1)
            toolOriginalSlot = player.getInventory().getSelectedSlot();

        toolSelectedSlot = bestToolSlot;
        PlayerUtil.INSTANCE.doSpoof(toolOriginalSlot);
        toolSpoofing = true;

        if (player.getInventory().getSelectedSlot() != bestToolSlot)
            player.getInventory().setSelectedSlot(bestToolSlot);

        return true;
    }

    private void resetToolSlot() {
        var player = mc.player;

        if (player != null && toolOriginalSlot >= 0 && toolOriginalSlot <= 8 && player.getInventory().getSelectedSlot() == toolSelectedSlot)
            player.getInventory().setSelectedSlot(toolOriginalSlot);

        breakingPos = null;
        toolOriginalSlot = -1;
        toolSelectedSlot = -1;
        restoreDelayTicks = 0;
        if (toolSpoofing)
            PlayerUtil.INSTANCE.stopSpoof();
        toolSpoofing = false;
    }

}
