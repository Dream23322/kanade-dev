package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import net.minecraft.core.Direction;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.hacks.features.exploit.Blink;
import com.instrumentalist.krs.hacks.features.exploit.disabler.DisablerModule;
import com.instrumentalist.krs.hacks.features.player.LookTP;
import com.instrumentalist.krs.hacks.features.player.MurdererDetector;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.hacks.features.exploit.disabler.features.HypixelDisabler;
import com.instrumentalist.krs.hacks.features.level.Breaker;
import com.instrumentalist.krs.hacks.features.level.CivBreak;
import com.instrumentalist.krs.hacks.features.level.Nuker;
import com.instrumentalist.krs.hacks.features.player.Stalker;
import com.instrumentalist.krs.utils.entity.StreamConverter;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NanoVGTextFormatter;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.screen.NanoVGClickGuiScreen;
import com.instrumentalist.krs.utils.render.HudEditor;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.*;
import com.instrumentalist.krs.utils.math.Tuple;
import com.instrumentalist.mixin.injector.ChatComponentAccessor;
import com.instrumentalist.mixin.injector.ChatScreenAccessor;
import com.instrumentalist.mixin.injector.CommandSuggestionsAccessor;
import com.instrumentalist.mixin.injector.SuggestionsListAccessor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import com.instrumentalist.krs.utils.input.Keys;
import org.lwjgl.nanovg.NanoVG;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.LinearGradientDirection;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.util.*;

public class Interface extends Module {
    private static final int MINIMAP_VISIBLE_RADIUS_BLOCKS = 24;
    private static final int MINIMAP_TEXTURE_MARGIN_BLOCKS = 10;
    private static final int MINIMAP_REBUILD_DRIFT_BLOCKS = 8;
    private static final int MINIMAP_TERRAIN_SAMPLES_PER_FRAME = 96;
    private static final int MINIMAP_TERRAIN_TEXTURE_SCALE = 4;
    private static final int MINIMAP_TERRAIN_BLEND_COLOR_DISTANCE = 52;
    private static final int MINIMAP_TERRAIN_BLEND_ALPHA_DISTANCE = 48;
    private static final long MINIMAP_TERRAIN_REFRESH_NANOS = 5_000_000_000L;
    private static final long PLAYER_LIST_REFRESH_NANOS = 250_000_000L;
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.0"));
    private static final ThreadLocal<DecimalFormat> TWO_DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.00"));
    private static final float SESSION_INFO_WIDTH = 270f;
    private static final float SESSION_INFO_HEIGHT = 80f;
    private static final float MODULE_LIST_FONT_SIZE = 11f;
    private static final float TAB_GUI_ROW_HEIGHT = 19f;
    private static final float TAB_GUI_FONT_SIZE = 12f;
    private static final float HUD_FONT_SIZE = 15f;
    private static final float CHAT_SUGGESTION_GAP = 4f;
    private static final float CHAT_INPUT_TOP_OFFSET = 14f;
    private static final float INFO_HUD_ICON_GAP = 4f;
    private static final Color MODULE_LIST_TEXT_NEUTRAL = new Color(190, 193, 203);
    private static final Color MODULE_LIST_EDGE_NEUTRAL = new Color(205, 209, 220);
    private static final float MODULE_LIST_HORIZONTAL_GRADIENT_SPAN = 220f;
    private static final float MODULE_LIST_ACCENT_WIDTH = 0.75f;
    private static final int TARGET_HUD_MAX_COLUMNS = 3;
    private static final float WATERMARK_FADE_LENGTH = 0.7f;
    private static final int WATERMARK_SOFT_WHITE = 232;
    private static final int FADE_LUT_SIZE = 2048;
    private static final int[] FADE_RGB = new int[FADE_LUT_SIZE];
    private static final Color[] FADE_OPAQUE_COLORS = new Color[FADE_LUT_SIZE];

    static {
        for (int i = 0; i < FADE_LUT_SIZE; i++) {
            int rgb = computeFadedRgb(i / (float) FADE_LUT_SIZE);
            FADE_RGB[i] = rgb;
            FADE_OPAQUE_COLORS[i] = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
        }
    }
    private static long cachedTimeSecond = -1L;
    private static String cachedTimeText = "00:00:00";

    @Override
    public String description() {
        return "HUD elements such as the watermark, module list and player list";
    }

