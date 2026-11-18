package com.instrumentalist.krs.utils;

import com.instrumentalist.krs.hacks.features.render.Chat;
import com.instrumentalist.krs.utils.chat.ClientTag;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.awt.Color;

public class ChatUtil implements IMinecraft {

    public static void showLog(String string) {
        LogUtils.getLogger().info(string);
    }

    public static void printChat(String string) {
        printModifiedChat(Component.literal("> " + string));
    }

    /**
     * Flag line: {@code kanade | message}. The custom chat renderer adds the tag itself, so the
     * text stays plain there; with custom chat off the tag is baked into the component with the
     * active theme colours so flags never fall back to a bare {@code > message} line.
     */
    public static void printFlag(String message) {
        if (message == null)
            return;

        if (Chat.customEnabled()) {
            printChat(message);
            return;
        }

        MutableComponent line = Component.empty()
                .append(Component.literal(ClientTag.label()).withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(rgb(NanoVGTheme.ACCENT)))))
                .append(Component.literal(" | ").withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(0xFFFFFF))))
                .append(Component.literal(message).withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(rgb(NanoVGTheme.TEXT)))));
        printModifiedChat(line);
    }

    private static int rgb(Color colour) {
        return colour.getRGB() & 0xFFFFFF;
    }



    public static void printModifiedChat(MutableComponent mutableText) {
        printModifiedChat((Component) mutableText);
    }

    public static void printModifiedChat(Component component) {
        if (component == null)
            return;

        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(component));
    }
}
