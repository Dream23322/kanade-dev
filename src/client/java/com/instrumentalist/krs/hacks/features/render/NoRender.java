package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.HugeExplosionSeedParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TotemParticle;

public class NoRender extends Module {

    @Setting
    public static final BooleanValue allParticles = new BooleanValue("All Particles", false)
            .describe("Hides every particle; overrides the particle options below.");

    @Setting
    public static final BooleanValue explosions = new BooleanValue("Explosions", true, () -> !allParticles.get())
            .describe("Hides explosion particles.");

    @Setting
    public static final BooleanValue blockParticles = new BooleanValue("Block Particles", false, () -> !allParticles.get())
            .describe("Hides the particles from breaking blocks.");

    @Setting
    public static final BooleanValue totemParticles = new BooleanValue("Totem Particles", true, () -> !allParticles.get())
            .describe("Hides the particles from popping a totem.");

    @Setting
    public static final BooleanValue fireworks = new BooleanValue("Fireworks", false, () -> !allParticles.get())
            .describe("Hides firework particles.");

    @Setting
    public static final BooleanValue campfireSmoke = new BooleanValue("Campfire Smoke", true, () -> !allParticles.get())
            .describe("Hides the smoke rising from campfires.");

    @Setting
    public static final BooleanValue totemAnimation = new BooleanValue("Totem Animation", true)
            .describe("Hides the totem pop animation and sound.");

    @Setting
    public static final BooleanValue fog = new BooleanValue("Fog", false)
            .describe("Removes distance and environmental fog.");

    @Setting
    public static final BooleanValue vignette = new BooleanValue("Vignette", true)
            .describe("Hides the dark vignette around the screen edges.");

    @Setting
    public static final BooleanValue portalOverlay = new BooleanValue("Portal Overlay", true)
            .describe("Hides the purple overlay shown while standing inside a nether portal.");

    @Setting
    public static final BooleanValue noHurtCam = new BooleanValue("No Hurt Cam", false)
            .describe("Stops the camera from tilting and shaking when you take damage.");

    public NoRender() {
        super("No Render", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public String description() {
        return "Hides particles, overlays and camera effects that get in the way";
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    public static boolean active(BooleanValue option) {
        return ModuleManager.getModuleState(NoRender.class) && option.get();
    }

    public static boolean shouldHideParticle(Particle particle) {
        if (!ModuleManager.getModuleState(NoRender.class))
            return false;
        if (allParticles.get())
            return true;

        if (explosions.get() && (particle instanceof ExplodeParticle || particle instanceof HugeExplosionParticle || particle instanceof HugeExplosionSeedParticle))
            return true;
        if (blockParticles.get() && particle instanceof TerrainParticle)
            return true;
        if (totemParticles.get() && particle instanceof TotemParticle)
            return true;
        if (fireworks.get() && particle.getClass().getEnclosingClass() == FireworkParticles.class)
            return true;
        return campfireSmoke.get() && particle instanceof CampfireSmokeParticle;
    }
}
