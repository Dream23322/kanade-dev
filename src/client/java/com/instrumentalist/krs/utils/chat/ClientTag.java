package com.instrumentalist.krs.utils.chat;

import com.instrumentalist.krs.hacks.features.render.Chat;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.NVGFont;

import java.awt.Color;

/**
 * The {@code kanade | message} prefix shared by chat lines and alert cards.
 */
public final class ClientTag {
    private static final NVGFont FONT = NVGFonts.INTER;
    private static final float GAP = 4f;

    private ClientTag() {
    }

    public static String label() {
        String value = Chat.tagText.get();
        if (value == null || value.isBlank())
            return Chat.CLIENT_TAG_DEFAULT;
        return value.strip();
    }

    /** Plain-text form used for copyable strings and mention notifications. */
    public static String format(String message) {
        return label() + " | " + (message == null ? "" : message);
    }

    public static float width(float size) {
        return FONT.getWidth(label(), size) + GAP + FONT.getWidth("|", size) + GAP;
    }

    /** Draws the tag plus separator and returns the advance width including the trailing gap. */
    public static float render(NVGU vg, float x, float y, float size, float alpha) {
        if (vg == null || alpha <= 0.002f)
            return width(size);

        int alphaInt = Math.clamp(Math.round(alpha * 255f), 0, 255);
        String label = label();

        if (Chat.tagGlow.get()) {
            int glowAlpha = Math.round(alphaInt * 0.22f);
            Color glow = new Color(255, 255, 255, glowAlpha);
            FONT.drawText(label, x - 0.6f, y, size, glow, Alignment.LEFT_TOP, false);
            FONT.drawText(label, x + 0.6f, y, size, glow, Alignment.LEFT_TOP, false);
            FONT.drawText(label, x, y - 0.6f, size, glow, Alignment.LEFT_TOP, false);
        }

        String style = Chat.tagStyle.get();
        if ("Gradient".equalsIgnoreCase(style)) {
            drawGradient(vg, label, x, y, size, alphaInt);
        } else {
            FONT.drawText(label, x, y, size, color(style, alphaInt), Alignment.LEFT_TOP, true);
        }

        float labelWidth = FONT.getWidth(label, size);
        float separatorX = x + labelWidth + GAP;
        FONT.drawText("|", separatorX, y, size,
                new Color(255, 255, 255, Math.min(alphaInt, 235)), Alignment.LEFT_TOP, true);
        return separatorX + FONT.getWidth("|", size) + GAP - x;
    }

    private static void drawGradient(NVGU vg, String text, float x, float y, float size, int alpha) {
        Color start = NanoVGTheme.ACCENT;
        Color end = NanoVGTheme.ACCENT_END;
        float cursor = x;
        int count = text.codePointCount(0, text.length());
        for (int index = 0; index < count; index++) {
            int startOffset = text.offsetByCodePoints(0, index);
            int endOffset = text.offsetByCodePoints(0, index + 1);
            String glyph = text.substring(startOffset, endOffset);

            float progress = count <= 1 ? 0f : index / (float) (count - 1);
            Color color = new Color(
                    Math.clamp(Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress), 0, 255),
                    Math.clamp(Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress), 0, 255),
                    Math.clamp(Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress), 0, 255),
                    alpha
            );
            FONT.drawText(glyph, cursor, y, size, color, Alignment.LEFT_TOP, true);
            cursor += FONT.getWidth(glyph, size);
        }
    }

    private static Color color(String style, int alpha) {
        if ("White".equalsIgnoreCase(style))
            return new Color(255, 255, 255, alpha);
        if ("Custom".equalsIgnoreCase(style)) {
            Color custom = Chat.tagColor.get();
            return new Color(custom.getRed(), custom.getGreen(), custom.getBlue(), alpha);
        }
        return NanoVGTheme.accent(alpha);
    }
}
