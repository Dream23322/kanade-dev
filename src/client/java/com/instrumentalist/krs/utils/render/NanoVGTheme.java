package com.instrumentalist.krs.utils.render;

import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.LinearGradientDirection;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.util.List;

public final class NanoVGTheme {
    public static final Color DEFAULT_BACKGROUND = new Color(0x0D, 0x09, 0x14);
    public static final Color DEFAULT_TEXT = new Color(0xE9, 0xE2, 0xF0);
    public static final Color DEFAULT_BASE = new Color(0x08, 0x06, 0x0B);
    public static final Color DEFAULT_MUTED = new Color(0x8E, 0x82, 0x99);
    public static final Color DEFAULT_DIM = new Color(0x6C, 0x61, 0x78);
    /** Theme-driven palette; replaced by {@link #setTheme(Color, Color, Color, Color)}. */
    public static Color BASE = DEFAULT_BASE;
    public static Color TEXT = DEFAULT_TEXT;
    public static Color MUTED = DEFAULT_MUTED;
    public static Color DIM = DEFAULT_DIM;
    public static Color HUD_BACKGROUND = DEFAULT_BACKGROUND;
    public static final Color DANGER = new Color(0xFF, 0x5D, 0x7A);
    public static final Color WARNING = new Color(0xF2, 0xB4, 0x5C);
    public static final Color SUCCESS = new Color(0x7B, 0xDB, 0xA4);
    public static final Color DEFAULT_ACCENT = new Color(0xD6, 0x6B, 0xE8);
    public static final Color DEFAULT_ACCENT_END = new Color(0x8B, 0x6B, 0xF2);
    /** Active accent colours; replaced by {@link #setAccent(Color, Color)} so every consumer follows the theme. */
    public static Color ACCENT = DEFAULT_ACCENT;
    public static Color ACCENT_END = DEFAULT_ACCENT_END;
    public static final Color MODE = new Color(0x8F, 0xA7, 0xFF);
    public static final Color PANEL_EDGE = new Color(255, 255, 255, 30);
    public static final float RADIUS_LABEL = 0f;
    public static final float RADIUS_HUD = 0f;
    public static final float RADIUS_PANEL = 0f;
    public static final float RADIUS_MENU = 0f;
    public static final float RADIUS_CONTROL = RADIUS_HUD;
    private static final Shader2DRenderer.GlassRequest PANEL_GLASS = Shader2DRenderer.GlassRequest.panel();
    private static final Shader2DRenderer.GlassRequest COMPACT_GLASS = Shader2DRenderer.GlassRequest.compact();
    private static final Shader2DRenderer.GlassRequest CONTROL_GLASS = Shader2DRenderer.GlassRequest.control();
    private static final Shader2DRenderer.GlassRequest CONTROL_DARK_GLASS = Shader2DRenderer.GlassRequest.controlDark();
    private static Color PANEL_TOP = new Color(0x12, 0x0C, 0x1B, 150);
    private static Color PANEL_BOTTOM = new Color(0x0A, 0x07, 0x10, 190);

    public static Color COMPACT_BACKGROUND = new Color(0x0D, 0x09, 0x14, 140);
    public static Color SWITCH_ON = ACCENT;
    public static Color SLIDER_FILL = ACCENT;
    public static final Color INPUT_FOCUS = new Color(0xE7, 0xDB, 0xF2);
    public static final Color SCROLLBAR_THUMB = new Color(0xCF, 0xC3, 0xDD);
    public static final Color CONTROL_TRACK = new Color(255, 255, 255, 30);
    public static final Color LIST_CHIP = new Color(255, 255, 255, 38);
    public static final Color KNOB_FILL = new Color(0xED, 0xE6, 0xF5);
    private static final float[] CONNECTED_BOXES = new float[256];
    private static final float[] CONNECTED_RADII = new float[256];
    private static final float[] CORNER_SCRATCH = new float[4];
    private static float[] connectedGeometry = new float[32 * 8];

    private NanoVGTheme() {
    }

