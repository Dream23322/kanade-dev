package com.instrumentalist.krs.utils.chat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.features.level.AntiCheatDetector;
import com.instrumentalist.krs.hacks.features.level.anticheat.AntiCheatFlags;
import com.instrumentalist.krs.hacks.features.render.Chat;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.HudEditor;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.sounds.SoundEvents;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** NanoVG chat HUD: themed panel, client tag, timestamps, mention highlight and alert flash. */
public final class ChatRenderer implements IMinecraft {
    private static final NVGFont FONT = NVGFonts.INTER;
    private static final float SCREEN_MARGIN = 4f;
    private static final float BOTTOM_MARGIN = 44f;
    private static final float TOP_MARGIN = 8f;
    private static final float PADDING_X = 6f;
    private static final float PADDING_Y = 4f;
    private static final float FONT_SIZE = 9.5f;
    private static final float LINE_HEIGHT = 11f;
    private static final float ICON_SIZE = 10f;
    private static final float ICON_GAP = 4f;
    private static final float TIME_GAP = 4f;
    private static final float FADE_MS = 180f;
    private static final long FLASH_MS = 650L;

    private record Line(ChatLog.Entry entry, String text, boolean tagged, String time) {
    }

    private static final List<Line> cachedLines = new ArrayList<>();
    private static long cachedVersion = -1L;
    private static float cachedWidth = -1f;
    private static boolean cachedIcons;
    private static String cachedTimeMode = "";
    private static String cachedTag = "";
    private static String cachedPattern = "";
    private static DateTimeFormatter cachedFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private static float lastHeight;
    private static long flashStartNanos;
    private static long lastFrameNanos = System.nanoTime();

    private ChatRenderer() {
    }

    public static boolean isCustomActive() {
        return Chat.customEnabled();
    }

    /** Space the info HUD must leave free above the chat panel. */
    public static float screenOffset() {
        return lastHeight + 18f;
    }

    public static void render(NVGU vg) {
        if (vg == null || !isCustomActive())
            return;
        if (mc.gui == null || mc.gui.hud == null)
            return;
        if (mc.gui.hud.isHidden() && !HudEditor.isEditing())
            return;
        // Keep the custom panel out of menus; vanilla chat also sits behind screen layers.
        if (mc.gui.screen() != null && !HudEditor.isEditing())
            return;

        List<ChatLog.Entry> entries = ChatLog.snapshot();
        if (entries.isEmpty()) {
            lastHeight = 0f;
            return;
        }

        long now = System.nanoTime();
        lastFrameNanos = now;

        boolean focused = mc.gui != null && mc.gui.screen() instanceof ChatScreen;
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();

        String timeMode = Chat.timestamps.get();
        boolean showTime = !"Off".equalsIgnoreCase(timeMode);
        boolean showIcons = Chat.icons.get();
        float panelWidth = Math.clamp(Chat.width.get(), 80f, Math.max(80f, screenWidth - SCREEN_MARGIN * 2f));
        ensureLayout(entries, panelWidth, showTime, showIcons, timeMode);

        int maxLines = Chat.maxLines.get();
        int scroll = focused ? chatScroll() : 0;
        int end = Math.max(0, cachedLines.size() - scroll);
        int start = Math.max(0, end - maxLines);
        List<Line> visible = cachedLines.subList(start, Math.max(start, end));
        if (visible.isEmpty()) {
            lastHeight = 0f;
            return;
        }

        float spacing = Chat.lineSpacing.get();
        float height = PADDING_Y * 2f + visible.size() * (LINE_HEIGHT + spacing) - spacing;
        lastHeight = height;

        float offsetX = HudEditor.offsetX(HudEditor.Element.CHAT);
        float offsetY = HudEditor.offsetY(HudEditor.Element.CHAT);
        boolean bottom = !"Top Left".equalsIgnoreCase(Chat.position.get());
        float x = SCREEN_MARGIN + offsetX;
        float y = bottom
                ? screenHeight - BOTTOM_MARGIN - height + offsetY
                : TOP_MARGIN + offsetY;
        y = Math.clamp(y, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN));

        HudEditor.register(HudEditor.Element.CHAT, x, y, panelWidth, height);

        final float panelX = x;
        final float panelY = y;
        final float panelHeight = height;
        final float lineSpacing = spacing;
        final float frameAlpha = Chat.opacity.get();
        final float mouseX = focused && mc.mouseHandler != null
                ? NanoVGManager.toScaledMouseX(mc.mouseHandler.getScaledXPos(mc.getWindow()))
                : Float.NaN;
        final float mouseY = focused && mc.mouseHandler != null
                ? NanoVGManager.toScaledMouseY(mc.mouseHandler.getScaledYPos(mc.getWindow()))
                : Float.NaN;
        final ChatLog.Entry[] hovered = new ChatLog.Entry[1];

