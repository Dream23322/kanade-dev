package com.instrumentalist.krs.utils.math;

import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;

public final class ToolUtil implements IMinecraft {
    public static final ToolUtil INSTANCE = new ToolUtil();

    /** 1.8 damage advantage of a sword, which can block while an axe cannot. */
    private static final double LEGACY_SWORD_BONUS = 0.5D;
    /** Keeps 1.8 scores on the same scale as a sword's 1.6 hits per second. */
    private static final double LEGACY_SWING_DIVISOR = 12.5D;

    private boolean legacyCombat;

    public static final int TOOL_SWORD = 0;
    public static final int TOOL_PICKAXE = 1;
    public static final int TOOL_AXE = 2;
    public static final int TOOL_SHOVEL = 3;

    /** Protection caps used by the vanilla damage reduction formula. */
    private static final double MAX_ARMOR_POINTS = 20.0D;
    private static final double MAX_ENCHANTMENT_PROTECTION = 20.0D;

    private static final Map<Item, int[]> TOOL_INFO = new HashMap<>();

    private static final Map<Item, Double> FALLBACK_WEAPON_DAMAGE = Map.ofEntries(
            Map.entry(Items.NETHERITE_SWORD, 8.0D),
            Map.entry(Items.DIAMOND_SWORD, 7.0D),
            Map.entry(Items.IRON_SWORD, 6.0D),
            Map.entry(Items.COPPER_SWORD, 5.0D),
            Map.entry(Items.STONE_SWORD, 5.0D),
            Map.entry(Items.GOLDEN_SWORD, 4.0D),
            Map.entry(Items.WOODEN_SWORD, 4.0D),
            Map.entry(Items.NETHERITE_AXE, 10.0D),
            Map.entry(Items.DIAMOND_AXE, 9.0D),
            Map.entry(Items.IRON_AXE, 9.0D),
            Map.entry(Items.COPPER_AXE, 8.0D),
            Map.entry(Items.STONE_AXE, 9.0D),
            Map.entry(Items.GOLDEN_AXE, 7.0D),
            Map.entry(Items.WOODEN_AXE, 7.0D),
            Map.entry(Items.TRIDENT, 9.0D),
            Map.entry(Items.MACE, 6.0D)
    );

    static {
        Item[][] tools = {
                {Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL},
                {Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL},
                {Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL},
                {Items.COPPER_SWORD, Items.COPPER_PICKAXE, Items.COPPER_AXE, Items.COPPER_SHOVEL},
                {Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL},
                {Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL},
                {Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL}
        };
        for (int tier = 0; tier < tools.length; tier++) {
            for (int category = 0; category < tools[tier].length; category++)
                TOOL_INFO.put(tools[tier][category], new int[]{category, tier + 1});
        }
    }

    public final Minecraft mc = Minecraft.getInstance();

    private ToolUtil() {
    }

    public int findBestTool(BlockPos pos) {
        var player = mc.player;
        if (player == null || player.isCreative()) return -1;

        var blockState = player.level().getBlockState(pos);
        int bestSlot = -1;
        double bestSpeed = 0.0;

        for (int i = 0; i <= 8; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.has(DataComponents.TOOL) || stack.getItem() instanceof ShearsItem) {
                float speed = stack.getDestroySpeed(blockState);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        return bestSpeed > 1.0 ? bestSlot : -1;
    }

    public int findBestSword() {
        var player = mc.player;
        if (player == null) return -1;

        int bestSwordSlot = -1;
        double bestScore = 0.0D;

        for (int i = 0; i <= 8; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isWeaponCandidate(stack)) continue;

            double score = getWeaponScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSwordSlot = i;
            }
        }

