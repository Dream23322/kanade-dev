package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class ViewModel extends Module {

    @Setting
    public static final FloatValue itemSize = new FloatValue("Item Size", 0f, -2f, 2f)
            .describe("Scales the held item.");

    @Setting
    public static final FloatValue itemFov = new FloatValue("Item Fov", 0f, -2f, 2f)
            .describe("Field of view used for the held item.");

    @Setting
    public static final FloatValue itemPositionX = new FloatValue("Item Position X", 0f, -2f, 2f)
            .describe("Horizontal offset of the held item.");

    @Setting
    public static final FloatValue itemPositionY = new FloatValue("Item Position Y", 0f, -2f, 2f)
            .describe("Vertical offset of the held item.");

    @Setting
    public static final FloatValue itemPositionZ = new FloatValue("Item Position Z", 0f, -2f, 2f)
            .describe("Forward offset of the held item.");

    @Setting
    public static final BooleanValue noEquipAnimation = new BooleanValue("No Equip Animation", false)
            .describe("Skips the equip animation when switching items.");

    public static boolean shouldSkipEquipAnimation() {
        return ModuleManager.getModuleState(ViewModel.class) && noEquipAnimation.get() || OldHitting.shouldSkipEquipAnimation();
    }

    @Setting
    public static final BooleanValue fluxSwing = new BooleanValue("Flux Swing", false)
            .describe("Uses the Flux swing animation.");

    @Setting
    public static final BooleanValue oldEatSwing = new BooleanValue("Old Eat Swing", false)
            .describe("Uses the old eating swing animation.");

    @Setting
    public static final BooleanValue oldItemPosition = new BooleanValue("Old Item Position", false)
            .describe("Uses the old held item position.");

    @Setting
    public static final BooleanValue punchingABlockWhileDoingStuff = new BooleanValue("Punching a block while doing stuff", false)
            .describe("Animates the arm while hitting blocks and eating at once.");

    @Setting
    private static final BooleanValue slowSwing = new BooleanValue("Slow Swing", false)
            .describe("Slows down the swing animation.");

    @Setting
    private static final IntValue setSwingSpeed = new IntValue("Set Swing Speed", 5, -6, 20, slowSwing::get)
            .describe("Swing speed used while slow swing is enabled.");

    @Override
    public String description() {
        return "Changes the size, position and animations of your held item";
    }

    public ViewModel() {
        super("View Model", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    @Override
    public void onDisable() {
        if (Client.loaded)
            this.toggle();
    }

    @Override
    public void onEnable() {
    }

    public static int hookSwingSpeed(int original, Entity entity) {
        if (ModuleManager.getModuleState(ViewModel.class) && slowSwing.get() && entity instanceof LocalPlayer)
            return 6 + setSwingSpeed.get();

        return original;
    }

}