    public Interface() {
        super("Interface", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    private static Color alphaColor(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.clamp(alpha, 0, 255));
    }

    private static int minimapTerrainTextureRangeBlocks() {
        return MINIMAP_VISIBLE_RADIUS_BLOCKS + MINIMAP_TEXTURE_MARGIN_BLOCKS;
    }

    private static int minimapTerrainResolution() {
        int range = minimapTerrainTextureRangeBlocks();
        return range * 2 + 1;
    }

    private static int minimapTerrainTextureBytes() {
        int resolution = minimapTerrainTextureResolution();
        return resolution * resolution * 4;
    }

    private static int minimapTerrainTextureResolution() {
        return minimapTerrainResolution() * MINIMAP_TERRAIN_TEXTURE_SCALE;
    }

    @Setting
    private static final BooleanValue waterMark = new BooleanValue(
            "Watermark",
            true
    ).describe("Shows the client watermark.");

    @Setting
    private final TextValue waterMarkText = new TextValue(
            "Watermark Component",
            "kanade",
            waterMark::get
    ).describe("Text shown in the watermark.");

    @Setting
    private static final ListValue waterMarkMode = new ListValue(
            "Watermark Mode",
            new String[]{"Simple", "Bullshit"},
            "Simple",
            waterMark::get
    ).describe("Style used to render the watermark.");

    @Setting
    private final BooleanValue moduleList = new BooleanValue(
            "Module List",
            true
    ).describe("Shows the list of enabled modules.");

    @Setting
    private static final BooleanValue tabGui = new BooleanValue(
            "TabGui",
            true
    ).describe("Shows the tab GUI in the top corner.");

    @Setting
    private final BooleanValue notifications = new BooleanValue(
            "Notifications",
            true
    ).describe("Shows client notifications.");

    @Setting
    public static final ListValue notificationCorner = new ListValue(
            "Notification Corner",
            new String[]{"Top Right", "Bottom Right", "Top Left", "Bottom Left"},
            "Top Right"
    ).describe("Screen corner the notifications stack from.");

    @Setting
    public static final FloatValue notificationWidth = new FloatValue(
            "Notification Width",
            190f,
            120f,
            280f,
            "px"
    ).describe("Maximum width of a notification card.");

    @Setting
    public static final IntValue notificationDuration = new IntValue(
            "Notification Duration",
            6000,
            2000,
            12000,
            "ms"
    ).describe("How long a notification stays before fading out.");

    @Setting
    public static final IntValue notificationMax = new IntValue(
            "Notification Max",
            5,
            1,
            10
    ).describe("How many notifications are shown at once.");

    @Setting
    public static final BooleanValue notificationProgress = new BooleanValue(
            "Notification Progress",
            true
    ).describe("Draws a remaining time bar on each notification.");

    @Setting
    public static final BooleanValue notificationIcons = new BooleanValue(
            "Notification Icons",
            true
    ).describe("Shows a severity icon on each notification.");

    @Setting
    private static final BooleanValue scaffoldBlocks = new BooleanValue(
            "Scaffold Blocks",
            true
    ).describe("Shows how many blocks scaffold has left.");

    @Setting
    private static final BooleanValue miniMap = new BooleanValue(
            "Minimap",
            true
    ).describe("Shows the minimap.");

    @Setting
    private static final BooleanValue sessionInfo = new BooleanValue(
            "Session Info",
            true
    ).describe("Shows server and session details.");

    @Setting
    private static final BooleanValue playerList = new BooleanValue(
            "Players",
            true
    ).describe("Shows the nearby player list.");

    @Setting
    private final BooleanValue moduleInformation = new BooleanValue(
            "Module Information",
            true
    ).describe("Shows extra information under the module list.");

    @Setting
    public static final BooleanValue someInformation = new BooleanValue(
            "Some Information",
            true
    ).describe("Shows the FPS, ping and coordinate line.");

    @Setting
    public static final FloatValue shadowStrength = new FloatValue(
            "Shadow Strength",
            1f,
            0f,
            3f,
            "x"
    ).describe("Strength of the drop shadow behind HUD text.");

    @Setting
    private static final ListValue theme = new ListValue(
            "Theme",
            NanoVGTheme.Theme.labels(),
            NanoVGTheme.Theme.KANADE.label()
    ).describe("Accent colour preset. Pick Custom to keep the colour pickers below.");

    @Setting
    private static final ColorValue themeAccent = new ColorValue(
            "Accent Colour",
            NanoVGTheme.DEFAULT_ACCENT
    ).describe("Primary accent colour used across the HUD and click GUI.");

    @Setting
    private static final ColorValue themeAccentEnd = new ColorValue(
            "Accent Gradient End",
            NanoVGTheme.DEFAULT_ACCENT_END
    ).describe("Second accent colour used for gradients.");

    @Setting
    private static final ColorValue themeBackground = new ColorValue(
            "Theme Background",
            NanoVGTheme.DEFAULT_BACKGROUND
    ).describe("Panel and row background colour used across the HUD and click GUI.");

    @Setting
    private static final ColorValue themeText = new ColorValue(
            "Theme Text",
            NanoVGTheme.DEFAULT_TEXT
    ).describe("Primary text colour used across the HUD and click GUI.");

    @Setting
    private static final BooleanValue moduleListOutline = new BooleanValue(
            "Module List Outline",
            true
    ).describe("Draws a stepped outline around the module rows.");

    @Setting
    private static final BooleanValue moduleListBackground = new BooleanValue(
            "Module List Background",
            true
    ).describe("Draws a background behind each module list row.");

    @Setting
    private static final BooleanValue moduleListAccentBar = new BooleanValue(
            "Module List Accent Bar",
            false
    ).describe("Draws the accent strip on each module list row.");

    @Setting
    private static final BooleanValue moduleListTags = new BooleanValue(
            "Module List Tags",
            true
    ).describe("Shows module tags after the module name.");

    @Setting
    private static final BooleanValue moduleListShadow = new BooleanValue(
            "Module List Shadow",
            true
    ).describe("Draws a drop shadow under module list text.");

    @Setting
    private static final BooleanValue moduleListGradient = new BooleanValue(
            "Module List Gradient",
            false
    ).describe("Blends the theme accent across each module list row.");

    @Setting
    private static final FloatValue moduleListSpacing = new FloatValue(
            "Module List Spacing",
            0f,
            0f,
            4f,
            "x"
    ).describe("Extra vertical padding on each module list row.");

    @Setting
    private static final FloatValue moduleListPadding = new FloatValue(
            "Module List Padding",
            2f,
            0f,
            16f,
            "px"
    ).describe("Horizontal space between the module list border and its text.");

    public static List<Module> sortedModules;

    private static String appliedThemeName;
    private static int appliedThemeColours = Integer.MIN_VALUE;

    /** Pushes the selected theme preset or custom accent pickers into the shared NanoVG palette. */
    public static void applyThemeFromSettings() {
        Interface module = ModuleManager.getModule(Interface.class);
        if (module == null) return;

        NanoVGTheme.Theme preset = NanoVGTheme.Theme.byName(theme.get());
        if (!preset.label().equals(appliedThemeName)) {
            appliedThemeName = preset.label();
            if (!preset.custom()) {
                themeAccent.set(preset.start());
                themeAccentEnd.set(preset.end());
                themeBackground.set(preset.background());
                themeText.set(preset.text());
            }
        }

        Color start = themeAccent.get();
        Color end = themeAccentEnd.get();
        Color background = themeBackground.get();
        Color text = themeText.get();
        if (start == null) start = NanoVGTheme.DEFAULT_ACCENT;
        if (end == null) end = NanoVGTheme.DEFAULT_ACCENT_END;
        if (background == null) background = NanoVGTheme.DEFAULT_BACKGROUND;
        if (text == null) text = NanoVGTheme.DEFAULT_TEXT;

        int key = (start.getRGB() * 31) ^ (end.getRGB() * 17) ^ (background.getRGB() * 7) ^ text.getRGB();
        if (key == appliedThemeColours) return;
        appliedThemeColours = key;
        NanoVGTheme.setTheme(start, end, background, text);
    }

    public static void migrateBranding() {
        Interface module = ModuleManager.getModule(Interface.class);
        if (module == null)
            return;

        String watermark = module.waterMarkText.get();
        if (watermark != null && watermark.trim().equalsIgnoreCase("krs"))
            module.waterMarkText.set("kanade");
    }

    private static volatile boolean sortedModulesDirty = true;
    private static final int INFO_HYPIXEL_DISABLER = 0;
    private static final int INFO_LOOK_TP = 1;
    private static final int INFO_BREAKING = 2;
    private static final int INFO_BLINKING = 3;
    private static final int INFO_SCAFFOLD_BLOCKS = 4;
    private static final int INFO_MURDERER_COUNT = 5;
    private static final float[] moduleInformationFadeProgress = new float[6];
    private static long hudFrameCounter;
    private static long playerListCacheFrame = -1L;
    private static long playerListNextRefreshNanos;
    private static boolean playerListCacheInitialized;
    private static List<PlayerInfo> cachedPlayerListEntries = Collections.emptyList();
    private static final ArrayList<PlayerInfo> playerListEntryBuffer = new ArrayList<>();
    private static final ArrayList<PlayerListRow> playerListRowBuffer = new ArrayList<>(10);
    private static float cachedPlayerListHeight;
    private static float playerListDisplayHeight = Float.NaN;
    private static long sessionStartedAtMillis;
    private static int sessionKills;
    private static int sessionDeaths;
    private static boolean sessionLocalWasDead;
    private static final Set<UUID> sessionCountedKills = new HashSet<>();
    private long lastRenderNanos;

    private static final List<TargetHudData> data = new ArrayList<>();
    private static final List<TargetHudRenderEntry> targetHudEntryBuffer = new ArrayList<>(4);
    private static final ArrayList<TargetHudRenderEntry> targetHudFrontToBackBuffer = new ArrayList<>(4);
    private static final ArrayList<TargetHudRenderEntry> targetHudVisibleEntryBuffer = new ArrayList<>(4);
    private static final float[] targetHudColumnWidths = new float[TARGET_HUD_MAX_COLUMNS];
    private final List<ModuleListEntry> moduleListEntryBuffer = new ArrayList<>();
    private final Map<Module, ModuleListEntry> moduleListEntryCache = new HashMap<>();
    private final Map<Module, Float> moduleListSortWidthCache = new HashMap<>();
    private final List<Module> moduleListChangedSortKeys = new ArrayList<>();
    private final List<ModuleListRenderEntry> moduleListRenderEntryBuffer = new ArrayList<>();
    private final List<ModuleListRenderEntry> moduleListRenderEntryPool = new ArrayList<>();
    private float[] moduleListBackgroundGeometry = new float[32 * 8];
    private static final List<Entity> targetBuffer = new ArrayList<>(4);
    private static final Set<UUID> targetUuidBuffer = new HashSet<>();
    private final Map<UUID, MinimapPlayerDotState> minimapPlayerDots = new HashMap<>();
    private final Set<UUID> minimapPlayerUuidBuffer = new HashSet<>();
    private final ByteBuffer minimapTerrainBuffer = BufferUtils.createByteBuffer(minimapTerrainTextureBytes());
    private int[] minimapTerrainColors = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private int[] minimapTerrainBuildColors = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private final int[] minimapTerrainDirtyIndices = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private final BlockPos.MutableBlockPos minimapTerrainSamplePos = new BlockPos.MutableBlockPos();
    private int minimapTerrainCenterBlockX = Integer.MIN_VALUE;
    private int minimapTerrainCenterBlockZ = Integer.MIN_VALUE;
    private int minimapTerrainPlayerBlockY = Integer.MIN_VALUE;
    private long minimapTerrainLastUpdateNanos;
    private boolean minimapTerrainDirty = true;
    private boolean minimapTerrainDataReady;
    private boolean minimapTerrainTextureReady;
    private boolean minimapTerrainBuildInProgress;
    private int minimapTerrainBuildCenterBlockX;
    private int minimapTerrainBuildCenterBlockZ;
    private int minimapTerrainBuildPlayerBlockY;
    private int minimapTerrainDirtyCount;
    private int minimapTerrainDirtyCursor;
    private boolean minimapViewInitialized;
    private double minimapViewX;
    private double minimapViewZ;
    private final List<StyledTextRenderEntry> moduleInformationEntryBuffer = new ArrayList<>();
    private final List<ModuleCategory> tabGuiCategoryBuffer = new ArrayList<>();
    private final List<Module> tabGuiModuleBuffer = new ArrayList<>();
    private final ArrayList<InfoHudEntry> informationEntryBuffer = new ArrayList<>(2);
    private final ArrayList<PotionHudEntry> potionEntryBuffer = new ArrayList<>();
    private final ArrayList<InfoHudEntry> connectionEntryBuffer = new ArrayList<>(3);
    private final SomeInformationRenderState someInformationRenderState = new SomeInformationRenderState(
            informationEntryBuffer, potionEntryBuffer, connectionEntryBuffer
    );
    private int tabGuiCategoryIndex;
    private int tabGuiModuleIndex;
    private boolean tabGuiExpanded;
    private float tabGuiExpandProgress;
    private float tabGuiModuleSelectionY = Float.NaN;

    private static class ModuleListEntry {
        String text;
        String tagText;
        float progress;
        float textWidth;
        float fullWidth;

        void update(String text, String tagText, float progress, float fontSize) {
            if (!Objects.equals(this.text, text) || !Objects.equals(this.tagText, tagText)) {
                float textWidth = NVGFonts.INTER.getWidth(text, fontSize);
                float tagWidth = tagText != null ? NVGFonts.INTER.getWidth(tagText, fontSize) : 0f;
                this.textWidth = textWidth;
                this.fullWidth = textWidth + tagWidth;
            }
            this.text = text;
            this.tagText = tagText;
            this.progress = progress;
        }
    }

    private interface ConnectedHudRect extends NanoVGTheme.ConnectedGlassRect {
        float x();

        float y();

        float width();

        float height();

        default float right() {
            return x() + width();
        }
    }

    private static class ModuleListRenderEntry implements ConnectedHudRect {
        ModuleListEntry entry;
        float x;
        float y;
        float width;
        float height;
        float textX;
        float textY;
        int alpha;
        int colorIndex;
        int colorCount;
        long colorTimeMillis;
        float topLeftRadius;
        float topRightRadius;
        float bottomRightRadius;
        float bottomLeftRadius;

        void update(ModuleListEntry entry, float x, float y, float width, float height,
                    float textX, float textY, int alpha, int colorIndex, int colorCount, long colorTimeMillis) {
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.textX = textX;
            this.textY = textY;
            this.alpha = alpha;
            this.colorIndex = colorIndex;
            this.colorCount = colorCount;
            this.colorTimeMillis = colorTimeMillis;
        }

        void updateCornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeftRadius = topLeft;
            this.topRightRadius = topRight;
            this.bottomRightRadius = bottomRight;
            this.bottomLeftRadius = bottomLeft;
        }

        @Override
        public boolean hasExplicitCorners() {
            return true;
        }

        @Override
        public float topLeftRadius() {
            return topLeftRadius;
        }

        @Override
        public float topRightRadius() {
            return topRightRadius;
        }

        @Override
        public float bottomRightRadius() {
            return bottomRightRadius;
        }

        @Override
        public float bottomLeftRadius() {
            return bottomLeftRadius;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class StyledTextRenderEntry {
        final String text;
        final float x;
        final float y;
        final float alpha;

        StyledTextRenderEntry(String text, float x, float y, float alpha) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.alpha = alpha;
        }
    }

    private static class SomeInformationRenderState {
        final List<InfoHudEntry> informationEntries;
        final List<PotionHudEntry> potionEntries;
        final List<InfoHudEntry> connectionEntries;

        SomeInformationRenderState(List<InfoHudEntry> informationEntries, List<PotionHudEntry> potionEntries, List<InfoHudEntry> connectionEntries) {
            this.informationEntries = informationEntries;
            this.potionEntries = potionEntries;
            this.connectionEntries = connectionEntries;
        }
    }

    private static class PotionHudEntry implements ConnectedHudRect {
        final String text;
        final float rectX;
        final float rectY;
        final float width;
        final float height;
        final float textX;
        final float textY;

        PotionHudEntry(String text, float rectX, float rectY, float width, float height, float textX, float textY) {
            this.text = text;
            this.rectX = rectX;
            this.rectY = rectY;
            this.width = width;
            this.height = height;
            this.textX = textX;
            this.textY = textY;
        }

        public float x() {
            return rectX;
        }

        public float y() {
            return rectY;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class InfoHudEntry implements ConnectedHudRect {
        final String text;
        final @Nullable String icon;
        final float iconWidth;
        final float width;
        final float height;
        float x;
        float y;

        InfoHudEntry(String text, @Nullable String icon) {
            this.text = text;
            this.icon = icon;
            this.iconWidth = icon != null ? NVGFonts.ICON.getWidth(icon, HUD_FONT_SIZE) + INFO_HUD_ICON_GAP : 0f;
            this.width = NVGFonts.INTER.getWidth(text, HUD_FONT_SIZE) + iconWidth + 16f;
            this.height = NVGFonts.INTER.getHeight(HUD_FONT_SIZE) + 8f;
        }

        public float right() {
            return x + width;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class MinimapPlayerDotState {
        double x;
        double z;

        MinimapPlayerDotState(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private static class ModuleSortEntry {
        final Module module;
        final float width;

        ModuleSortEntry(Module module, float width) {
            this.module = module;
            this.width = width;
        }
    }

    public static Color getFadedColor(int index, int totalModules) {
        return getFadedColor(index, totalModules, 255, System.currentTimeMillis(), 0f);
    }

    private static Color getFadedColor(int index, int totalModules, int alpha, long currentTimeMillis, float phaseOffset) {
        float progress = totalModules <= 1
                ? phaseOffset
                : (float) index / Math.max(1, totalModules) * 0.55f + phaseOffset * 0.45f;
        progress -= (float) Math.floor(progress);

        int lutIndex = (int) (progress * FADE_LUT_SIZE + 0.5f) & (FADE_LUT_SIZE - 1);
        int rgb = FADE_RGB[lutIndex];
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.clamp(alpha, 0, 255));
    }

    private static int computeFadedRgb(float progress) {
        return NanoVGTheme.accentMix(progress, 255).getRGB() & 0xFFFFFF;
    }

    private static String getCachedTimeText() {
        long second = System.currentTimeMillis() / 1000L;
        if (second != cachedTimeSecond) {
            cachedTimeSecond = second;
            cachedTimeText = formatTime(LocalTime.now());
        }
        return cachedTimeText;
    }

    private static String formatTime(LocalTime time) {
        char[] text = new char[8];
        twoDigits(text, 0, time.getHour());
        text[2] = ':';
        twoDigits(text, 3, time.getMinute());
        text[5] = ':';
        twoDigits(text, 6, time.getSecond());
        return new String(text);
    }

    private static void twoDigits(char[] text, int offset, int value) {
        text[offset] = (char) ('0' + value / 10);
        text[offset + 1] = (char) ('0' + value % 10);
    }

    private static String formatOneDecimal(DecimalFormat formatter, double value) {
        return formatter.format(value);
    }

    private static String formatDurationTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "(" + minutes + ":" + (seconds < 10 ? "0" : "") + seconds + ")";
    }

    private static String formatPositionText(double x, double y, double z, double netherX, double netherZ, String direction) {
        return String.format(Locale.ROOT, "%.3f, %.5f, %.3f | %.3f, %.3f (%s)", x, y, z, netherX, netherZ, direction);
    }

    // The reference places the session card beside the category menu, not below it.
    private static float getLeftHudTopY() {
        float top = getTabGuiBaseY();
        float categoryHeight = tabGui.get() ? getTabGuiCategoryPanelHeight() : 0f;
        float sessionHeight = getSessionInfoHeight();
        float occupiedHeight = Math.max(categoryHeight, sessionHeight);
        return top + occupiedHeight + (occupiedHeight > 0f ? 6f : 0f);
    }

    private static float getTabGuiBaseY() {
        if (!waterMark.get())
            return 4f;
        return waterMarkMode.get().equalsIgnoreCase("bullshit") ? 42f : 29f;
    }

    private static float getLeftHudX() {
        return 4f;
    }

    private static float getSessionInfoX() {
        if (!tabGui.get() || getTabGuiCategoryCount() == 0)
            return getLeftHudX();
        float width = 120f;
        for (ModuleCategory category : ModuleCategory.DISPLAY_ORDER) {
            if (category != ModuleCategory.Dev && hasTabGuiModules(category))
                width = Math.max(width, NVGFonts.INTER_MEDIUM.getWidth(category.label(), TAB_GUI_FONT_SIZE) + 12f);
        }
        return getLeftHudX() + width + 6f;
    }

    private static float getTabGuiCategoryPanelHeight() {
        int categoryCount = getTabGuiCategoryCount();
        return categoryCount <= 0 ? 0f : getTabGuiCategoryListHeight(categoryCount);
    }

    private static float getTabGuiCategoryListHeight(int rowCount) {
        return rowCount <= 0 ? 0f : rowCount * TAB_GUI_ROW_HEIGHT;
    }

    private static float getTabGuiPanelHeight(int rowCount) {
        return getTabGuiCategoryListHeight(rowCount);
    }

    private static int getTabGuiCategoryCount() {
        int count = 0;
        for (ModuleCategory category : ModuleCategory.DISPLAY_ORDER) {
            if (category != ModuleCategory.Dev && hasTabGuiModules(category))
                count++;
        }
        return count;
    }

    private static boolean hasTabGuiModules(ModuleCategory category) {
        for (Module module : ModuleManager.modules) {
            if (module.moduleCategory == category)
                return true;
        }
        return false;
    }

    private static List<PlayerInfo> getPlayerListEntries() {
        if (playerListCacheFrame == hudFrameCounter)
            return cachedPlayerListEntries;

        playerListCacheFrame = hudFrameCounter;

        if (!playerList.get() || mc.getConnection() == null)
            return clearPlayerListEntries();

        long now = System.nanoTime();
        if (playerListCacheInitialized && now < playerListNextRefreshNanos)
            return cachedPlayerListEntries;

        playerListCacheInitialized = true;
        playerListNextRefreshNanos = now + PLAYER_LIST_REFRESH_NANOS;
        cachedPlayerListHeight = 0f;

        ArrayList<PlayerInfo> players = playerListEntryBuffer;
        players.clear();
        players.addAll(mc.getConnection().getListedOnlinePlayers());
        players.sort((first, second) -> {
            boolean firstSelf = isSelfPlayerListEntry(first);
            boolean secondSelf = isSelfPlayerListEntry(second);
            if (firstSelf != secondSelf)
                return firstSelf ? -1 : 1;

            return getPlayerListPlainName(first).compareToIgnoreCase(getPlayerListPlainName(second));
        });
        cachedPlayerListEntries = players;
        preparePlayerListRows(players);
        return cachedPlayerListEntries;
    }

    private static final float PLAYER_LIST_WIDTH = 280f;
    private static final float PLAYER_LIST_HEADER_HEIGHT = 22f;
    private static final float PLAYER_LIST_ROW_HEIGHT = 18f;
    private static final int PLAYER_LIST_MAX_ROWS = 13;
    private static final Map<UUID, PlayerListFaceState> playerListFaces = new HashMap<>();

    private static final class PlayerListFaceState {
        InputStream pending;
        boolean requested;
        boolean failed;
        long generation;
    }

    private static final class PlayerListRow {
        final UUID uuid;
        final String name;
        final String hpText;
        final String distanceText;
        final boolean self;

        private PlayerListRow(UUID uuid, String name, String hpText, String distanceText, boolean self) {
            this.uuid = uuid;
            this.name = name;
            this.hpText = hpText;
            this.distanceText = distanceText;
            this.self = self;
        }
    }

    private static List<PlayerInfo> clearPlayerListEntries() {
        playerListEntryBuffer.clear();
        playerListRowBuffer.clear();
        cachedPlayerListEntries = Collections.emptyList();
        playerListCacheInitialized = false;
        playerListNextRefreshNanos = 0L;
        cachedPlayerListHeight = 0f;
        playerListDisplayHeight = Float.NaN;
        return cachedPlayerListEntries;
    }

    private static float getPlayerListTargetHeight() {
        getPlayerListEntries();
        if (playerListRowBuffer.isEmpty())
            return 0f;
        return cachedPlayerListHeight;
    }

    private static float getPlayerListHeight() {
        if (!Float.isNaN(playerListDisplayHeight))
            return playerListDisplayHeight > 0.01f ? playerListDisplayHeight : 0f;
        return getPlayerListTargetHeight();
    }

    private void updatePlayerListHeightAnimation(float deltaSpeed) {
        float target = getPlayerListTargetHeight();
        if (Float.isNaN(playerListDisplayHeight)) {
            playerListDisplayHeight = target;
            return;
        }
        playerListDisplayHeight = animateTabGuiValue(playerListDisplayHeight, target, deltaSpeed);
        if (Math.abs(playerListDisplayHeight - target) < 0.25f)
            playerListDisplayHeight = target;
    }

    private static void preparePlayerListRows(List<PlayerInfo> players) {
        playerListRowBuffer.clear();
        int visiblePlayers = Math.min(players.size(), PLAYER_LIST_MAX_ROWS);
        float nameWidth = PLAYER_LIST_WIDTH - 118f;
        for (int i = 0; i < visiblePlayers; i++) {
            PlayerInfo entry = players.get(i);
            String name = trimToWidth(getPlayerListName(entry), 12f, nameWidth);
            playerListRowBuffer.add(new PlayerListRow(
                    entry.getProfile().id(),
                    name,
                    getPlayerListHealthText(entry),
                    getPlayerListDistanceText(entry),
                    isSelfPlayerListEntry(entry)
            ));
        }
        cachedPlayerListHeight = PLAYER_LIST_HEADER_HEIGHT + visiblePlayers * PLAYER_LIST_ROW_HEIGHT;
    }

    private static String getPlayerListDistanceText(PlayerInfo entry) {
        Player player = findWorldPlayer(entry.getProfile().id());
        if (player == null)
            return "—";
        if (player == mc.player)
            return "0m";
        return Math.round(mc.player.distanceTo(player)) + "m";
    }

    private static String getPlayerListHealthText(PlayerInfo entry) {
        Player player = findWorldPlayer(entry.getProfile().id());
        if (player == null)
            return "—";

        float maxHealth = player.getMaxHealth();
        if (!(maxHealth > 0f))
            return "—";

        float ratio = Math.clamp(player.getHealth(), 0f, maxHealth) / maxHealth;
        return Math.round(ratio * 100f) + "%";
    }

    private static Player findWorldPlayer(UUID uuid) {
        if (mc.level == null || uuid == null)
            return null;

        for (Player player : mc.level.players()) {
            if (uuid.equals(player.getUUID()))
                return player;
        }
        return null;
    }

    private static String trimToWidth(String text, float size, float maxWidth) {
        if (text == null)
            return "";
        if (maxWidth <= 0f || NVGFonts.INTER.getWidth(text, size) <= maxWidth)
            return text;

        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && NVGFonts.INTER.getWidth(text.substring(0, end) + ellipsis, size) > maxWidth)
            end--;
        return text.substring(0, end) + ellipsis;
    }

    private static String formatSessionDuration(long startedAtMillis) {
        if (startedAtMillis <= 0L)
            return "0s";

        long seconds = Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1000L);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0L)
            return hours + "h " + String.format(Locale.ROOT, "%02dm", minutes);
        if (minutes > 0L)
            return minutes + "m " + String.format(Locale.ROOT, "%02ds", remainingSeconds);
        return remainingSeconds + "s";
    }

    private static boolean ensurePlayerListFace(NVGU vg, UUID uuid, Player player) {
        String identifier = playerListFaceIdentifier(uuid);
        if (vg.hasTexture(identifier))
            return true;

        PlayerListFaceState state = playerListFaces.computeIfAbsent(uuid, ignored -> new PlayerListFaceState());
        if (state.failed)
            return false;

        if (state.pending != null) {
            try {
                vg.createTexture(identifier, state.pending);
            } catch (RuntimeException ignored) {
            } finally {
                closeInputStream(state.pending);
                state.pending = null;
            }
            if (vg.hasTexture(identifier))
                return true;

            state.failed = true;
            return false;
        }

        if (!state.requested && player != null) {
            state.requested = true;
            long generation = ++state.generation;
            StreamConverter.getPlayerFaceAsInputStream(player, inputStream -> {
                if (state.generation != generation) {
                    closeInputStream(inputStream);
                    return;
                }
                if (inputStream == null)
                    state.failed = true;
                else
                    state.pending = inputStream;
            });
        }
        return false;
    }

    private static String playerListFaceIdentifier(UUID uuid) {
        return "listface_" + uuid.toString().toLowerCase(Locale.ROOT);
    }

    private static void clearPlayerListFaces() {
        for (PlayerListFaceState state : playerListFaces.values())
            closeInputStream(state.pending);
        playerListFaces.clear();
    }

    private static void beginHudFrame() {
        hudFrameCounter++;
        HudEditor.beginFrame();
        updateSessionStats();
    }

    private static void updateSessionStats() {
        if (mc.player == null || mc.level == null)
            return;

        boolean dead = mc.player.isDeadOrDying() || mc.player.getHealth() <= 0f;
        if (dead && !sessionLocalWasDead)
            sessionDeaths++;
        sessionLocalWasDead = dead;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isDeadOrDying())
                continue;
            if (player.getLastHurtByMob() == mc.player && sessionCountedKills.add(player.getUUID()))
                sessionKills++;
        }
    }

    private static float getSessionInfoHeight() {
        return sessionInfo.get() ? SESSION_INFO_HEIGHT : 0f;
    }

    private static float getPlayerListTopY() {
        return getLeftHudTopY();
    }

    private static float getMinimapY() {
        float y = getPlayerListTopY();
        float playerListHeight = getPlayerListHeight();
        if (playerListHeight > 0f)
            y += playerListHeight + 8f;
        return y;
    }

    private static float getInformationBaseY() {
        float y = getPlayerListTopY();
        float playerListHeight = getPlayerListHeight();
        if (playerListHeight > 0f)
            y += playerListHeight + 8f;

        if (miniMap.get())
            y += 150f + 20f;
        else
            y += 6f;

        return y;
    }

    private SomeInformationRenderState prepareSomeInformation() {
        float baseY = getInformationBaseY();
        float infoLeftX = getLeftHudX();

        boolean freecam = ModuleManager.getModuleState(Freecam.class) && Freecam.getCanFly();
        Entity cameraEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        Vec3 debugPosition = freecam
                ? Freecam.getCamPos(TimerUtil.selfPartialTick(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)))
                : cameraEntity.position();
        Direction debugDirection = freecam ? Direction.fromYRot(Freecam.getCamYaw()) : cameraEntity.getDirection();
        double x = debugPosition.x;
        double y = debugPosition.y;
        double z = debugPosition.z;
        boolean inNether = mc.level.dimension() == Level.NETHER;
        double netherX = inNether ? x * 8 : x / 8;
        double netherZ = inNether ? z * 8 : z / 8;
        String positionText = formatPositionText(x, y, z, netherX, netherZ, debugDirection.getSerializedName().toUpperCase(Locale.ROOT));
        List<InfoHudEntry> informationEntries = informationEntryBuffer;
        informationEntries.clear();
        informationEntries.add(new InfoHudEntry(getCachedTimeText(), MaterialIcon.CLOCK));
        informationEntries.add(new InfoHudEntry(positionText, MaterialIcon.CORDS));
        layoutConnectedInfoHudChips(informationEntries, infoLeftX, baseY + informationEntries.get(0).height);

        Map<Holder<MobEffect>, MobEffectInstance> activeEffects = mc.player.getActiveEffectsMap();
        List<PotionHudEntry> potionEntries = potionEntryBuffer;
        potionEntries.clear();
        potionEntryBuffer.ensureCapacity(activeEffects.size());

        if (!activeEffects.isEmpty()) {
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float yOffset = NanoVGManager.getScaledScreenHeight() - 20f;

            for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : activeEffects.entrySet()) {
                MobEffect effect = entry.getKey().value();
                MobEffectInstance instance = entry.getValue();
                var effectKey = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (effectKey == null) continue;
                String effectName = StreamConverter.formatNameByPath(effectKey.getPath());

                String durationText = instance.isInfiniteDuration() ? "(\u221e)" : formatDurationTicks(instance.getDuration());
                String effectText = effectName + " " + getRomanNumeral(instance.getAmplifier() + 1) + " " + durationText;

                float textWidth = NVGFonts.INTER.getWidth(effectText, HUD_FONT_SIZE);
                float fontHeight = NVGFonts.INTER.getHeight(HUD_FONT_SIZE);
                float width = textWidth + 16f;
                float height = fontHeight + 8f;
                float rectX = screenWidth - 10f - width;
                float rectY = yOffset - height;
                float textX = rectX + width - 8f;
                float textY = rectY + 4f;

                potionEntries.add(new PotionHudEntry(effectText, rectX, rectY, width, height, textX, textY));

                yOffset -= height;
            }

