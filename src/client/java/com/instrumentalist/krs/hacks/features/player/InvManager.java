package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.AutoPot;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.hacks.features.exploit.disabler.features.HypixelDisabler;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.util.Prediction;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InvManager extends Module {

    @Setting
    private final IntValue startDelay = new IntValue("Start Delay", 2, 0, 10)
            .describe("Ticks to wait before sorting starts.");

    @Setting
    private final IntValue maxItemDelay = new IntValue("Max Item Delay", 2, 0, 10)
            .describe("Longest delay between item moves.");

    @Setting
    private final IntValue minItemDelay = new IntValue("Min Item Delay", 0, 0, 10)
            .describe("Shortest delay between item moves.");

    @Setting
    private final IntValue maxArmorDelay = new IntValue("Max Armor Delay", 2, 0, 10)
            .describe("Longest delay between armour moves.");

    @Setting
    private final IntValue minArmorDelay = new IntValue("Min Armor Delay", 0, 0, 10)
            .describe("Shortest delay between armour moves.");

    @Setting
    private final IntValue blockRefill = new IntValue("Block Refill", 16, 1, 64)
            .describe("Amount of blocks kept in your hotbar.");

    @Setting
    private final BooleanValue cleaner = new BooleanValue("Cleaner", true)
            .describe("Drops junk items automatically.");

    @Setting
    private final BooleanValue strict = new BooleanValue("Strict", false, cleaner::get)
            .describe("Cleaner keeps only gaps, blocks, armour, swords, pickaxes, axes and potions.");

    @Setting
    private final BooleanValue onlyInInventory = new BooleanValue("Only in Inventory", false)
            .describe("Only sorts while the inventory screen is open.");

    @Setting
    private final ListValue retryMode = new ListValue("Retry Mode", new String[]{"Static", "Dynamic"}, "Dynamic")
            .describe("Dynamic backs off when the server reverts a move, Static retries instantly.");

    @Setting
    private final IntValue retryThreshold = new IntValue("Retry Threshold", 3, 1, 10)
            .describe("Reverted attempts before extra delay is added.");

    @Setting
    private final IntValue retryStep = new IntValue("Retry Step", 2, 0, 20, "ticks")
            .describe("Extra delay added per revert past the threshold.");

    @Setting
    private final IntValue maxRetryDelay = new IntValue("Max Retry Delay", 20, 0, 100, "ticks")
            .describe("Longest extra delay a reverted move can get.");

    @Setting
    private final IntValue skipAfter = new IntValue("Skip After", 6, 1, 20)
            .describe("Reverts before the move is skipped completely.");

    @Setting
    private final IntValue skipTime = new IntValue("Skip Time", 100, 0, 600, "ticks")
            .describe("How long a skipped move is ignored.");

    @Setting
    private final IntValue cleanerPause = new IntValue("Cleaner Pause", 40, 0, 600, "ticks")
            .describe("How long to stop dropping items after a reverted move.");

    @Setting
    private final ListValue combatStyle = new ListValue("Combat Style", new String[]{"Auto", "Modern", "1.8.9"}, "Auto")
            .describe("Auto detects a 1.8 server, where damage per hit matters instead of attack speed.");

    @Setting
    private final ListValue weaponScoring = new ListValue("Weapon Scoring", new String[]{"DPS", "Tier"}, "DPS")
            .describe("DPS compares damage per second, Tier compares material and then enchantments.");

    @Setting
    private final ListValue armorScoring = new ListValue("Armor Scoring", new String[]{"EPF", "Simple"}, "EPF")
            .describe("EPF weighs armour points with protection, Simple weighs raw armour points.");

    @Setting
    private final ListValue preferWeapon = new ListValue("Prefer Weapon", new String[]{"Auto", "Sword", "Axe", "Mace"}, "Auto")
            .describe("Weapon type that is kept in the first hotbar slot.");

    private int tickCounter = 0;
    private int startCounter = 0;
    private final ItemStack[] cleanerTargets = new ItemStack[9];
    private final ItemStack[] armorTargets = new ItemStack[4];
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private final EnumMap<EquipmentSlot, ArmorCandidate> armorCandidates = new EnumMap<>(EquipmentSlot.class);
    private final boolean[] excessSlots = new boolean[36];
    private final Map<MoveKey, MoveState> moveStates = new HashMap<>();
    private PendingMove pendingMove;
    private int cleanerPausedUntilTick;
    private final Map<ItemStack, Double> scoreCache = new IdentityHashMap<>();
    private int scoreCacheTick = Integer.MIN_VALUE;

    private static final int MAX_THROWABLE_STACKS = 2;
    private static final int MAX_BLOCK_STACKS = 3;
    private static final int MAX_HARMFUL_POTIONS = 3;
    private static final int GOLDEN_APPLE_SLOT = 4;
    private static final Pattern SERVER_VERSION = Pattern.compile("(\\d+)\\.(\\d+)");
    private static final int ACTION_GAP_TICKS = 2;
    private static final int MOVE_STATE_LIMIT = 128;
    private static int lastClickTick = Integer.MIN_VALUE / 2;

    private static boolean cleaning = false;

    @Override
    public String description() {
        return "Sorts your inventory and refills blocks automatically";
    }

    @Override
    public String tag() {
        if (!isDynamic())
            return retryMode.get();

        int failures = 0;
        for (MoveState state : moveStates.values())
            failures = Math.max(failures, state.failures);

        return failures > 0 ? retryMode.get() + " " + failures : retryMode.get();
    }

    public InvManager() {
        super("Inv Manager", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetState();
    }

    private void resetState() {
        tickCounter = 0;
        startCounter = 0;
        cleaning = false;
        pendingMove = null;
        cleanerPausedUntilTick = 0;
        moveStates.clear();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null || gameMode.getPlayerMode() == GameType.SPECTATOR) {
            resetState();
            return;
        }

        if (onlyInInventory.get() && !(mc.gui.screen() instanceof InventoryScreen) || mc.gui.screen() instanceof ContainerScreen || mc.gui.screen() instanceof FurnaceScreen || HypixelDisabler.isInventoryMovePausedForChest()) {
            resetState();
            return;
        }

        ToolUtil.INSTANCE.setLegacyCombat(resolveLegacyCombat());

        if (startCounter < startDelay.get()) {
            cleaning = false;
            startCounter++;
            return;
        }

        var inventory = player.getInventory();

        verifyPendingMove(player);
        pruneMoveStates(player.tickCount);

        if (tickCounter > 0) {
            cleaning = true;
            tickCounter--;
            return;
        }

        // One move at a time: the previous click has to be confirmed before we touch anything else.
        if (pendingMove != null) {
            cleaning = true;
            return;
        }

        if (isAutoPotActing(player)) {
            cleaning = false;
            return;
        }

        if (sortWeapon(player, gameMode, inventory)) return;
        if (equipArmor(player, gameMode, inventory)) return;
        if (sortGoldenApple(player, gameMode, inventory)) return;
        if (handleBlockBorrow(player, gameMode, inventory)) return;
        if (sortToolCategory(player, gameMode, inventory, ToolUtil.TOOL_PICKAXE)) return;
        if (sortToolCategory(player, gameMode, inventory, ToolUtil.TOOL_AXE)) return;
        if (sortToolCategory(player, gameMode, inventory, ToolUtil.TOOL_SHOVEL)) return;
        if (handlePotionBorrow(player, gameMode, inventory)) return;
        if (cleaner.get() && runCleaner(player, gameMode, inventory)) return;

        cleaning = false;
    }

    /** 1.8 combat: no attack cooldown, so a slower weapon is not a worse weapon. */
    private boolean resolveLegacyCombat() {
        String style = combatStyle.get();
        if (style.equalsIgnoreCase("1.8.9")) return true;
        if (style.equalsIgnoreCase("Modern")) return false;

        return isLegacyServerVersion() || KillAura.autoBlockMode.get().toLowerCase().startsWith("1.8");
    }

    private static boolean isLegacyServerVersion() {
        ServerData server = mc.getCurrentServer();
        if (server == null || server.version == null) return false;

        Matcher matcher = SERVER_VERSION.matcher(server.version.getString());
        int major = -1;
        int minor = -1;
        while (matcher.find()) {
            int candidateMajor = Integer.parseInt(matcher.group(1));
            int candidateMinor = Integer.parseInt(matcher.group(2));
            if (candidateMajor > major || (candidateMajor == major && candidateMinor > minor)) {
                major = candidateMajor;
                minor = candidateMinor;
            }
        }

        return major == 1 && minor >= 0 && minor <= 8;
    }

    private boolean sortWeapon(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        int bestIndex = -1;
        double bestScore = 0.0D;

        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!matchesPreferredWeapon(stack)) continue;

            double score = weaponScore(stack);
            if (score <= 0.0D) continue;

            if (bestIndex == -1 || score > bestScore) {
                bestIndex = i;
                bestScore = score;
            }
        }

        if (bestIndex == -1 || bestIndex == ToolUtil.TOOL_SWORD) return false;

        ItemStack current = inventory.getItem(ToolUtil.TOOL_SWORD);
        if (matchesPreferredWeapon(current) && weaponScore(current) >= bestScore) return false;

        MoveKey key = new MoveKey("weapon", bestIndex, ToolUtil.TOOL_SWORD, inventory.getItem(bestIndex).getItem());
        if (!isMoveAvailable(key)) return false;

        cleaning = true;
        swapItems(player, gameMode, bestIndex, ToolUtil.TOOL_SWORD, key);
        tickCounter = nextItemDelay();
        return true;
    }

    private boolean matchesPreferredWeapon(ItemStack stack) {
        if (!ToolUtil.INSTANCE.isWeaponCandidate(stack)) return false;

        return switch (preferWeapon.get().toLowerCase()) {
            case "sword" -> ToolUtil.INSTANCE.getToolCategory(stack.getItem()) == ToolUtil.TOOL_SWORD;
            case "axe" -> ToolUtil.INSTANCE.getToolCategory(stack.getItem()) == ToolUtil.TOOL_AXE;
            case "mace" -> stack.getItem() == Items.MACE;
            default -> true;
        };
    }

    private double weaponScore(ItemStack stack) {
        if (!ToolUtil.INSTANCE.isWeaponCandidate(stack)) return 0.0D;

        if (!usesDpsScoring())
            return ToolUtil.INSTANCE.getToolTier(stack.getItem()) * 100.0D + getEnchantmentScore(stack);

        // Every scan in a tick asks about the same stacks again; the modifiers only change between ticks.
        int tick = mc.player == null ? 0 : mc.player.tickCount;
        if (tick != scoreCacheTick) {
            scoreCache.clear();
            scoreCacheTick = tick;
        }

        Double cached = scoreCache.get(stack);
        if (cached != null) return cached;

        double score = ToolUtil.INSTANCE.getWeaponScore(stack);
        scoreCache.put(stack, score);
        return score;
    }

    private boolean usesDpsScoring() {
        return !weaponScoring.get().equalsIgnoreCase("tier");
    }

    private boolean usesEnchantmentProtection() {
        return !armorScoring.get().equalsIgnoreCase("simple");
    }

    private boolean sortToolCategory(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory, int category) {
        ItemStack targetStack = null;
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            // The first slot belongs to sortWeapon, so a weapon that doubles as a tool stays there.
            if (category != ToolUtil.TOOL_SWORD && i == ToolUtil.TOOL_SWORD) continue;

            ItemStack stack = inventory.getItem(i);
            if (ToolUtil.INSTANCE.getToolCategory(stack.getItem()) != category) continue;
            if (targetStack == null || compareItems(stack, targetStack) > 0)
                targetStack = stack;
        }
        if (targetStack == null) return false;

        if (findItemInHotbarCategory(inventory, targetStack, category)) return false;

        int inventoryIndex = -1;
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            if (category != ToolUtil.TOOL_SWORD && i == ToolUtil.TOOL_SWORD) continue;

            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == targetStack.getItem() && compareItems(stack, targetStack) == 0) {
                inventoryIndex = i;
                break;
            }
        }
        if (inventoryIndex == -1) return false;

        MoveKey key = new MoveKey("tool:" + category, inventoryIndex, category, targetStack.getItem());
        if (!isMoveAvailable(key)) return false;

        cleaning = true;
        swapItems(player, gameMode, inventoryIndex, category, key);
        tickCounter = nextItemDelay();
        return true;
    }

    private boolean equipArmor(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        armorCandidates.clear();

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = inventory.getItem(i);
            if (ToolUtil.INSTANCE.isArmor(stack)) {
                EquipmentSlot armorEquipSlot = ToolUtil.INSTANCE.getArmorEquipmentSlot(stack);
                ArmorCandidate currentBest = armorCandidates.get(armorEquipSlot);
                if (currentBest == null) {
                    armorCandidates.put(armorEquipSlot, new ArmorCandidate(stack, i));
                } else if (ToolUtil.INSTANCE.isBetterArmor(stack, currentBest.stack, usesEnchantmentProtection())) {
                    currentBest.stack = stack;
                    currentBest.inventoryIndex = i;
                }
            }
        }

        for (Map.Entry<EquipmentSlot, ArmorCandidate> entry : armorCandidates.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ArmorCandidate candidate = entry.getValue();
            ItemStack bestArmor = candidate.stack;
            int inventoryIndex = candidate.inventoryIndex;
            ItemStack currentArmor = player.getItemBySlot(slot);

            if (!currentArmor.isEmpty() && !ToolUtil.INSTANCE.isBetterArmor(bestArmor, currentArmor, usesEnchantmentProtection()))
                continue;

            if (!currentArmor.isEmpty()) {
                // Do not throw gear away while the inventory is desynced, and never touch a bound piece.
                if (isCleanerPaused() || ToolUtil.INSTANCE.hasBindingCurse(currentArmor)) continue;

                int armorSlot = ToolUtil.INSTANCE.getItemSlotId(currentArmor);
                MoveKey dropKey = new MoveKey("armor:" + slot.getName(), armorSlot, -1, currentArmor.getItem());
                if (!isMoveAvailable(dropKey)) continue;

                cleaning = true;
                dropItem(player, gameMode, armorSlot, false);
                tickCounter = nextArmorDelay();
                return true;
            }

            MoveKey key = new MoveKey("armor:" + slot.getName(), inventoryIndex, -1, bestArmor.getItem());
            if (!isMoveAvailable(key)) continue;

            cleaning = true;
            int slotIndex = inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
            pendingMove = new PendingMove(key, player.tickCount, verifyDelay(), inventoryIndex, -1, slot,
                    bestArmor.copy(), ItemStack.EMPTY, bestArmor.copy());
            gameMode.handleContainerInput(player.containerMenu.containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, player);
            markClick(player);
            tickCounter = nextArmorDelay();
            return true;
        }

        return false;
    }

    private boolean handleBlockBorrow(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        if (!ModuleManager.getModuleState(Scaffold.class) || Scaffold.countUsableHotbarBlocks(inventory) >= blockRefill.get())
            return false;

        int inventoryIndex = -1;
        for (int i = 9; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!isScaffoldBlockStack(stack)) continue;

            if (inventoryIndex == -1 || stack.getCount() > inventory.getItem(inventoryIndex).getCount())
                inventoryIndex = i;
        }
        if (inventoryIndex == -1) return false;

        int hotbarSlot = findBorrowHotbarSlot(player, inventory);
        if (hotbarSlot == -1) return false;

        MoveKey key = new MoveKey("blocks", inventoryIndex, hotbarSlot, inventory.getItem(inventoryIndex).getItem());
        if (!isMoveAvailable(key)) return false;

        cleaning = true;
        swapItems(player, gameMode, inventoryIndex, hotbarSlot, key);
        tickCounter = nextItemDelay();
        return true;
    }

    private boolean sortGoldenApple(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        int bestIndex = -1;
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            if (getGoldenAppleRank(stack) == 0) continue;

            if (bestIndex == -1 || compareGoldenApples(stack, inventory.getItem(bestIndex)) > 0)
                bestIndex = i;
        }

        if (bestIndex == -1 || bestIndex == GOLDEN_APPLE_SLOT) return false;

        ItemStack current = inventory.getItem(GOLDEN_APPLE_SLOT);
        if (getGoldenAppleRank(current) >= getGoldenAppleRank(inventory.getItem(bestIndex))) return false;

        MoveKey key = new MoveKey("gapple", bestIndex, GOLDEN_APPLE_SLOT, inventory.getItem(bestIndex).getItem());
        if (!isMoveAvailable(key)) return false;

        cleaning = true;
        swapItems(player, gameMode, bestIndex, GOLDEN_APPLE_SLOT, key);
        tickCounter = nextItemDelay();
        return true;
    }

    private boolean runCleaner(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        // A reverted move means our slot data is stale, so drops would hit the wrong stack.
        if (isCleanerPaused()) return false;

        Arrays.fill(cleanerTargets, null);
        Arrays.fill(armorTargets, null);
        markExcessSlots(inventory);

        // Only one stack per tool category and one armour piece per slot is worth keeping.
        for (int i = 0; i <= 35; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            int slot = ToolUtil.INSTANCE.getToolCategory(stack.getItem());
            if (slot >= 0) {
                ItemStack targetStack = cleanerTargets[slot];
                if (targetStack == null || compareItems(stack, targetStack) > 0)
                    cleanerTargets[slot] = stack;
            }

            int armorSlot = armorSlotIndex(stack);
            if (armorSlot >= 0) {
                ItemStack bestArmor = armorTargets[armorSlot];
                if (bestArmor == null || ToolUtil.INSTANCE.isBetterArmor(stack, bestArmor, usesEnchantmentProtection()))
                    armorTargets[armorSlot] = stack;
            }
        }

        for (int i = 0; i <= 35; i++) {
            // Never drop the weapon that was just selected for the first slot.
            if (i == ToolUtil.TOOL_SWORD && ToolUtil.INSTANCE.isWeaponCandidate(inventory.getItem(i)))
                continue;

            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            boolean excess = excessSlots[i];
            boolean junk = strict.get() ? !isStrictKeep(stack) : !isLenientKeep(stack);
            if (!excess && !junk) continue;

            cleaning = true;
            int slot = i < 9 ? i + 36 : i;
            dropItem(player, gameMode, slot, excess || stack.getCount() > 1);
            tickCounter = nextItemDelay();
            return true;
        }

        return false;
    }

    private boolean isLenientKeep(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem && stack.getItem() != Items.CHEST) return true;
        if (stack.getItem() instanceof SpawnEggItem) return true;
        if (stack.getItem() instanceof PotionItem) return true;
        if (stack.getItem() instanceof SplashPotionItem) return true;
        if (stack.getItem() instanceof LingeringPotionItem) return true;
        if (isUsefulArmor(stack)) return true;

        return isAllowedCleanerItem(stack.getItem()) || containsCleanerTarget(stack);
    }

    /**
     * Armour only survives the cleaner when it is the one piece that would actually be worn for its
     * slot and it still beats what is equipped. Duplicates and worse pieces are dropped.
     */
    private boolean isUsefulArmor(ItemStack stack) {
        int slot = armorSlotIndex(stack);
        if (slot < 0) return false;
        if (stack != armorTargets[slot]) return false;

        var player = mc.player;
        if (player == null) return true;

        ItemStack equipped = player.getItemBySlot(ARMOR_SLOTS[slot]);
        return equipped.isEmpty()
                || ToolUtil.INSTANCE.isBetterArmor(stack, equipped, usesEnchantmentProtection());
    }

    /** 0 = helmet, 1 = chestplate, 2 = leggings, 3 = boots. */
    private static int armorSlotIndex(ItemStack stack) {
        if (!ToolUtil.INSTANCE.isArmor(stack)) return -1;

        return switch (ToolUtil.INSTANCE.getItemSlotId(stack)) {
            case 5 -> 0;
            case 6 -> 1;
            case 7 -> 2;
            case 8 -> 3;
            default -> -1;
        };
    }

    /** Strict mode keeps exactly the fight essentials and nothing else, still only the best of each. */
    private boolean isStrictKeep(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) return true;
        if (item instanceof BlockItem) return true;
        if (item == Items.ELYTRA) return true;
        if (isUsefulArmor(stack)) return true;
        if (item instanceof PotionItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem)
            return true;

        int category = ToolUtil.INSTANCE.getToolCategory(item);
        if (category != ToolUtil.TOOL_SWORD && category != ToolUtil.TOOL_PICKAXE && category != ToolUtil.TOOL_AXE)
            return false;

        return containsCleanerTarget(stack);
    }

    private void verifyPendingMove(LocalPlayer player) {
        PendingMove move = pendingMove;
        if (move == null) return;

        if (player.tickCount - move.issuedTick < move.verifyAfterTicks) return;

        Container inventory = player.getInventory();

        if (isMoveApplied(move, player, inventory)) {
            MoveState state = moveStates.get(move.key);
            if (state != null) {
                state.failures = 0;
                state.nextAttemptTick = 0;
            }
            pendingMove = null;
            return;
        }

        if (isMoveReverted(move, player, inventory)) {
            pendingMove = null;
            onMoveReverted(move.key);
            return;
        }

        // Still syncing: wait one more window before giving up quietly.
        if (move.extraWaits >= 1) {
            pendingMove = null;
            return;
        }

        move.extraWaits++;
        move.issuedTick = player.tickCount;
    }

    private boolean isMoveApplied(PendingMove move, LocalPlayer player, Container inventory) {
        if (!ItemStack.matches(inventory.getItem(move.sourceIndex), move.expectedSource))
            return false;

        if (move.armorSlot != null)
            return ItemStack.matches(player.getItemBySlot(move.armorSlot), move.movedStack);

        return ItemStack.matches(inventory.getItem(move.destinationIndex), move.expectedDestination);
    }

    private boolean isMoveReverted(PendingMove move, LocalPlayer player, Container inventory) {
        // The server put the item straight back where it was.
        if (!ItemStack.matches(inventory.getItem(move.sourceIndex), move.movedStack))
            return false;

        if (move.armorSlot != null)
            return !ItemStack.matches(player.getItemBySlot(move.armorSlot), move.movedStack);

        return !ItemStack.matches(inventory.getItem(move.destinationIndex), move.movedStack);
    }

    private void onMoveReverted(MoveKey key) {
        if (mc.player == null)
            return;

        int tick = mc.player.tickCount;
        cleanerPausedUntilTick = Math.max(cleanerPausedUntilTick, tick + cleanerPause.get());

        if (!isDynamic())
            return;

        MoveState state = moveStates.computeIfAbsent(key, ignored -> new MoveState());
        state.failures++;

        if (state.failures >= skipAfter.get()) {
            state.blockedUntilTick = tick + skipTime.get();
            state.nextAttemptTick = 0;
            state.failures = 0;
            return;
        }

        if (state.failures >= retryThreshold.get()) {
            int steps = state.failures - retryThreshold.get() + 1;
            int delay = Math.min(maxRetryDelay.get(), retryStep.get() * steps);
            state.nextAttemptTick = tick + delay;
            if (delay > 0)
                tickCounter = Math.max(tickCounter, delay);
        }
    }

    private void pruneMoveStates(int tick) {
        if (moveStates.isEmpty()) return;

        if (moveStates.size() > MOVE_STATE_LIMIT) {
            moveStates.clear();
            return;
        }

        moveStates.entrySet().removeIf(entry -> {
            MoveState state = entry.getValue();
            return state.failures == 0 && tick >= state.blockedUntilTick && tick >= state.nextAttemptTick;
        });
    }

    private boolean isMoveAvailable(MoveKey key) {
        if (!isDynamic() || mc.player == null)
            return true;

        MoveState state = moveStates.get(key);
        if (state == null)
            return true;

        int tick = mc.player.tickCount;
        return tick >= state.blockedUntilTick && tick >= state.nextAttemptTick;
    }

    private boolean isDynamic() {
        return retryMode.get().equalsIgnoreCase("Dynamic");
    }

    private boolean isCleanerPaused() {
        return mc.player != null && mc.player.tickCount < cleanerPausedUntilTick;
    }

    private int verifyDelay() {
        return Math.max(2, nextItemDelay());
    }

    private boolean isAutoPotActing(LocalPlayer player) {
        return ModuleManager.getModuleState(AutoPot.class)
                && (AutoPot.isThrowing() || AutoPot.getTicksSinceAction(player) < ACTION_GAP_TICKS);
    }

    public static int getTicksSinceClick(LocalPlayer player) {
        int ticks = player.tickCount - lastClickTick;
        return ticks < 0 ? Integer.MAX_VALUE : ticks;
    }

    private static void markClick(LocalPlayer player) {
        lastClickTick = player.tickCount;
    }

    private boolean handlePotionBorrow(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        if (!ModuleManager.getModuleState(AutoPot.class) || AutoPot.isThrowing() || AutoPot.getRequestedPotions().isEmpty() || hasRequestedPotionInHotbar(inventory))
            return false;

        int inventoryIndex = -1;
        for (int i = 9; i < Math.min(36, inventory.getContainerSize()); i++) {
            if (AutoPot.isRequestedPotion(inventory.getItem(i))) {
                inventoryIndex = i;
                break;
            }
        }
        if (inventoryIndex == -1) return false;

        int hotbarSlot = findBorrowHotbarSlot(player, inventory);
        if (hotbarSlot == -1) return false;

        MoveKey key = new MoveKey("potion", inventoryIndex, hotbarSlot, inventory.getItem(inventoryIndex).getItem());
        if (!isMoveAvailable(key)) return false;

        cleaning = true;
        swapItems(player, gameMode, inventoryIndex, hotbarSlot, key);
        tickCounter = nextItemDelay();
        return true;
    }

    private boolean hasRequestedPotionInHotbar(Container inventory) {
        for (int i = 0; i <= 8; i++) {
            if (AutoPot.isRequestedPotion(inventory.getItem(i)))
                return true;
        }
        return false;
    }

    private int findBorrowHotbarSlot(LocalPlayer player, Container inventory) {
        int selected = player.getInventory().getSelectedSlot();
        int fallback = -1;

        for (int i = 8; i >= 0; i--) {
            if (i == selected || i == GOLDEN_APPLE_SLOT || isToolHotbarSlot(i) || isScaffoldBlockStack(inventory.getItem(i))) continue;

            if (inventory.getItem(i).isEmpty())
                return i;

            if (fallback == -1)
                fallback = i;
        }
        return fallback;
    }

    private static boolean isScaffoldBlockStack(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && Scaffold.isScaffoldBlock(blockItem.getBlock());
    }

    private int compareGoldenApples(ItemStack apple1, ItemStack apple2) {
        int rank = Integer.compare(getGoldenAppleRank(apple1), getGoldenAppleRank(apple2));
        if (rank != 0) return rank;

        return Integer.compare(apple1.getCount(), apple2.getCount());
    }

    private static int getGoldenAppleRank(ItemStack stack) {
        if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return 2;
        if (stack.getItem() == Items.GOLDEN_APPLE) return 1;
        return 0;
    }

    private boolean isToolHotbarSlot(int slot) {
        return slot == ToolUtil.TOOL_SWORD || slot == ToolUtil.TOOL_PICKAXE || slot == ToolUtil.TOOL_AXE || slot == ToolUtil.TOOL_SHOVEL;
    }

    private void markExcessSlots(Container inventory) {
        Arrays.fill(excessSlots, false);

        List<Integer> throwables = new ArrayList<>();
        List<Integer> blocks = new ArrayList<>();
        List<Integer> harmfulPotions = new ArrayList<>();
        int bestRod = -1;
        boolean hasWaterBucket = false;

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (isLimitedThrowable(item)) {
                throwables.add(i);
            } else if (item == Items.FISHING_ROD) {
                if (bestRod == -1 || compareRods(stack, inventory.getItem(bestRod)) > 0)
                    bestRod = i;
            } else if (item == Items.WATER_BUCKET) {
                if (hasWaterBucket) excessSlots[i] = true;
                hasWaterBucket = true;
            } else if (isHarmfulPotion(stack)) {
                harmfulPotions.add(i);
            } else if (item instanceof BlockItem blockItem) {
                if (blockItem.getBlock() instanceof BasePressurePlateBlock)
                    excessSlots[i] = true;
                else if (Scaffold.isScaffoldBlock(blockItem.getBlock()))
                    blocks.add(i);
            }
        }

        for (int i = 0; i <= 35; i++) {
            if (i != bestRod && inventory.getItem(i).getItem() == Items.FISHING_ROD)
                excessSlots[i] = true;
        }

        markStacksOverLimit(inventory, throwables, MAX_THROWABLE_STACKS);
        if (!ModuleManager.getModuleState(Scaffold.class)) {
            int blockLimit = MAX_BLOCK_STACKS;
            if (mc.player != null && blocks.remove(Integer.valueOf(mc.player.getInventory().getSelectedSlot())))
                blockLimit--;
            markStacksOverLimit(inventory, blocks, blockLimit);
        }
        markItemsOverLimit(inventory, harmfulPotions, MAX_HARMFUL_POTIONS);
    }

    private void markItemsOverLimit(Container inventory, List<Integer> slots, int limit) {
        int kept = 0;
        for (int slot : slots) {
            int count = inventory.getItem(slot).getCount();
            if (kept + count <= limit) kept += count;
            else excessSlots[slot] = true;
        }
    }

    private static boolean isHarmfulPotion(ItemStack stack) {
        Item item = stack.getItem();
        if (!(item instanceof PotionItem) && !(item instanceof SplashPotionItem) && !(item instanceof LingeringPotionItem))
            return false;

        boolean harmful = false;
        PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effectInstance : potionContents.getAllEffects()) {
            MobEffectCategory category = effectInstance.getEffect().value().getCategory();
            if (category == MobEffectCategory.BENEFICIAL) return false;
            if (category == MobEffectCategory.HARMFUL) harmful = true;
        }
        return harmful;
    }

    private void markStacksOverLimit(Container inventory, List<Integer> slots, int limit) {
        if (slots.size() <= limit) return;

        slots.sort(Comparator.<Integer>comparingInt(i -> inventory.getItem(i).getCount()).reversed().thenComparingInt(i -> i));
        for (int i = limit; i < slots.size(); i++)
            excessSlots[slots.get(i)] = true;
    }

    private int compareRods(ItemStack rod1, ItemStack rod2) {
        int enchantments = Integer.compare(getEnchantmentScore(rod1), getEnchantmentScore(rod2));
        if (enchantments != 0) return enchantments;

        int durability1 = rod1.getMaxDamage() - rod1.getDamageValue();
        int durability2 = rod2.getMaxDamage() - rod2.getDamageValue();
        return Integer.compare(durability1, durability2);
    }

    private static boolean isLimitedThrowable(Item item) {
        return item == Items.SNOWBALL
                || item == Items.EGG
                || item == Items.BLUE_EGG
                || item == Items.BROWN_EGG;
    }

    private boolean findItemInHotbarCategory(Container inventory, ItemStack targetStack, int category) {
        ItemStack stack = inventory.getItem(category);
        return stack.getItem() == targetStack.getItem() && compareItems(stack, targetStack) == 0;
    }

    private int compareItems(ItemStack stack1, ItemStack stack2) {
        Item item1 = stack1.getItem();
        Item item2 = stack2.getItem();

        int rank1 = 0;
        int rank2 = 0;

        int category = ToolUtil.INSTANCE.getToolCategory(item1);
        if (category >= 0 && category == ToolUtil.INSTANCE.getToolCategory(item2)) {
            if (category == ToolUtil.TOOL_SWORD
                    || ToolUtil.INSTANCE.isWeaponCandidate(stack1) && ToolUtil.INSTANCE.isWeaponCandidate(stack2)) {
                if (usesDpsScoring()) {
                    int weaponComparison = Double.compare(weaponScore(stack1), weaponScore(stack2));
                    if (weaponComparison != 0)
                        return weaponComparison;
                } else {
                    int attackDamageComparison = Double.compare(
                            ToolUtil.INSTANCE.getAttackDamage(stack1),
                            ToolUtil.INSTANCE.getAttackDamage(stack2));
                    if (attackDamageComparison != 0)
                        return attackDamageComparison;
                }
            }

            rank1 = ToolUtil.INSTANCE.getToolTier(item1);
            rank2 = ToolUtil.INSTANCE.getToolTier(item2);
        }

        if (rank1 != rank2)
            return Integer.compare(rank1, rank2);

        if (item1 == item2) {
            int enchantments1 = getEnchantmentScore(stack1);
            int enchantments2 = getEnchantmentScore(stack2);
            if (enchantments1 != enchantments2)
                return Integer.compare(enchantments1, enchantments2);
        }

        return 0;
    }

    private int getEnchantmentScore(ItemStack stack) {
        int score = 0;
        for (var entry : stack.getEnchantments().entrySet())
            score += entry.getIntValue();
        return score;
    }

    private boolean containsCleanerTarget(ItemStack stack) {
        // Identity, not equality: only the exact best stack of a category survives.
        for (ItemStack target : cleanerTargets) {
            if (stack == target)
                return true;
        }
        return false;
    }

    private static boolean isAllowedCleanerItem(Item item) {
        return item == Items.ENDER_PEARL
                || item == Items.ENDER_EYE
                || item == Items.TRIDENT
                || item == Items.MACE
                || item == Items.BOW
                || item == Items.CROSSBOW
                || item == Items.ARROW
                || item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.APPLE
                || item == Items.MUSHROOM_STEW
                || item == Items.BREAD
                || item == Items.PORKCHOP
                || item == Items.COOKED_PORKCHOP
                || item == Items.GOLDEN_CARROT
                || item == Items.CARROT
                || item == Items.POTATO
                || item == Items.BAKED_POTATO
                || item == Items.COOKED_BEEF
                || item == Items.BEEF
                || item == Items.COOKED_CHICKEN
                || item == Items.CHICKEN
                || item == Items.COOKED_MUTTON
                || item == Items.MUTTON
                || item == Items.COOKED_RABBIT
                || item == Items.RABBIT
                || item == Items.RABBIT_STEW
                || item == Items.BEETROOT
                || item == Items.BEETROOT_SOUP
                || item == Items.MELON_SLICE
                || item == Items.PUMPKIN_PIE
                || item == Items.COOKIE
                || item == Items.SWEET_BERRIES
                || item == Items.COD
                || item == Items.COOKED_COD
                || item == Items.SALMON
                || item == Items.COOKED_SALMON
                || item == Items.TROPICAL_FISH
                || item == Items.PUFFERFISH
                || item == Items.HONEY_BOTTLE
                || item == Items.GLOW_BERRIES
                || item == Items.DRIED_KELP
                || item == Items.ROTTEN_FLESH
                || item == Items.POISONOUS_POTATO
                || item == Items.COMPASS
                || item == Items.RECOVERY_COMPASS
                || item == Items.WATER_BUCKET
                || item == Items.ELYTRA
                || item == Items.IRON_INGOT
                || item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.GOLD_INGOT
                || item == Items.NETHERITE_INGOT
                || item == Items.SHIELD
                || item == Items.BLAZE_POWDER
                || item == Items.PAPER
                || item == Items.FISHING_ROD
                || item == Items.SNOWBALL
                || item == Items.EGG
                || item == Items.BLUE_EGG
                || item == Items.BROWN_EGG
                || item == Items.BUCKET;
    }

    private int nextItemDelay() {
        return nextDelay(minItemDelay.get(), maxItemDelay.get());
    }

    private int nextArmorDelay() {
        return nextDelay(minArmorDelay.get(), maxArmorDelay.get());
    }

    private int nextDelay(int min, int max) {
        int lower = Math.min(min, max);
        int upper = Math.max(min, max);

        return RandomUtil.nextInt(lower, upper + 1);
    }

    private void swapItems(LocalPlayer player, MultiPlayerGameMode gameMode, int from, int to, MoveKey key) {
        int syncId = player.containerMenu.containerId;
        int fromSlot = from < 9 ? from + 36 : from;
        if (fromSlot == to + 36)
            return;

        Container inventory = player.getInventory();
        ItemStack movedStack = inventory.getItem(from).copy();
        ItemStack replacedStack = inventory.getItem(to).copy();

        pendingMove = new PendingMove(key, player.tickCount, verifyDelay(), from, to, null,
                movedStack, replacedStack, movedStack);

        gameMode.handleContainerInput(syncId, fromSlot, to, ContainerInput.SWAP, player);
        markClick(player);
    }

    private void dropItem(LocalPlayer player, MultiPlayerGameMode gameMode, int slot, boolean wholeStack) {
        markClick(player);
        if (!player.hasInfiniteMaterials()) {
            int button = wholeStack ? 1 : 0;
            gameMode.handleContainerInput(player.containerMenu.containerId, slot, button, ContainerInput.THROW, player);
            return;
        }

        // A creative THROW click sends both the creative drop packet and the regular container
        // click packet. Mirror CreativeModeInventoryScreen so the server processes only one drop.
        var inventorySlot = player.inventoryMenu.getSlot(slot);
        if (!inventorySlot.hasItem()) return;

        int amount = wholeStack ? inventorySlot.getItem().getMaxStackSize() : 1;
        ItemStack droppedStack = inventorySlot.remove(amount);
        if (droppedStack.isEmpty()) return;

        player.drop(droppedStack, true, Prediction.PREDICTED);
        gameMode.handleCreativeModeItemDrop(droppedStack);
        gameMode.handleCreativeModeItemAdd(inventorySlot.getItem(), inventorySlot.index);
    }

    public static boolean getCleaning() {
        return cleaning;
    }

    public static void setCleaning(boolean value) {
        cleaning = value;
    }

    private record MoveKey(String task, int fromSlot, int toSlot, Item item) {
    }

    private static final class MoveState {
        private int failures;
        private int nextAttemptTick;
        private int blockedUntilTick;
    }

    private static final class PendingMove {
        private final MoveKey key;
        private final int verifyAfterTicks;
        private final int sourceIndex;
        private final int destinationIndex;
        private final EquipmentSlot armorSlot;
        private final ItemStack movedStack;
        private final ItemStack expectedSource;
        private final ItemStack expectedDestination;
        private int issuedTick;
        private int extraWaits;

        private PendingMove(MoveKey key, int issuedTick, int verifyAfterTicks, int sourceIndex, int destinationIndex,
                            EquipmentSlot armorSlot, ItemStack movedStack, ItemStack expectedSource,
                            ItemStack expectedDestination) {
            this.key = key;
            this.issuedTick = issuedTick;
            this.verifyAfterTicks = verifyAfterTicks;
            this.sourceIndex = sourceIndex;
            this.destinationIndex = destinationIndex;
            this.armorSlot = armorSlot;
            this.movedStack = movedStack;
            this.expectedSource = expectedSource;
            this.expectedDestination = expectedDestination;
        }
    }

    private static final class ArmorCandidate {
        private ItemStack stack;
        private int inventoryIndex;

        private ArmorCandidate(ItemStack stack, int inventoryIndex) {
            this.stack = stack;
            this.inventoryIndex = inventoryIndex;
        }
    }
}
