package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class Capes extends Module {

    @Setting
    public static final BooleanValue customCape = new BooleanValue("Custom Cape", false)
            .describe("Shows your selected custom cape.");

    @Setting
    public static final BooleanValue capeOverride = new BooleanValue("Cape Override", true, customCape::get)
            .describe("Replaces other capes with your custom cape.");

    @Setting
    public static final BooleanValue enchantmentGlint = new BooleanValue("Enchantment Glint", true)
            .describe("Shows the enchantment glint on capes.");

    @Setting
    public static final BooleanValue oldCapeMovement = new BooleanValue("1.8 Cape Movement", true)
            .describe("Uses the 1.8 cape swinging animation.");

    @Override
    public String description() {
        return "Renders custom capes on yourself and other players";
    }

    public Capes() {
        super("Capes", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

}
