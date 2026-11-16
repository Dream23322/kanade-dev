package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class HUDHider extends Module {

    @Override
    public String description() {
        return "Hides vanilla HUD elements you don't want to see";
    }

    public HUDHider() {
        super("Hud Hider", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    public static final BooleanValue board = new BooleanValue("Scoreboard", true)
            .describe("Hides the scoreboard sidebar.");

    @Setting
    public static final BooleanValue bos = new BooleanValue("Boss Bar", true)
            .describe("Hides boss health bars.");

    @Setting
    public static final BooleanValue bar = new BooleanValue("Action Bar", true)
            .describe("Hides the action bar text above the hotbar.");

    @Setting
    public static final BooleanValue titled = new BooleanValue("Title", true)
            .describe("Hides title and subtitle text.");

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
