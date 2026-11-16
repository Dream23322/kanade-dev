package com.instrumentalist.krs.utils.render;

import java.awt.Color;

public final class GuiTheme {
    public final String name;
    public final Color headerBg;
    public final Color headerText;
    public final Color bodyBg;
    public final Color rowBg;
    public final Color rowHover;
    public final Color rowDivider;
    public final Color text;
    public final Color textBright;
    public final Color muted;
    public final Color enabled;
    public final Color enabledWash;
    public final Color dropdownBg;
    public final Color bar;
    public final Color scrollbar;
    public final Color searchBg;
    public final Color searchBorder;
    public final Color searchText;
    public final Color searchPlaceholder;
    public final Color gearHover;

    private static final GuiTheme[] THEMES = {
            new GuiTheme(
                    "Atmosphere",
                    new Color(10, 11, 16, 235),
                    new Color(235, 235, 238, 245),
                    new Color(52, 53, 58, 165),
                    new Color(255, 255, 255, 14),
                    new Color(255, 255, 255, 26),
                    new Color(255, 255, 255, 22),
                    new Color(177, 177, 190, 235),
                    new Color(250, 250, 252, 250),
                    new Color(139, 139, 144, 235),
                    new Color(109, 158, 232, 255),
                    new Color(109, 158, 232, 24),
                    new Color(14, 14, 16, 235),
                    new Color(255, 255, 255, 235),
                    new Color(220, 220, 224, 200),
                    new Color(14, 14, 16, 190),
                    new Color(255, 255, 255, 24),
                    new Color(235, 235, 238, 240),
                    new Color(150, 150, 158, 200),
                    new Color(45, 47, 54, 225)
            ),
            new GuiTheme(
                    "Kanade",
                    new Color(18, 12, 27, 235),
                    new Color(233, 226, 240, 245),
                    new Color(13, 9, 20, 185),
                    new Color(255, 255, 255, 10),
                    new Color(214, 107, 232, 30),
                    new Color(255, 255, 255, 18),
                    new Color(214, 206, 224, 235),
                    new Color(245, 240, 250, 250),
                    new Color(142, 130, 153, 235),
                    new Color(214, 107, 232, 255),
                    new Color(214, 107, 232, 24),
                    new Color(8, 6, 11, 235),
                    new Color(214, 107, 232, 235),
                    new Color(207, 195, 221, 200),
                    new Color(10, 7, 16, 190),
                    new Color(214, 107, 232, 40),
                    new Color(233, 226, 240, 240),
                    new Color(142, 130, 153, 200),
                    new Color(28, 24, 38, 205)
            ),
            new GuiTheme(
                    "Midnight",
                    new Color(0, 0, 0, 240),
                    new Color(242, 242, 242, 245),
                    new Color(12, 12, 12, 200),
                    new Color(255, 255, 255, 8),
                    new Color(255, 255, 255, 20),
                    new Color(255, 255, 255, 14),
                    new Color(214, 214, 214, 235),
                    new Color(255, 255, 255, 250),
                    new Color(120, 120, 120, 235),
                    new Color(255, 255, 255, 250),
                    new Color(255, 255, 255, 20),
                    new Color(0, 0, 0, 245),
                    new Color(255, 255, 255, 235),
                    new Color(255, 255, 255, 200),
                    new Color(24, 24, 24, 210),
                    new Color(255, 255, 255, 30),
                    new Color(242, 242, 242, 240),
                    new Color(120, 120, 120, 200),
                    new Color(40, 40, 40, 225)
            ),
            new GuiTheme(
                    "Ocean",
                    new Color(8, 20, 34, 235),
                    new Color(220, 234, 246, 245),
                    new Color(10, 28, 46, 175),
                    new Color(120, 190, 255, 10),
                    new Color(120, 190, 255, 22),
                    new Color(120, 190, 255, 16),
                    new Color(200, 220, 236, 235),
                    new Color(240, 250, 255, 250),
                    new Color(124, 147, 166, 235),
                    new Color(79, 195, 247, 255),
                    new Color(79, 195, 247, 24),
                    new Color(4, 12, 20, 240),
                    new Color(79, 195, 247, 235),
                    new Color(160, 210, 245, 200),
                    new Color(14, 32, 50, 210),
                    new Color(79, 195, 247, 40),
                    new Color(220, 234, 246, 240),
                    new Color(124, 147, 166, 200),
                    new Color(24, 48, 70, 225)
            )
    };

    private static int current;

    private GuiTheme(String name, Color headerBg, Color headerText, Color bodyBg, Color rowBg, Color rowHover,
                     Color rowDivider, Color text, Color textBright, Color muted, Color enabled, Color enabledWash,
                     Color dropdownBg, Color bar, Color scrollbar, Color searchBg, Color searchBorder,
                     Color searchText, Color searchPlaceholder, Color gearHover) {
        this.name = name;
        this.headerBg = headerBg;
        this.headerText = headerText;
        this.bodyBg = bodyBg;
        this.rowBg = rowBg;
        this.rowHover = rowHover;
        this.rowDivider = rowDivider;
        this.text = text;
        this.textBright = textBright;
        this.muted = muted;
        this.enabled = enabled;
        this.enabledWash = enabledWash;
        this.dropdownBg = dropdownBg;
        this.bar = bar;
        this.scrollbar = scrollbar;
        this.searchBg = searchBg;
        this.searchBorder = searchBorder;
        this.searchText = searchText;
        this.searchPlaceholder = searchPlaceholder;
        this.gearHover = gearHover;
    }

    public static GuiTheme current() {
        return THEMES[Math.clamp(current, 0, THEMES.length - 1)];
    }

    public static int currentIndex() {
        return Math.clamp(current, 0, THEMES.length - 1);
    }

    public static int count() {
        return THEMES.length;
    }

    public static String nameOf(int index) {
        return THEMES[Math.clamp(index, 0, THEMES.length - 1)].name;
    }

    public static void setCurrent(int index) {
        current = Math.clamp(index, 0, THEMES.length - 1);
    }
}