        return bestSwordSlot;
    }

    public boolean isSword(ItemStack stack) {
        return materialStrength(stack.getItem()) >= 0;
    }

    private int materialStrength(Item item) {
        return getToolCategory(item) == TOOL_SWORD ? getToolTier(item) : -1;
    }

    public int getToolCategory(Item item) {
        int[] info = TOOL_INFO.get(item);
        return info != null ? info[0] : -1;
    }

    public int getToolTier(Item item) {
        int[] info = TOOL_INFO.get(item);
        return info != null ? info[1] : 0;
    }

    /**
     * Any item that can be used as a melee weapon. Covers every weapon type the game adds through
     * the weapon component or attack damage modifiers, so new materials do not need to be listed.
     */
    public boolean isWeaponCandidate(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.has(DataComponents.WEAPON)) return true;
        if (getToolCategory(stack.getItem()) == TOOL_SWORD) return true;
        if (stack.getItem() == Items.MACE || stack.getItem() == Items.TRIDENT) return true;

        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.modifier().amount() > 0.0D)
                    return true;
            }
        }

        return false;
    }

    /** Main hand attack damage including the sharpness bonus. */
    public double getAttackDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0.0D;

        double base = mc.player != null ? mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) : 1.0D;
        ModifierTotals totals = collectModifiers(stack, Attributes.ATTACK_DAMAGE);

        double damage = totals.found()
                ? (base + totals.add()) * (1.0D + totals.multipliedBase()) * (1.0D + totals.multipliedTotal())
                : FALLBACK_WEAPON_DAMAGE.getOrDefault(stack.getItem(), 0.0D);

        return damage + getSharpnessDamage(stack.getEnchantments());
    }

    /** Main hand attack speed, used to turn damage into damage per second. */
    public double getAttackSpeed(ItemStack stack) {
        double base = mc.player != null ? mc.player.getAttributeBaseValue(Attributes.ATTACK_SPEED) : 4.0D;
        ModifierTotals totals = collectModifiers(stack, Attributes.ATTACK_SPEED);
        if (!totals.found()) return base;

        return Math.max(0.1D, (base + totals.add())
                * (1.0D + totals.multipliedBase())
                * (1.0D + totals.multipliedTotal()));
    }

    /**
     * Effective melee damage per second. Damage per hit is divided by the cooldown the attack speed
     * implies, so a slower but harder hitting weapon is compared on equal footing with a sword.
     *
     * <p>1.8 combat has no attack cooldown, so the attack speed no longer separates weapons and the
     * hit itself decides the ranking; swords get a small bonus because they can still block.</p>
     */
    public double getWeaponScore(ItemStack stack) {
        if (!isWeaponCandidate(stack)) return 0.0D;

        double dps;
        if (legacyCombat) {
            double damage = getAttackDamage(stack);
            if (getToolCategory(stack.getItem()) == TOOL_SWORD) damage += LEGACY_SWORD_BONUS;
            dps = damage / LEGACY_SWING_DIVISOR;
        } else {
            dps = getAttackDamage(stack) * getAttackSpeed(stack) / 20.0D;
        }

        ItemEnchantments enchantments = stack.getEnchantments();
        dps += getEnchantmentLevel(enchantments, Enchantments.FIRE_ASPECT) * 0.4D;
        dps += getEnchantmentLevel(enchantments, Enchantments.KNOCKBACK) * 0.1D;
        dps += getEnchantmentLevel(enchantments, Enchantments.UNBREAKING) * 0.05D;
        dps += getEnchantmentLevel(enchantments, Enchantments.MENDING) * 0.1D;

        if (stack.getMaxDamage() > 0) {
            double remaining = durabilityFraction(stack);
            if (remaining < 0.15D) dps *= 0.5D;
            else if (remaining < 0.35D) dps *= 0.8D;
        }

        return dps;
    }

    /** Whether the current server runs 1.8 combat, where hit damage beats attack speed. */
    public boolean isLegacyCombat() {
        return legacyCombat;
    }

    public void setLegacyCombat(boolean legacy) {
        legacyCombat = legacy;
    }

    /** Sharpness adds {@code 1 + 0.5 * (level - 1)} damage per hit. */
    public double getSharpnessDamage(ItemEnchantments itemEnchantments) {
        int level = getEnchantmentLevel(itemEnchantments, Enchantments.SHARPNESS);
        if (level <= 0) return 0.0D;

        return 1.0D + 0.5D * (level - 1);
    }

    private ModifierTotals collectModifiers(ItemStack stack, Holder<Attribute> attribute) {
        ModifierTotals totals = new ModifierTotals();
        var component = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (component == null) return totals;

        for (var entry : component.modifiers()) {
            if (!entry.attribute().is(attribute)) continue;

            var modifier = entry.modifier();
            totals.found = true;
            switch (modifier.operation()) {
                case ADD_VALUE -> totals.add += modifier.amount();
                case ADD_MULTIPLIED_BASE -> totals.multipliedBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> totals.multipliedTotal += modifier.amount();
            }
        }

        return totals;
    }

    private static final class ModifierTotals {
        private double add;
        private double multipliedBase;
        private double multipliedTotal;
        private boolean found;

        private double add() { return add; }
        private double multipliedBase() { return multipliedBase; }
        private double multipliedTotal() { return multipliedTotal; }
        private boolean found() { return found; }
    }

    private int getEnchantmentLevel(ItemEnchantments itemEnchantments, ResourceKey<Enchantment> enchantment) {
        for (var entry : itemEnchantments.entrySet()) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    public int getItemSlotId(ItemStack itemStack) {
        var data = itemStack.get(DataComponents.EQUIPPABLE);
        if (data == null) return -1;

        EquipmentSlot slot = data.slot();
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> slot.getId();
        };
    }

    public boolean isArmor(ItemStack itemStack) {
        if (itemStack.isEmpty() || !itemStack.has(DataComponents.EQUIPPABLE)) return false;

        var equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || !equippable.slot().isArmor()) return false;

        var modifiers = itemStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return false;

        for (var modifier : modifiers.modifiers()) {
            if (modifier.attribute() == Attributes.ARMOR || modifier.attribute() == Attributes.ARMOR_TOUGHNESS)
                return true;
        }

        return false;
    }

    public EquipmentSlot getArmorEquipmentSlot(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null ? equippable.slot() : EquipmentSlot.HEAD;
    }

    public boolean hasBindingCurse(ItemStack itemStack) {
        if (itemStack.isEmpty()) return false;
        return getEnchantmentLevel(itemStack.getEnchantments(), Enchantments.BINDING_CURSE) > 0;
    }

    public boolean isBetterArmor(ItemStack newArmor, ItemStack currentArmor) {
        return isBetterArmor(newArmor, currentArmor, true);
    }

    public boolean isBetterArmor(ItemStack newArmor, ItemStack currentArmor, boolean enchantmentProtection) {
        if (!isArmor(newArmor) || !isArmor(currentArmor)) return false;
        return getArmorScore(newArmor, enchantmentProtection) > getArmorScore(currentArmor, enchantmentProtection);
    }

    public double getArmorScore(ItemStack itemStack) {
        return getArmorScore(itemStack, true);
    }

    /**
     * Armour value as a percentage of incoming damage that the piece removes.
     *
     * <p>Armour points scale up to an 80% reduction on their own, protection enchantments add
     * their own multiplier on top of that, and the enchantment contribution is capped at the
     * vanilla 20 point limit. Durability and the two upkeep enchantments only break ties.</p>
     */
    public double getArmorScore(ItemStack itemStack, boolean enchantmentProtection) {
        if (itemStack.isEmpty()) return 0.0D;

        double armorPoints = 0.0D;
        double toughness = 0.0D;
        var modifiers = itemStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                double amount = entry.modifier().amount();
                if (entry.attribute().is(Attributes.ARMOR)) armorPoints += amount;
                else if (entry.attribute().is(Attributes.ARMOR_TOUGHNESS)) toughness += amount;
            }
        }

        ItemEnchantments enchantments = itemStack.getEnchantments();
        int protection = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
        int blastProtection = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
        int fireProtection = getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
        int projectileProtection = getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);

        double tieBreaker = durabilityFraction(itemStack) * 10.0D
                + getEnchantmentLevel(enchantments, Enchantments.UNBREAKING) * 2.0D
                + getEnchantmentLevel(enchantments, Enchantments.MENDING) * 4.0D;

        if (!enchantmentProtection)
            return (armorPoints + toughness * 2.0D) * 10.0D
                    + (protection + blastProtection + fireProtection + projectileProtection) * 6.0D
                    + tieBreaker;

        double armorReduction = Math.min(MAX_ARMOR_POINTS, armorPoints + toughness * 0.5D) / 25.0D;
        double enchantmentPoints = protection
                + 2.0D * (blastProtection + fireProtection + projectileProtection);
        double enchantmentReduction = Math.min(MAX_ENCHANTMENT_PROTECTION, enchantmentPoints) * 0.04D;
        double reduction = 1.0D - (1.0D - armorReduction) * (1.0D - enchantmentReduction);

        return reduction * 1000.0D + tieBreaker;
    }

    private static double durabilityFraction(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) return 1.0D;

        return Math.max(0.0D, (double) (max - stack.getDamageValue()) / max);
    }
}
