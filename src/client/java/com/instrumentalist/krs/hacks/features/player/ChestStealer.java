package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.instrumentalist.krs.utils.input.Keys;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;

import java.util.ArrayList;
import java.util.Collections;

public class ChestStealer extends Module {

    @Setting
    private static final IntValue startDelay = new IntValue("Start Delay", 2, 0, 10)
            .describe("Ticks to wait before taking the first item.");

    @Setting
    private static final IntValue maxDelay = new IntValue("Max Delay", 2, 0, 10)
            .describe("Longest delay between items.");

    @Setting
    private static final IntValue minDelay = new IntValue("Min Delay", 0, 0, 10)
            .describe("Shortest delay between items.");

    @Setting
    public static final BooleanValue blink = new BooleanValue("Blink", false)
            .describe("Blinks while looting so your movement looks still.");

    @Setting
    public static final BooleanValue pauseSpeed = new BooleanValue("Pause Speed", true)
            .describe("Pauses Speed while looting, but still lets you move.");

    @Setting
    private static final BooleanValue cameraMove = new BooleanValue("Camera Move", false)
            .describe("Moves the camera towards the chest while looting.");

    @Setting
    public static final BooleanValue silent = new BooleanValue("Silent", false)
            .describe("Opens chests without showing the chest UI.");

    @Setting
    private static final BooleanValue renderSilent = new BooleanValue("Render Silent", true, silent::get)
            .describe("Draws a small stealing bar above the chest while it loots.");

    @Setting
    private static final FloatValue renderScale = new FloatValue("Render Scale", 1f, 0.5f, 2f, "x", renderSilent::get)
            .describe("Size of the stealing bar.");

    private static final float BAR_WIDTH = 52f;
    private static final float BAR_HEIGHT = 12f;
    private static final float BAR_FONT_SIZE = 8f;
    private static final double BAR_WORLD_OFFSET = 1.15D;
    private static final String BAR_TEXT = "Stealing";
    private static final long BAR_PULSE_NANOS = 1_600_000_000L;

    private static BlockPos lootTarget = null;

    private int tickCounter = 0;
    private int startCounter = 0;
    private int closeCounter = 0;
    private int closeDelayTarget = -1;
    private boolean stealing = false;
    private boolean ownsBlink = false;
    private final ArrayList<Integer> slotIndices = new ArrayList<>(27);

    private boolean looting = false;
    private int lootTotal = 0;
    private int lootStolen = 0;
    private float barProgress = 0f;
    private final float[] projected = new float[3];
    private float barScreenX = 0f;
    private float barScreenY = 0f;
    private boolean barVisible = false;

    @Override
    public String description() {
        return "Takes everything out of chests into your inventory";
    }