            if (!potionEntries.isEmpty()) {
                potionEntries.sort(Comparator.comparingDouble(PotionHudEntry::y));
            }
        }

        String connectionType;
        int ping = -1;
        String serverIp = "Unknown";

        if (mc.isLocalServer()) {
            connectionType = "Singleplayer";
        } else if (mc.getCurrentServer() != null) {
            connectionType = "Multiplayer";
            serverIp = mc.getCurrentServer().ip;
        } else {
            connectionType = "Integrated Server";
        }

        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null) {
                ping = entry.getLatency();
            }
        }

        float leftX = 10f;
        float windowHeight = NanoVGManager.getScaledScreenHeight();
        float startY = windowHeight - 30f - getChatScreenOffset();
        List<InfoHudEntry> connectionEntries = connectionEntryBuffer;
        connectionEntries.clear();

        connectionEntries.add(new InfoHudEntry(connectionType, MaterialIcon.PUBLIC));

        if (!connectionType.equals("Singleplayer")) {
            String pingText = (ping >= 0) ? ping + "ms" : "Unknown";
            connectionEntries.add(new InfoHudEntry(serverIp, MaterialIcon.LAN));
            connectionEntries.add(new InfoHudEntry(pingText, MaterialIcon.SIGNAL));
        }

        startY = Math.min(startY, getChatSuggestionTop() - CHAT_SUGGESTION_GAP - connectionEntries.getFirst().height);
        layoutConnectedInfoHudChips(connectionEntries, leftX, startY);

        float offsetX = HudEditor.offsetX(HudEditor.Element.SOME_INFO);
        float offsetY = HudEditor.offsetY(HudEditor.Element.SOME_INFO);
        shiftInfoEntries(informationEntries, offsetX, offsetY);
        shiftInfoEntries(connectionEntries, offsetX, offsetY);
        registerInfoBounds(informationEntries, connectionEntries);

        return someInformationRenderState;
    }

    private static void shiftInfoEntries(List<InfoHudEntry> entries, float offsetX, float offsetY) {
        for (InfoHudEntry entry : entries) {
            entry.x += offsetX;
            entry.y += offsetY;
        }
    }

    private static void registerInfoBounds(List<InfoHudEntry> informationEntries, List<InfoHudEntry> connectionEntries) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (List<InfoHudEntry> entries : List.of(informationEntries, connectionEntries)) {
            for (InfoHudEntry entry : entries) {
                minX = Math.min(minX, entry.x);
                minY = Math.min(minY, entry.y);
                maxX = Math.max(maxX, entry.x + entry.width);
                maxY = Math.max(maxY, entry.y + entry.height);
            }
        }

        if (Float.isFinite(minX) && Float.isFinite(minY))
            HudEditor.register(HudEditor.Element.SOME_INFO, minX, minY, maxX - minX, maxY - minY);
    }

    private void renderSomeInformation(NVGU vg) {
        SomeInformationRenderState state = prepareSomeInformation();
        vg.beginEffectBatch();
        renderSomeInformationEffects(vg, state);
        vg.flushEffectBatch();
        renderSomeInformationBody(vg, state);
    }

    private static void renderSomeInformationEffects(NVGU vg, SomeInformationRenderState state) {
        // Flat HUD chips: no per-frame backdrop capture or blur pass.
    }

    private static void renderSomeInformationBody(NVGU vg, SomeInformationRenderState state) {
        renderConnectedInfoHudChipBodies(vg, state.informationEntries, 1f);
        renderPotionHudBodies(vg, state.potionEntries);
        renderConnectedInfoHudChipBodies(vg, state.connectionEntries, 1f);
    }

    private static void renderConnectedSecondStyledTextWithIcons(NVGU vg, List<InfoHudEntry> entries, float x, float bottomY, float alpha) {
        if (entries.isEmpty()) return;

        layoutConnectedInfoHudChips(entries, x, bottomY);
        vg.beginEffectBatch();
        renderConnectedInfoHudChipEffects(vg, entries, alpha);
        vg.flushEffectBatch();
        renderConnectedInfoHudChipBodies(vg, entries, alpha);
    }

    private static void layoutConnectedInfoHudChips(List<InfoHudEntry> entries, float x, float bottomY) {
        if (entries.isEmpty()) return;

        float rowHeight = entries.get(0).height;
        float topY = bottomY - rowHeight * (entries.size() - 1);
        for (int i = 0, n = entries.size(); i < n; i++) {
            InfoHudEntry entry = entries.get(i);
            entry.x = x;
            entry.y = topY + rowHeight * i;
        }
    }

    private static void renderConnectedInfoHudChipEffects(NVGU vg, List<InfoHudEntry> entries, float alpha) {
        // Flat chips: no blur pass.
    }

    private static void renderConnectedInfoHudChipBodies(NVGU vg, List<InfoHudEntry> entries, float alpha) {
        NanoVGTheme.renderConnected(vg, entries, NanoVGTheme.RADIUS_HUD, alpha);

        for (InfoHudEntry entry : entries) {
            renderInfoHudChipText(entry);
        }
    }

    private static void renderPotionHudBodies(NVGU vg, List<PotionHudEntry> potionEntries) {
        NanoVGTheme.renderConnected(vg, potionEntries, NanoVGTheme.RADIUS_HUD, 1f);
        for (int i = 0, n = potionEntries.size(); i < n; i++) {
            PotionHudEntry entry = potionEntries.get(i);
            NVGFonts.INTER.drawText(entry.text, entry.textX, entry.textY, HUD_FONT_SIZE, NanoVGTheme.mode(228), Alignment.RIGHT_TOP, true);
        }
    }

    private static float getExposedHudCornerRadius(@Nullable ConnectedHudRect neighbor, float cornerX, float maxRadius) {
        if (neighbor == null)
            return maxRadius;
        if (cornerX >= neighbor.x() - 0.5f && cornerX <= neighbor.right() + 0.5f)
            return 0f;

        float exposedWidth = cornerX < neighbor.x()
                ? neighbor.x() - cornerX
                : cornerX - neighbor.right();
        return Math.min(maxRadius, Math.max(0f, exposedWidth));
    }

    private static void renderInfoHudChipText(InfoHudEntry entry) {
        if (entry.icon != null)
            NVGFonts.ICON.drawText(entry.icon, entry.x + 8f, entry.y + 3f, HUD_FONT_SIZE, NanoVGTheme.ACCENT, Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText(entry.text, entry.x + 8f + entry.iconWidth, entry.y + 4f, HUD_FONT_SIZE, NanoVGTheme.text(236), Alignment.LEFT_TOP, true);
    }

    private static float getChatSuggestionTop() {
        if (!(mc.gui.screen() instanceof ChatScreen chatScreen))
            return Float.MAX_VALUE;

        CommandSuggestions suggestions = ((ChatScreenAccessor) chatScreen).krs$getCommandSuggestions();
        if (suggestions == null)
            return Float.MAX_VALUE;

        float guiToNanoVgScale = NanoVGManager.getScaledScreenHeight() / Math.max(1f, mc.getWindow().getGuiScaledHeight());
        CommandSuggestionsAccessor accessor = (CommandSuggestionsAccessor) suggestions;
        CommandSuggestions.SuggestionsList list = accessor.krs$getSuggestions();
        if (list != null)
            return (((SuggestionsListAccessor) (Object) list).krs$getRect().getY() - 1) * guiToNanoVgScale;

        int usageLines = accessor.krs$getCommandUsage().size();
        if (usageLines > 0)
            return (chatScreen.height - CommandSuggestions.USAGE_OFFSET_FROM_BOTTOM - CommandSuggestions.LINE_HEIGHT * (usageLines - 1)) * guiToNanoVgScale;

        return Float.MAX_VALUE;
    }

    private static float getChatScreenOffset() {
        if (!(mc.gui.screen() instanceof ChatScreen))
            return 0f;

        ChatComponent chat = mc.gui.hud.getChat();
        ChatComponentAccessor chatAccessor = (ChatComponentAccessor) chat;
        int visibleLines = Math.max(0, Math.min(
                chat.getLinesPerPage(),
                chatAccessor.krs$getTrimmedMessages().size() - Math.max(0, chatAccessor.krs$getChatScrollbarPos())
        ));
        double lineSpacing = (Double) mc.options.chatLineSpacing().get();
        float chatLineHeight = (int) (9.0D * (lineSpacing + 1.0D));
        float chatScale = ((Double) mc.options.chatScale().get()).floatValue();
        float guiToNanoVgScale = NanoVGManager.getScaledScreenHeight() / Math.max(1f, mc.getWindow().getGuiScaledHeight());
        float visibleChatHeight = visibleLines * chatLineHeight * chatScale;

        if (visibleLines == 0)
            return CHAT_INPUT_TOP_OFFSET * guiToNanoVgScale - 30f + (16f + 12f);

        return (40f + visibleChatHeight) * guiToNanoVgScale - 30f + (16f + 12f);
    }


    private static class TargetHudState {
        Entity lastTargetEntity;
        InputStream cachedPlayerTexture;
        boolean playerTextureRequestPending;
        boolean playerTextureReady;
        boolean playerTextureFailed;
        long playerTextureRequestGeneration;
        float targetHudAlpha = 0f;
        float previousHealthRatio = 0.5f;
        float lastHealth = Float.NaN;
        float damageValue;
        long damageNanos;

        void resetPlayerTexture() {
            playerTextureRequestGeneration++;
            playerTextureRequestPending = false;
            playerTextureReady = false;
            playerTextureFailed = false;
            closeInputStream(cachedPlayerTexture);
            cachedPlayerTexture = null;
        }
    }

    private static void requestPlayerTexture(TargetHudState state, Player player) {
        String identifier = player.getStringUUID().toLowerCase(Locale.ROOT);
        NVGU vg = NVGU.INSTANCE;
        if (vg != null && vg.isCreated() && vg.hasTexture(identifier)) {
            state.playerTextureReady = true;
            state.playerTextureFailed = false;
            return;
        }

        if (state.playerTextureReady)
            state.playerTextureReady = false;
        if (state.playerTextureRequestPending || state.playerTextureFailed || state.cachedPlayerTexture != null)
            return;

        state.playerTextureRequestPending = true;
        long requestGeneration = ++state.playerTextureRequestGeneration;
        Entity requestedEntity = state.lastTargetEntity;
        StreamConverter.getPlayerFaceAsInputStream(player, inputStream -> {
            if (state.playerTextureRequestGeneration != requestGeneration || state.lastTargetEntity != requestedEntity) {
                closeInputStream(inputStream);
                return;
            }

            state.playerTextureRequestPending = false;
            if (inputStream == null) {
                state.playerTextureFailed = true;
                return;
            }
            state.cachedPlayerTexture = inputStream;
        });
    }

    private static void closeInputStream(InputStream inputStream) {
        if (inputStream == null)
            return;

        try {
            inputStream.close();
        } catch (java.io.IOException ignored) {
        }
    }

    static void clearTargetHudTracking() {
        for (TargetHudState state : hudStates.values())
            state.resetPlayerTexture();
        hudStates.clear();
        actualTargetEntitiesData.clear();
        BehaviorUtils.clearTargetInfoTracking();
    }

    private static class TargetHudData {
        final UUID id;
        @Nullable
        final Entity entity;
        final float hudX;
        final float hudY;
        final double depthSquared;

        TargetHudData(UUID id, @Nullable Entity entity, float hudX, float hudY, double depthSquared) {
            this.id = id;
            this.entity = entity;
            this.hudX = hudX;
            this.hudY = hudY;
            this.depthSquared = depthSquared;
        }
    }

    private static class HudClipRect {
        final float x;
        final float y;
        final float width;
        final float height;

        HudClipRect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        float right() {
            return x + width;
        }

        float bottom() {
            return y + height;
        }
    }

    private static final float TARGET_HUD_HEIGHT = 44f;
    private static final float TARGET_HUD_FACE_SIZE = 30f;
    private static final float TARGET_HUD_FACE_PAD = 7f;
    private static final float TARGET_HUD_FACE_GAP = 8f;
    private static final float TARGET_HUD_FONT = 11f;
    private static final float TARGET_HUD_ICON_FONT = 10f;
    private static final float TARGET_HUD_MIN_CONTENT_WIDTH = 92f;
    private static final float TARGET_HUD_MAX_CONTENT_WIDTH = 118f;
    private static final float TARGET_HUD_2D_CROSSHAIR_OFFSET = 34f;
    private static final float TARGET_HUD_STACK_GAP = 4f;
    private static final float TARGET_HUD_SCREEN_MARGIN = 6f;
    private static final long DAMAGE_POPUP_NANOS = 1_100_000_000L;

    private static class TargetHudRenderEntry implements ConnectedHudRect {
        final TargetHudState state;
        final Entity targetEntity;
        float hudX;
        float hudY;
        final float hudWidth;
        final String entityName;
        final String healthPctStr;
        final String healthValueText;
        final String distanceText;
        final String damageText;
        final float damageAlpha;
        final float healthRatio;
        final int alphaValue;
        final int barAlpha;
        final double depthSquared;
        HudClipRect bounds;
        List<HudClipRect> visibleRects = List.of();

        TargetHudRenderEntry(TargetHudState state, Entity targetEntity, float hudX, float hudY, float hudWidth,
                             String entityName, String healthPctStr, String healthValueText, String distanceText,
                             String damageText, float damageAlpha,
                             float healthRatio, int alphaValue, int barAlpha, double depthSquared) {
            this.state = state;
            this.targetEntity = targetEntity;
            this.hudX = hudX;
            this.hudY = hudY;
            this.hudWidth = hudWidth;
            this.entityName = entityName;
            this.healthPctStr = healthPctStr;
            this.healthValueText = healthValueText;
            this.distanceText = distanceText;
            this.damageText = damageText;
            this.damageAlpha = damageAlpha;
            this.healthRatio = healthRatio;
            this.alphaValue = alphaValue;
            this.barAlpha = barAlpha;
            this.depthSquared = depthSquared;
            this.bounds = new HudClipRect(hudX, hudY, hudWidth, TARGET_HUD_HEIGHT);
        }

        HudClipRect bounds() {
            return bounds;
        }

        void setHudY(float y) {
            hudY = y;
            bounds = new HudClipRect(hudX, y, hudWidth, TARGET_HUD_HEIGHT);
        }

        void setHudPosition(float x, float y) {
            hudX = x;
            hudY = y;
            bounds = new HudClipRect(x, y, hudWidth, TARGET_HUD_HEIGHT);
        }

        public float x() {
            return hudX;
        }

        public float y() {
            return hudY;
        }

        public float width() {
            return hudWidth;
        }

        public float height() {
            return TARGET_HUD_HEIGHT;
        }
    }

    private static final Map<UUID, TargetHudState> hudStates = new HashMap<>();
    private static final List<Tuple<@Nullable Entity, UUID>> actualTargetEntitiesData = new ArrayList<>();

    static void renderTargetHudModule(NVGU vg, float deltaSpeed) {
        if (mc.player == null || mc.level == null)
            return;

        List<TargetHudRenderEntry> visibleTargetHudEntries = Collections.emptyList();
        if (RenderUtil.shouldRenderWorldHudOverlays()) {
            targetHudEntryBuffer.clear();
            for (int i = 0, n = data.size(); i < n; i++) {
                TargetHudData hudData = data.get(i);
                TargetHudRenderEntry entry = prepareTargetHud(hudData.id, hudData.entity, deltaSpeed, hudData.hudX, hudData.hudY, hudData.depthSquared);
                if (entry != null)
                    targetHudEntryBuffer.add(entry);
            }

            if (!targetHudEntryBuffer.isEmpty())
                visibleTargetHudEntries = buildVisibleTargetHudEntries(targetHudEntryBuffer);
        } else {
            targetHudEntryBuffer.clear();
        }

        if (HudEditor.isEditing() && visibleTargetHudEntries.isEmpty()) {
            TargetHudRenderEntry preview = prepareTargetHudPreview();
            if (preview != null) {
                preview.visibleRects = List.of(preview.bounds());
                visibleTargetHudEntries = List.of(preview);
            }
        }
        if (HudEditor.isEditing() && !visibleTargetHudEntries.isEmpty()) {
            TargetHudRenderEntry editorEntry = null;
            for (TargetHudRenderEntry entry : visibleTargetHudEntries) {
                if (BehaviorUtils.isTarget(entry.targetEntity)) {
                    editorEntry = entry;
                    break;
                }
            }
            if (editorEntry == null)
                editorEntry = visibleTargetHudEntries.getFirst();
            HudEditor.register(HudEditor.Element.TARGET_HUD, editorEntry.hudX, editorEntry.hudY,
                    editorEntry.hudWidth, TARGET_HUD_HEIGHT);
        }

        vg.beginEffectBatch();
        for (TargetHudRenderEntry entry : visibleTargetHudEntries)
            drawTargetHudEffects(vg, entry);
        vg.flushEffectBatch();

        for (TargetHudRenderEntry entry : visibleTargetHudEntries)
            HudEditor.withScaleAt(vg, HudEditor.Element.TARGET_HUD, entry.hudX, entry.hudY,
                    () -> renderTargetHudVisibleRegions(vg, entry, () -> drawTargetHudBody(vg, entry)));
    }

    static boolean isTargetHud2D() {
        return TargetHud.is2D();
    }

    private static TargetHudRenderEntry prepareTargetHud(UUID id, @Nullable Entity entity, float deltaSpeed, float hudX, float hudY, double depthSquared) {
        TargetHudState state = hudStates.computeIfAbsent(id, k -> new TargetHudState());

        if (entity != null) {
            if (state.lastTargetEntity != entity) {
                state.resetPlayerTexture();
                state.lastTargetEntity = entity;
            }
        }

        boolean activeTarget = BehaviorUtils.isTarget(entity);
        float animationDelta = Float.isFinite(deltaSpeed) ? Math.clamp(deltaSpeed, 0.01f, 1.0f) : 1.0f;
        float targetAlpha = activeTarget ? 1.0f : 0.0f;
        state.targetHudAlpha = Math.clamp(
                Interpolation.INSTANCE.lerp(state.targetHudAlpha, targetAlpha, animationDelta),
                0.0f,
                1.0f
        );

        if (!activeTarget && state.targetHudAlpha < 0.1f) {
            state.previousHealthRatio = 0.5f;
            state.lastHealth = Float.NaN;
            state.damageNanos = 0L;
            Iterator<Tuple<Entity, UUID>> iterator = actualTargetEntitiesData.iterator();
            while (iterator.hasNext()) {
                Tuple<Entity, UUID> pair = iterator.next();
                if (pair.getSecond().equals(id)) {
                    state.lastTargetEntity = null;
                    iterator.remove();
                }
            }
            state.resetPlayerTexture();
            hudStates.remove(id, state);
            return null;
        }

        if (state.lastTargetEntity == null) return null;

        if (state.lastTargetEntity instanceof Player player)
            requestPlayerTexture(state, player);

        String entityName = state.lastTargetEntity instanceof Player || state.lastTargetEntity.hasCustomName() ? state.lastTargetEntity.getName().getString() : StreamConverter.formatNameByPath(BuiltInRegistries.ENTITY_TYPE.getKey(state.lastTargetEntity.getType()).getPath());

        float health = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).getHealth() : -1;
        float maxHealth = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).getMaxHealth() : 1;
        if (!Float.isFinite(health))
            health = 0f;
        if (!Float.isFinite(maxHealth) || maxHealth <= 0f)
            maxHealth = 1f;
        DecimalFormat formatter = ONE_DECIMAL_FORMAT.get();
        String healthPctStr = formatOneDecimal(formatter, (health / maxHealth) * 100) + "%";
        String healthValueText = formatOneDecimal(formatter, Math.max(0f, health)) + " / " + formatOneDecimal(formatter, maxHealth);
        String distanceText = mc.player != null
                ? Math.round(mc.player.distanceTo(state.lastTargetEntity)) + "m"
                : "";
        float healthRatioTarget = Math.max(0f, Math.min(health / maxHealth, 1f));
        state.previousHealthRatio = Math.clamp(
                Interpolation.INSTANCE.lerp(state.previousHealthRatio, healthRatioTarget, animationDelta),
                0f,
                1f
        );

        long now = System.nanoTime();
        if (Float.isFinite(state.lastHealth) && Math.abs(health - state.lastHealth) > 0.05f) {
            state.damageValue = health - state.lastHealth;
            state.damageNanos = now;
        }
        state.lastHealth = health;

        float damageAlpha = 0f;
        String damageText = "";
        long damageAge = now - state.damageNanos;
        if (state.damageNanos > 0L && damageAge >= 0L && damageAge < DAMAGE_POPUP_NANOS) {
            damageAlpha = 1f - damageAge / (float) DAMAGE_POPUP_NANOS;
            damageAlpha *= damageAlpha;
            damageText = (state.damageValue > 0f ? "+" : "") + formatOneDecimal(formatter, state.damageValue);
        }

        float nameWidth = Math.min(TARGET_HUD_MAX_CONTENT_WIDTH,
                NVGFonts.INTER_MEDIUM.getWidth(entityName, TARGET_HUD_FONT));
        float damageWidth = damageText.isEmpty() ? 0f : NVGFonts.INTER.getWidth(damageText, 9f);
        float contentWidth = Math.max(TARGET_HUD_MIN_CONTENT_WIDTH,
                Math.max(nameWidth, 36f + damageWidth + 4f));
        float hudWidth = TARGET_HUD_FACE_PAD * 2f + TARGET_HUD_FACE_SIZE + TARGET_HUD_FACE_GAP + contentWidth + 2f;
        if (isTargetHud2D()) {
            hudX = (NanoVGManager.getScaledScreenWidth() - hudWidth) / 2f;
            hudY = NanoVGManager.getScaledScreenHeight() / 2f + TARGET_HUD_2D_CROSSHAIR_OFFSET;
        }
        hudX += HudEditor.offsetX(HudEditor.Element.TARGET_HUD);
        hudY += HudEditor.offsetY(HudEditor.Element.TARGET_HUD);
        int alphaValue = Math.min(255, (int) (state.targetHudAlpha * 252));
        int barAlpha = Math.min(255, (int) (state.targetHudAlpha * 235));

        return new TargetHudRenderEntry(
                state,
                state.lastTargetEntity,
                hudX,
                hudY,
                hudWidth,
                entityName,
                healthPctStr,
                healthValueText,
                distanceText,
                damageText,
                damageAlpha,
                state.previousHealthRatio,
                alphaValue,
                barAlpha,
                depthSquared
        );
    }

    private static TargetHudRenderEntry prepareTargetHudPreview() {
        if (mc.player == null)
            return null;

        String entityName = "Target";
        String healthPctStr = "100%";
        float nameWidth = Math.min(TARGET_HUD_MAX_CONTENT_WIDTH,
                NVGFonts.INTER_MEDIUM.getWidth(entityName, TARGET_HUD_FONT));
        float contentWidth = Math.max(TARGET_HUD_MIN_CONTENT_WIDTH, nameWidth);
        float hudWidth = TARGET_HUD_FACE_PAD * 2f + TARGET_HUD_FACE_SIZE + TARGET_HUD_FACE_GAP + contentWidth + 2f;
        float hudX = (NanoVGManager.getScaledScreenWidth() - hudWidth) / 2f
                + HudEditor.offsetX(HudEditor.Element.TARGET_HUD);
        float hudY = NanoVGManager.getScaledScreenHeight() / 2f + TARGET_HUD_2D_CROSSHAIR_OFFSET
                + HudEditor.offsetY(HudEditor.Element.TARGET_HUD);

        TargetHudState state = new TargetHudState();
        state.targetHudAlpha = 1f;
        state.lastTargetEntity = mc.player;
        state.previousHealthRatio = 1f;
        return new TargetHudRenderEntry(state, mc.player, hudX, hudY, hudWidth, entityName, healthPctStr,
                "20.0 / 20.0", "0m", "", 0f, 1f, 255, 235, 0.0);
    }

    private static List<TargetHudRenderEntry> buildVisibleTargetHudEntries(List<TargetHudRenderEntry> entries) {
        // Keep the acquisition order so a card never swaps slots while its target is still alive.
        List<TargetHudRenderEntry> ordered = targetHudFrontToBackBuffer;
        ordered.clear();
        ordered.addAll(entries);

        List<TargetHudRenderEntry> visibleEntries = targetHudVisibleEntryBuffer;
        visibleEntries.clear();
        if (ordered.isEmpty())
            return visibleEntries;

        int count = ordered.size();
        float gap = TARGET_HUD_STACK_GAP;
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();

        float[] columnWidths = targetHudColumnWidths;
        Arrays.fill(columnWidths, 0f);
        int usedColumns = 0;
        int usedRows = 0;
        for (int i = 0; i < count; i++) {
            int row = targetHudSlotRow(i);
            int column = targetHudSlotColumn(i);
            columnWidths[column] = Math.max(columnWidths[column], ordered.get(i).hudWidth);
            usedColumns = Math.max(usedColumns, column + 1);
            usedRows = Math.max(usedRows, row + 1);
        }

        float gridWidth = gap * (usedColumns - 1);
        for (int column = 0; column < usedColumns; column++)
            gridWidth += columnWidths[column];
        float gridHeight = usedRows * TARGET_HUD_HEIGHT + gap * (usedRows - 1);

        // Anchor on the first card so extra targets grow right/down without moving the ones already shown.
        // Its hudX/hudY already contain the layout editor offsets in both 2D and 3D mode, so they must not
        // be recomputed here or a dragged card snaps back to its default spot.
        float anchorX = ordered.get(0).hudX;
        float originX = Math.clamp(anchorX, TARGET_HUD_SCREEN_MARGIN,
                Math.max(TARGET_HUD_SCREEN_MARGIN, screenWidth - TARGET_HUD_SCREEN_MARGIN - gridWidth));
        float originY = Math.clamp(ordered.get(0).hudY, TARGET_HUD_SCREEN_MARGIN,
                Math.max(TARGET_HUD_SCREEN_MARGIN, screenHeight - TARGET_HUD_SCREEN_MARGIN - gridHeight));

        for (int i = 0; i < count; i++) {
            TargetHudRenderEntry entry = ordered.get(i);
            int row = targetHudSlotRow(i);
            int column = targetHudSlotColumn(i);

            float x = originX;
            for (int previousColumn = 0; previousColumn < column; previousColumn++)
                x += columnWidths[previousColumn] + gap;

            entry.setHudPosition(x, originY + row * (TARGET_HUD_HEIGHT + gap));
            entry.visibleRects = List.of(entry.bounds());
            visibleEntries.add(entry);
        }

        return visibleEntries;
    }

    /**
     * Slot order requested for the target hud: the first four cards fill a 2x2 block, the next two
     * extend it with a third column, and every later row is a full three column row.
     * <pre>
     * [1][2][5]
     * [3][4][6]
     * [7][8]
     * </pre>
     */
    private static int targetHudSlotRow(int index) {
        if (index < 4)
            return index / 2;
        if (index < 6)
            return index - 4;
        return 2 + (index - 6) / 3;
    }

    private static int targetHudSlotColumn(int index) {
        if (index < 4)
            return index % 2;
        if (index < 6)
            return 2;
        return (index - 6) % 3;
    }

    private static void renderTargetHudVisibleRegions(NVGU vg, TargetHudRenderEntry entry, Runnable render) {
        for (HudClipRect rect : entry.visibleRects) {
            vg.pushScissor(rect.x, rect.y, rect.width, rect.height);
            try {
                render.run();
            } finally {
                vg.popScissor();
            }
        }
    }

    private static void drawTargetHudEffects(NVGU vg, TargetHudRenderEntry entry) {
        // Target cards are flat, square overlays; no glass or rounding.
    }

    private static Color targetHealthColor(float healthRatio, int alpha) {
        float t = Math.clamp(1f - healthRatio, 0f, 1f);
        Color start = t < 0.5f ? NanoVGTheme.TEXT : NanoVGTheme.ACCENT;
        Color end = t < 0.5f ? NanoVGTheme.ACCENT : NanoVGTheme.ACCENT_END;
        float local = t < 0.5f ? t * 2f : (t - 0.5f) * 2f;
        return new Color(
                Math.round(start.getRed() + (end.getRed() - start.getRed()) * local),
                Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * local),
                Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * local),
                Math.clamp(alpha, 0, 255)
        );
    }

    private static void drawTargetHudBody(NVGU vg, TargetHudRenderEntry entry) {
        TargetHudState state = entry.state;
        float x = entry.hudX;
        float y = entry.hudY;
        float width = entry.hudWidth;
        int alpha = entry.alphaValue;
        float opacity = alpha / 255f;
        if (opacity <= 0.001f)
            return;

        NanoVGTheme.renderCompact(vg, x, y, width, TARGET_HUD_HEIGHT, NanoVGTheme.RADIUS_HUD, opacity, 70);

        float faceX = x + TARGET_HUD_FACE_PAD;
        float faceY = y + (TARGET_HUD_HEIGHT - TARGET_HUD_FACE_SIZE) / 2f;
        vg.rectangle(faceX, faceY, TARGET_HUD_FACE_SIZE, TARGET_HUD_FACE_SIZE, alphaColor(8, 6, 12, alpha));

        boolean faceDrawn = false;
        if (entry.targetEntity instanceof Player) {
            String identifier = entry.targetEntity.getStringUUID().toLowerCase(Locale.ROOT);
            InputStream playerTexture = state.cachedPlayerTexture;
            if (playerTexture != null) {
                state.cachedPlayerTexture = null;
                try {
                    vg.createTexture(identifier, playerTexture);
                    state.playerTextureReady = vg.hasTexture(identifier);
                    state.playerTextureFailed = !state.playerTextureReady;
                } catch (RuntimeException ignored) {
                    state.playerTextureReady = false;
                    state.playerTextureFailed = true;
                } finally {
                    closeInputStream(playerTexture);
                }
            }

            if (state.playerTextureReady && vg.hasTexture(identifier)) {
                faceDrawn = true;
                vg.globalAlpha(state.targetHudAlpha, () ->
                        vg.texturedRectangle(faceX, faceY, TARGET_HUD_FACE_SIZE, TARGET_HUD_FACE_SIZE, identifier));
            } else if (state.playerTextureReady) {
                state.playerTextureReady = false;
                state.playerTextureFailed = false;
            }
        }

        if (!faceDrawn) {
            NVGFonts.ICON.drawText(MaterialIcon.PERSON, faceX + TARGET_HUD_FACE_SIZE / 2f, faceY + 9f, TARGET_HUD_ICON_FONT,
                    alphaColor(142, 130, 153, (int) (alpha * 0.8f)), Alignment.CENTER_TOP, true);
        }

        vg.rectangleBorder(faceX + 0.5f, faceY + 0.5f, TARGET_HUD_FACE_SIZE - 1f, TARGET_HUD_FACE_SIZE - 1f, 1f,
                alphaColor(255, 255, 255, (int) (opacity * 0.16f)), Border.INSIDE);

        float contentX = faceX + TARGET_HUD_FACE_SIZE + TARGET_HUD_FACE_GAP;
        float contentRight = x + width - TARGET_HUD_FACE_PAD;
        float contentWidth = contentRight - contentX;
        float percentWidth = NVGFonts.INTER_MEDIUM.getWidth(entry.healthPctStr, 10f);

        NVGFonts.INTER_MEDIUM.drawText(trimToWidth(entry.entityName, TARGET_HUD_FONT, Math.max(0f, contentWidth - percentWidth - 6f)),
                contentX, y + 7f, TARGET_HUD_FONT, alphaColor(233, 226, 240, alpha), Alignment.LEFT_TOP, true);
        NVGFonts.INTER_MEDIUM.drawText(entry.healthPctStr, contentRight, y + 8f, 10f,
                targetHealthColor(entry.healthRatio, alpha), Alignment.RIGHT_TOP, true);

        float barY = y + 22f;
        float barWidth = contentWidth;
        vg.rectangle(contentX, barY, barWidth, 3f, alphaColor(0, 0, 0, (int) (opacity * 0.7f)));
        if (entry.healthRatio > 0.01f) {
            vg.rectangle(contentX, barY, Math.max(1f, barWidth * entry.healthRatio), 3f,
                    targetHealthColor(entry.healthRatio, entry.barAlpha));
        }

        float footY = barY + 7f;
        NVGFonts.INTER.drawText(entry.healthValueText, contentX, footY, 8.5f,
                alphaColor(142, 130, 153, (int) (opacity * 225)), Alignment.LEFT_TOP, true);

        if (!entry.damageText.isEmpty() && entry.damageAlpha > 0.01f) {
            Color damageColor = entry.damageText.startsWith("+")
                    ? alphaColor(233, 226, 240, (int) (entry.damageAlpha * alpha))
                    : alphaColor(255, 93, 122, (int) (entry.damageAlpha * alpha));
            NVGFonts.INTER_MEDIUM.drawText(entry.damageText, contentRight, footY - 0.5f, 9f,
                    damageColor, Alignment.RIGHT_TOP, true);
        } else if (!entry.distanceText.isEmpty()) {
            NVGFonts.INTER.drawText(entry.distanceText, contentRight, footY, 8.5f,
                    alphaColor(142, 130, 153, (int) (opacity * 215)), Alignment.RIGHT_TOP, true);
        }
    }

    public static void renderModuleInformation(NVGU vg, float startY, float deltaSpeed) {
        List<StyledTextRenderEntry> entries = new ArrayList<>();
        prepareModuleInformationEntries(entries, startY, deltaSpeed);

        vg.beginEffectBatch();
        renderStyledTextEffects(vg, entries);
        vg.flushEffectBatch();
        renderStyledTextBodies(vg, entries);
    }

    private static void prepareModuleInformationEntries(List<StyledTextRenderEntry> entries, float startY, float deltaSpeed) {
        entries.clear();
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float centerX = screenWidth * 0.5f;

        boolean hypixelDisablerActive = ModuleManager.getModuleState(DisablerModule.class) && HypixelDisabler.stuckOnAir && HypixelDisabler.airTicks >= 9 && !HypixelDisabler.watchDogDisabled;
        float progress = advanceModuleInformationFade(INFO_HYPIXEL_DISABLER, hypixelDisablerActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Disabling... " + HypixelDisabler.airStuckTicks + "/25", centerX, startY, progress));
        if (hypixelDisablerActive) startY -= 40f;

        LookTP lookTP = ModuleManager.getModule(LookTP.class);
        boolean lookTpTeleporting = lookTP != null && lookTP.isClientsideTeleporting();
        progress = advanceModuleInformationFade(INFO_LOOK_TP, lookTpTeleporting, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Look TP: " + lookTP.getTeleportProgressPercent() + "%", centerX, startY, progress));
        if (lookTpTeleporting) startY -= 40f;

        boolean breakingActive = mc.gameMode != null && (
                ModuleManager.getModuleState(Breaker.class) && Breaker.getWasBreaking()
                        || ModuleManager.getModuleState(CivBreak.class) && CivBreak.getWasBreaking()
                        || ModuleManager.getModuleState(Nuker.class) && Nuker.getWasBreaking());
        progress = advanceModuleInformationFade(INFO_BREAKING, breakingActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Breaking...", centerX, startY, progress));
        if (breakingActive) startY -= 40f;

        boolean blinking = BlinkUtil.INSTANCE.getBlinking();
        progress = advanceModuleInformationFade(INFO_BLINKING, blinking, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Blinking... " + (Blink.directions.get().equalsIgnoreCase("incoming") ? "(Incoming)" : "(x" + BlinkUtil.INSTANCE.getPacketCount() + ")"), centerX, startY, progress));
        if (blinking) startY -= 40f;

        boolean scaffoldActive = ModuleManager.getModuleState(Scaffold.class) && mc.player != null;
        progress = advanceModuleInformationFade(INFO_SCAFFOLD_BLOCKS, scaffoldActive, deltaSpeed);
        if (progress > 0f && !scaffoldBlocks.get()) {
            int scaffoldBlockCount = mc.player != null ? Scaffold.countUsableHotbarBlocks(mc.player.getInventory()) : 0;
            entries.add(new StyledTextRenderEntry(scaffoldBlockCount + " blocks", centerX, startY, progress));
        }
        if (scaffoldActive && !scaffoldBlocks.get()) startY -= 40f;

        List<Player> murdererList = MurdererDetector.murderers;
        if (!murdererList.isEmpty()) {
            for (Player murderer : murdererList) {
                String distance = mc.player != null ? Interpolation.INSTANCE.getDecimalFormat().format(mc.player.distanceTo(murderer)) : Interpolation.INSTANCE.getDecimalFormat().format(0f);
                entries.add(new StyledTextRenderEntry("> " + murderer.getName().getString() + " (" + distance + "m)", centerX, startY, 1f));
                startY -= 27f;
            }
        }
        boolean murdererDetectorActive = ModuleManager.getModuleState(MurdererDetector.class);
        progress = advanceModuleInformationFade(INFO_MURDERER_COUNT, murdererDetectorActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Murderers: " + murdererList.size(), centerX, startY, progress));
    }

    public void renderPlayerList(NVGU vg) {
        float height = getPlayerListHeight();
        if (height <= 0.01f)
            return;

        getPlayerListEntries();
        float x = getLeftHudX() + HudEditor.offsetX(HudEditor.Element.PLAYER_LIST);
        float y = getPlayerListTopY() + HudEditor.offsetY(HudEditor.Element.PLAYER_LIST);
        int visiblePlayers = playerListRowBuffer.size();
        int totalPlayers = cachedPlayerListEntries.size();
        float width = PLAYER_LIST_WIDTH;

        HudEditor.register(HudEditor.Element.PLAYER_LIST, x, y, width, height);
        vg.rectangle(x, y, width, height, alphaColor(10, 11, 14, 190));
        vg.rectangle(x, y, width, PLAYER_LIST_HEADER_HEIGHT, alphaColor(7, 8, 11, 220));
        vg.rectangle(x, y + PLAYER_LIST_HEADER_HEIGHT - 1f, width, 1f, alphaColor(255, 255, 255, 24));

        NVGFonts.INTER.drawText("Players " + totalPlayers, x + 35f, y + 5f, 10f,
                NanoVGTheme.text(225), Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText("Dist.", x + width - 68f, y + 5f, 10f,
                NanoVGTheme.text(210), Alignment.RIGHT_TOP, true);
        NVGFonts.INTER.drawText("HP", x + width - 8f, y + 5f, 10f,
                NanoVGTheme.text(210), Alignment.RIGHT_TOP, true);

        vg.pushScissor(x, y, width, height);
        float rowY = y + PLAYER_LIST_HEADER_HEIGHT;
        for (int i = 0; i < visiblePlayers; i++) {
            PlayerListRow row = playerListRowBuffer.get(i);
            Player player = findWorldPlayer(row.uuid);
            boolean hasFace = player != null && ensurePlayerListFace(vg, row.uuid, player);
            if (hasFace) {
                vg.texturedRoundedRectangle(x + 4f, rowY + 2f, 14f, 14f, 0f, playerListFaceIdentifier(row.uuid));
            } else {
                vg.rectangle(x + 4f, rowY + 2f, 14f, 14f, NanoVGTheme.base(150));
                NVGFonts.INTER.drawText(row.name.isEmpty() ? "?" : row.name.substring(0, 1).toUpperCase(Locale.ROOT),
                        x + 8f, rowY + 4f, 9f, NanoVGTheme.muted(200), Alignment.LEFT_TOP, true);
            }
            Color nameColor = row.self ? NanoVGTheme.ACCENT : NanoVGTheme.text(225);
            NVGFonts.INTER.drawText(row.name, x + 23f, rowY + 3f, 12f, nameColor, Alignment.LEFT_TOP, true);
            NVGFonts.INTER.drawText(row.distanceText, x + width - 68f, rowY + 4f, 10f,
                    NanoVGTheme.muted(215), Alignment.RIGHT_TOP, true);
            NVGFonts.INTER.drawText(row.hpText, x + width - 8f, rowY + 4f, 10f,
                    NanoVGTheme.text(220), Alignment.RIGHT_TOP, true);
            rowY += PLAYER_LIST_ROW_HEIGHT;
        }
        vg.popScissor();
    }

    private void renderSessionInfo(NVGU vg) {
        float x = getSessionInfoX() + HudEditor.offsetX(HudEditor.Element.SESSION);
        float y = getTabGuiBaseY() + HudEditor.offsetY(HudEditor.Element.SESSION);
        float width = SESSION_INFO_WIDTH;

        if (sessionStartedAtMillis <= 0L)
            sessionStartedAtMillis = System.currentTimeMillis();

        HudEditor.register(HudEditor.Element.SESSION, x, y, width, SESSION_INFO_HEIGHT);
        vg.rectangle(x, y, width, SESSION_INFO_HEIGHT, alphaColor(10, 11, 14, 200));
        vg.rectangle(x, y, width, 21f, alphaColor(5, 6, 9, 225));
        vg.rectangle(x, y + 20f, width, 1f, alphaColor(255, 255, 255, 25));
        NVGFonts.INTER.drawText("Session Information", x + width / 2f, y + 5f, 11f,
                NanoVGTheme.text(225), Alignment.CENTER_TOP, true);

        float faceX = x + 9f;
        float faceY = y + 28f;
        float faceSize = 42f;
        boolean hasFace = mc.player != null && ensurePlayerListFace(vg, mc.player.getUUID(), mc.player);
        if (hasFace) {
            vg.texturedRoundedRectangle(faceX, faceY, faceSize, faceSize, 0f,
                    playerListFaceIdentifier(mc.player.getUUID()));
        } else {
            vg.rectangle(faceX, faceY, faceSize, faceSize, NanoVGTheme.base(145));
            String initial = mc.player == null || mc.player.getName().getString().isBlank()
                    ? "?" : mc.player.getName().getString().substring(0, 1).toUpperCase(Locale.ROOT);
            NVGFonts.INTER_MEDIUM.drawText(initial, faceX + faceSize / 2f, faceY + 13f, 14f,
                    NanoVGTheme.muted(220), Alignment.CENTER_TOP, true);
        }

        float contentX = x + 58f;
        float contentWidth = width - 67f;
        String localName = mc.player != null ? trimToWidth(mc.player.getName().getString(), 14f, contentWidth) : "—";
        NVGFonts.INTER_MEDIUM.drawText(localName, contentX, y + 29f, 14f,
                NanoVGTheme.text(245), Alignment.LEFT_TOP, true);
        vg.rectangle(contentX, y + 49f, contentWidth, 3f, NanoVGTheme.ACCENT);
        NVGFonts.INTER.drawText(formatSessionDuration(sessionStartedAtMillis), contentX, y + 58f, 11f,
                NanoVGTheme.text(220), Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText(formatSessionKdr() + " KDR", x + width - 9f, y + 58f, 11f,
                NanoVGTheme.text(220), Alignment.RIGHT_TOP, true);
    }

    private static String formatSessionKdr() {
        float ratio = sessionDeaths == 0 ? sessionKills : sessionKills / (float) sessionDeaths;
        return TWO_DECIMAL_FORMAT.get().format(ratio);
    }

    private static void renderPlayerListEffects(NVGU vg) {
        // Flat session card: no blur pass.
    }

    private static boolean isSelfPlayerListEntry(PlayerInfo entry) {
        return mc.player != null && Objects.equals(entry.getProfile().id(), mc.player.getUUID());
    }

    private static String getPlayerListName(PlayerInfo entry) {
        Component displayName = getPlayerListDisplayName(entry);
        String plain = displayName.getString();
        String plainName = stripPlayerListRankPrefix(plain);
        if (plainName == null || plainName.isBlank()) {
            String formatted = NanoVGTextFormatter.formatColors(displayName).strip();
            return formatted.isEmpty() ? "Unknown" : formatted;
        }
        return NanoVGTextFormatter.formatColors(displayName, getPlayerListRankPrefixLength(plain));
    }

    private static String getPlayerListPlainName(PlayerInfo entry) {
        String name = stripPlayerListRankPrefix(getPlayerListDisplayName(entry).getString());
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    private static Component getPlayerListDisplayName(PlayerInfo entry) {
        return mc.gui.hud.getTabList().getNameForDisplay(entry);
    }

    private static String stripPlayerListRankPrefix(String name) {
        if (name == null)
            return name;

        int prefixLength = getPlayerListRankPrefixLength(name);
        return prefixLength <= 0 ? name : name.substring(prefixLength);
    }

    private static int getPlayerListRankPrefixLength(String name) {
        if (name == null || name.isEmpty())
            return 0;

        int separatorIndex = name.indexOf('|');
        if (separatorIndex >= 0) {
            int start = separatorIndex + 1;
            while (start < name.length() && Character.isWhitespace(name.charAt(start)))
                start++;
            return start;
        }

        if (!name.startsWith("["))
            return 0;

        int closingBracketIndex = name.indexOf(']');
        if (closingBracketIndex <= 0)
            return 0;

        int start = closingBracketIndex + 1;
        while (start < name.length() && Character.isWhitespace(name.charAt(start)))
            start++;
        return start;
    }

    private static String getPlayerListPingText(int latency) {
        return latency >= 0 ? latency + "ms" : "--";
    }

    private static Color getPlayerListPingColor(int latency) {
        if (latency < 0)
            return NanoVGTheme.muted(160);
        if (latency <= 80)
            return NanoVGTheme.muted(220);
        if (latency <= 180)
            return NanoVGTheme.warning(220);

        return NanoVGTheme.danger(220);
    }

    private static List<String> wrapTextToWidth(String text, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0f)
            return Collections.singletonList("");
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        if (NVGFonts.INTER.getWidth(text, fontSize) <= maxWidth)
            return Collections.singletonList(text);

        int start = 0;
        while (start < text.length()) {
            int end = findWrapEnd(text, start, maxWidth, fontSize);
            lines.add(text.substring(start, end).strip());
            start = end;

            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }

        return lines.isEmpty() ? Collections.singletonList("") : lines;
    }

    private static int findWrapEnd(String text, int start, float maxWidth, float fontSize) {
        int bestFit = start + 1;
        int lastWhitespace = -1;

        for (int end = start + 1; end <= text.length(); end++) {
            String candidate = text.substring(start, end);
            if (NVGFonts.INTER.getWidth(candidate, fontSize) > maxWidth)
                break;

            bestFit = end;
            if (Character.isWhitespace(text.charAt(end - 1)))
                lastWhitespace = end;
        }

        if (bestFit >= text.length())
            return text.length();
        if (lastWhitespace > start)
            return lastWhitespace;

        return bestFit;
    }

    public void renderMinimap(NVGU vg, long deltaTime) {
        if (mc.player == null || mc.level == null) return;

        ClientLevel level = mc.level;
        BlockPos playerBlockPos = mc.player.blockPosition();
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        updateMinimapView(playerX, playerZ, deltaTime);

        updateMinimapTerrainTexture(vg, level, (int) Math.floor(minimapViewX), (int) Math.floor(minimapViewZ), playerBlockPos.getY());
        drawMinimap(vg, minimapViewX, minimapViewZ, deltaTime);
    }

    private void drawMinimap(NVGU vg, double viewX, double viewZ, long deltaTime) {
        float mapX = getLeftHudX() + HudEditor.offsetX(HudEditor.Element.MINIMAP);
        float mapY = getMinimapY() + HudEditor.offsetY(HudEditor.Element.MINIMAP);
        HudEditor.register(HudEditor.Element.MINIMAP, mapX, mapY, 150f, 150f);
        float halfSize = 150f / 2f;
        float centerX = mapX + halfSize;
        float centerY = mapY + halfSize;

        NanoVGTheme.renderPanel(vg, mapX, mapY, 150f, 150f, NanoVGTheme.RADIUS_MENU, 1f);
        float blockScale = (150f - 12f) / (MINIMAP_VISIBLE_RADIUS_BLOCKS * 2f);
        renderMinimapTerrain(vg, mapX, mapY, centerX, centerY, blockScale, viewX, viewZ);
        vg.line(centerX, mapY + 7f, centerX, mapY + 150f - 7f, 1f, new Color(255, 255, 255, 28));
        vg.line(mapX + 7f, centerY, mapX + 150f - 7f, centerY, 1f, new Color(255, 255, 255, 28));

        renderMinimapPlayerDots(vg, centerX, centerY, blockScale, viewX, viewZ, deltaTime);

        float yawRadians = (float) Math.toRadians(mc.player.getYRot() - 180f);
        float dirX = (float) Math.sin(yawRadians);
        float dirZ = (float) -Math.cos(yawRadians);
        vg.line(centerX, centerY, centerX + dirX * 12f, centerY + dirZ * 12f, 1.5f, new Color(255, 255, 0, 220));

        float selfX = centerX - 4f / 2f;
        float selfY = centerY - 4f / 2f;
        vg.rectangle(selfX, selfY, 4f, 4f, NanoVGTheme.accent(240));

    }

    private static void renderMinimapEffects(NVGU vg) {
        // Flat minimap card: no blur pass.
    }

    private void renderMinimapTerrain(NVGU vg, float mapX, float mapY, float centerX, float centerY, float blockScale, double viewX, double viewZ) {
        if (!minimapTerrainTextureReady || !vg.hasTexture("krs_minimap_terrain")) {
            minimapTerrainDirty = true;
            return;
        }
        float inset = 6f;
        float terrainX = mapX + inset;
        float terrainY = mapY + inset;
        float terrainSize = 150f - inset * 2f;
        int terrainStartX = minimapTerrainCenterBlockX - minimapTerrainTextureRangeBlocks();
        int terrainStartZ = minimapTerrainCenterBlockZ - minimapTerrainTextureRangeBlocks();
        float textureX = centerX + (float) (terrainStartX - viewX) * blockScale;
        float textureY = centerY + (float) (terrainStartZ - viewZ) * blockScale;
        float textureSize = minimapTerrainResolution() * blockScale;
        float clipX = Math.max(terrainX, textureX);
        float clipY = Math.max(terrainY, textureY);
        float clipRight = Math.min(terrainX + terrainSize, textureX + textureSize);
        float clipBottom = Math.min(terrainY + terrainSize, textureY + textureSize);
        if (clipRight <= clipX || clipBottom <= clipY)
            return;

        vg.scissor(clipX, clipY, clipRight - clipX, clipBottom - clipY, () ->
                vg.roundedRectangle(
                        terrainX,
                        terrainY,
                        terrainSize,
                        terrainSize,
                        0f,
                        vg.texture("krs_minimap_terrain", textureX, textureY, textureSize, textureSize)
                )
        );
    }

    private void renderMinimapPlayerDots(NVGU vg, float centerX, float centerY, float blockScale, double viewX, double viewZ, long deltaTime) {
        double radius = MINIMAP_VISIBLE_RADIUS_BLOCKS + 1.5;
        double radiusSquared = radius * radius;
        minimapPlayerUuidBuffer.clear();

        for (AbstractClientPlayer otherPlayer : mc.level.players()) {
            if (otherPlayer == mc.player) continue;

            UUID uuid = otherPlayer.getUUID();
            minimapPlayerUuidBuffer.add(uuid);
            MinimapPlayerDotState dotState = minimapPlayerDots.get(uuid);

            if (dotState == null) {
                dotState = new MinimapPlayerDotState(otherPlayer.getX(), otherPlayer.getZ());
                minimapPlayerDots.put(uuid, dotState);
            } else {
                smoothMinimapDot(dotState, otherPlayer.getX(), otherPlayer.getZ(), deltaTime);
            }

            double dx = dotState.x - viewX;
            double dz = dotState.z - viewZ;
            if (dx * dx + dz * dz > radiusSquared) continue;

            float drawX = centerX + (float) dx * blockScale - 3f / 2f;
            float drawY = centerY + (float) dz * blockScale - 3f / 2f;
            vg.rectangle(drawX, drawY, 3f, 3f, new Color(0, 255, 0, 230));
        }

        minimapPlayerDots.keySet().removeIf(uuid -> !minimapPlayerUuidBuffer.contains(uuid));
        minimapPlayerUuidBuffer.clear();
    }

    private void updateMinimapView(double playerX, double playerZ, long deltaTime) {
        if (!minimapViewInitialized || !Double.isFinite(minimapViewX) || !Double.isFinite(minimapViewZ)) {
            minimapViewX = playerX;
            minimapViewZ = playerZ;
            minimapViewInitialized = true;
            return;
        }

        double dx = playerX - minimapViewX;
        double dz = playerZ - minimapViewZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (!Double.isFinite(distance) || distance > (MINIMAP_VISIBLE_RADIUS_BLOCKS * 6.0)) {
            minimapViewX = playerX;
            minimapViewZ = playerZ;
            return;
        }

        double factor = getMinimapSmoothFactor(deltaTime, 14.0, 0.012, distance);
        minimapViewX += dx * factor;
        minimapViewZ += dz * factor;
    }

    private static void smoothMinimapDot(MinimapPlayerDotState dotState, double targetX, double targetZ, long deltaTime) {
        double dx = targetX - dotState.x;
        double dz = targetZ - dotState.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (!Double.isFinite(distance) || distance > (MINIMAP_VISIBLE_RADIUS_BLOCKS * 4.0)) {
            dotState.x = targetX;
            dotState.z = targetZ;
            return;
        }

        double factor = getMinimapSmoothFactor(deltaTime, 18.0, 0.018, distance);
        dotState.x += dx * factor;
        dotState.z += dz * factor;
    }

    private static double getMinimapSmoothFactor(long deltaTime, double responsiveness, double distanceBoost, double distance) {
        long clampedDelta = Math.clamp(deltaTime, 0L, 100L);
        double baseFactor = 1.0 - Math.exp(-responsiveness * (clampedDelta / 1000.0));
        return Math.clamp(baseFactor + distance * distanceBoost, baseFactor, 0.92);
    }

    private void updateMinimapTerrainTexture(NVGU vg, ClientLevel level, int centerBlockX, int centerBlockZ, int playerBlockY) {
        boolean hasTexture = vg.hasTexture("krs_minimap_terrain");
        if (!hasTexture) {
            minimapTerrainTextureReady = false;
            if (minimapTerrainDataReady && !minimapTerrainBuildInProgress) {
                uploadMinimapTerrainTexture(vg, minimapTerrainColors);
                return;
            }
            minimapTerrainDirty = true;
        }

        long now = System.nanoTime();
        boolean hasCachedCenter = minimapTerrainDataReady
                && minimapTerrainCenterBlockX != Integer.MIN_VALUE
                && minimapTerrainCenterBlockZ != Integer.MIN_VALUE;
        long centerDriftX = hasCachedCenter ? Math.abs((long) centerBlockX - minimapTerrainCenterBlockX) : Long.MAX_VALUE;
        long centerDriftZ = hasCachedCenter ? Math.abs((long) centerBlockZ - minimapTerrainCenterBlockZ) : Long.MAX_VALUE;
        boolean centerMovedFar = Math.max(centerDriftX, centerDriftZ) >= MINIMAP_REBUILD_DRIFT_BLOCKS;
        boolean verticalMovedFar = hasCachedCenter
                && Math.abs((long) playerBlockY - minimapTerrainPlayerBlockY) >= 16L;
        boolean refreshDue = hasCachedCenter
                && now - minimapTerrainLastUpdateNanos >= MINIMAP_TERRAIN_REFRESH_NANOS;

        if (minimapTerrainBuildInProgress) {
            long buildDriftX = Math.abs((long) centerBlockX - minimapTerrainBuildCenterBlockX);
            long buildDriftZ = Math.abs((long) centerBlockZ - minimapTerrainBuildCenterBlockZ);
            if (Math.max(buildDriftX, buildDriftZ) > minimapTerrainTextureRangeBlocks())
                startMinimapTerrainBuild(centerBlockX, centerBlockZ, playerBlockY, false);
        } else if (minimapTerrainDirty || !hasCachedCenter || centerMovedFar || verticalMovedFar || refreshDue) {
            boolean reuseCachedTerrain = hasCachedCenter && centerMovedFar
                    && !verticalMovedFar && !refreshDue && !minimapTerrainDirty;
            startMinimapTerrainBuild(centerBlockX, centerBlockZ, playerBlockY, reuseCachedTerrain);
        }

        if (!minimapTerrainBuildInProgress)
            return;

        int sampleBudget = minimapTerrainTextureReady
                ? MINIMAP_TERRAIN_SAMPLES_PER_FRAME
                : MINIMAP_TERRAIN_SAMPLES_PER_FRAME * 2;
        processMinimapTerrainBuild(level, sampleBudget);
        if (minimapTerrainDirtyCursor < minimapTerrainDirtyCount)
            return;

        int[] previousColors = minimapTerrainColors;
        minimapTerrainColors = minimapTerrainBuildColors;
        minimapTerrainBuildColors = previousColors;
        minimapTerrainCenterBlockX = minimapTerrainBuildCenterBlockX;
        minimapTerrainCenterBlockZ = minimapTerrainBuildCenterBlockZ;
        minimapTerrainPlayerBlockY = minimapTerrainBuildPlayerBlockY;
        minimapTerrainLastUpdateNanos = now;
        minimapTerrainBuildInProgress = false;
        minimapTerrainDataReady = true;
        minimapTerrainDirty = false;
        uploadMinimapTerrainTexture(vg, minimapTerrainColors);
    }

    private void startMinimapTerrainBuild(int centerBlockX, int centerBlockZ, int playerBlockY, boolean reuseCachedTerrain) {
        minimapTerrainBuildCenterBlockX = centerBlockX;
        minimapTerrainBuildCenterBlockZ = centerBlockZ;
        minimapTerrainBuildPlayerBlockY = playerBlockY;
        minimapTerrainDirtyCount = 0;
        minimapTerrainDirtyCursor = 0;
        minimapTerrainBuildInProgress = true;

        int resolution = minimapTerrainResolution();
        int range = minimapTerrainTextureRangeBlocks();
        int buildStartX = centerBlockX - range;
        int buildStartZ = centerBlockZ - range;
        int cachedStartX = minimapTerrainCenterBlockX - range;
        int cachedStartZ = minimapTerrainCenterBlockZ - range;

        for (int pixelZ = 0; pixelZ < resolution; pixelZ++) {
            int sampleZ = buildStartZ + pixelZ;
            int cachedPixelZ = sampleZ - cachedStartZ;
            for (int pixelX = 0; pixelX < resolution; pixelX++) {
                int destinationIndex = pixelZ * resolution + pixelX;
                int sampleX = buildStartX + pixelX;
                int cachedPixelX = sampleX - cachedStartX;
                if (reuseCachedTerrain
                        && cachedPixelX >= 0 && cachedPixelX < resolution
                        && cachedPixelZ >= 0 && cachedPixelZ < resolution) {
                    minimapTerrainBuildColors[destinationIndex] = minimapTerrainColors[cachedPixelZ * resolution + cachedPixelX];
                } else {
                    minimapTerrainDirtyIndices[minimapTerrainDirtyCount++] = destinationIndex;
                }
            }
        }
    }

    private void processMinimapTerrainBuild(ClientLevel level, int sampleBudget) {
        int resolution = minimapTerrainResolution();
        int range = minimapTerrainTextureRangeBlocks();
        int startX = minimapTerrainBuildCenterBlockX - range;
        int startZ = minimapTerrainBuildCenterBlockZ - range;
        int end = Math.min(minimapTerrainDirtyCount, minimapTerrainDirtyCursor + Math.max(1, sampleBudget));

        while (minimapTerrainDirtyCursor < end) {
            int destinationIndex = minimapTerrainDirtyIndices[minimapTerrainDirtyCursor++];
            int pixelX = destinationIndex % resolution;
            int pixelZ = destinationIndex / resolution;
            int sampleX = startX + pixelX;
            int sampleZ = startZ + pixelZ;
            int rgba = 0;

            if (level.hasChunk(sampleX >> 4, sampleZ >> 4)) {
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) - 1;
                if (surfaceY >= level.getMinY()) {
                    minimapTerrainSamplePos.set(sampleX, surfaceY, sampleZ);
                    rgba = getMinimapTerrainColor(
                            level,
                            minimapTerrainSamplePos,
                            surfaceY,
                            minimapTerrainBuildPlayerBlockY
                    );
                }
            }
            minimapTerrainBuildColors[destinationIndex] = rgba;
        }
    }

    private void uploadMinimapTerrainTexture(NVGU vg, int[] colors) {
        minimapTerrainBuffer.clear();
        int sourceResolution = minimapTerrainResolution();
        int textureResolution = minimapTerrainTextureResolution();
        for (int textureZ = 0; textureZ < textureResolution; textureZ++) {
            for (int textureX = 0; textureX < textureResolution; textureX++) {
                putRgba(
                        minimapTerrainBuffer,
                        getMinimapTerrainInterpolatedColor(colors, sourceResolution, textureX, textureZ)
                );
            }
        }
        minimapTerrainBuffer.flip();
        vg.createOrUpdateTextureRGBA(
                "krs_minimap_terrain",
                textureResolution,
                textureResolution,
                minimapTerrainBuffer,
                NanoVG.NVG_IMAGE_NEAREST
        );
        minimapTerrainTextureReady = vg.hasTexture("krs_minimap_terrain");
    }

    private static int getMinimapTerrainInterpolatedColor(int[] colors, int resolution, int textureX, int textureZ) {
        float sourceX = (textureX + 0.5f) / MINIMAP_TERRAIN_TEXTURE_SCALE - 0.5f;
        float sourceZ = (textureZ + 0.5f) / MINIMAP_TERRAIN_TEXTURE_SCALE - 0.5f;
        int left = (int) Math.floor(sourceX);
        int top = (int) Math.floor(sourceZ);
        float horizontalProgress = sourceX - left;
        float verticalProgress = sourceZ - top;

        int leftIndex = Math.clamp(left, 0, resolution - 1);
        int rightIndex = Math.clamp(left + 1, 0, resolution - 1);
        int topIndex = Math.clamp(top, 0, resolution - 1);
        int bottomIndex = Math.clamp(top + 1, 0, resolution - 1);
        int nearestX = Math.clamp(Math.round(sourceX), 0, resolution - 1);
        int nearestZ = Math.clamp(Math.round(sourceZ), 0, resolution - 1);
        int anchor = colors[nearestZ * resolution + nearestX];

        float topLeftWeight = (1f - horizontalProgress) * (1f - verticalProgress);
        float topRightWeight = horizontalProgress * (1f - verticalProgress);
        float bottomLeftWeight = (1f - horizontalProgress) * verticalProgress;
        float bottomRightWeight = horizontalProgress * verticalProgress;

        int topLeft = colors[topIndex * resolution + leftIndex];
        int topRight = colors[topIndex * resolution + rightIndex];
        int bottomLeft = colors[bottomIndex * resolution + leftIndex];
        int bottomRight = colors[bottomIndex * resolution + rightIndex];

        float totalWeight = 0f;
        float red = 0f;
        float green = 0f;
        float blue = 0f;
        float alpha = 0f;

        if (shouldBlendMinimapTerrainColors(anchor, topLeft)) {
            totalWeight += topLeftWeight;
            red += ((topLeft >> 24) & 0xFF) * topLeftWeight;
            green += ((topLeft >> 16) & 0xFF) * topLeftWeight;
            blue += ((topLeft >> 8) & 0xFF) * topLeftWeight;
            alpha += (topLeft & 0xFF) * topLeftWeight;
        }
        if (shouldBlendMinimapTerrainColors(anchor, topRight)) {
            totalWeight += topRightWeight;
            red += ((topRight >> 24) & 0xFF) * topRightWeight;
            green += ((topRight >> 16) & 0xFF) * topRightWeight;
            blue += ((topRight >> 8) & 0xFF) * topRightWeight;
            alpha += (topRight & 0xFF) * topRightWeight;
        }
        if (shouldBlendMinimapTerrainColors(anchor, bottomLeft)) {
            totalWeight += bottomLeftWeight;
            red += ((bottomLeft >> 24) & 0xFF) * bottomLeftWeight;
            green += ((bottomLeft >> 16) & 0xFF) * bottomLeftWeight;
            blue += ((bottomLeft >> 8) & 0xFF) * bottomLeftWeight;
            alpha += (bottomLeft & 0xFF) * bottomLeftWeight;
        }
        if (shouldBlendMinimapTerrainColors(anchor, bottomRight)) {
            totalWeight += bottomRightWeight;
            red += ((bottomRight >> 24) & 0xFF) * bottomRightWeight;
            green += ((bottomRight >> 16) & 0xFF) * bottomRightWeight;
            blue += ((bottomRight >> 8) & 0xFF) * bottomRightWeight;
            alpha += (bottomRight & 0xFF) * bottomRightWeight;
        }

        if (totalWeight <= 0f)
            return anchor;

        int blendedRed = Math.clamp(Math.round(red / totalWeight), 0, 255);
        int blendedGreen = Math.clamp(Math.round(green / totalWeight), 0, 255);
        int blendedBlue = Math.clamp(Math.round(blue / totalWeight), 0, 255);
        int blendedAlpha = Math.clamp(Math.round(alpha / totalWeight), 0, 255);
        return blendedRed << 24 | blendedGreen << 16 | blendedBlue << 8 | blendedAlpha;
    }

    private static boolean shouldBlendMinimapTerrainColors(int first, int second) {
        int firstAlpha = first & 0xFF;
        int secondAlpha = second & 0xFF;
        if (firstAlpha == 0 || secondAlpha == 0)
            return firstAlpha == secondAlpha;
        if (Math.abs(firstAlpha - secondAlpha) > MINIMAP_TERRAIN_BLEND_ALPHA_DISTANCE)
            return false;

        int redDifference = ((first >> 24) & 0xFF) - ((second >> 24) & 0xFF);
        int greenDifference = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int blueDifference = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int colorDistanceSquared = redDifference * redDifference
                + greenDifference * greenDifference
                + blueDifference * blueDifference;
        return colorDistanceSquared <= MINIMAP_TERRAIN_BLEND_COLOR_DISTANCE * MINIMAP_TERRAIN_BLEND_COLOR_DISTANCE;
    }

    private static int getMinimapTerrainColor(ClientLevel level, BlockPos.MutableBlockPos pos, int surfaceY, int playerBlockY) {
        int playerTopY = Math.min(surfaceY, playerBlockY + 16);
        int playerBottomY = Math.max(level.getMinY(), playerBlockY - 96);
        int color = scanMinimapTerrainColumn(level, pos, playerTopY, playerBottomY, playerBlockY);

        if (color != 0)
            return color;

        int fallbackBottomY = Math.max(level.getMinY(), surfaceY - 256);
        return scanMinimapTerrainColumn(level, pos, surfaceY, fallbackBottomY, playerBlockY);
    }

    private static int scanMinimapTerrainColumn(ClientLevel level, BlockPos.MutableBlockPos pos, int topY, int bottomY, int playerBlockY) {
        if (topY < bottomY)
            return 0;

        for (int y = topY; y >= bottomY; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);

            if (state.isAir() || shouldHideMinimapTerrainBlock(state))
                continue;

            int color = getMinimapTerrainColor(level, state, pos, y, playerBlockY);
            if (color != 0)
                return color;
        }

        return 0;
    }

    private static int getMinimapTerrainColor(ClientLevel level, BlockState state, BlockPos pos, int surfaceY, int playerBlockY) {
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor == MapColor.NONE)
            return 0;

        int argb = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        float heightShade = Math.clamp(0.92f + (surfaceY - playerBlockY) * 0.018f, 0.68f, 1.18f);

        red = Math.clamp((int) (red * heightShade), 0, 255);
        green = Math.clamp((int) (green * heightShade), 0, 255);
        blue = Math.clamp((int) (blue * heightShade), 0, 255);

        int alpha;
        if (!state.getFluidState().isEmpty())
            alpha = 135;
        else if (state.canOcclude())
            alpha = 170;
        else
            alpha = 115;

        return red << 24 | green << 16 | blue << 8 | alpha;
    }

    private static boolean shouldHideMinimapTerrainBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.BARRIER
                || block == Blocks.GLASS_PANE
                || block instanceof TransparentBlock
                || block instanceof StainedGlassPaneBlock;
    }

    private static void putRgba(ByteBuffer buffer, int rgba) {
        buffer.put((byte) ((rgba >> 24) & 0xFF));
        buffer.put((byte) ((rgba >> 16) & 0xFF));
        buffer.put((byte) ((rgba >> 8) & 0xFF));
        buffer.put((byte) (rgba & 0xFF));
    }

    private void resetMinimapTerrainCache() {
        minimapTerrainCenterBlockX = Integer.MIN_VALUE;
        minimapTerrainCenterBlockZ = Integer.MIN_VALUE;
        minimapTerrainPlayerBlockY = Integer.MIN_VALUE;
        minimapTerrainLastUpdateNanos = 0L;
        minimapTerrainDirty = true;
        minimapTerrainDataReady = false;
        minimapTerrainTextureReady = false;
        minimapTerrainBuildInProgress = false;
        minimapTerrainDirtyCount = 0;
        minimapTerrainDirtyCursor = 0;
        minimapViewInitialized = false;
        minimapViewX = 0.0;
        minimapViewZ = 0.0;
        minimapPlayerDots.clear();
        minimapPlayerUuidBuffer.clear();
        minimapTerrainBuffer.clear();
    }

    private static float advanceModuleInformationFade(int slot, boolean active, float deltaSpeed) {
        float progress = moduleInformationFadeProgress[slot] + (active ? deltaSpeed : -deltaSpeed);
        progress = Math.max(0f, Math.min(1f, progress));
        moduleInformationFadeProgress[slot] = progress;
        return progress;
    }

    private static void renderStyledText(NVGU vg, String text, float x, float y, float alpha) {
        StyledTextRenderEntry entry = new StyledTextRenderEntry(text, x, y, alpha);
        vg.beginEffectBatch();
        renderStyledTextEffects(vg, List.of(entry));
        vg.flushEffectBatch();
        renderStyledTextBody(vg, entry);
    }

    private static void renderStyledTextEffects(NVGU vg, List<StyledTextRenderEntry> entries) {
        for (StyledTextRenderEntry entry : entries) {
            renderStyledTextEffect(vg, entry);
        }
    }

    private static void renderStyledTextBodies(NVGU vg, List<StyledTextRenderEntry> entries) {
        for (StyledTextRenderEntry entry : entries) {
            renderStyledTextBody(vg, entry);
        }
    }

    private static void renderStyledTextEffect(NVGU vg, StyledTextRenderEntry entry) {
        // Flat HUD text: no blur pass.
    }

    private static void renderStyledTextBody(NVGU vg, StyledTextRenderEntry entry) {
        String text = entry.text;
        float x = entry.x;
        float y = entry.y;
        float alpha = entry.alpha;
        float textWidth = NVGFonts.INTER.getWidth(text, HUD_FONT_SIZE) + 16f;
        float textHeight = NVGFonts.INTER.getHeight(HUD_FONT_SIZE) + 8f;
        Color textColor = NanoVGTheme.text((int) (255 * alpha));

        NanoVGTheme.renderCompact(vg, x - textWidth / 2f, y, textWidth, textHeight, NanoVGTheme.RADIUS_HUD, alpha);
        NVGFonts.INTER.drawText(text, x, y + 4f, HUD_FONT_SIZE, textColor, Alignment.CENTER_TOP, true);
    }

    private List<ModuleCategory> prepareTabGuiCategories() {
        tabGuiCategoryBuffer.clear();
        for (ModuleCategory category : ModuleCategory.DISPLAY_ORDER) {
            if (category != ModuleCategory.Dev && hasTabGuiModules(category))
                tabGuiCategoryBuffer.add(category);
        }

        tabGuiCategoryIndex = clampTabGuiIndex(tabGuiCategoryIndex, tabGuiCategoryBuffer.size());
        if (tabGuiCategoryBuffer.isEmpty()) {
            tabGuiModuleIndex = 0;
            tabGuiExpanded = false;
        }
        return tabGuiCategoryBuffer;
    }

    private List<Module> prepareTabGuiModules(@Nullable ModuleCategory category) {
        tabGuiModuleBuffer.clear();
        if (category == null) {
            tabGuiModuleIndex = 0;
            return tabGuiModuleBuffer;
        }

        for (Module module : ModuleManager.modules) {
            if (module.moduleCategory == category)
                tabGuiModuleBuffer.add(module);
        }

        tabGuiModuleIndex = clampTabGuiIndex(tabGuiModuleIndex, tabGuiModuleBuffer.size());
        return tabGuiModuleBuffer;
    }

    @Nullable
    private ModuleCategory selectedTabGuiCategory(List<ModuleCategory> categories) {
        if (categories.isEmpty())
            return null;

        tabGuiCategoryIndex = clampTabGuiIndex(tabGuiCategoryIndex, categories.size());
        return categories.get(tabGuiCategoryIndex);
    }

    private int clampTabGuiIndex(int index, int size) {
        if (size <= 0)
            return 0;

        return Math.max(0, Math.min(index, size - 1));
    }

    private int moveTabGuiIndex(int index, int amount, int size) {
        if (size <= 0)
            return 0;

        int next = (index + amount) % size;
        return next < 0 ? next + size : next;
    }

    public boolean isHandlingTabGuiKey(int key, int action) {
        return tempEnabled
                && tabGui.get()
                && !ModuleManager.getModuleState(Stalker.class)
                && mc.gui.screen() == null
                && (action == Keys.PRESS || action == Keys.REPEAT)
                && isTabGuiArrowKey(key);
    }

    private static boolean isTabGuiArrowKey(int key) {
        return key == Keys.KEY_UP
                || key == Keys.KEY_DOWN
                || key == Keys.KEY_LEFT
                || key == Keys.KEY_RIGHT;
    }

    private void updateTabGuiAnimations(float deltaSpeed, List<ModuleCategory> categories, List<Module> modules) {
        tabGuiExpandProgress = animateTabGuiValue(tabGuiExpandProgress, tabGuiExpanded && !modules.isEmpty() ? 1f : 0f, deltaSpeed);

        if (categories.isEmpty()) {
            tabGuiModuleSelectionY = Float.NaN;
            return;
        }

        float y = getTabGuiBaseY() + HudEditor.offsetY(HudEditor.Element.TAB_GUI);

        if (modules.isEmpty()) {
            tabGuiModuleSelectionY = Float.NaN;
            return;
        }

        float moduleHeight = getTabGuiVisiblePanelHeight(modules.size(), y);
        int firstModule = getFirstVisibleTabGuiModuleIndex(modules.size(), moduleHeight);
        float targetModuleSelectionY = y + 5f + (tabGuiModuleIndex - firstModule) * TAB_GUI_ROW_HEIGHT;
        tabGuiModuleSelectionY = Float.isNaN(tabGuiModuleSelectionY)
                ? targetModuleSelectionY
                : animateTabGuiValue(tabGuiModuleSelectionY, targetModuleSelectionY, deltaSpeed);
    }

    private float animateTabGuiValue(float current, float target, float deltaSpeed) {
        float amount = Math.clamp(deltaSpeed * 0.9f, 0.08f, 1f);
        return current + (target - current) * amount;
    }

    private static float easeTabGuiProgress(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return progress * progress * (3f - 2f * progress);
    }

    private void renderTabGuiEffects(NVGU vg, List<ModuleCategory> categories, List<Module> modules) {
        // Flat TabGui panels: no blur pass.
    }

    private void renderTabGuiBody(NVGU vg, List<ModuleCategory> categories, List<Module> modules) {
        if (categories.isEmpty())
            return;

        float x = getLeftHudX() + HudEditor.offsetX(HudEditor.Element.TAB_GUI);
        float y = getTabGuiBaseY() + HudEditor.offsetY(HudEditor.Element.TAB_GUI);
        float categoryWidth = getTabGuiCategoryPanelWidth(categories);
        float categoryHeight = getTabGuiCategoryListHeight(categories.size());
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);
        float modulePanelWidth = expandProgress > 0.01f && !modules.isEmpty()
                ? getTabGuiModulePanelWidth(modules) * expandProgress
                : 0f;
        float modulePanelHeight = expandProgress > 0.01f && !modules.isEmpty()
                ? getTabGuiVisiblePanelHeight(modules.size(), y)
                : 0f;

        HudEditor.register(HudEditor.Element.TAB_GUI, x, y,
                categoryWidth + (modulePanelWidth > 0f ? 6f + modulePanelWidth : 0f),
                Math.max(categoryHeight, modulePanelHeight));

        vg.rectangle(x, y, categoryWidth, categoryHeight, alphaColor(10, 11, 14, 205));
        for (int i = 0, n = categories.size(); i < n; i++) {
            ModuleCategory category = categories.get(i);
            float rowY = y + i * TAB_GUI_ROW_HEIGHT;
            boolean selected = i == tabGuiCategoryIndex;
            NVGFont categoryFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
            if (selected) {
                Color accent = NanoVGTheme.ACCENT;
                vg.rectangle(x, rowY, categoryWidth, TAB_GUI_ROW_HEIGHT - 1f,
                        alphaColor(accent.getRed(), accent.getGreen(), accent.getBlue(), 155));
            }
            categoryFont.drawText(category.label(), x + 5f, rowY + 3f, TAB_GUI_FONT_SIZE,
                    selected ? NanoVGTheme.text(250) : NanoVGTheme.muted(215), Alignment.LEFT_TOP, true);
        }

        if (expandProgress > 0.01f && !modules.isEmpty()) {
            renderTabGuiModulePanel(vg, modules, x + categoryWidth + 6f, y);
        }
    }

    private void renderTabGuiModulePanel(NVGU vg, List<Module> modules, float x, float y) {
        float width = getTabGuiModulePanelWidth(modules);
        float height = getTabGuiVisiblePanelHeight(modules.size(), y);
        int visibleRows = getTabGuiVisibleRows(modules.size(), height);
        int firstModule = getFirstVisibleTabGuiModuleIndex(modules.size(), height);
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);
        float visibleWidth = width * expandProgress;

        vg.pushScissor(x, y, visibleWidth, height);
        vg.globalAlpha(expandProgress, () -> {
            vg.rectangle(x, y, width, height, alphaColor(10, 11, 14, 205));

            int lastModule = Math.min(modules.size(), firstModule + visibleRows);
            if (!Float.isNaN(tabGuiModuleSelectionY)) {
                renderTabGuiSelectionMark(vg, x, tabGuiModuleSelectionY, width);
            }

            for (int i = firstModule; i < lastModule; i++) {
                Module module = modules.get(i);
                float rowY = y + 5f + (i - firstModule) * TAB_GUI_ROW_HEIGHT;
                NVGFont moduleFont = module.tempEnabled ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
                Color moduleColor = module.tempEnabled ? NanoVGTheme.ACCENT : NanoVGTheme.muted(238);
                moduleFont.drawText(module.moduleName, x + 12f, rowY + 2.5f, TAB_GUI_FONT_SIZE, moduleColor, Alignment.LEFT_TOP, true);
            }

            if (modules.size() > visibleRows) {
                float trackY = y + 10f;
                float trackHeight = height - 16f;
                float thumbHeight = Math.max(12f, trackHeight * visibleRows / modules.size());
                float thumbY = trackY + (trackHeight - thumbHeight) * firstModule / (modules.size() - visibleRows);
                vg.rectangle(x + width - 4f, thumbY, 2f, thumbHeight, NanoVGTheme.scrollbarThumb(150));
            }
        });
        vg.popScissor();
    }

    private static void renderTabGuiSelectionMark(NVGU vg, float x, float y, float width) {
        vg.rectangle(x + 5f, y + 3f, 2f, 12f, NanoVGTheme.ACCENT);
    }

    private float getTabGuiCategoryPanelWidth(List<ModuleCategory> categories) {
        float width = 120f;
        for (ModuleCategory category : categories) {
            width = Math.max(width, NVGFonts.INTER_MEDIUM.getWidth(category.label(), TAB_GUI_FONT_SIZE) + 12f);
        }
        return width;
    }

    private float getTabGuiModulePanelWidth(List<Module> modules) {
        float width = 72f;
        for (Module module : modules) {
            width = Math.max(width, NVGFonts.INTER_MEDIUM.getWidth(module.moduleName, TAB_GUI_FONT_SIZE) + 18f);
        }
        return width;
    }

    private static float getTabGuiVisiblePanelHeight(int rowCount, float y) {
        if (rowCount <= 0)
            return 0f;

        float panelHeight = getTabGuiPanelHeight(rowCount);
        float availableHeight = NanoVGManager.getScaledScreenHeight() - y - 12f;
        return Math.min(panelHeight, Math.max(getTabGuiPanelHeight(1), availableHeight));
    }

    private static int getTabGuiVisibleRows(int rowCount, float panelHeight) {
        int visibleRows = (int) ((panelHeight - 10f) / TAB_GUI_ROW_HEIGHT);
        return Math.max(1, Math.min(rowCount, visibleRows));
    }

    private int getFirstVisibleTabGuiModuleIndex(int moduleCount, float panelHeight) {
        int visibleRows = getTabGuiVisibleRows(moduleCount, panelHeight);
        if (moduleCount <= visibleRows)
            return 0;

        int first = tabGuiModuleIndex - visibleRows / 2;
        return Math.max(0, Math.min(first, moduleCount - visibleRows));
    }

    private List<ModuleListEntry> prepareModuleListEntries(long deltaTime) {
        if (sortedModulesDirty || sortedModules == null || moduleListSortWidthCache.size() != sortedModules.size())
            refreshSortedModules();

        List<Module> sourceModules = sortedModules != null ? sortedModules : Collections.emptyList();
        List<ModuleListEntry> renderEntries = moduleListEntryBuffer;
        renderEntries.clear();
        moduleListChangedSortKeys.clear();
        float speed = 0.14f * (deltaTime / 16f);

        for (Module m : sourceModules) {
            if (!m.showOnArray) continue;

            boolean isActive = m.tempEnabled;
            ModuleListEntry entry = moduleListEntryCache.get(m);
            if (!isActive && (entry == null || entry.progress <= 0.0f)) continue;
            if (entry == null) {
                entry = new ModuleListEntry();
                moduleListEntryCache.put(m, entry);
            }

            float progress = entry.progress;
            progress = isActive ? Math.min(1.0f, progress + speed) : Math.max(0.0f, progress - speed);

            if (progress <= 0.0f && !isActive) {
                entry.progress = 0.0f;
                continue;
            }

            String text = m.moduleName;
            String tagText = getModuleListTagText(m);
            entry.update(text, tagText, progress, MODULE_LIST_FONT_SIZE);
            Float sortedWidth = moduleListSortWidthCache.get(m);
            if (sortedWidth == null || Float.compare(sortedWidth, entry.fullWidth) != 0) {
                moduleListSortWidthCache.put(m, entry.fullWidth);
                moduleListChangedSortKeys.add(m);
            }
            renderEntries.add(entry);
        }

        if (!moduleListChangedSortKeys.isEmpty()) {
            updateChangedModuleSortKeys();
            rebuildModuleListEntriesInSortOrder(renderEntries);
        }

        return renderEntries;
    }

    private void updateChangedModuleSortKeys() {
        List<Module> reorderedModules = new ArrayList<>(sortedModules);
        reorderedModules.removeAll(moduleListChangedSortKeys);

        for (Module module : moduleListChangedSortKeys) {
            int insertionIndex = Collections.binarySearch(reorderedModules, module, this::compareModuleListOrder);
            if (insertionIndex < 0)
                insertionIndex = -insertionIndex - 1;
            reorderedModules.add(insertionIndex, module);
        }

        sortedModules = List.copyOf(reorderedModules);
    }

    private int compareModuleListOrder(Module first, Module second) {
        float firstWidth = moduleListSortWidthCache.getOrDefault(first, 0f);
        float secondWidth = moduleListSortWidthCache.getOrDefault(second, 0f);
        int widthComparison = Float.compare(secondWidth, firstWidth);
        if (widthComparison != 0)
            return widthComparison;

        int nameComparison = String.CASE_INSENSITIVE_ORDER.compare(first.moduleName, second.moduleName);
        if (nameComparison != 0)
            return nameComparison;

        return first.moduleName.compareTo(second.moduleName);
    }

    private void rebuildModuleListEntriesInSortOrder(List<ModuleListEntry> renderEntries) {
        renderEntries.clear();
        for (Module module : sortedModules) {
            ModuleListEntry entry = moduleListEntryCache.get(module);
            if (entry != null && entry.progress > 0.0f)
                renderEntries.add(entry);
        }
    }

    private List<ModuleListRenderEntry> prepareModuleListRenderEntries(List<ModuleListEntry> entries) {
        List<ModuleListRenderEntry> renderEntries = moduleListRenderEntryBuffer;
        renderEntries.clear();
        if (entries.isEmpty())
            return renderEntries;

        float listY = HudEditor.offsetY(HudEditor.Element.MODULE_LIST);
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float fontHeight = NVGFonts.INTER.getHeight(MODULE_LIST_FONT_SIZE);
        float rowPadding = 0.5f + Math.max(0f, moduleListSpacing.get());
        float rowHeight = fontHeight + rowPadding * 2f;
        float rightMargin = -HudEditor.offsetX(HudEditor.Element.MODULE_LIST);
        float horizontalPadding = Math.max(0f, moduleListPadding.get());
        float rightX = screenWidth - rightMargin;
        int visibleCount = countVisibleModuleListEntries(entries, rowHeight);

        if (visibleCount == 0)
            return renderEntries;

        int visibleIndex = 0;
        long colorTimeMillis = System.currentTimeMillis();
        for (ModuleListEntry entry : entries) {
            if (rowHeight * entry.progress <= 0.5f)
                continue;

            float textX = rightX - horizontalPadding - entry.fullWidth;
            int alpha = Math.clamp((int) (entry.progress * 255f), 0, 255);
            float leftX = textX - horizontalPadding;
            float topPadding = rowPadding;
            float bottomPadding = rowPadding;
            float effectiveHeight = (fontHeight + topPadding + bottomPadding) * entry.progress;
            float textY = listY + topPadding;

            while (moduleListRenderEntryPool.size() <= visibleIndex)
                moduleListRenderEntryPool.add(new ModuleListRenderEntry());
            ModuleListRenderEntry renderEntry = moduleListRenderEntryPool.get(visibleIndex);
            renderEntry.update(
                    entry,
                    leftX,
                    listY,
                    rightX - leftX,
                    effectiveHeight,
                    textX,
                    textY,
                    alpha,
                    visibleIndex,
                    visibleCount,
                    colorTimeMillis
            );
            renderEntries.add(renderEntry);
            listY += effectiveHeight;
            visibleIndex++;
        }

        for (int i = 0, n = renderEntries.size(); i < n; i++) {
            ModuleListRenderEntry entry = renderEntries.get(i);
            float radius = Math.min(NanoVGTheme.RADIUS_HUD, entry.height / 2f);
            ConnectedHudRect previous = i > 0 ? renderEntries.get(i - 1) : null;
            ConnectedHudRect next = i + 1 < n ? renderEntries.get(i + 1) : null;
            entry.updateCornerRadii(
                    getExposedHudCornerRadius(previous, entry.x, radius),
                    getExposedHudCornerRadius(previous, entry.right(), radius),
                    getExposedHudCornerRadius(next, entry.right(), radius),
                    getExposedHudCornerRadius(next, entry.x, radius)
            );
        }

        return renderEntries;
    }

    private static void renderModuleListEffects(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
    }

    private void renderModuleListRowBackgrounds(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
        boolean background = moduleListBackground.get();
        boolean accentBar = moduleListAccentBar.get();
        if (!background && !accentBar) return;

        for (ModuleListRenderEntry entry : renderEntries) {
            int alpha = Math.clamp(entry.alpha, 0, 255);
            if (alpha <= 0 || entry.width <= 0f || entry.height <= 0f)
                continue;

            if (background)
                vg.rectangle(entry.x, entry.y, entry.width, entry.height,
                        NanoVGTheme.background((int) (alpha * 0.40f)));

            if (accentBar) {
                float barWidth = Math.min(MODULE_LIST_ACCENT_WIDTH, entry.width);
                Color color = moduleListOutlineColor((int) (alpha * 0.60f));
                vg.rectangle(entry.right() - barWidth, entry.y, barWidth, entry.height, color);
            }
        }
    }

    private static Color moduleListOutlineColor(int alpha) {
        Color color = NanoVGTheme.blend(NanoVGTheme.ACCENT, MODULE_LIST_EDGE_NEUTRAL, 0.70f);
        return alphaColor(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void renderModuleListSteppedOutline(NVGU vg, List<ModuleListRenderEntry> entries) {
        final float line = 0.75f;
        for (int i = 0; i < entries.size(); i++) {
            ModuleListRenderEntry entry = entries.get(i);
            if (entry.width <= 0f || entry.height <= 0f || entry.alpha <= 0)
                continue;
            Color color = moduleListOutlineColor((int) (entry.alpha * 0.56f));
            vg.rectangle(entry.x, entry.y, line, entry.height, color);
            vg.rectangle(entry.right() - line, entry.y, line, entry.height, color);
            if (i == 0)
                vg.rectangle(entry.x, entry.y, entry.width, line, color);
            if (i + 1 == entries.size()) {
                vg.rectangle(entry.x, entry.y + entry.height - line, entry.width, line, color);
            } else {
                ModuleListRenderEntry next = entries.get(i + 1);
                float x = Math.min(entry.x, next.x);
                float width = Math.abs(next.x - entry.x);
                if (width > 0.01f) {
                    Color stepColor = moduleListOutlineColor((int) (Math.min(entry.alpha, next.alpha) * 0.56f));
                    vg.rectangle(x, entry.y + entry.height - line / 2f, width, line, stepColor);
                }
            }
        }
    }

    private static final class ModuleListGlyphColour implements NVGU.GlyphColour {
        private int alpha;
        private float rowLeft;
        private float rowWidth = 1f;
        private boolean gradient;

        private void update(ModuleListRenderEntry entry) {
            this.alpha = entry.alpha;
            this.rowLeft = entry.x;
            this.rowWidth = Math.max(1f, entry.width);
            this.gradient = moduleListGradient.get();
        }

        @Override
        public Color colourAt(float glyphCenterX) {
            Color accent = gradient
                    ? NanoVGTheme.blend(NanoVGTheme.ACCENT, NanoVGTheme.ACCENT_END,
                    (glyphCenterX - rowLeft) / rowWidth)
                    : NanoVGTheme.ACCENT;
            // Keep the chosen theme, but soften the colour like the reference.
            Color muted = NanoVGTheme.blend(accent, MODULE_LIST_TEXT_NEUTRAL, 0.68f);
            return new Color(muted.getRed(), muted.getGreen(), muted.getBlue(), Math.clamp(alpha, 0, 255));
        }
    }

    private final ModuleListGlyphColour moduleListGlyphColour = new ModuleListGlyphColour();

    private static final class WatermarkGlyphColour implements NVGU.GlyphColour {
        private long timeMillis;
        private float originX;
        private float startX;
        private float endX;
        private int fadeTarget = 255;

        private void update(String text, float x, float size, int fadeTarget) {
            this.fadeTarget = fadeTarget;
            this.timeMillis = System.currentTimeMillis();
            this.originX = x;
            if (text.isEmpty()) {
                this.startX = x;
                this.endX = x;
                return;
            }

            int firstEnd = text.offsetByCodePoints(0, 1);
            int lastStart = text.offsetByCodePoints(text.length(), -1);
            float width = NVGFonts.INTER.getWidth(text, size);
            this.startX = x + NVGFonts.INTER.getWidth(text.substring(0, firstEnd), size) * 0.5f;
            this.endX = x + width - NVGFonts.INTER.getWidth(text.substring(lastStart), size) * 0.5f;
        }

        @Override
        public Color colourAt(float glyphCenterX) {
            Color rainbow = getFadedColor(0, 1, 255, timeMillis, (originX - glyphCenterX) / MODULE_LIST_HORIZONTAL_GRADIENT_SPAN);
            float progress = endX > startX ? Math.clamp((glyphCenterX - startX) / ((endX - startX) * WATERMARK_FADE_LENGTH), 0f, 1f) : 0f;
            float fade = progress * progress * (3f - 2f * progress);
            return new Color(
                    Math.round(rainbow.getRed() + (fadeTarget - rainbow.getRed()) * fade),
                    Math.round(rainbow.getGreen() + (fadeTarget - rainbow.getGreen()) * fade),
                    Math.round(rainbow.getBlue() + (fadeTarget - rainbow.getBlue()) * fade),
                    255
            );
        }
    }

    private final WatermarkGlyphColour watermarkGlyphColour = new WatermarkGlyphColour();

    private void renderModuleListBody(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
        if (renderEntries.isEmpty()) return;

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (ModuleListRenderEntry entry : renderEntries) {
            minX = Math.min(minX, entry.x);
            minY = Math.min(minY, entry.y);
            maxX = Math.max(maxX, entry.x + entry.width);
            maxY = Math.max(maxY, entry.y + entry.height);
        }
        if (Float.isFinite(minX) && Float.isFinite(minY))
            HudEditor.register(HudEditor.Element.MODULE_LIST, minX, minY, maxX - minX, maxY - minY);

        renderModuleListRowBackgrounds(vg, renderEntries);

        if (moduleListOutline.get())
            renderModuleListSteppedOutline(vg, renderEntries);

        if (renderModuleListGlyphBatch(vg, renderEntries))
            return;

        for (ModuleListRenderEntry entry : renderEntries) {
            boolean clipAnimatedEntry = entry.alpha != 255;
            boolean shadow = moduleListShadow.get();
            if (clipAnimatedEntry)
                vg.pushScissor(entry.x, entry.y, entry.width, entry.height);
            try {
                moduleListGlyphColour.update(entry);
                NVGFonts.INTER.drawGradientText(entry.entry.text, entry.textX, entry.textY, MODULE_LIST_FONT_SIZE,
                        Alignment.LEFT_TOP, shadow, moduleListGlyphColour);
                if (entry.entry.tagText != null) {
                    NVGFonts.INTER.drawGradientText(entry.entry.tagText,
                            entry.textX + entry.entry.textWidth, entry.textY, MODULE_LIST_FONT_SIZE,
                            Alignment.LEFT_TOP, shadow, moduleListGlyphColour);
                }
            } finally {
                if (clipAnimatedEntry)
                    vg.popScissor();
            }
        }
    }

    private boolean renderModuleListGlyphBatch(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
        if (!vg.beginGlyphBatch())
            return false;

        String font = NVGFonts.INTER.getIdentifier();
        boolean shadow = moduleListShadow.get();
        float shadowOffset = shadow ? NVGFont.SHADOW_OFFSET : 0f;
        for (ModuleListRenderEntry entry : renderEntries) {
            boolean clip = entry.alpha != 255;
            float clipX = clip ? entry.x : 0f;
            float clipY = clip ? entry.y : 0f;
            float clipWidth = clip ? entry.width : Float.POSITIVE_INFINITY;
            float clipHeight = clip ? entry.height : Float.POSITIVE_INFINITY;
            moduleListGlyphColour.update(entry);
            boolean added = vg.addGlyphRun(entry.entry.text, entry.textX, entry.textY, font, MODULE_LIST_FONT_SIZE,
                    moduleListGlyphColour, null, shadow, shadowOffset, shadowOffset,
                    clipX, clipY, clipWidth, clipHeight);
            if (added && entry.entry.tagText != null) {
                added = vg.addGlyphRun(entry.entry.tagText, entry.textX + entry.entry.textWidth, entry.textY, font, MODULE_LIST_FONT_SIZE,
                        moduleListGlyphColour, null, shadow, shadowOffset, shadowOffset,
                        clipX, clipY, clipWidth, clipHeight);
            }
            if (!added) {
                vg.cancelGlyphBatch();
                return false;
            }
        }

        vg.flushGlyphBatch();
        return true;
    }

    private void ensureModuleListBackgroundGeometryCapacity(int rectangleCount) {
        int requiredFloats = rectangleCount * 8;
        if (requiredFloats <= moduleListBackgroundGeometry.length)
            return;

        int newLength = Math.max(requiredFloats, moduleListBackgroundGeometry.length * 2);
        moduleListBackgroundGeometry = Arrays.copyOf(moduleListBackgroundGeometry, newLength);
    }

    private static int countVisibleModuleListEntries(List<ModuleListEntry> renderEntries, float rowHeight) {
        int visibleCount = 0;
        for (ModuleListEntry entry : renderEntries) {
            if (rowHeight * entry.progress > 0.5f)
                visibleCount++;
        }
        return visibleCount;
    }

    private static String getModuleListTagText(Module module) {
        if (!moduleListTags.get()) return null;
        String tag = module.tag();
        return tag != null && !tag.isEmpty() ? " " + tag : null;
    }

    private static float getModuleListWidth(Module module, float fontSize) {
        String tagText = getModuleListTagText(module);
        return NVGFonts.INTER.getWidth(module.moduleName, fontSize) + (tagText != null ? NVGFonts.INTER.getWidth(tagText, fontSize) : 0f);
    }

    private void renderWatermark(NVGU vg) {
        String watermark = waterMarkText.get();
        if (watermark == null || watermark.isBlank()) {
            watermark = "kanade";
        } else if (watermark.trim().equalsIgnoreCase("krs")) {
            watermark = "kanade";
            waterMarkText.set("kanade");
        }

        String server = mc.isLocalServer() ? "Singleplayer"
                : mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "";
        String meta = Client.clientVersion + (server == null || server.isBlank() ? "" : "  " + server);
        boolean stacked = waterMarkMode.get().equalsIgnoreCase("bullshit");
        float x = 4f + HudEditor.offsetX(HudEditor.Element.WATERMARK);
        float y = 3f + HudEditor.offsetY(HudEditor.Element.WATERMARK);
        float size = 12f;
        float wordWidth = NVGFonts.INTER_MEDIUM.getWidth(watermark, size);
        float metaWidth = NVGFonts.INTER.getWidth(meta, 10f);
        float width = stacked ? Math.max(wordWidth + 17f, metaWidth + 9f) : wordWidth + metaWidth + 23f;
        float height = stacked ? 32f : 21f;
        HudEditor.register(HudEditor.Element.WATERMARK, x, y, width, height);
        vg.rectangle(x, y, width, height, NanoVGTheme.background(205));
        vg.rectangle(x, y, width, 1f, NanoVGTheme.ACCENT);
        vg.rectangle(x + 3f, y + 5f, 2f, 11f, NanoVGTheme.ACCENT);
        NVGFonts.INTER_MEDIUM.drawText(watermark, x + 9f, y + 4f, size,
                NanoVGTheme.text(245), Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText(meta, stacked ? x + 9f : x + 15f + wordWidth,
                stacked ? y + 19f : y + 5f, 10f, NanoVGTheme.muted(220), Alignment.LEFT_TOP, true);
    }

    private void renderWatermarkEffects(NVGU vg) {
    }

    private void renderScaffoldBlocks(NVGU vg) {
        int blockCount = Scaffold.countUsableHotbarBlocks(mc.player.getInventory());
        String text = blockCount + " blocks";
        float width = NVGFonts.INTER.getWidth(text, 8.5f) + 12f;
        float height = 13f;
        float x = (NanoVGManager.getScaledScreenWidth() - width) / 2f;
        float y = NanoVGManager.getScaledScreenHeight() / 2f + 16f;

        vg.rectangle(x, y, width, height, new Color(4, 3, 8, 165));
        vg.roundedRectangleBorder(x + 0.5f, y + 0.5f, width - 1f, height - 1f, 0f, 1f,
                new Color(255, 255, 255, 42), Border.INSIDE);
        NVGFonts.INTER.drawText(text, x + width / 2f, y + 2.5f, 8.5f,
                new Color(233, 226, 240, 235), Alignment.CENTER_TOP, true);
    }

    private static String getRomanNumeral(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    public static void reloadSortedModules() {
        sortedModulesDirty = true;
    }

    private void refreshSortedModules() {
        final float fontSize = MODULE_LIST_FONT_SIZE;
        List<ModuleSortEntry> entries = new ArrayList<>();
        moduleListSortWidthCache.clear();
        for (Module module : ModuleManager.allModules) {
            if (!module.showOnArray)
                continue;

            float width = getModuleListWidth(module, fontSize);
            moduleListSortWidthCache.put(module, width);
            entries.add(new ModuleSortEntry(module, width));
        }

        entries.sort((first, second) -> {
            int widthComparison = Float.compare(second.width, first.width);
            if (widthComparison != 0)
                return widthComparison;

            int nameComparison = String.CASE_INSENSITIVE_ORDER.compare(first.module.moduleName, second.module.moduleName);
            if (nameComparison != 0)
                return nameComparison;

            return first.module.moduleName.compareTo(second.module.moduleName);
        });

        List<Module> modules = new ArrayList<>(entries.size());
        for (int i = 0, n = entries.size(); i < n; i++) {
            modules.add(entries.get(i).module);
        }
        sortedModules = List.copyOf(modules);
        sortedModulesDirty = false;
    }

    @Override
    public void onKey(KeyboardEvent event) {
        if (!isHandlingTabGuiKey(event.key, event.action))
            return;

        List<ModuleCategory> categories = prepareTabGuiCategories();
        if (categories.isEmpty())
            return;

        ModuleCategory selectedCategory = selectedTabGuiCategory(categories);
        List<Module> modules = prepareTabGuiModules(selectedCategory);

        switch (event.key) {
            case Keys.KEY_UP -> {
                if (tabGuiExpanded && !modules.isEmpty()) {
                    tabGuiModuleIndex = moveTabGuiIndex(tabGuiModuleIndex, -1, modules.size());
                } else {
                    tabGuiCategoryIndex = moveTabGuiIndex(tabGuiCategoryIndex, -1, categories.size());
                    tabGuiModuleIndex = 0;
                }
            }
            case Keys.KEY_DOWN -> {
                if (tabGuiExpanded && !modules.isEmpty()) {
                    tabGuiModuleIndex = moveTabGuiIndex(tabGuiModuleIndex, 1, modules.size());
                } else {
                    tabGuiCategoryIndex = moveTabGuiIndex(tabGuiCategoryIndex, 1, categories.size());
                    tabGuiModuleIndex = 0;
                }
            }
            case Keys.KEY_RIGHT -> {
                if (!tabGuiExpanded) {
                    tabGuiExpanded = true;
                    tabGuiModuleIndex = clampTabGuiIndex(tabGuiModuleIndex, modules.size());
                } else if (event.action == Keys.PRESS && !modules.isEmpty()) {
                    modules.get(tabGuiModuleIndex).toggle();
                }
            }
            case Keys.KEY_LEFT -> {
                if (tabGuiExpanded)
                    tabGuiExpanded = false;
            }
        }
    }

    @Override
    public void onDisable() {
        data.clear();
        targetHudEntryBuffer.clear();
        targetHudFrontToBackBuffer.clear();
        targetHudVisibleEntryBuffer.clear();
        moduleListEntryBuffer.clear();
        moduleListEntryCache.clear();
        moduleListRenderEntryBuffer.clear();
        moduleListRenderEntryPool.clear();
        moduleInformationEntryBuffer.clear();
        tabGuiCategoryBuffer.clear();
        tabGuiModuleBuffer.clear();
        informationEntryBuffer.clear();
        potionEntryBuffer.clear();
        connectionEntryBuffer.clear();
        clearPlayerListEntries();
        tabGuiExpanded = false;
        tabGuiExpandProgress = 0f;
        tabGuiModuleSelectionY = Float.NaN;
        lastRenderNanos = 0L;
        clearTargetHudTracking();
        resetMinimapTerrainCache();
    }

    @Override
    public void onEnable() {
        lastRenderNanos = 0L;
        resetMinimapTerrainCache();
    }

    @Override
    public void onWorld(WorldEvent event) {
        sessionStartedAtMillis = mc.player != null && mc.level != null ? System.currentTimeMillis() : 0L;
        sessionKills = 0;
        sessionDeaths = 0;
        sessionLocalWasDead = false;
        sessionCountedKills.clear();
        clearPlayerListFaces();
        data.clear();
        clearTargetHudTracking();
        targetHudEntryBuffer.clear();
        targetHudFrontToBackBuffer.clear();
        targetHudVisibleEntryBuffer.clear();
        informationEntryBuffer.clear();
        potionEntryBuffer.clear();
        connectionEntryBuffer.clear();
        clearPlayerListEntries();
        lastRenderNanos = 0L;
        resetMinimapTerrainCache();
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        applyThemeFromSettings();
        if (mc.player == null || mc.level == null) return;
        // Keep the HUD (and therefore the editor's module geometry) alive while the editor is open.
        if (mc.gui.hud.isHidden() && !HudEditor.isEditing()) return;

        Client.nanoVgManager.load(vg -> {
            beginHudFrame();
            long now = System.nanoTime();
            long deltaNanos = lastRenderNanos == 0L ? 16_666_667L : now - lastRenderNanos;
            lastRenderNanos = now;
            long deltaTime = Math.clamp(deltaNanos / 1_000_000L, 1L, 100L);

            float deltaSpeed = 0.16f * (deltaTime / 16f);
            deltaSpeed = Math.min(deltaSpeed, 1.0f);
            float interfaceAlpha = NanoVGManager.shouldRenderBelowDebugOverlay() ? 0.2f : 1f;
            float finalDeltaSpeed = deltaSpeed;

            vg.globalAlpha(interfaceAlpha, () -> {
                if (playerList.get())
                    updatePlayerListHeightAnimation(finalDeltaSpeed);
                else
                    playerListDisplayHeight = Float.NaN;

                List<ModuleListEntry> moduleListEntries = moduleList.get() ? prepareModuleListEntries(deltaTime) : Collections.emptyList();
                List<ModuleListRenderEntry> moduleListRenderEntries = moduleList.get()
                        ? prepareModuleListRenderEntries(moduleListEntries)
                        : Collections.emptyList();
                SomeInformationRenderState someInformationState = someInformation.get() ? prepareSomeInformation() : null;
                if (moduleInformation.get())
                    prepareModuleInformationEntries(moduleInformationEntryBuffer, NanoVGManager.getScaledScreenHeight() / 2f - 300f, finalDeltaSpeed);
                else
                    moduleInformationEntryBuffer.clear();
                List<ModuleCategory> tabGuiCategories = tabGui.get() ? prepareTabGuiCategories() : Collections.emptyList();
                List<Module> tabGuiModules = Collections.emptyList();
                if (tabGui.get()) {
                    ModuleCategory selectedTabGuiCategory = selectedTabGuiCategory(tabGuiCategories);
                    if (selectedTabGuiCategory != null && (tabGuiExpanded || tabGuiExpandProgress > 0.01f))
                        tabGuiModules = prepareTabGuiModules(selectedTabGuiCategory);
                    updateTabGuiAnimations(finalDeltaSpeed, tabGuiCategories, tabGuiModules);
                } else {
                    tabGuiExpandProgress = 0f;
                    tabGuiModuleSelectionY = Float.NaN;
                }
                final List<Module> tabGuiModulesForDraw = tabGuiModules;
                if (notifications.get())
                    Client.notificationManager.prepareNotifications();

                vg.beginEffectBatch();
                if (waterMark.get() && (mc.gui.screen() == null || mc.gui.screen() instanceof NanoVGClickGuiScreen || HudEditor.isEditing()))
                    renderWatermarkEffects(vg);
                if (moduleList.get())
                    renderModuleListEffects(vg, moduleListRenderEntries);
                if (playerList.get())
                    renderPlayerListEffects(vg);
                if (miniMap.get())
                    renderMinimapEffects(vg);
                if (someInformationState != null)
                    renderSomeInformationEffects(vg, someInformationState);
                if (moduleInformation.get())
                    renderStyledTextEffects(vg, moduleInformationEntryBuffer);
                if (notifications.get())
                    Client.notificationManager.renderNotificationEffects(vg);
                if (tabGui.get())
                    renderTabGuiEffects(vg, tabGuiCategories, tabGuiModules);
                vg.flushEffectBatch();

                if (waterMark.get() && (mc.gui.screen() == null || mc.gui.screen() instanceof NanoVGClickGuiScreen || HudEditor.isEditing()))
                    HudEditor.withScale(vg, HudEditor.Element.WATERMARK, () -> renderWatermark(vg));

                if (moduleList.get())
                    HudEditor.withScale(vg, HudEditor.Element.MODULE_LIST,
                            () -> renderModuleListBody(vg, moduleListRenderEntries));

                if (sessionInfo.get())
                    HudEditor.withScale(vg, HudEditor.Element.SESSION, () -> renderSessionInfo(vg));

                if (playerList.get())
                    HudEditor.withScale(vg, HudEditor.Element.PLAYER_LIST, () -> renderPlayerList(vg));

                if (miniMap.get())
                    HudEditor.withScale(vg, HudEditor.Element.MINIMAP, () -> renderMinimap(vg, deltaTime));

                if (someInformationState != null)
                    HudEditor.withScale(vg, HudEditor.Element.SOME_INFO,
                            () -> renderSomeInformationBody(vg, someInformationState));

                if (moduleInformation.get())
                    renderStyledTextBodies(vg, moduleInformationEntryBuffer);

                if (notifications.get())
                    HudEditor.withScale(vg, HudEditor.Element.NOTIFICATIONS,
                            () -> Client.notificationManager.renderNotificationBodies(vg));

                if (tabGui.get())
                    HudEditor.withScale(vg, HudEditor.Element.TAB_GUI,
                            () -> renderTabGuiBody(vg, tabGuiCategories, tabGuiModulesForDraw));

                if (scaffoldBlocks.get() && ModuleManager.getModuleState(Scaffold.class) && mc.player != null)
                    renderScaffoldBlocks(vg);
            });
        });
    }

    static void collectTargetHudData(Render3DEvent event) {
        if (mc.player == null || mc.level == null || !ModuleManager.getModuleState(TargetHud.class)) {
            data.clear();
            clearTargetHudTracking();
            return;
        }
        if (!RenderUtil.shouldRenderWorldHudOverlays()) {
            data.clear();
            return;
        }

        float partialTicks = event.partialTicks;
        data.clear();

        BehaviorUtils.fillTargetList(targetBuffer);

        if (!targetBuffer.isEmpty()) {
            int existingSize = actualTargetEntitiesData.size();
            targetUuidBuffer.clear();
            for (int i = 0; i < existingSize; i++) {
                targetUuidBuffer.add(actualTargetEntitiesData.get(i).getSecond());
            }
            for (int i = 0, n = targetBuffer.size(); i < n; i++) {
                Entity target = targetBuffer.get(i);
                UUID uuid = target.getUUID();
                if (!targetUuidBuffer.contains(uuid)) {
                    actualTargetEntitiesData.add(new Tuple<>(target, uuid));
                }
            }
        }

        if (actualTargetEntitiesData.isEmpty()) return;

        if (isTargetHud2D()) {
            for (Tuple<Entity, UUID> pair : actualTargetEntitiesData) {
                Entity target = pair.getFirst();
                if (target == null || target.isRemoved())
                    continue;
                data.add(new TargetHudData(pair.getSecond(), target, 0f, 0f, 0.0));
            }
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        Vec3 forward = mc.player.getViewVector(event.selfPartialTicks);
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize().scale(0.7);

        for (int i = 0; i < actualTargetEntitiesData.size();) {
            Tuple<Entity, UUID> pair = actualTargetEntitiesData.get(i);
            Entity entity = pair.getFirst();
            UUID targetId = pair.getSecond();
            Vec3 lerpedPos = RenderUtil.INSTANCE.getLerpedPos(entity, partialTicks).add(right);
            Vec3 targetHudWorldPos = new Vec3(lerpedPos.x, lerpedPos.y + entity.getBbHeight(), lerpedPos.z);
            Vec3 screenPos = RenderUtil.INSTANCE.isEntityInCameraFrustum(entity, partialTicks)
                    ? RenderUtil.INSTANCE.worldToScreen(targetHudWorldPos)
                    : null;
            if (screenPos != null) {
                float hudX = NanoVGManager.fromFramebufferX(screenPos.x);
                float hudY = NanoVGManager.fromFramebufferY(screenPos.y);
                double depthSquared = targetHudWorldPos.distanceToSqr(cameraPos);
                data.add(new TargetHudData(targetId, entity, hudX, hudY, depthSquared));
            } else {
                boolean activeTarget = BehaviorUtils.isTarget(entity);
                if (activeTarget) {
                    i++;
                    continue;
                }

                actualTargetEntitiesData.remove(i);
                TargetHudState removedState = hudStates.remove(targetId);
                if (removedState != null)
                    removedState.resetPlayerTexture();
                continue;
            }
            i++;
        }
    }
}
