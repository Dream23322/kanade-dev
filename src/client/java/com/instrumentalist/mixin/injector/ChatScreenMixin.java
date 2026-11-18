package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.anticheat.AntiCheatFlags;
import com.instrumentalist.krs.hacks.features.player.ChatCommands;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.breadloaf.imguimc.customwindow.ModuleRenderable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin implements IMinecraft {

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void chatCommandHook(String message, boolean addToHistory, CallbackInfo ci) {
        String prefix = ChatCommands.prefix.get();
        boolean prefixed = !prefix.isEmpty() && message.startsWith(prefix);
        String args = prefixed ? message.substring(prefix.length()).trim() : "";

        // The flag preview is a diagnostic and stays available with Chat Commands disabled.
        if (prefixed && args.equalsIgnoreCase("flag")) {
            ci.cancel();
            AntiCheatFlags.printSample();
            mc.gui.hud.getChat().addRecentChat(message);
            return;
        }

        if (ModuleManager.getModuleState(ChatCommands.class) && prefixed) {
            ci.cancel();
            ModuleRenderable.executeCommand(args, true);
            mc.gui.hud.getChat().addRecentChat(message);
        }
    }
}
