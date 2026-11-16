package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.player.ChestStealer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void chestStealerSilentScreenHook(Screen screen, CallbackInfo ci) {
        if (ChestStealer.shouldHideChestScreen(screen))
            ci.cancel();
    }
}
