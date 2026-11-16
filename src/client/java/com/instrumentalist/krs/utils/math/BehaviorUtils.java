package com.instrumentalist.krs.utils.math;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.AntiBot;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.hacks.features.combat.Teams;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import java.util.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class BehaviorUtils implements IMinecraft {

    private static final long TARGET_INFO_GRACE_NANOS = 1_000_000_000L;
    private static final Map<UUID, Entity> targetInfoEntities = new HashMap<>();
    private static final Map<UUID, Long> targetInfoSeenNanos = new HashMap<>();

    public static boolean noKillAura = false;

    public static boolean isBot(LivingEntity entity) {
        return isBot(entity, ModuleManager.getModuleState(AntiBot.class));
    }

    public static boolean isBot(LivingEntity entity, boolean antiBotEnabled) {
        if (!antiBotEnabled || !(entity instanceof Player) || entity instanceof LocalPlayer) return false;
        return AntiBot.inBotList(entity);
    }

    public static boolean isTeammate(LivingEntity entity) {
        return isTeammate(entity, ModuleManager.getModuleState(Teams.class));
    }

    public static boolean isTeammate(LivingEntity entity, boolean teamsEnabled) {
        if (!teamsEnabled || !(entity instanceof Player) || entity instanceof LocalPlayer) return false;
        return Teams.isInClientPlayersTeam(entity);
    }

    public static List<Entity> getTargetList() {
        ArrayList<Entity> result = new ArrayList<>(4);
        fillTargetList(result);
        return result.isEmpty() ? Collections.emptyList() : result;
    }

    public static void fillTargetList(List<Entity> result) {
        if (result == null) return;

        result.clear();

        boolean killAura = ModuleManager.getModuleState(KillAura.class);
        if (killAura)
            updateTargetInfoTracking(System.nanoTime());
        else
            clearTargetInfoTracking();

        List<Entity> multi = killAura ? KillAura.multiTargets : null;
        Entity closest = killAura ? KillAura.closestEntity : null;
        Entity targeted = (mc.crosshairPickEntity instanceof LivingEntity
                && !(mc.crosshairPickEntity instanceof ArmorStand))
                ? mc.crosshairPickEntity : null;

        if (multi != null && !multi.isEmpty()) {
            for (int i = 0, n = multi.size(); i < n; i++) {
                addTarget(result, multi.get(i));
            }
        }
        addTarget(result, closest);
        addTarget(result, targeted);

        if (killAura) {
            for (Entity entity : targetInfoEntities.values())
                addTarget(result, entity);
        }
    }

    private static void addTarget(List<Entity> result, Entity entity) {
        if (entity == null || entity.isRemoved() || !entity.isAlive() || result.contains(entity))
            return;
        result.add(entity);
    }

    private static void updateTargetInfoTracking(long now) {
        Iterator<Map.Entry<UUID, Entity>> iterator = targetInfoEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entity> entry = iterator.next();
            Entity entity = entry.getValue();
            Long seen = targetInfoSeenNanos.get(entry.getKey());
            if (entity == null || entity.isRemoved() || !entity.isAlive()
                    || seen == null || now - seen >= TARGET_INFO_GRACE_NANOS) {
                targetInfoSeenNanos.remove(entry.getKey());
                iterator.remove();
            }
        }

        for (Entity entity : KillAura.targetInfoTargets) {
            if (entity == null || entity.isRemoved() || !entity.isAlive())
                continue;
            UUID id = entity.getUUID();
            targetInfoEntities.put(id, entity);
            targetInfoSeenNanos.put(id, now);
        }
    }

    public static void clearTargetInfoTracking() {
        targetInfoEntities.clear();
        targetInfoSeenNanos.clear();
    }

    private static boolean isTargetInfoRetained(Entity entity) {
        if (entity == null || targetInfoEntities.get(entity.getUUID()) != entity)
            return false;

        Long seen = targetInfoSeenNanos.get(entity.getUUID());
        return seen != null && System.nanoTime() - seen < TARGET_INFO_GRACE_NANOS;
    }

    public static boolean isTarget(Entity entity) {
        if (entity == null) return false;

        boolean killAura = ModuleManager.getModuleState(KillAura.class);
        if (killAura) {
            if (entity == KillAura.closestEntity) return true;

            List<Entity> multi = KillAura.multiTargets;
            for (int i = 0, n = multi.size(); i < n; i++) {
                if (entity == multi.get(i)) return true;
            }

            List<Entity> inRange = KillAura.targetInfoTargets;
            for (int i = 0, n = inRange.size(); i < n; i++) {
                if (entity == inRange.get(i)) return true;
            }

            if (isTargetInfoRetained(entity)) return true;
        }

        return mc.crosshairPickEntity == entity
                && entity instanceof LivingEntity
                && !(entity instanceof ArmorStand);
    }

    public static Entity findEnemy(double range) {
        if (mc.player == null || mc.level == null)
            return null;

        boolean antiBot = ModuleManager.getModuleState(AntiBot.class);
        boolean teams = ModuleManager.getModuleState(Teams.class);
        Vec3 eye = mc.player.getEyePosition();
        double rangeSquared = range * range;

        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)
                    || living.isDeadOrDying()
                    || entity instanceof ArmorStand
                    || entity instanceof LocalPlayer
                    || !entity.isAttackable()) continue;

            double distance = EntityExtension.squaredBoxedDistanceTo(entity, eye);
            if (distance > rangeSquared || distance >= closestDistance) continue;

            if (isBot(living, antiBot) || isTeammate(living, teams)) continue;

            closest = entity;
            closestDistance = distance;
        }

        return closest;
    }
}
