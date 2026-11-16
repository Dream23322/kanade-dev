package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.Ambience;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public abstract class WorldMixin implements LevelAccessor, AutoCloseable {

    @ModifyReturnValue(at = @At("RETURN"), method = "getRainLevel(F)F")
    private float ambienceRainLevelHook(float original) {
        return Ambience.rainLevelOverride(original);
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "getThunderLevel(F)F")
    private float ambienceThunderLevelHook(float original) {
        return Ambience.thunderLevelOverride(original);
    }

    @ModifyReturnValue(at = @At("RETURN"), method = {"isRaining", "isRainingAt"})
    private boolean ambienceRainingHook(boolean original) {
        return Ambience.rainingOverride(original);
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "isThundering")
    private boolean ambienceThunderingHook(boolean original) {
        return Ambience.thunderingOverride(original);
    }

    @ModifyReturnValue(at = @At("RETURN"), method = {"getDefaultClockTime", "getOverworldClockTime"})
    private long ambienceClockHook(long original) {
        Long timeTicks = Ambience.getActiveTimeTicks();
        return timeTicks != null ? timeTicks : original;
    }
}