    public static void renderPanelEffects(NVGU vg, float x, float y, float width, float height,
                                          float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, PANEL_GLASS);
    }

    public static void renderCompactEffects(NVGU vg, float x, float y, float width, float height,
                                            float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, COMPACT_GLASS);
    }

    public static void renderControlEffects(NVGU vg, float x, float y, float width, float height,
                                            float radius, float alpha) {
        renderControlEffects(vg, x, y, width, height, radius, alpha, 0f);
    }

    public static void renderControlEffects(NVGU vg, float x, float y, float width, float height,
                                            float radius, float alpha, float brightness) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        CONTROL_GLASS.brightness = brightness;
        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, CONTROL_GLASS);
    }

    public static void renderControlDarkEffects(NVGU vg, float x, float y, float width, float height,
                                                float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, CONTROL_DARK_GLASS);
    }

    public static void renderConnectedEffects(NVGU vg, List<? extends ConnectedGlassRect> entries, float radius, float alpha) {
        int written = packConnected(entries, radius);
        if (written == 0)
            return;

        float opacity = opacity(alpha);
        if (opacity <= 0.001f)
            return;

        vg.liquidGlassConnectedRoundedRectangles(CONNECTED_BOXES, CONNECTED_RADII, written, opacity, COMPACT_GLASS);
    }

    public static void renderConnected(NVGU vg, List<? extends ConnectedGlassRect> entries, float radius, float alpha) {
        if (entries == null || entries.isEmpty())
            return;

        float opacity = opacity(alpha);
        if (opacity <= 0.001f)
            return;

        int count = entries.size();
        if (connectedGeometry.length < count * 8)
            connectedGeometry = new float[Math.max(count * 8, connectedGeometry.length * 2)];

        int written = 0;
        for (int i = 0; i < count; i++) {
            ConnectedGlassRect entry = entries.get(i);
            if (entry == null || entry.width() <= 0f || entry.height() <= 0f)
                continue;

            writeCorners(entries, i, radius, CORNER_SCRATCH);
            int offset = written * 8;
            connectedGeometry[offset] = entry.x();
            connectedGeometry[offset + 1] = entry.y();
            connectedGeometry[offset + 2] = entry.width();
            connectedGeometry[offset + 3] = entry.height();
            connectedGeometry[offset + 4] = CORNER_SCRATCH[0];
            connectedGeometry[offset + 5] = CORNER_SCRATCH[1];
            connectedGeometry[offset + 6] = CORNER_SCRATCH[2];
            connectedGeometry[offset + 7] = CORNER_SCRATCH[3];
            written++;
        }

        if (written > 0)
            vg.roundedRectangles(connectedGeometry, written, scaledAlpha(COMPACT_BACKGROUND, opacity, 0));
    }

    private static int packConnected(List<? extends ConnectedGlassRect> entries, float radius) {
        if (entries == null || entries.isEmpty())
            return 0;

        int written = 0;
        int limit = Math.min(entries.size(), CONNECTED_BOXES.length / 4);
        for (int i = 0; i < limit; i++) {
            ConnectedGlassRect entry = entries.get(i);
            if (entry == null || entry.width() <= 0f || entry.height() <= 0f)
                continue;

            int offset = written * 4;
            CONNECTED_BOXES[offset] = entry.x();
            CONNECTED_BOXES[offset + 1] = entry.y();
            CONNECTED_BOXES[offset + 2] = entry.width();
            CONNECTED_BOXES[offset + 3] = entry.height();
            writeCorners(entries, i, radius, CORNER_SCRATCH);
            CONNECTED_RADII[offset] = CORNER_SCRATCH[0];
            CONNECTED_RADII[offset + 1] = CORNER_SCRATCH[1];
            CONNECTED_RADII[offset + 2] = CORNER_SCRATCH[2];
            CONNECTED_RADII[offset + 3] = CORNER_SCRATCH[3];
            written++;
        }
        return written;
    }

    private static void writeCorners(List<? extends ConnectedGlassRect> entries, int index, float radius, float[] output) {
        ConnectedGlassRect entry = entries.get(index);
        if (entry.hasExplicitCorners()) {
            output[0] = entry.topLeftRadius();
            output[1] = entry.topRightRadius();
            output[2] = entry.bottomRightRadius();
            output[3] = entry.bottomLeftRadius();
            return;
        }

        ConnectedGlassRect previous = index > 0 ? entries.get(index - 1) : null;
        ConnectedGlassRect next = index + 1 < entries.size() ? entries.get(index + 1) : null;
        float left = entry.x();
        float right = entry.x() + entry.width();
        output[0] = exposedCornerRadius(previous, left, radius);
        output[1] = exposedCornerRadius(previous, right, radius);
        output[2] = exposedCornerRadius(next, right, radius);
        output[3] = exposedCornerRadius(next, left, radius);
    }

    private static float exposedCornerRadius(ConnectedGlassRect neighbor, float cornerX, float radius) {
        if (neighbor == null)
            return radius;

        float neighborLeft = neighbor.x();
        float neighborRight = neighbor.x() + neighbor.width();
        if (cornerX >= neighborLeft - 0.5f && cornerX <= neighborRight + 0.5f)
            return 0f;

        float exposedWidth = cornerX < neighborLeft ? neighborLeft - cornerX : cornerX - neighborRight;
        return Math.min(radius, exposedWidth);
    }

    public interface ConnectedGlassRect {
        float x();

        float y();

        float width();

        float height();

        default boolean hasExplicitCorners() {
            return false;
        }

        default float topLeftRadius() {
            return 0f;
        }

        default float topRightRadius() {
            return 0f;
        }

        default float bottomRightRadius() {
            return 0f;
        }

        default float bottomLeftRadius() {
            return 0f;
        }
    }

    public static void renderPanel(NVGU vg, float x, float y, float width, float height,
                                   float radius, float alpha) {
        renderPanel(vg, x, y, width, height, radius, alpha, 0);
    }

    public static void renderPanel(NVGU vg, float x, float y, float width, float height,
                                   float radius, float alpha, int backgroundAlphaOffset) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        float feather = Math.max(1f, height);
        vg.roundedRectangle(
                x, y, width, height, radius,
                vg.linearGradient(
                        x, y, width, height, feather,
                        scaledAlpha(PANEL_TOP, opacity, backgroundAlphaOffset),
                        scaledAlpha(PANEL_BOTTOM, opacity, backgroundAlphaOffset),
                        LinearGradientDirection.TOP_TO_BOTTOM
                )
        );
        vg.roundedRectangleBorder(x, y, width, height, radius, 1f, scaledAlpha(PANEL_EDGE, opacity, 0), Border.INSIDE);
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float radius, float alpha) {
        renderCompact(vg, x, y, width, height, radius, alpha, 0);
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float radius, float alpha, int backgroundAlphaOffset) {
        renderCompact(vg, x, y, width, height, radius, radius, radius, radius, alpha, backgroundAlphaOffset);
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float topLeft, float topRight, float bottomRight, float bottomLeft,
                                     float alpha) {
        renderCompact(vg, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, alpha, 0);
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float topLeft, float topRight, float bottomRight, float bottomLeft,
                                     float alpha, int backgroundAlphaOffset) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.roundedRectangle(
                x, y, width, height,
                topLeft, topRight, bottomRight, bottomLeft,
                scaledAlpha(COMPACT_BACKGROUND, opacity, backgroundAlphaOffset)
        );

        if (topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft)
            vg.roundedRectangleBorder(x, y, width, height, topLeft, 1f,
                    scaledAlpha(PANEL_EDGE, opacity * 0.65f, 0), Border.INSIDE);
    }

    public static void renderEdge(NVGU vg, float x, float y, float width, float height,
                                  float radius, float alpha) {
        if (!isDrawable(width, height, alpha))
            return;

        vg.roundedRectangleBorder(x, y, width, height, radius, 1f,
                scaledAlpha(PANEL_EDGE, opacity(alpha), 0), Border.INSIDE);
    }

    public static void renderAccentUnderline(NVGU vg, float x, float y, float width, float height, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.rectangle(
                x, y, width, height,
                vg.linearGradient(x, y, width, height, Math.max(1f, width),
                        accentStart(Math.round(235f * opacity)),
                        accentEnd(Math.round(235f * opacity)),
                        LinearGradientDirection.LEFT_TO_RIGHT)
        );
    }

    public static float trackedTextWidth(NVGFont font, String text, float size, float tracking) {
        if (text == null || text.isEmpty())
            return 0f;

        float width = 0f;
        int count = text.offsetByCodePoints(0, text.codePointCount(0, text.length()));
        for (int index = 0; index < count; index++) {
            int start = text.offsetByCodePoints(0, index);
            int end = text.offsetByCodePoints(0, index + 1);
            width += font.getWidth(text.substring(start, end), size);
            if (index + 1 < count)
                width += tracking;
        }
        return width;
    }

    public static float drawTrackedText(NVGFont font, String text, float x, float y, float size,
                                        float tracking, Color color, boolean shadow) {
        if (text == null || text.isEmpty())
            return 0f;

        float cursor = x;
        int count = text.codePointCount(0, text.length());
        for (int index = 0; index < count; index++) {
            int start = text.offsetByCodePoints(0, index);
            int end = text.offsetByCodePoints(0, index + 1);
            String glyph = text.substring(start, end);
            font.drawText(glyph, cursor, y, size, color, Alignment.LEFT_TOP, shadow);
            cursor += font.getWidth(glyph, size);
            if (index + 1 < count)
                cursor += tracking;
        }
        return cursor - x;
    }

    public static Color accent(int alpha) {
        return withAlpha(ACCENT, alpha);
    }

    public static Color accentStart(int alpha) {
        return withAlpha(ACCENT, alpha);
    }

    public static Color accentEnd(int alpha) {
        return withAlpha(ACCENT_END, alpha);
    }

    public static Color mode(int alpha) {
        return withAlpha(MODE, alpha);
    }

    public static Color success(int alpha) {
        return withAlpha(SUCCESS, alpha);
    }

    public static Color text(int alpha) {
        return withAlpha(TEXT, alpha);
    }

    public static Color muted(int alpha) {
        return withAlpha(MUTED, alpha);
    }

    public static Color dim(int alpha) {
        return withAlpha(DIM, alpha);
    }

    public static Color danger(int alpha) {
        return withAlpha(DANGER, alpha);
    }

    public static Color warning(int alpha) {
        return withAlpha(WARNING, alpha);
    }

    public static Color switchOn(int alpha) {
        return withAlpha(SWITCH_ON, alpha);
    }

    public static Color sliderFill(int alpha) {
        return withAlpha(SLIDER_FILL, alpha);
    }

    public static Color inputFocus(int alpha) {
        return withAlpha(INPUT_FOCUS, alpha);
    }

    public static Color scrollbarThumb(int alpha) {
        return withAlpha(SCROLLBAR_THUMB, alpha);
    }

    public static Color base(int alpha) {
        return withAlpha(BASE, alpha);
    }

    public static Color accentMix(float progress, int alpha) {
        float t = progress - (float) Math.floor(progress);
        Color start = ACCENT;
        Color end = t < 0.58f ? ACCENT_END : MODE;
        float local = t < 0.58f ? t / 0.58f : (t - 0.58f) / 0.42f;
        local = local * local * (3f - 2f * local);
        return new Color(
                Math.round(start.getRed() + (end.getRed() - start.getRed()) * local),
                Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * local),
                Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * local),
                Math.clamp(alpha, 0, 255)
        );
    }

    public static Color scaledAlpha(Color color, float alpha) {
        return scaledAlpha(color, alpha, 0);
    }

    public static Color scaledAlpha(Color color, float alpha, int baseAlphaOffset) {
        float opacity = opacity(alpha);
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.clamp(Math.round(Math.clamp(color.getAlpha() + baseAlphaOffset, 0, 255) * opacity), 0, 255)
        );
    }

    public static Color offsetAlpha(Color color, int alphaOffset) {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.clamp(color.getAlpha() + alphaOffset, 0, 255)
        );
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private static boolean isDrawable(float width, float height, float alpha) {
        return width > 0f && height > 0f && alpha > 0.001f;
    }

    private static float opacity(float alpha) {
        return Float.isFinite(alpha) ? Math.clamp(alpha, 0f, 1f) : 0f;
    }

    /** Activates the accent pair used by the HUD and the click GUI. */
    public static void setAccent(Color start, Color end) {
        setTheme(start, end, HUD_BACKGROUND, TEXT);
    }

    /** Replaces the whole theme palette: accents, panel background and text colours. */
    public static void setTheme(Color accentStart, Color accentEnd, Color background, Color text) {
        ACCENT = accentStart == null ? DEFAULT_ACCENT : accentStart;
        ACCENT_END = accentEnd == null ? DEFAULT_ACCENT_END : accentEnd;
        SWITCH_ON = ACCENT;
        SLIDER_FILL = ACCENT;

        Color bg = background == null ? DEFAULT_BACKGROUND : background;
        Color fg = text == null ? DEFAULT_TEXT : text;

        HUD_BACKGROUND = new Color(bg.getRed(), bg.getGreen(), bg.getBlue());
        BASE = blend(HUD_BACKGROUND, Color.BLACK, 0.45f);
        COMPACT_BACKGROUND = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 140);
        PANEL_TOP = new Color(
                Math.min(255, Math.round(bg.getRed() + 8f)),
                Math.min(255, Math.round(bg.getGreen() + 6f)),
                Math.min(255, Math.round(bg.getBlue() + 10f)),
                150);
        PANEL_BOTTOM = new Color(
                Math.max(0, Math.round(bg.getRed() * 0.7f)),
                Math.max(0, Math.round(bg.getGreen() * 0.7f)),
                Math.max(0, Math.round(bg.getBlue() * 0.7f)),
                190);
        TEXT = new Color(fg.getRed(), fg.getGreen(), fg.getBlue());
        MUTED = blend(TEXT, HUD_BACKGROUND, 0.45f);
        DIM = blend(TEXT, HUD_BACKGROUND, 0.62f);
    }

    /** Theme background at the given alpha, for HUD cards and rows. */
    public static Color background(int alpha) {
        return withAlpha(HUD_BACKGROUND, alpha);
    }

    public static Color blend(Color start, Color end, float progress) {
        if (start == null) return end;
        if (end == null) return start;

        float t = Float.isFinite(progress) ? Math.clamp(progress, 0f, 1f) : 0f;
        return new Color(
                Math.clamp(Math.round(start.getRed() + (end.getRed() - start.getRed()) * t), 0, 255),
                Math.clamp(Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * t), 0, 255),
                Math.clamp(Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * t), 0, 255),
                Math.clamp(Math.round(start.getAlpha() + (end.getAlpha() - start.getAlpha()) * t), 0, 255)
        );
    }

    /** Accent presets offered by the Interface module. {@link #CUSTOM} keeps the colour pickers as-is. */
    public enum Theme {
        KANADE("Kanade", 0xD66BE8, 0x8B6BF2, 0x0D0914, 0xE9E2F0),
        AQUA("Aqua", 0x3FD8E0, 0x4A7DFF, 0x04121A, 0xE2F6FA),
        SUNSET("Sunset", 0xFF9A4D, 0xFF4D7D, 0x170A0C, 0xFCEDE6),
        GRAPE("Grape", 0xA46BFF, 0x5D4DFF, 0x0E0818, 0xEEE7FA),
        LIME("Lime", 0xA8E05F, 0x35C48C, 0x0A1408, 0xEBF6E4),
        EMBER("Ember", 0xFF6B4D, 0xB82D6B, 0x160806, 0xFAE8E2),
        MONO("Mono", 0xE4E4EA, 0x9AA0AE, 0x0C0C0E, 0xECECEC),
        CUSTOM("Custom", 0xD66BE8, 0x8B6BF2, 0x0D0914, 0xE9E2F0);

        private final String label;
        private final Color start;
        private final Color end;
        private final Color background;
        private final Color text;

        Theme(String label, int startRgb, int endRgb, int backgroundRgb, int textRgb) {
            this.label = label;
            this.start = new Color(startRgb);
            this.end = new Color(endRgb);
            this.background = new Color(backgroundRgb);
            this.text = new Color(textRgb);
        }

        public String label() {
            return label;
        }

        public Color start() {
            return start;
        }

        public Color end() {
            return end;
        }

        public Color background() {
            return background;
        }

        public Color text() {
            return text;
        }

        public boolean custom() {
            return this == CUSTOM;
        }

        public static String[] labels() {
            Theme[] themes = values();
            String[] labels = new String[themes.length];
            for (int index = 0; index < themes.length; index++)
                labels[index] = themes[index].label;
            return labels;
        }

        public static Theme byName(String name) {
            if (name != null) {
                for (Theme theme : values()) {
                    if (theme.label.equalsIgnoreCase(name))
                        return theme;
                }
            }
            return KANADE;
        }
    }
}
