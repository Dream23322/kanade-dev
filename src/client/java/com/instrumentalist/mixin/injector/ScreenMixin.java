package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.render.HudEditor;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int blockRenderMouseXWhenImguiCaptures(int mouseX) {
        return GuiInputBlocker.sanitizeMouseCoordinate(mouseX);
    }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int blockRenderMouseYWhenImguiCaptures(int mouseY) {
        return GuiInputBlocker.sanitizeMouseCoordinate(mouseY);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void queueHudEditorInChat(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof ChatScreen))
            return;

        CursorType cursor = switch (HudEditor.pointerAt(mouseX, mouseY)) {
            case RESIZE -> CursorTypes.RESIZE_ALL;
            case MOVE -> CursorTypes.POINTING_HAND;
            case NONE -> null;
        };
        if (cursor != null)
            context.requestCursor(cursor);

        HudEditor.queueOverlay();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void abortHudEditorDragInChat(CallbackInfo ci) {
        if ((Object) this instanceof ChatScreen)
            HudEditor.abortDrag();
    }

    @ModifyReturnValue(method = "isMouseOver(DD)Z", at = @At("RETURN"))
    private boolean blockScreenHoverWhenImguiCaptures(boolean original) {
        return original && !GuiInputBlocker.shouldBlockMinecraftMouse();
    }
}
