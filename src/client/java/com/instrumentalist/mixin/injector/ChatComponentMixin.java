package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.Chat;
import com.instrumentalist.krs.utils.chat.ChatLog;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the client chat log, suppresses vanilla chat drawing and blocks chat wipes. */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void krs$addPlayerMessage(Component message, MessageSignature signature, GuiMessageTag tag,
                                      CallbackInfo ci) {
        ChatLog.record(ChatLog.Kind.PLAYER, message, signature);
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void krs$addServerSystemMessage(Component message, CallbackInfo ci) {
        ChatLog.record(ChatLog.Kind.SERVER, message, null);
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void krs$addClientSystemMessage(Component message, CallbackInfo ci) {
        ChatLog.record(ChatLog.Kind.CLIENT, message, null);
    }

    @Inject(method = "deleteMessage", at = @At("HEAD"))
    private void krs$deleteMessage(MessageSignature signature, CallbackInfo ci) {
        ChatLog.remove(signature);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void krs$clearMessages(boolean history, CallbackInfo ci) {
        if (Chat.featuresEnabled() && Chat.antiClear.get())
            ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void krs$extractRenderState(CallbackInfo ci) {
        if (Chat.customEnabled())
            ci.cancel();
    }

    @Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
    private void krs$captureClickableText(CallbackInfo ci) {
        if (Chat.customEnabled())
            ci.cancel();
    }
}
