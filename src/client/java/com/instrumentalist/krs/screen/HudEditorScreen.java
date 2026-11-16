package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.HudEditor;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends Screen {
    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // The live HUD stays visible underneath the editor.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.requestCursor(resolveCursor(mouseX, mouseY));

        // The editor owns its own frame: chrome, hit feedback and drag tracking all run here.
        HudEditor.queueOverlay();
    }

    private CursorType resolveCursor(double mouseX, double mouseY) {
        return switch (HudEditor.pointerAt(mouseX, mouseY)) {
            case RESIZE -> CursorTypes.RESIZE_ALL;
            case MOVE -> CursorTypes.POINTING_HAND;
            case NONE -> CursorType.DEFAULT;
        };
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == Keys.KEY_R) {
            HudEditor.reset();
            return true;
        }

        if (event.key() == Keys.KEY_ESCAPE) {
            if (HudEditor.dismissPanel()) return true;
            onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        HudEditor.abortDrag();
        if (minecraft != null)
            minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
