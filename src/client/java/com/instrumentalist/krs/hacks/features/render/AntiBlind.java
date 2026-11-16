package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class AntiBlind extends Module {

    @Setting
    public static final BooleanValue fire = new BooleanValue("Fire", true)
            .describe("Hides the fire overlay while you are burning.");

    @Setting
    public static final BooleanValue pumpkin = new BooleanValue("Pumpkin", true)
            .describe("Hides the carved pumpkin overlay on your screen.");

    @Setting
    public static final BooleanValue camera = new BooleanValue("Camera", true)
            .describe("Removes the camera wobble from nausea and portals.");

    @Setting
    public static final BooleanValue effects = new BooleanValue("Effects", true)
            .describe("Hides the screen effect overlay from status effects.");

    @Override
    public String description() {
        return "Removes the fire, pumpkin and camera overlays that block your view";
    }

    public AntiBlind() {
        super("Anti Blind", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

}
