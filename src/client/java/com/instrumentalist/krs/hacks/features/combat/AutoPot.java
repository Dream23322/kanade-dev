package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.events.features.WorldLeaveEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.player.InvManager;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.TickTimer;
import com.instrumentalist.krs.utils.network.Latency;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoPot extends Module {
    private static final int ACTION_GAP_TICKS = 2;
    private static final int HOP_GRACE_TICKS = 12;
    private static final double HOP_MAX_GROUND_DISTANCE = 1.5D;
    private static final int POT_COOLDOWN_TICKS = 10;
    private static final int CONFIRM_RETRIES = 1;
    private static final int MAX_SAMPLES = 200;
    private static final int POTION_FLIGHT_TICKS = 4;
    private static final int HEAL_HORIZON_TICKS = 100;
    private static final int PREPOT_LINGER_TICKS = 20;
    private static final double MIN_DPS = 0.25D;

    private static final List<Holder<MobEffect>> HEALING_EFFECTS = List.of(
            MobEffects.INSTANT_HEALTH,
            MobEffects.REGENERATION
    );

    /** Order the prepot bind throws in. Anything else beneficial is appended after these. */
    private static final List<Holder<MobEffect>> PREPOT_ORDER = List.of(
            MobEffects.STRENGTH,
            MobEffects.RESISTANCE,
            MobEffects.REGENERATION
    );

    @Setting
    private final BooleanValue heal = new BooleanValue("Heal", true)
            .describe("Throws healing potions when health is low.");

    @Setting
    private final BooleanValue buffs = new BooleanValue("Buffs", true)
            .describe("Throws beneficial potions that are missing.");

    @Setting
    private final KeyBindValue prepotKey = new KeyBindValue("Prepot Bind", Keys.KEY_UNKNOWN)
            .describe("Throws one of every beneficial potion you are missing when pressed.");

    @Setting
    private final BooleanValue prepotStatus = new BooleanValue("Prepot Status", true)
            .describe("Shows the prepot progress in the on screen status line, like the blinking indicator.");

    @Setting
    private final FloatValue healAtHealth = new FloatValue("Heal At Health", 10f, 1f, 20f, heal::get)
            .describe("Health at which a healing potion is thrown.");

    @Setting
    private final FloatValue regenHealth = new FloatValue("Regen Health", 16f, 1f, 20f, heal::get)
            .describe("Health at which regeneration is thrown. It heals over time, so it goes earlier than instant health.");

    @Setting
    private final FloatValue panicHealth = new FloatValue("Panic Health", 6f, 1f, 20f)
            .describe("Health below which the mid fight restriction no longer applies.");

    @Setting
    private final BooleanValue notMidFight = new BooleanValue("Not Mid Fight", true)
            .describe("Only throws healing potions when you are not fighting.");

    @Setting
    private final FloatValue combatRange = new FloatValue("Combat Range", 6f, 1f, 12f, "m", notMidFight::get)
            .describe("Distance at which another player counts as a fight.");

    @Setting
    private final IntValue combatRecall = new IntValue("Combat Recall", 40, 0, 200, "t", notMidFight::get)
            .describe("Ticks a recent hit keeps you counted as fighting.");

    @Setting
    private final BooleanValue avoidOthers = new BooleanValue("Avoid Others", true)
            .describe("Does not throw when another entity would be caught by the splash.");

    @Setting
    private final FloatValue splashRadius = new FloatValue("Splash Radius", 4f, 2f, 8f, "m", avoidOthers::get)
            .describe("Radius that counts as healing someone else.");

    @Setting
    private final BooleanValue ignoreTeammates = new BooleanValue("Ignore Teammates", true, avoidOthers::get)
            .describe("Teammates do not block a splash.");

    @Setting
    private final BooleanValue ignoreMobs = new BooleanValue("Ignore Mobs", true, avoidOthers::get)
            .describe("Non player entities do not block a splash.");

    @Setting
    private final BooleanValue healAnyway = new BooleanValue("Heal Anyway", false, avoidOthers::get)
            .describe("Throws after Max Wait even when someone else is in the splash.");

    @Setting
    private final IntValue maxWait = new IntValue("Max Wait", 20, 0, 100, "t", () -> avoidOthers.get() && healAnyway.get())
            .describe("Ticks to wait before healing anyway.");

    @Setting
    private final BooleanValue highPingConfirm = new BooleanValue("High Ping Confirm", true)
            .describe("Waits for the potion to land before throwing the next one.");

    @Setting
    private final BooleanValue groundOnly = new BooleanValue("Ground Only", false)
            .describe("Only throws while standing on the ground.");

    @Setting
    private final BooleanValue smartHeal = new BooleanValue("Smart Heal", false)
            .describe("Only heals when the damage maths says healing beats attacking.");

    @Setting
    private final IntValue dpsWindow = new IntValue("DPS Window", 60, 20, 200, "t", smartHeal::get)
            .describe("Ticks of taken and dealt damage used for the decision.");

    @Setting
    private final BooleanValue pingStop = new BooleanValue("Ping Stop", false)
            .describe("Stops speed and movement while throwing on a slow connection.");

    @Setting
    private final IntValue pingStopThreshold = new IntValue("Ping Stop Threshold", 150, 50, 500, "ms", pingStop::get)
            .describe("Ping at which speed and movement are stopped for the throw.");

    private final TickTimer potTimer = new TickTimer();
    private final ArrayList<Holder<MobEffect>> needPots = new ArrayList<>();
    private final ArrayList<Holder<MobEffect>> prepotQueue = new ArrayList<>();
    private int prepotTotal;
    private int prepotDone;
    private int prepotLingerTicks;
    private final ArrayList<Holder<MobEffect>> buffOrder = new ArrayList<>(List.of(
            MobEffects.SPEED,
            MobEffects.RESISTANCE,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.INVISIBILITY,
            MobEffects.WATER_BREATHING,
            MobEffects.STRENGTH
    ));

    private static int oldSlot = -1;
    private static final List<Holder<MobEffect>> requestedPotions = new ArrayList<>();
    private static boolean throwing;
    private static int lastActionTick = Integer.MIN_VALUE / 2;

    private boolean wasRotating;
    private int lastHurtTime;
    private int lastSelfHurtTick = Integer.MIN_VALUE / 2;
    private int waitTicks;
    private int retries;
    private int confirmUntilTick;
    private int lastGroundTick = Integer.MIN_VALUE / 2;
    private float healthAtThrow;
    private boolean awaitingConfirmation;
    private LocalPlayer lastPrepotPlayer;

    private final float[] incomingSamples = new float[MAX_SAMPLES];
    private final float[] outgoingSamples = new float[MAX_SAMPLES];
    private int sampleIndex;
    private int sampleCount;
    private float lastSelfHealth = Float.NaN;
    private float lastTargetHealth = Float.NaN;
    private int lastTargetId = -1;
    private boolean stoppingForThrow;
    private boolean speedSuppressed;

    @Override
    public String description() {
        return "Throws healing or speed potions automatically";
    }

    public AutoPot() {
        super("Auto Pot", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        cancelThrow(mc.player);
        updatePingStop(false);
        requestedPotions.clear();
        needPots.clear();
        clearPrepot();
        potTimer.reset();
    }

    private static AutoPot activeForStatus() {
        AutoPot module = ModuleManager.getModule(AutoPot.class);
        return module != null && module.tempEnabled && module.prepotStatus.get() ? module : null;
    }

    /** True while the prepot bind still has potions to throw, plus a short tail after the last one. */
    public static boolean isPrepotting() {
        AutoPot module = activeForStatus();
        return module != null && (!module.prepotQueue.isEmpty() || module.prepotLingerTicks > 0);
    }

    public static int getPrepotDone() {
        AutoPot module = activeForStatus();
        return module == null ? 0 : module.prepotDone;
    }

    public static int getPrepotTotal() {
        AutoPot module = activeForStatus();
        return module == null ? 0 : module.prepotTotal;
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearPrepot();
    }

    @Override
    public void onWorldLeave(WorldLeaveEvent event) {
        clearPrepot();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onKey(KeyboardEvent event) {
        int key = prepotKey.get();
        if (key == Keys.KEY_UNKNOWN || event.key != key || event.action != Keys.PRESS)
            return;

        var player = mc.player;
        if (player == null || !tempEnabled)
            return;

        queuePrepot(player);
    }

    /** Queues one potion per beneficial effect we are missing, starting with the fight buffs. */
    private void queuePrepot(LocalPlayer player) {
        prepotQueue.clear();

        Inventory inventory = player.getInventory();
        for (Holder<MobEffect> effect : PREPOT_ORDER) {
            if (player.hasEffect(effect) || !containsPotion(inventory, effect)) continue;
            prepotQueue.add(effect);
        }

        // Anything else that is beneficial, so new potions work without touching this list.
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() != Items.SPLASH_POTION) continue;

            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            for (var instance : contents.getAllEffects()) {
                Holder<MobEffect> effect = instance.getEffect();
                if (isBadEffect(effect) || player.hasEffect(effect) || prepotQueue.contains(effect)) continue;
                prepotQueue.add(effect);
            }
        }

        prepotTotal = prepotQueue.size();
        prepotDone = 0;
    }

    /** Called when the HvH profile rewrites settings mid fight, so an in-flight throw cannot double up. */
    public void onProfileChanged() {
        cancelThrow(mc.player);
        updatePingStop(false);
        requestedPotions.clear();
        retries = 0;
        waitTicks = 0;
        potTimer.reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null)
            return;

        // A death or respawn gives a new player entity; a queued prepot must not survive it.
        if (player != lastPrepotPlayer || player.isDeadOrDying()) {
            lastPrepotPlayer = player;
            clearPrepot();
        }

        // Keep the status line on screen for a moment after the last potion so it fades out cleanly.
        if (!prepotQueue.isEmpty())
            prepotLingerTicks = PREPOT_LINGER_TICKS;
        else if (prepotLingerTicks > 0)
            prepotLingerTicks--;

        if (isGroundDirectlyBelow(player))
            lastGroundTick = player.tickCount;

        trackCombat(player);
        sampleDamage(player);
        updatePingStop(shouldStopForThrow());

        // KillAura keeps the server looking at the target. While a throw is planned or in flight the
        // server rotation is held straight down, so the potion lands on us instead of on them.
        if (wasRotating)
            Client.rotationManager.syncWithServerRotation(player.getYRot(), 90f);

        if (awaitingConfirmation && confirmPotion(player))
            return;

        if (!canThrowHere(player)
                || mc.gui.screen() instanceof ContainerScreen
                || ModuleManager.getModuleState(Scaffold.class)
                || player.isUsingItem()) {
            cancelThrow(player);
            requestedPotions.clear();
            return;
        }

        buildNeeds(player);
        requestedPotions.clear();
        requestedPotions.addAll(needPots);
        updatePingStop(shouldStopForThrow());

        if (needPots.isEmpty()) {
            cancelThrow(player);
            return;
        }

        if (!stripHealingWhileFighting(player)) {
            cancelThrow(player);
            return;
        }

        if (healingBlockedByCompany(player)) {
            cancelThrow(player);
            return;
        }

        if (!potTimer.hasTimePassed(POT_COOLDOWN_TICKS)) {
            potTimer.update();
            return;
        }

        if (!wasRotating && ModuleManager.getModuleState(InvManager.class)
                && InvManager.getTicksSinceClick(player) < ACTION_GAP_TICKS)
            return;

        int hotbarIndex = findPotInHotbar(player.getInventory(), needPots);
        if (hotbarIndex == -1) {
            cancelThrow(player);
            return;
        }

        if (player.getInventory().getSelectedSlot() != hotbarIndex) {
            if (oldSlot == -1)
                oldSlot = player.getInventory().getSelectedSlot();
            PlayerUtil.INSTANCE.doSpoof(oldSlot);
            player.getInventory().setSelectedSlot(hotbarIndex);
            // Tell the server about the slot now, so a delayed connection cannot throw the item that
            // was selected a moment ago.
            PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(hotbarIndex));
            markAction(player);
            throwing = true;
            return;
        }

        ItemStack mainHandStack = player.getMainHandItem();
        if (!(mainHandStack.getItem() instanceof SplashPotionItem)
                || !isThrowablePotion(mainHandStack, needPots)) {
            cancelThrow(player);
            return;
        }

        throwPotion(player);
    }

    private void throwPotion(LocalPlayer player) {
        PlayerUtil.INSTANCE.doSpoof(oldSlot);
        BehaviorUtils.noKillAura = true;
        if (!wasRotating) {
            Client.rotationManager.stopRotation();
            ModuleManager.rotTick = 0;
        }

        float yaw = player.getYRot();
        // Instant, not a 180 deg/s ramp: the manager records 90 as the server rotation right away, so
        // neither our packets nor the tick's own motion packet can hand KillAura's pitch to the server.
        Client.rotationManager.syncWithServerRotation(yaw, 90f);
        wasRotating = true;
        throwing = true;

        // Slot, downward rotation and the use packet all go out together. The server resolves the
        // throw with whatever it has already received, so relying on the tick's motion packet or on
        // the vanilla slot sync throws the potion with stale data on a slow connection.
        PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Rot(
                yaw,
                90f,
                player.onGround(),
                player.horizontalCollision
        ));
        PacketUtil.sendPacket(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND,
                0,
                yaw,
                90f
        ));

        consumePrepot(player.getMainHandItem());

        markAction(player);
        potTimer.reset();
        healthAtThrow = player.getHealth();
        retries = 0;

        if (highPingConfirm.get()) {
            awaitingConfirmation = true;
            confirmUntilTick = player.tickCount + 2 + Latency.ticks() * 2;
        } else {
            awaitingConfirmation = false;
            throwing = false;
            restoreSlot(player);
        }
    }

    /**
     * @return true while the throw is still being tracked and the tick should not be used for
     * anything else.
     */
    private boolean confirmPotion(LocalPlayer player) {
        boolean landed = player.getHealth() > healthAtThrow + 0.5f || hasRequestedEffect(player);
        if (landed) {
            awaitingConfirmation = false;
            retries = 0;
            throwing = false;
            restoreSlot(player);
            return true;
        }

        if (player.tickCount > confirmUntilTick) {
            awaitingConfirmation = false;
            restoreSlot(player);
            if (retries < CONFIRM_RETRIES) {
                retries++;
                potTimer.reset();
                return false;
            }

            retries = 0;
            throwing = false;
            return true;
        }

        return true;
    }

    /**
     * Drops the buff that was just thrown from the queue. Consuming at send time (instead of waiting
     * for the landing confirmation) is what keeps every buff to exactly one potion per press: the
     * confirmation can be retried, interrupted or never observed, and none of that repeats a buff.
     */
    private void consumePrepot(ItemStack stack) {
        if (prepotQueue.isEmpty() || stack.getItem() != Items.SPLASH_POTION)
            return;

        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var instance : contents.getAllEffects()) {
            if (prepotQueue.remove(instance.getEffect())) {
                prepotDone++;
                return;
            }
        }
    }

    private void clearPrepot() {
        prepotQueue.clear();
        prepotTotal = 0;
        prepotDone = 0;
        prepotLingerTicks = 0;
    }

    private static boolean hasRequestedEffect(LocalPlayer player) {
        for (Holder<MobEffect> effect : requestedPotions) {
            if (player.hasEffect(effect))
                return true;
        }
        return false;
    }

        private void buildNeeds(LocalPlayer player) {

        needPots.clear();

        if (!prepotQueue.isEmpty()) {
            // Manual prepot: one of each buff, regardless of health, fight state or the heal toggles.
            prepotQueue.removeIf(effect -> player.hasEffect(effect)
                    || !containsPotion(player.getInventory(), effect));
            needPots.addAll(prepotQueue);
            return;
        }


        if (heal.get()) {
            Inventory inventory = player.getInventory();
            Holder<MobEffect> healEffect = chooseHealEffect(inventory);
            // Regeneration only pays off if it is started early, so it has its own, higher threshold.
            float threshold = healEffect.equals(MobEffects.REGENERATION)
                    ? effectiveRegenHealth()
                    : effectiveHealHealth();

            if (player.getHealth() <= threshold) {
                if (canHealNow(player) && worthHealing(player))
                    needPots.add(healEffect);
            } else if (player.getHealth() < player.getMaxHealth()
                    && !player.hasEffect(MobEffects.REGENERATION)
                    && containsPotion(inventory, MobEffects.REGENERATION)
                    && canHealNow(player) && worthHealing(player)) {
                needPots.add(MobEffects.REGENERATION);
            }
        }

        if (buffs.get()) {
            Collections.shuffle(buffOrder);
            for (Holder<MobEffect> effect : buffOrder) {
                if (player.hasEffect(effect)) continue;
                if (!containsPotion(player.getInventory(), effect)) continue;
                needPots.add(effect);
            }
        }
    }

    /** Removes healing potions from the plan while a fight is going on. Buffs are still allowed. */
    private boolean stripHealingWhileFighting(LocalPlayer player) {
        if (!prepotQueue.isEmpty())
            return !needPots.isEmpty();

        if (!notMidFight.get())
            return !needPots.isEmpty();

        boolean needsHealing = false;
        for (Holder<MobEffect> effect : needPots) {
            if (HEALING_EFFECTS.contains(effect)) {
                needsHealing = true;
                break;
            }
        }

        if (!needsHealing || player.getHealth() <= panicHealth.get() || !isInCombat(player))
            return !needPots.isEmpty();

        // Regeneration is kept mid fight up to its own threshold; it is a heal over time that has to
        // be running before the damage arrives, unlike an instant potion that is thrown on panic.
        boolean regenInTime = player.getHealth() <= effectiveRegenHealth();
        needPots.removeIf(effect -> HEALING_EFFECTS.contains(effect)
                && !(regenInTime && effect.equals(MobEffects.REGENERATION)));
        return !needPots.isEmpty();
    }

    private boolean healingBlockedByCompany(LocalPlayer player) {
        if (!prepotQueue.isEmpty())
            return false;

        if (!avoidOthers.get())
            return false;

        boolean needsHealing = false;
        for (Holder<MobEffect> effect : needPots) {
            if (HEALING_EFFECTS.contains(effect)) {
                needsHealing = true;
                break;
            }
        }
        if (!needsHealing)
            return false;

        if (!splashesAnotherEntity(player)) {
            waitTicks = 0;
            return false;
        }

        waitTicks++;
        if (healAnyway.get() && waitTicks > maxWait.get()) {
            waitTicks = 0;
            return false;
        }

        return true;
    }

    /** Splash potions reach four blocks from the impact, so anyone that close would be healed too. */
    private boolean splashesAnotherEntity(LocalPlayer player) {
        var level = mc.level;
        if (level == null)
            return false;

        Vec3 impact = player.position();
        double radius = splashRadius.get();
        double radiusSquared = radius * radius;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || !(entity instanceof LivingEntity living) || living.isDeadOrDying())
                continue;
            if (ignoreMobs.get() && !(entity instanceof Player))
                continue;
            // Whoever KillAura is hitting is never treated as a teammate, otherwise a wrong team
            // entry would let the heal splash them.
            if (entity != KillAura.closestEntity
                    && ignoreTeammates.get() && entity instanceof Player && BehaviorUtils.isTeammate(living))
                continue;
            if (living.getBoundingBox().distanceToSqr(impact) <= radiusSquared)
                return true;
        }

        return false;
    }

    private void trackCombat(LocalPlayer player) {
        if (player.hurtTime > lastHurtTime && player.hurtTime > 0)
            lastSelfHurtTick = player.tickCount;
        lastHurtTime = player.hurtTime;
    }

    private boolean isInCombat(LocalPlayer player) {
        int recall = combatRecall.get();
        if (recall > 0 && player.tickCount - lastSelfHurtTick <= recall)
            return true;

        float range = combatRange.get();
        double rangeSquared = range * range;

        Entity killAuraTarget = KillAura.closestEntity;
        if (killAuraTarget != null && killAuraTarget.isAlive()
                && player.distanceToSqr(killAuraTarget) <= rangeSquared)
            return true;

        SilentAura silentAura = ModuleManager.getModule(SilentAura.class);
        if (silentAura != null && ModuleManager.getModuleState(SilentAura.class)) {
            Entity silentTarget = silentAura.getCurrentTarget();
            if (silentTarget != null && silentTarget.isAlive()
                    && player.distanceToSqr(silentTarget) <= rangeSquared)
                return true;
        }

        var level = mc.level;
        if (level == null)
            return false;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || !(entity instanceof Player other) || other.isDeadOrDying() || other.isSpectator())
                continue;
            if (player.distanceToSqr(other) <= rangeSquared)
                return true;
        }

        return false;
    }

    private float effectiveHealHealth() {
        return Math.min(20f, healAtHealth.get() + Latency.ticks() * 0.5f);
    }

    private float effectiveRegenHealth() {
        return Math.min(20f, regenHealth.get() + Latency.ticks() * 0.5f);
    }

    private boolean canHealNow(LocalPlayer player) {
        return !groundOnly.get() || isGroundDirectlyBelow(player);
    }

    private Holder<MobEffect> chooseHealEffect(Inventory inventory) {
        return containsPotion(inventory, MobEffects.INSTANT_HEALTH)
                ? MobEffects.INSTANT_HEALTH
                : MobEffects.REGENERATION;
    }

    /**
     * Decides whether a heal is actually worth the potion.
     *
     * <p>Both sides of the fight are turned into "seconds until dead" from the damage each side has
     * dealt recently. Healing wins when the current race is lost or about to be, and the heal still
     * arrives in time to leave us outliving the target. When we are clearly winning the race the
     * potion is kept and the attack continues.</p>
     */
    private boolean worthHealing(LocalPlayer player) {
        if (!smartHeal.get())
            return true;

        if (player.getHealth() <= panicHealth.get())
            return true;

        Entity target = currentTarget();
        if (!(target instanceof LivingEntity living) || !living.isAlive())
            return true;

        double incoming = recentDps(incomingSamples);
        if (incoming <= MIN_DPS)
            return true;

        double outgoing = recentDps(outgoingSamples);
        if (outgoing <= MIN_DPS)
            return true;

        ItemStack potion = findHealPotion(player.getInventory());
        double heal = potion.isEmpty() ? 0.0D : predictedHeal(potion);

        double myTtk = player.getHealth() / incoming;
        double theirTtk = living.getHealth() / outgoing;
        double delay = healDelaySeconds();
        double healthAfter = player.getHealth() - incoming * delay + heal;
        double myTtkAfter = healthAfter / incoming;

        boolean urgent = myTtk <= delay + 0.75D;
        boolean losing = myTtk <= theirTtk;
        return (urgent || losing) && myTtkAfter >= theirTtk;
    }

    /** Seconds until the potion can be expected to have healed us. */
    private double healDelaySeconds() {
        return (Latency.ticks() + POTION_FLIGHT_TICKS + 1) / 20.0D;
    }

    /** Health a potion is expected to restore shortly after it lands. */
    private static double predictedHeal(ItemStack potion) {
        double heal = 0.0D;
        PotionContents contents = potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var instance : contents.getAllEffects()) {
            if (instance.getEffect().equals(MobEffects.INSTANT_HEALTH)) {
                heal += Math.max(4 << Math.min(3, instance.getAmplifier()), 0);
            } else if (instance.getEffect().equals(MobEffects.REGENERATION)) {
                int interval = Math.max(1, 50 >> Math.min(3, instance.getAmplifier()));
                int ticks = Math.min(HEAL_HORIZON_TICKS, instance.getDuration());
                heal += (double) ticks / interval;
            }
        }
        return heal;
    }

    private static ItemStack findHealPotion(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isThrowablePotionFor(stack, MobEffects.INSTANT_HEALTH))
                return stack;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isThrowablePotionFor(stack, MobEffects.REGENERATION))
                return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Damage taken and dealt over the sampling window, per second. */
    private double recentDps(float[] samples) {
        int window = Math.min(dpsWindow.get(), sampleCount);
        if (window <= 0)
            return 0.0D;

        double total = 0.0D;
        for (int i = 1; i <= window; i++)
            total += samples[(sampleIndex - i + MAX_SAMPLES) % MAX_SAMPLES];

        return total / (window / 20.0D);
    }

    private void sampleDamage(LocalPlayer player) {
        float health = player.getHealth();
        float incoming = 0.0F;
        if (!Float.isNaN(lastSelfHealth) && health < lastSelfHealth)
            incoming = lastSelfHealth - health;
        lastSelfHealth = health;

        float outgoing = 0.0F;
        Entity target = currentTarget();
        if (target instanceof LivingEntity living) {
            float targetHealth = living.getHealth();
            if (lastTargetId == target.getId() && !Float.isNaN(lastTargetHealth) && targetHealth < lastTargetHealth)
                outgoing = lastTargetHealth - targetHealth;
            lastTargetId = target.getId();
            lastTargetHealth = targetHealth;
        } else {
            lastTargetId = -1;
            lastTargetHealth = Float.NaN;
        }

        incomingSamples[sampleIndex] = incoming;
        outgoingSamples[sampleIndex] = outgoing;
        sampleIndex = (sampleIndex + 1) % MAX_SAMPLES;
        if (sampleCount < MAX_SAMPLES)
            sampleCount++;
    }

    private Entity currentTarget() {
        Entity target = KillAura.closestEntity;
        if (target != null && target.isAlive())
            return target;

        SilentAura silentAura = ModuleManager.getModule(SilentAura.class);
        if (silentAura != null && ModuleManager.getModuleState(SilentAura.class)) {
            Entity silentTarget = silentAura.getCurrentTarget();
            if (silentTarget != null && silentTarget.isAlive())
                return silentTarget;
        }

        return null;
    }

    private boolean shouldStopForThrow() {
        if (!pingStop.get() || Latency.millis() < pingStopThreshold.get())
            return false;

        if (awaitingConfirmation)
            return true;

        for (Holder<MobEffect> effect : requestedPotions) {
            if (HEALING_EFFECTS.contains(effect))
                return true;
        }

        return false;
    }

    /** Freezes movement and speed on a slow connection so the throw cannot be outrun. */
    private void updatePingStop(boolean stop) {
        if (stop == stoppingForThrow)
            return;

        stoppingForThrow = stop;

        if (stop) {
            SpeedModule speed = ModuleManager.getModule(SpeedModule.class);
            if (speed != null && speed.tempEnabled) {
                speed.setState(false);
                speedSuppressed = true;
            }

            if (mc.player != null)
                mc.player.setSprinting(false);
        } else if (speedSuppressed) {
            SpeedModule speed = ModuleManager.getModule(SpeedModule.class);
            if (speed != null && !speed.tempEnabled)
                speed.setState(true);
            speedSuppressed = false;
        }
    }

    public static void hookInput(ClientInput input) {
        AutoPot autoPot = ModuleManager.getModule(AutoPot.class);
        if (autoPot == null || !autoPot.tempEnabled || !autoPot.stoppingForThrow)
            return;

        MovementFix.overrideInput(input, Input.EMPTY);
    }

    private static boolean containsPotion(Inventory inventory, Holder<MobEffect> effect) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isThrowablePotionFor(inventory.getItem(i), effect))
                return true;
        }
        return false;
    }

    private boolean canThrowHere(LocalPlayer player) {
        if (groundOnly.get())
            return isGroundDirectlyBelow(player);

        if (isGroundDirectlyBelow(player))
            return true;
        if (player.isInWater() || player.isInLava() || player.getAbilities().flying || player.isFallFlying())
            return false;
        int sinceGround = player.tickCount - lastGroundTick;
        if (sinceGround < 0 || sinceGround > HOP_GRACE_TICKS || player.fallDistance > 2.0)
            return false;
        return hasGroundWithin(player, HOP_MAX_GROUND_DISTANCE);
    }

    private static boolean hasGroundWithin(LocalPlayer player, double distance) {
        var level = mc.level;
        if (level == null)
            return false;
        AABB box = player.getBoundingBox();
        AABB probe = new AABB(box.minX, box.minY - distance, box.minZ, box.maxX, box.minY, box.maxZ);
        return level.getBlockCollisions(player, probe).iterator().hasNext();
    }

    private static boolean isGroundDirectlyBelow(LocalPlayer player) {
        var level = mc.level;
        if (level == null || !player.onGround() || player.isInWater() || player.isInLava())
            return false;

        double feetY = player.getY();
        BlockPos pos = BlockPos.containing(player.getX(), feetY - 0.01, player.getZ());
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
            return false;

        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty())
            return false;

        double localX = player.getX() - pos.getX();
        double localZ = player.getZ() - pos.getZ();
        for (AABB box : shape.toAabbs()) {
            if (localX < box.minX || localX > box.maxX || localZ < box.minZ || localZ > box.maxZ)
                continue;
            if (Math.abs(pos.getY() + box.maxY - feetY) <= 0.05)
                return true;
        }
        return false;
    }

    private static void markAction(LocalPlayer player) {
        lastActionTick = player.tickCount;
    }

    public static int getTicksSinceAction(LocalPlayer player) {
        int ticks = player.tickCount - lastActionTick;
        return ticks < 0 ? Integer.MAX_VALUE : ticks;
    }

    private int findPotInHotbar(Inventory inventory, List<Holder<MobEffect>> needs) {
        for (Holder<MobEffect> effect : needs) {
            for (int i = 0; i <= 8; i++) {
                if (isThrowablePotionFor(inventory.getItem(i), effect))
                    return i;
            }
        }
        return -1;
    }

    private static boolean isThrowablePotionFor(ItemStack stack, Holder<MobEffect> wanted) {
        if (stack.getItem() != Items.SPLASH_POTION) return false;

        boolean hasWanted = false;
        PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effectInstance : potionContents.getAllEffects()) {
            if (isBadEffect(effectInstance.getEffect())) return false;
            if (effectInstance.getEffect().equals(wanted)) hasWanted = true;
        }
        return hasWanted;
    }

    private static boolean isThrowablePotion(ItemStack stack, List<Holder<MobEffect>> wanted) {
        if (stack.getItem() != Items.SPLASH_POTION) return false;

        boolean hasWanted = false;
        PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effectInstance : potionContents.getAllEffects()) {
            if (isBadEffect(effectInstance.getEffect())) return false;
            if (wanted.contains(effectInstance.getEffect())) hasWanted = true;
        }
        return hasWanted;
    }

    private static boolean isBadEffect(Holder<MobEffect> effect) {
        return effect.value().getCategory() == MobEffectCategory.HARMFUL || effect.is(MobEffects.JUMP_BOOST);
    }

    private void restoreSlot(LocalPlayer player) {
        if (wasRotating) {
            Client.rotationManager.stopRotation();
            BehaviorUtils.noKillAura = false;
            wasRotating = false;
        }

        PlayerUtil.INSTANCE.stopSpoof();

        if (oldSlot != -1) {
            if (player != null) {
                player.getInventory().setSelectedSlot(oldSlot);
                markAction(player);
            }
            oldSlot = -1;
        }
    }

    private void cancelThrow(LocalPlayer player) {
        restoreSlot(player);
        throwing = false;
        awaitingConfirmation = false;
        retries = 0;
    }

    public static List<Holder<MobEffect>> getRequestedPotions() {
        return Collections.unmodifiableList(requestedPotions);
    }

    public static int takeOriginalSlot() {
        int slot = oldSlot;
        oldSlot = -1;
        return slot;
    }

    public static boolean isThrowing() {
        return throwing;
    }

    public static boolean shouldBlockUse() {
        var player = mc.player;
        return player != null
                && throwing
                && ModuleManager.getModuleState(AutoPot.class)
                && player.getMainHandItem().getItem() instanceof SplashPotionItem;
    }

    public static boolean isRequestedPotion(ItemStack stack) {
        return isThrowablePotion(stack, requestedPotions);
    }
}