    public ChestStealer() {
        super("Chest Stealer", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    public static boolean shouldMoveCamera() {
        return ModuleManager.getModuleState(ChestStealer.class)
                && cameraMove.get()
                && mc.player != null
                && mc.player.containerMenu instanceof ChestMenu
                && (mc.gui.screen() instanceof ContainerScreen
                || silent.get() && mc.gui.screen() == null);
    }

    public static boolean shouldHideChestScreen(Screen screen) {
        return screen instanceof ContainerScreen
                && ModuleManager.getModuleState(ChestStealer.class)
                && silent.get()
                && mc.player != null
                && mc.player.containerMenu instanceof ChestMenu;
    }

    public static void noteLootTarget(BlockPos pos) {
        lootTarget = pos == null ? null : pos.immutable();
    }

    /**
     * A silent chest has no screen, so the menu would otherwise stay open and
     * keep swallowing clicks after the feature that opened it is gone.
     */
    public static void closeSilentContainer() {
        var player = mc.player;
        if (player == null || mc.gui.screen() instanceof ContainerScreen || !(player.containerMenu instanceof ChestMenu))
            return;

        if (mc.getConnection() != null)
            player.closeContainer();
        else
            player.containerMenu = player.inventoryMenu;
    }

    @Override
    public void onDisable() {
        closeSilentContainer();
        tickCounter = 0;
        startCounter = 0;
        closeCounter = 0;
        closeDelayTarget = -1;
        stealing = false;
        endLoot();
        if (ownsBlink)
            releaseBlink();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        closeSilentContainer();
        endLoot();
    }

    private void releaseBlink() {
        if (BlinkUtil.INSTANCE.getBlinking()) {
            BlinkUtil.INSTANCE.sync(true, false);
            BlinkUtil.INSTANCE.stopBlink();
        }
        ownsBlink = false;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;

        syncSpeedPause();

        if (!silent.get())
            closeSilentContainer();

        if (!stealing && ownsBlink)
            releaseBlink();

        if (tickCounter > 0) {
            tickCounter--;
            return;
        }

        if (player.containerMenu instanceof ChestMenu screenHandler) {
            if (!looting)
                beginLoot(screenHandler);

            if (startCounter < startDelay.get()) {
                startCounter++;
                return;
            }

            if (blink.get() && !stealing) {
                BlinkUtil.INSTANCE.doBlink();
                ownsBlink = true;
                stealing = true;
            }

            slotIndices.clear();
            for (int i = 0; i < Math.min(27, screenHandler.slots.size()); i++)
                slotIndices.add(i);
            Collections.shuffle(slotIndices);

            boolean itemStolen = false;
            for (int i : slotIndices) {
                var slot = screenHandler.getSlot(i);
                if (slot.hasItem() && canFit(slot.getItem(), player) && shouldSteal(slot.getItem(), player)) {
                    gameMode.handleContainerInput(screenHandler.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
                    lootStolen++;
                    itemStolen = true;
                    tickCounter = nextDelay();
                    closeCounter = 0;
                    closeDelayTarget = -1;
                    break;
                }
            }

            if (!itemStolen) {
                if (closeDelayTarget < 0)
                    closeDelayTarget = nextDelay();

                if (closeCounter < closeDelayTarget) {
                    closeCounter++;
                    return;
                }

                closeCounter = 0;
                closeDelayTarget = -1;
                player.closeContainer();
                stealing = false;
                endLoot();
            }
        } else {
            closeCounter = 0;
            closeDelayTarget = -1;
            startCounter = 0;
            stealing = false;
            if (looting)
                endLoot();
        }
    }

    private void beginLoot(ChestMenu menu) {
        looting = true;
        syncSpeedPause();
        lootTotal = 0;
        lootStolen = 0;
        barProgress = 0f;

        for (int i = 0; i < Math.min(27, menu.slots.size()); i++) {
            if (menu.getSlot(i).hasItem())
                lootTotal++;
        }

        if (lootTarget == null)
            lootTarget = hitContainerTarget();
    }

    private void endLoot() {
        lootTarget = null;
        looting = false;
        syncSpeedPause();
        lootTotal = 0;
        lootStolen = 0;
        barProgress = 0f;
        barVisible = false;
    }

    private void syncSpeedPause() {
        SpeedModule.setPaused(pauseSpeed.get() && looting);
    }

    private static BlockPos hitContainerTarget() {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return null;

        Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof ChestBlock || block instanceof BarrelBlock ? hit.getBlockPos().immutable() : null;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        barVisible = false;
        if (!looting || lootTarget == null || !renderSilent.get() || mc.player == null)
            return;
        if (!RenderUtil.shouldRenderWorldHudOverlays())
            return;

        float targetProgress = lootTotal <= 0 ? 0f : Math.clamp(lootStolen / (float) lootTotal, 0f, 1f);
        barProgress += (targetProgress - barProgress) * 0.35f;

        if (!RenderUtil.INSTANCE.renderedWorldToScreen(
                lootTarget.getX() + 0.5D,
                lootTarget.getY() + BAR_WORLD_OFFSET,
                lootTarget.getZ() + 0.5D,
                projected))
            return;

        barScreenX = NanoVGManager.fromFramebufferX(projected[0]);
        barScreenY = NanoVGManager.fromFramebufferY(projected[1]);
        barVisible = true;
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (!barVisible || lootTarget == null || Client.nanoVgManager == null)
            return;

        Client.nanoVgManager.loadWorldAnchored(this::drawStealBar);
    }

    private void drawStealBar(NVGU vg) {
        float scale = Math.max(0.1f, renderScale.get());
        float width = BAR_WIDTH * scale;
        float height = BAR_HEIGHT * scale;
        float x = barScreenX - width / 2f;
        float y = barScreenY - height;

        NanoVGTheme.renderCompact(vg, x, y, width, height, NanoVGTheme.RADIUS_LABEL, 1f, 40);

        float fill = barProgress;
        if (lootTotal <= 0) {
            float pulse = (System.nanoTime() % BAR_PULSE_NANOS) / (float) BAR_PULSE_NANOS;
            fill = 0.15f + 0.5f * (pulse < 0.5f ? pulse * 2f : (1f - pulse) * 2f);
        }

        float fillWidth = (width - 3f * scale) * Math.clamp(fill, 0f, 1f);
        if (fillWidth > 0.05f)
            vg.rectangle(x + 1.5f * scale, y + height - 3f * scale, fillWidth, 1.5f * scale, NanoVGTheme.ACCENT);

        NVGFonts.INTER.drawText(BAR_TEXT, x + width / 2f, y + 2f * scale, BAR_FONT_SIZE * scale,
                NanoVGTheme.TEXT, Alignment.CENTER_TOP, true);
    }

    private boolean canFit(ItemStack stack, LocalPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (invStack.isEmpty())
                return true;
            if (ItemStack.isSameItemSameComponents(invStack, stack) && invStack.getCount() < invStack.getMaxStackSize())
                return true;
        }
        return false;
    }

    private boolean shouldSteal(ItemStack stack, LocalPlayer player) {
        if (stack.getItem() instanceof BlockItem
                || stack.getItem() instanceof PotionItem
                || stack.getItem() instanceof SplashPotionItem
                || stack.getItem() instanceof LingeringPotionItem
                || stack.getItem() == Items.GOLDEN_APPLE
                || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE
                || stack.getItem() == Items.ENDER_PEARL
                || stack.getItem() == Items.ENDER_EYE)
            return true;

        if (ToolUtil.INSTANCE.isArmor(stack)) {
            var armorSlot = ToolUtil.INSTANCE.getArmorEquipmentSlot(stack);
            ItemStack currentArmor = player.getItemBySlot(armorSlot);
            if (!currentArmor.isEmpty() && !ToolUtil.INSTANCE.isBetterArmor(stack, currentArmor))
                return false;
        }

        var inventory = player.getInventory();
        int toolCategory = ToolUtil.INSTANCE.getToolCategory(stack.getItem());
        int toolTier = ToolUtil.INSTANCE.getToolTier(stack.getItem());
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (ItemStack.matches(invStack, stack) && invStack.getCount() >= stack.getCount())
                return false;

            if (toolCategory >= 0
                    && ToolUtil.INSTANCE.getToolCategory(invStack.getItem()) == toolCategory
                    && ToolUtil.INSTANCE.getToolTier(invStack.getItem()) > toolTier)
                return false;
        }

        return true;
    }

    private int nextDelay() {
        int min = minDelay.get();
        int max = maxDelay.get();
        int lower = Math.min(min, max);
        int upper = Math.max(min, max);

        return RandomUtil.nextInt(lower, upper + 1);
    }

}