        HudEditor.withScaleAt(vg, HudEditor.Element.CHAT, panelX, panelY, () -> {
            if (Chat.background.get() && frameAlpha > 0.01f)
                drawPanel(vg, panelX, panelY, panelWidth, panelHeight, frameAlpha);

            float lineY = panelY + PADDING_Y;
            for (Line line : visible) {
                if (!Float.isNaN(mouseX) && mouseX >= panelX && mouseX <= panelX + panelWidth
                        && mouseY >= lineY - 1f && mouseY <= lineY + LINE_HEIGHT)
                    hovered[0] = line.entry();

                drawLine(vg, line, panelX, lineY, panelWidth, frameAlpha, now, showTime, showIcons);
                lineY += LINE_HEIGHT + lineSpacing;
            }
        });

        if (hovered[0] != null)
            drawDebugTooltip(vg, mouseX, mouseY, hovered[0], screenWidth, screenHeight);

        int flash = flashAlpha(now);
        if (flash > 2)
            drawFlash(vg, screenWidth, screenHeight, flash);
    }

    private static void ensureLayout(List<ChatLog.Entry> entries, float panelWidth, boolean showTime,
                                     boolean showIcons, String timeMode) {
        String tag = ClientTag.label();
        if (cachedVersion == ChatLog.version()
                && Float.compare(cachedWidth, panelWidth) == 0
                && cachedIcons == showIcons
                && cachedTimeMode.equals(timeMode)
                && cachedTag.equals(tag))
            return;

        cachedVersion = ChatLog.version();
        cachedWidth = panelWidth;
        cachedIcons = showIcons;
        cachedTimeMode = timeMode;
        cachedTag = tag;
        cachedLines.clear();

        float textWidth = panelWidth - PADDING_X * 2f;
        float timeIndent = showTime ? timestampWidth() + TIME_GAP : 0f;
        float iconIndent = showIcons ? ICON_SIZE + ICON_GAP : 0f;
        float tagIndent = ClientTag.width(FONT_SIZE);

        for (ChatLog.Entry entry : entries) {
            boolean tagged = entry.kind() == ChatLog.Kind.CLIENT;
            float firstIndent = iconIndent + timeIndent + (tagged ? tagIndent : 0f);
            float normalIndent = iconIndent + timeIndent;

            List<String> wrapped = wrap(entry.plain(), textWidth - normalIndent, textWidth - firstIndent);
            if (wrapped.isEmpty())
                continue;

            String time = showTime ? formatTime(entry.timeMs()) : "";
            for (int index = 0; index < wrapped.size(); index++)
                cachedLines.add(new Line(entry, wrapped.get(index), tagged && index == 0, time));
        }
    }

    /** Hover card shown for flag lines while the chat screen is open. */
    private static void drawDebugTooltip(NVGU vg, float mouseX, float mouseY, ChatLog.Entry entry,
                                         float screenWidth, float screenHeight) {
        AntiCheatFlags.FlagDebug debug = AntiCheatFlags.debugFor(entry.plain());
        if (debug == null)
            return;

        String[] rows = {
                "Player   " + debug.player(),
                "Check   " + debug.check(),
                "Violations   x" + debug.violations(),
                "Signals   " + debug.signals() + " / " + debug.threshold(),
                "Flagged   " + Math.max(0L, (System.currentTimeMillis() - debug.timeMs()) / 1000L) + "s ago",
                "Sensitivity   " + String.format(Locale.ROOT, "%.1f", AntiCheatDetector.sensitivity.get()) + "x"
        };

        float width = 0f;
        for (String row : rows)
            width = Math.max(width, NVGFonts.INTER.getWidth(row, 8f));
        width = Math.max(140f, width + 16f);
        float height = 17f + rows.length * 10f + 6f;

        float x = Math.clamp(mouseX + 10f, 4f, Math.max(4f, screenWidth - width - 4f));
        float y = mouseY + 12f;
        if (y + height > screenHeight - 4f)
            y = Math.max(4f, mouseY - height - 6f);

        NanoVGTheme.renderCompact(vg, x, y, width, height, 3f, 1f, 60);
        NanoVGTheme.renderEdge(vg, x, y, width, height, 3f, 0.9f);
        NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, "FLAG DEBUG", x + 8f, y + 5f,
                7f, 0.6f, NanoVGTheme.accent(235), true);

        float rowY = y + 17f;
        for (String row : rows) {
            NVGFonts.INTER.drawText(row, x + 8f, rowY, 8f, NanoVGTheme.text(225), Alignment.LEFT_TOP, true);
            rowY += 10f;
        }
    }

    private static void drawPanel(NVGU vg, float x, float y, float width, float height, float alpha) {
        float radius = Chat.radius.get();
        vg.roundedRectangle(x, y, width, height, radius, panelColor(alpha));
        vg.roundedRectangleBorder(x, y, width, height, radius, 1f,
                NanoVGTheme.scaledAlpha(NanoVGTheme.PANEL_EDGE, alpha * 0.7f), Border.INSIDE);
    }

    private static void drawLine(NVGU vg, Line line, float x, float y, float width, float alpha,
                                 long now, boolean showTime, boolean showIcons) {
        ChatLog.Entry entry = line.entry();
        float entryAlpha = alpha * entryAlpha(entry, now);
        if (entryAlpha <= 0.004f)
            return;

        float textX = x + PADDING_X;
        int alphaInt = Math.clamp(Math.round(entryAlpha * 255f), 0, 255);

        if (entry.mention() && Chat.highlight.get()) {
            vg.roundedRectangle(x + 1f, y - 1.5f, 2.5f, LINE_HEIGHT + 1f, 1.5f,
                    mentionColor(Math.min(255, Math.round(alphaInt * 0.95f))));
            vg.roundedRectangle(x, y - 2f, width, LINE_HEIGHT + 2f, 0f,
                    new Color(mentionColor(255).getRed(), mentionColor(255).getGreen(),
                            mentionColor(255).getBlue(), Math.min(48, alphaInt / 5)));
        }

        if (showTime) {
            FONT.drawText(line.time(), textX, y + 0.5f, FONT_SIZE, NanoVGTheme.muted(Math.round(alphaInt * 0.7f)),
                    Alignment.LEFT_TOP, true);
            textX += timestampWidth() + TIME_GAP;
        }

        if (showIcons) {
            String icon = switch (entry.kind()) {
                case PLAYER -> MaterialIcon.PERSON;
                case CLIENT -> MaterialIcon.BOLT;
                default -> MaterialIcon.INFO;
            };
            Color iconColor = entry.kind() == ChatLog.Kind.CLIENT
                    ? NanoVGTheme.accent(Math.round(alphaInt * 0.9f))
                    : NanoVGTheme.muted(Math.round(alphaInt * 0.75f));
            NVGFonts.ICON.drawText(icon, textX, y - 0.5f, ICON_SIZE, iconColor, Alignment.LEFT_TOP, true);
            textX += ICON_SIZE + ICON_GAP;
        }

        if (line.tagged())
            textX += ClientTag.render(vg, textX, y, FONT_SIZE, entryAlpha);

        Color textColor = entry.mention() && Chat.highlight.get()
                ? mentionColor(alphaInt)
                : textColor(alphaInt);
        FONT.drawText(line.text(), textX, y, FONT_SIZE, textColor, Alignment.LEFT_TOP, true);
    }

    private static float entryAlpha(ChatLog.Entry entry, long now) {
        if ("None".equalsIgnoreCase(Chat.animation.get()))
            return 1f;

        float ageMs = (now - entry.timeMs() * 1_000_000L) / 1_000_000.0f;
        if (ageMs >= FADE_MS)
            return 1f;
        return Math.clamp(ageMs / FADE_MS, 0.35f, 1f);
    }

    private static int flashAlpha(long now) {
        if (!Chat.flashScreen.get() || flashStartNanos == 0L)
            return 0;
        float elapsed = (now - flashStartNanos) / 1_000_000.0f;
        if (elapsed > FLASH_MS)
            return 0;
        return Math.round(110f * (1f - elapsed / FLASH_MS));
    }

    private static void drawFlash(NVGU vg, float width, float height, int alpha) {
        Color colour = glowColor(255);
        for (int layer = 3; layer >= 1; layer--) {
            float thickness = layer * 3f;
            int layerAlpha = Math.max(0, alpha / (layer * 2));
            Color strip = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), layerAlpha);
            vg.rectangle(0f, 0f, width, thickness, strip);
            vg.rectangle(0f, height - thickness, width, thickness, strip);
            vg.rectangle(0f, 0f, thickness, height, strip);
            vg.rectangle(width - thickness, 0f, thickness, height, strip);
        }
    }

    /** Alert hook called from {@link ChatLog#record} when a message mentions the player. */
    public static void ping(String sender, String plain) {
        flashStartNanos = System.nanoTime();

        if (Chat.notify.get() && Client.notificationManager != null) {
            String body = Chat.showSender.get() && sender != null && !sender.isBlank()
                    ? sender + ": " + plain
                    : plain;
            Client.notificationManager.addChatNotification(body);
        }

        if (mc.player == null)
            return;

        switch (Chat.sound.get()) {
            case "Pling" -> mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.4f);
            case "Click" -> mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.5f);
            case "None" -> {
            }
            default -> mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1f);
        }
    }

    private static Color panelColor(float alpha) {
        int alphaInt = Math.clamp(Math.round(alpha * 255f * 0.92f), 0, 255);
        Color base = switch (Chat.theme.get()) {
            case "Midnight" -> new Color(5, 7, 13);
            case "Accent Glass" -> NanoVGTheme.blend(new Color(10, 8, 16), NanoVGTheme.ACCENT, 0.16f);
            case "CS2" -> new Color(9, 13, 21);
            case "Light" -> new Color(238, 238, 245);
            case "Custom" -> Chat.customBackground.get();
            default -> NanoVGTheme.HUD_BACKGROUND;
        };
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alphaInt);
    }

    private static Color textColor(int alpha) {
        if ("Custom".equalsIgnoreCase(Chat.theme.get())) {
            Color custom = Chat.customText.get();
            return new Color(custom.getRed(), custom.getGreen(), custom.getBlue(), alpha);
        }
        if ("Light".equalsIgnoreCase(Chat.theme.get()))
            return new Color(24, 24, 30, alpha);
        return new Color(NanoVGTheme.TEXT.getRed(), NanoVGTheme.TEXT.getGreen(), NanoVGTheme.TEXT.getBlue(), alpha);
    }

    private static Color mentionColor(int alpha) {
        if ("Custom".equalsIgnoreCase(Chat.theme.get())) {
            Color custom = Chat.customMention.get();
            return new Color(custom.getRed(), custom.getGreen(), custom.getBlue(), alpha);
        }
        if ("CS2".equalsIgnoreCase(Chat.theme.get()))
            return new Color(255, 255, 255, alpha);
        return NanoVGTheme.accent(alpha);
    }

    private static Color glowColor(int alpha) {
        Color custom = "Custom".equalsIgnoreCase(Chat.theme.get())
                ? Chat.customGlow.get()
                : new Color(0xFF, 0xFF, 0xFF);
        return new Color(custom.getRed(), custom.getGreen(), custom.getBlue(), alpha);
    }

    private static float timestampWidth() {
        return FONT.getWidth("HH:mm:ss", FONT_SIZE);
    }

    private static String formatTime(long timeMs) {
        String mode = Chat.timestamps.get();
        String pattern = "HH:mm:ss".equalsIgnoreCase(mode) ? "HH:mm:ss" : "HH:mm";
        if (!cachedPattern.equals(pattern)) {
            cachedPattern = pattern;
            cachedFormatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
        }
        return cachedFormatter.format(Instant.ofEpochMilli(timeMs));
    }

    private static int chatScroll() {
        if (mc.gui == null)
            return 0;
        return ((com.instrumentalist.mixin.injector.ChatComponentAccessor) mc.gui.hud.getChat())
                .krs$getChatScrollbarPos();
    }

    private static List<String> wrap(String text, float normalWidth, float firstWidth) {
        if (text == null || text.isEmpty())
            return List.of("");

        String[] words = text.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        float limit = firstWidth;
        int index = 0;

        while (index < words.length) {
            String word = words[index];
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (current.isEmpty() || FONT.getWidth(candidate, FONT_SIZE) <= limit) {
                if (current.isEmpty() && FONT.getWidth(word, FONT_SIZE) > limit) {
                    // Hard-break a single oversized word so it never leaves the panel.
                    String remaining = word;
                    while (FONT.getWidth(remaining, FONT_SIZE) > limit) {
                        int cut = remaining.length();
                        while (cut > 1 && FONT.getWidth(remaining.substring(0, cut), FONT_SIZE) > limit)
                            cut--;
                        if (cut <= 1)
                            break;
                        lines.add(remaining.substring(0, cut));
                        remaining = remaining.substring(cut);
                        limit = normalWidth;
                    }
                    current.append(remaining);
                    index++;
                    continue;
                }
                current.setLength(0);
                current.append(candidate);
                index++;
                continue;
            }

            lines.add(current.toString());
            current.setLength(0);
            limit = normalWidth;
        }

        if (!current.isEmpty())
            lines.add(current.toString());
        return lines;
    }
}
