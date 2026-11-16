package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.configs.ConfigManager;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.ModuleTags;
import com.instrumentalist.krs.hacks.features.movement.InventoryMove;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.render.GuiTheme;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ColorValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.SettingValue;
import com.instrumentalist.krs.utils.value.TextValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.command.ChatCommandTree;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.NVGFont;
import xyz.breadloaf.imguimc.customwindow.ModuleRenderable;

import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

public class NanoVGClickGuiScreen extends Screen {
    private static final float LAYOUT = 1.35f;
    private static final float MIN_LAYOUT_SCALE = 0.7f;
    private static final float MAX_LAYOUT_SCALE = 1.5f;
    private static final float LAYOUT_SCALE_STEP = 0.08f;
    private static final float BOTTOM_BAR_HEIGHT = 0f;
    // Measured from the full 1920 x 1080 Atmosphere reference, not its cropped close-ups.
    private static final float ATMOSPHERE_COLUMN_SCREEN_RATIO = 150f / 1920f;
    private static final float ATMOSPHERE_TOP_RATIO = 216f / 1080f;
    private static final float COLUMN_ROW_FONT = 11f;
    private static final Color TOP_BAR_BUTTON_BACKGROUND = new Color(10, 7, 16, 165);
    private static final int WRAP_CACHE_LIMIT = 4096;
    private static final Map<WrapKey, List<String>> WRAP_CACHE = new HashMap<>();
    private static final Object INFO_TOOLTIP_TARGET = new Object();
    private static final int ROOT_PANEL_BACKGROUND_ALPHA_OFFSET = 70;
    private static final int COMPACT_BACKGROUND_ALPHA_OFFSET = 35;

    // Runtime scaled layout metrics, refreshed whenever the GUI scale changes.
    private float topBarHeight;
    private float atmosphereTitleBarHeight;
    private float atmosphereScale = 1f;
    private float columnGap;
    private float columnHeaderHeight;
    private float columnRowHeight;
    private float columnPadding;
    private float columnArrowSize;
    private float inlineSettingRowHeight;
    private float inlineSettingGap;
    private float inlineOptionRowHeight;
    private float inlineColorRowHeight;
    private float inlineColorSwatchWidth;
    private float inlineColorSwatchHeight;
    private float panelHeaderHeight;
    private float panelFooterHeight;
    private float panelPadding;
    private float categorySidebarWidth;
    private float categoryContentGap;
    private float settingsPanelHeaderHeight;
    private float settingsDockGap;
    private float rowHeight;
    private float settingRowHeight;
    private float settingRowStep;
    private float switchWidth;
    private float switchHeight;
    private float nameLineHeight;

    private static final DateTimeFormatter CONFIG_UPDATED_FORMAT = DateTimeFormatter
            .ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CONFIG_LIST_UPDATED_FORMAT = DateTimeFormatter
            .ofPattern("MM/dd", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private static NanoVGClickGuiScreen detachedClosingScreen;
    private static Module rememberedSettingsPanelModule;
    private static ConfigEntry rememberedSettingsPanelConfig;
    private static final Set<Module> rememberedExpandedModules = Collections.newSetFromMap(new IdentityHashMap<>());
    private static float rememberedLayoutScale = 1f;
    private static ModuleCategory rememberedSelectedCategory = ModuleCategory.Combat;
    private static boolean rememberedConfigView;
    private static ConfigTab rememberedConfigTab = ConfigTab.MODULE;
    private static boolean rememberedCommandView;
    private static CommandTab rememberedCommandTab = CommandTab.COMMAND;
    private static boolean rememberedClientSettingsView;
    private static ModuleTags.Pvp rememberedPvpTab;
    private static ModuleTags.Visibility rememberedVisibilityTab;
    private static final Set<ModuleTags.Pvp> rememberedPvpPins = EnumSet.noneOf(ModuleTags.Pvp.class);
    private static final Set<ModuleTags.Visibility> rememberedVisibilityPins = EnumSet.noneOf(ModuleTags.Visibility.class);
    private static final int MAX_COMMAND_INPUT_LENGTH = 256;
    private static final int MAX_COMMAND_HISTORY = 50;
    private static final int MAX_COMMAND_SUGGESTION_ROWS = 10;
    private static final List<String> commandHistory = new ArrayList<>();
    private static Module rememberedOpenedListModule;
    private static ListValue rememberedOpenedListValue;
    private static float rememberedListScroll;
    private static float rememberedTargetListScroll;
    private static float rememberedSettingsPanelScroll;
    private static float rememberedTargetSettingsPanelScroll;
    private static final Map<ModuleCategory, ScrollState> rememberedCategoryScrolls = new EnumMap<>(ModuleCategory.class);
    private static final Set<ModuleCategory> collapsedCategories = EnumSet.noneOf(ModuleCategory.class);

    private final List<TabBounds> tabBounds = new ArrayList<>();
    private final List<ModuleRowBounds> moduleRows = new ArrayList<>();
    private final List<ModuleExpandBounds> moduleExpandButtons = new ArrayList<>();
    private final List<ControlBounds> controls = new ArrayList<>();
    private final List<Rect> inputClips = new ArrayList<>();
    private final List<TextFieldLayout> textFields = new ArrayList<>();
    private final List<PendingControlGlass> pendingControlGlass = new ArrayList<>();
    private final List<Runnable> pendingControlForegrounds = new ArrayList<>();
    private boolean deferControlChrome;
    private final Map<ModuleCategory, ScrollState> categoryScrolls = new EnumMap<>(ModuleCategory.class);
    private final Map<Module, List<SettingValue<?>>> moduleSettingsCache = new IdentityHashMap<>();
    private final Set<Module> expandedModules = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ColorValue> inlineColorEditors = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Module, Float> inlineExpansionAnimations = new IdentityHashMap<>();
    private final Map<Module, Long> inlineExpansionFrames = new IdentityHashMap<>();
    private final Map<Module, Float> expandedSettingsHeightCache = new IdentityHashMap<>();
    private final Map<Module, Float> inlineSettingsHeightCache = new IdentityHashMap<>();
    private final Map<Module, Boolean> renderableSettingsCache = new IdentityHashMap<>();
    private final Map<Module, Float> enabledAnimations = new IdentityHashMap<>();
    private final Map<Module, Float> expansionAnimations = new IdentityHashMap<>();
    private final Map<ListValue, Float> listDropdownAnimations = new IdentityHashMap<>();
    private final Map<SettingValue<?>, Float> settingVisibilityAnimations = new IdentityHashMap<>();
    private final Map<Object, Float> switchAnimations = new IdentityHashMap<>();
    private final Map<Module, Long> enabledAnimationFrames = new IdentityHashMap<>();
    private final Map<Module, Long> expansionAnimationFrames = new IdentityHashMap<>();
    private final Map<ListValue, Long> listDropdownAnimationFrames = new IdentityHashMap<>();
    private final Map<SettingValue<?>, Long> settingVisibilityAnimationFrames = new IdentityHashMap<>();
    private final Map<Object, Long> switchAnimationFrames = new IdentityHashMap<>();
    private final Map<String, Float> animations = new HashMap<>();
    private final Consumer<NVGU> nanoVgRenderer = this::render;
    private final Screen returnScreen;

    private ModuleCategory selectedCategory = ModuleCategory.Combat;
    private float layoutScale = 1f;
    private boolean controlHeld;
    private boolean configView;
    private ModuleTags.Pvp pvpTab;
    private ModuleTags.Visibility visibilityTab;
    private final Set<ModuleTags.Pvp> pvpPins = EnumSet.noneOf(ModuleTags.Pvp.class);
    private final Set<ModuleTags.Visibility> visibilityPins = EnumSet.noneOf(ModuleTags.Visibility.class);
    private final List<TagTabBounds> tagTabBounds = new ArrayList<>();
    private float tagTabsBottom;
    private ConfigTab selectedConfigTab = ConfigTab.MODULE;
    private boolean commandView;
    private CommandTab selectedCommandTab = CommandTab.COMMAND;
    private boolean clientSettingsView;
    private boolean themeDropdownOpen;
    private String commandInput = "";
    private int commandHistoryIndex = -1;
    private List<Suggestion> commandSuggestionList = List.of();
    private List<String> commandUsageLines = List.of();
    private String commandSuggestionInput;
    private int commandSuggestionCaret = -1;
    private String commandSuggestionAppliedInput;
    private String commandSuggestionOriginal = "";
    private int commandSuggestionIndex;
    private int commandSuggestionOffset;
    private int commandSuggestionVisibleRows;
    private boolean commandSuggestionsVisible;
    private boolean commandSuggestionTabCycles;
    private Rect commandInputRect = new Rect(0f, 0f, 0f, 0f);
    private Rect commandSuggestionRect = new Rect(0f, 0f, 0f, 0f);
    private String lastSeenCommandLog;
    private int lastSeenCommandLogCount = -1;
    private float commandLogWrapWidth = -1f;
    private final Map<String, List<String>> commandLogLineCache = new IdentityHashMap<>();
    private String searchQuery = "";
    private String newConfigName = "";
    private String renameConfigName = "";
    private ConfigEntry renamingConfig;
    private float listScroll;
    private float targetListScroll;
    private float scrollVelocity;
    private float maxListScroll;
    private float settingsPanelScroll;
    private float targetSettingsPanelScroll;
    private float settingsPanelScrollVelocity;
    private float maxSettingsPanelScroll;
    private float screenMouseX;
    private float screenMouseY;
    private float scaledMouseX;
    private float scaledMouseY;
    private float openProgress;
    private float frameDelta = 1f;
    private long lastFrameNanos;
    private long animationFrame;
    private boolean closing;
    private Rect closeRect = new Rect(0f, 0f, 0f, 0f);
    private Rect hudEditorRect = new Rect(0f, 0f, 0f, 0f);
    private Rect clientSettingsRect = new Rect(0f, 0f, 0f, 0f);
    private Rect infoButtonRect = new Rect(0f, 0f, 0f, 0f);
    private Rect configButtonRect = new Rect(0f, 0f, 0f, 0f);
    private Rect searchRect = new Rect(0f, 0f, 0f, 0f);
    private Rect searchClearRect = new Rect(0f, 0f, 0f, 0f);
    private Rect listViewport = new Rect(0f, 0f, 0f, 0f);
    private Rect scrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
    private Rect scrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelCloseRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelViewport = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
    private TextFocus textFocus = TextFocus.NONE;
    private boolean textInputActive;
    private TextValue focusedTextValue;
    private Module focusedTextModule;
    private SettingValue<?> focusedNumberValue;
    private String numberInput = "";
    private int textCaret;
    private int textAnchor;
    private boolean draggingTextSelection;
    private Module bindingModule;
    private KeyBindValue bindingValue;
    private Module openedListModule;
    private ListValue openedListValue;
    private SliderDrag activeSlider;
    private ControlBounds pressedControl;
    private Module pressedModule;
    private ScrollbarDrag activeScrollbar;
    private ScrollbarDrag activeSettingsPanelScrollbar;
    private Module settingsPanelModule;
    private Module settingsPanelRenderModule;
    private ConfigEntry settingsPanelConfig;
    private ConfigEntry settingsPanelRenderConfig;
    private int settingsPanelControlStartIndex;
    private Module hoveredModule;
    private SettingValue<?> hoveredSetting;
    private Object tooltipHoverTarget;
    private Object hoveredTooltipTarget;
    private boolean infoButtonHovered;
    private long tooltipHoverStartNanos;
    private String tooltipText = "";
    private float settingsDockReveal;
    private float tabMarkY = Float.NaN;
    private final Map<ModuleCategory, ColumnState> columns = new EnumMap<>(ModuleCategory.class);
    private ColumnState activeColumnDrag;
    private float activeColumnDragOffset;
    private Rect overlayRect = new Rect(0f, 0f, 0f, 0f);
    private Rect overlayCloseRect = new Rect(0f, 0f, 0f, 0f);

    public NanoVGClickGuiScreen() {
        this(null);
    }

    public NanoVGClickGuiScreen(Screen returnScreen) {
        super(Component.literal("NanoVG Click GUI"));
        this.returnScreen = returnScreen instanceof NanoVGClickGuiScreen ? null : returnScreen;
        detachedClosingScreen = null;
        this.layoutScale = Math.clamp(rememberedLayoutScale, MIN_LAYOUT_SCALE, MAX_LAYOUT_SCALE);
        refreshLayoutMetrics();
        restoreUiState();
    }

    public static void renderDetachedIfNeeded() {
        if (detachedClosingScreen == null || Client.nanoVgManager == null)
            return;

        NanoVGClickGuiScreen screen = detachedClosingScreen;
        if (!screen.closing) {
            detachedClosingScreen = null;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.mouseHandler != null) {
            screen.screenMouseX = NanoVGManager.toScaledMouseX(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()));
            screen.screenMouseY = NanoVGManager.toScaledMouseY(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
            screen.updateScaledMouse();
        }

        Client.nanoVgManager.load(screen.nanoVgRenderer);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Screen backgroundScreen = getBackgroundScreen();
        if (backgroundScreen != null)
            backgroundScreen.extractRenderStateWithTooltipAndSubtitles(context, 0, 0, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Client.nanoVgManager == null) return;

        // Keeps theme preset/picker edits live while the click GUI is open, including on the title screen.
        Interface.applyThemeFromSettings();
        screenMouseX = NanoVGManager.toScaledMouseX(mouseX);
        screenMouseY = NanoVGManager.toScaledMouseY(mouseY);
        updateScaledMouse();
        context.requestCursor(resolveHoverCursor());
        syncTextInput();
        Client.nanoVgManager.load(nanoVgRenderer);
    }

    private void syncTextInput() {
        boolean wantsTextInput = textFocus != TextFocus.NONE;
        if (wantsTextInput == textInputActive || minecraft == null)
            return;

        textInputActive = wantsTextInput;
        minecraft.onTextInputFocusChange(this, wantsTextInput);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing)
            return true;

        float mouseX = NanoVGManager.toScaledMouseX(event.x());
        float mouseY = NanoVGManager.toScaledMouseY(event.y());
        int button = event.button();

        if ((bindingModule != null || bindingValue != null) && button != Keys.MOUSE_BUTTON_LEFT) {
            if (bindingModule != null)
                bindingModule.key = Keys.mouseKey(button);
            else
                bindingValue.set(Keys.mouseKey(button));
            bindingModule = null;
            bindingValue = null;
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && closeRect.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && hudEditorRect.contains(mouseX, mouseY)) {
            minecraft.gui.setScreen(new HudEditorScreen());
            return true;
        }

        if (handleTagTabClick(mouseX, mouseY, button))
            return true;

        if (isModuleView() && !controlHeld && button == Keys.MOUSE_BUTTON_LEFT
                && infoButtonRect.contains(mouseX, mouseY))
            return true;

        if (isModuleView() && !controlHeld && button == Keys.MOUSE_BUTTON_LEFT
                && configButtonRect.contains(mouseX, mouseY)) {
            configView = true;
            commandView = false;
            clientSettingsView = false;
            selectedConfigTab = ConfigTab.MODULE;
            searchQuery = "";
            clearInteractionState();
            resetListScroll();
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && clientSettingsRect.contains(mouseX, mouseY)) {
            clientSettingsView = !clientSettingsView;
            configView = false;
            commandView = false;
            searchQuery = "";
            clearInteractionState();
            resetListScroll();
            return true;
        }

        // Handle client settings icon click
        for (TabBounds tab : tabBounds) {
            if (tab.category == null && tab.configTab == null && tab.commandTab == null && tab.rect.contains(mouseX, mouseY)) {
                clientSettingsView = !clientSettingsView;
                configView = false;
                commandView = false;
                clearInteractionState();
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && !isModuleView() && !clientSettingsView && overlayCloseRect.contains(mouseX, mouseY)) {
            configView = false;
            commandView = false;
            clearInteractionState();
            resetListScroll();
            return true;
        }

        if (isSettingsDockOpen()) {
            if (button == Keys.MOUSE_BUTTON_LEFT && settingsPanelCloseRect.contains(mouseX, mouseY)) {
                closeSettingsPanel();
                return true;
            }

            if (button == Keys.MOUSE_BUTTON_LEFT && maxSettingsPanelScroll > 0f
                    && settingsPanelScrollbarTrackRect.contains(mouseX, mouseY)) {
                closeListDropdown();
                clearTextFocus();
                float offsetY = settingsPanelScrollbarThumbRect.contains(mouseX, mouseY)
                        ? mouseY - settingsPanelScrollbarThumbRect.y
                        : settingsPanelScrollbarThumbRect.height / 2f;
                activeSettingsPanelScrollbar = new ScrollbarDrag(offsetY);
                updateSettingsPanelScrollbarDrag(mouseY);
                return true;
            }

            if (settingsPanelRect.contains(mouseX, mouseY)
                    && (button == Keys.MOUSE_BUTTON_LEFT
                    || button == Keys.MOUSE_BUTTON_RIGHT
                    || button == Keys.MOUSE_BUTTON_MIDDLE)) {
                for (int i = settingsPanelControlStartIndex; i < controls.size(); i++) {
                    ControlBounds control = controls.get(i);
                    if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button, doubleClick))
                        return true;
                }

                clearTextFocus();
                closeListDropdown();
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && !searchQuery.isBlank() && searchClearRect.contains(mouseX, mouseY)) {
            clearSearch();
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && isModuleView()) {
            for (ColumnState column : columns.values()) {
                if (column.max <= 0f || !column.scrollbarTrack.contains(mouseX, mouseY))
                    continue;

                closeListDropdown();
                clearTextFocus();
                activeColumnDrag = column;
                activeColumnDragOffset = column.scrollbarThumb.contains(mouseX, mouseY)
                        ? mouseY - column.scrollbarThumb.y
                        : column.scrollbarThumb.height / 2f;
                updateColumnScrollbarDrag(mouseY);
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && maxListScroll > 0f && scrollbarTrackRect.contains(mouseX, mouseY)) {
            closeListDropdown();
            clearTextFocus();
            float offsetY = scrollbarThumbRect.contains(mouseX, mouseY) ? mouseY - scrollbarThumbRect.y : scrollbarThumbRect.height / 2f;
            activeScrollbar = new ScrollbarDrag(offsetY);
            updateScrollbarDrag(mouseY);
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT
                || button == Keys.MOUSE_BUTTON_RIGHT
                || button == Keys.MOUSE_BUTTON_MIDDLE) {
            for (ControlBounds control : controls) {
                if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button, doubleClick))
                    return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && searchRect.contains(mouseX, mouseY)) {
            focusSearch();
            beginTextSelectionOrSelectAll(mouseX, doubleClick);
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT) {
            for (TabBounds tab : tabBounds) {
                if (tab.rect.contains(mouseX, mouseY)) {
                    storeCurrentCategoryScroll();
                    if (tab.isConfig()) {
                        configView = true;
                        commandView = false;
                        clientSettingsView = false;
                        selectedConfigTab = tab.configTab();
                        searchQuery = "";
                        resetListScroll();
                    } else if (tab.isCommand()) {
                        configView = false;
                        commandView = true;
                        clientSettingsView = false;
                        selectedCommandTab = tab.commandTab();
                        searchQuery = "";
                        resetCommandScroll();
                    } else if (tab.category() != null) {
                        selectedCategory = tab.category();
                        configView = false;
                        commandView = false;
                        clientSettingsView = false;
                        searchQuery = "";
                        restoreCategoryScroll(selectedCategory);
                    } else {
                        // Client settings tab
                        clientSettingsView = !clientSettingsView;
                        configView = false;
                        commandView = false;
                    }
                    clearInteractionState();
                    return true;
                }
            }
        }

        if (isModuleView() && (button == Keys.MOUSE_BUTTON_LEFT || button == Keys.MOUSE_BUTTON_RIGHT)) {
            for (ColumnState column : columns.values()) {
                if (!column.header.contains(mouseX, mouseY))
                    continue;
                if (!collapsedCategories.add(column.category))
                    collapsedCategories.remove(column.category);
                activeColumnDrag = null;
                closeListDropdown();
                clearTextFocus();
                return true;
            }
        }

        for (ModuleExpandBounds expand : moduleExpandButtons) {
            if (!expand.rect.contains(mouseX, mouseY))
                continue;

            if (button == Keys.MOUSE_BUTTON_LEFT || button == Keys.MOUSE_BUTTON_RIGHT) {
                closeListDropdown();
                toggleInlineExpansion(expand.module);
                return true;
            }
        }

        for (ModuleRowBounds row : moduleRows) {
            if (!row.rect.contains(mouseX, mouseY)) continue;

            if (button == Keys.MOUSE_BUTTON_LEFT) {
                closeListDropdown();
                pressedModule = row.module;
                row.module.toggle();
                return true;
            }

            if (button == Keys.MOUSE_BUTTON_RIGHT) {
                closeListDropdown();
                toggleInlineExpansion(row.module);
                return true;
            }

            // The reference has no dedicated bind row; middle-click keeps binding accessible.
            if (button == Keys.MOUSE_BUTTON_MIDDLE) {
                closeListDropdown();
                clearTextFocus();
                bindingValue = null;
                bindingModule = row.module;
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_RIGHT && openedListValue != null) {
            closeListDropdown();
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT) {
            clearTextFocus();
            closeListDropdown();
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingTextSelection) {
            updateTextSelectionDrag(NanoVGManager.toScaledMouseX(event.x()));
            return true;
        }

        if (activeSettingsPanelScrollbar != null) {
            updateSettingsPanelScrollbarDrag(NanoVGManager.toScaledMouseY(event.y()));
            return true;
        }

        if (activeColumnDrag != null) {
            updateColumnScrollbarDrag(NanoVGManager.toScaledMouseY(event.y()));
            return true;
        }

        if (activeScrollbar != null) {
            updateScrollbarDrag(NanoVGManager.toScaledMouseY(event.y()));
            return true;
        }

        if (activeSlider == null) return false;

        updateSlider(activeSlider, NanoVGManager.toScaledMouseX(event.x()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        activeColumnDrag = null;
        draggingTextSelection = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float scaledX = NanoVGManager.toScaledMouseX(mouseX);
        float scaledY = NanoVGManager.toScaledMouseY(mouseY);

        if (nudgeHoveredColorSlider(scaledX, scaledY, vertical))
            return true;

        if (controlHeld && vertical != 0d) {
            setLayoutScale(layoutScale + (float) vertical * LAYOUT_SCALE_STEP);
            return true;
        }

        if (commandSuggestionsVisible && !commandSuggestionList.isEmpty() && commandSuggestionRect.contains(scaledX, scaledY)) {
            scrollCommandSuggestions(vertical > 0d ? -1 : 1);
            return true;
        }

        if (isSettingsDockOpen()
                && (settingsPanelViewport.contains(scaledX, scaledY)
                || settingsPanelScrollbarTrackRect.contains(scaledX, scaledY))) {
            float previousTarget = targetSettingsPanelScroll;
            float scrollStep = Math.clamp(settingsPanelViewport.height * 0.11f, u(36f), u(64f));
            targetSettingsPanelScroll = Math.clamp(
                    targetSettingsPanelScroll - (float) vertical * scrollStep,
                    0f,
                    maxSettingsPanelScroll
            );
            settingsPanelScrollVelocity = Math.clamp(
                    settingsPanelScrollVelocity + (targetSettingsPanelScroll - previousTarget) * 0.08f,
                    -22f,
                    22f
            );
            return true;
        }

        if (isModuleView()) {
            for (ColumnState column : columns.values()) {
                if (column.viewport.contains(scaledX, scaledY) || column.scrollbarTrack.contains(scaledX, scaledY)) {
                    float previousTarget = column.target;
                    float scrollStep = Math.clamp(column.viewport.height * 0.12f, u(28f), u(52f));
                    column.target = Math.clamp(column.target - (float) vertical * scrollStep, 0f, column.max);
                    column.velocity = Math.clamp(column.velocity + (column.target - previousTarget) * 0.08f, -22f, 22f);
                    return true;
                }
            }
        }

        if (listViewport.contains(scaledX, scaledY) || scrollbarTrackRect.contains(scaledX, scaledY)) {
            float previousTarget = targetListScroll;
            float scrollStep = Math.clamp(listViewport.height * 0.11f, u(36f), u(64f));
            targetListScroll = Math.clamp(targetListScroll - (float) vertical * scrollStep, 0f, maxListScroll);
            scrollVelocity = Math.clamp(scrollVelocity + (targetListScroll - previousTarget) * 0.08f, -22f, 22f);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        controlHeld = (event.modifiers() & Keys.MOD_CONTROL) != 0;

        if (closing)
            return true;

        int key = event.key();

        if (bindingModule != null) {
            if (key != Keys.KEY_ESCAPE)
                bindingModule.key = normalizeKey(key);
            bindingModule = null;
            return true;
        }

        if (bindingValue != null) {
            if (key != Keys.KEY_ESCAPE)
                bindingValue.set(normalizeKey(key));
            bindingValue = null;
            return true;
        }

        boolean commandModifier = (event.modifiers() & (Keys.MOD_CONTROL | Keys.MOD_SUPER)) != 0;
        if (key == Keys.KEY_F && commandModifier) {
            focusSearch();
            return true;
        }

        if (isModuleView() && textFocus == TextFocus.NONE
                && (key == Keys.KEY_ENTER || key == Keys.KEY_KP_ENTER)) {
            focusSearch();
            return true;
        }

        if (textFocus != TextFocus.NONE) {
            if (key == Keys.KEY_ESCAPE && (textFocus == TextFocus.NUMBER || textFocus == TextFocus.CONFIG_RENAME)) {
                if (textFocus == TextFocus.NUMBER)
                    cancelNumberInput();
                else
                    cancelConfigRename();
                return true;
            }

            if (textFocus == TextFocus.COMMAND && handleCommandSuggestionKey(key, (event.modifiers() & Keys.MOD_SHIFT) != 0))
                return true;

            if (textFocus == TextFocus.COMMAND && (key == Keys.KEY_UP || key == Keys.KEY_DOWN)) {
                browseCommandHistory(key == Keys.KEY_UP ? -1 : 1);
                return true;
            }

            if (key == Keys.KEY_ENTER || key == Keys.KEY_KP_ENTER) {
                if (textFocus == TextFocus.COMMAND)
                    runCommandInput();
                else if (textFocus == TextFocus.CONFIG_NAME)
                    createConfigFromInput();
                else if (textFocus == TextFocus.CONFIG_RENAME) {
                    if (commitConfigRename())
                        resetTextFocus();
                } else if (textFocus == TextFocus.NUMBER) {
                    if (commitNumberInput())
                        resetTextFocus();
                }
                else
                    clearTextFocus();
                return true;
            }

            if (key == Keys.KEY_A && commandModifier) {
                selectAllFocusedText();
                return true;
            }

            if (key == Keys.KEY_C && commandModifier) {
                copyFocusedTextToClipboard();
                return true;
            }

            if (key == Keys.KEY_V && commandModifier) {
                pasteClipboardIntoFocusedText();
                return true;
            }

            if (key == Keys.KEY_BACKSPACE) {
                removeLastFocusedCharacter();
                return true;
            }

            if (key == Keys.KEY_DELETE) {
                deleteForwardFocusedCharacter();
                return true;
            }

            boolean shift = (event.modifiers() & Keys.MOD_SHIFT) != 0;
            if (key == Keys.KEY_LEFT) {
                moveFocusedCaret(-1, shift);
                return true;
            }
            if (key == Keys.KEY_RIGHT) {
                moveFocusedCaret(1, shift);
                return true;
            }
        }

        if (key == Keys.KEY_UP || key == Keys.KEY_DOWN) {
            cycleCategory(key == Keys.KEY_DOWN ? 1 : -1);
            return true;
        }

        if (key == Keys.KEY_PAGE_UP || key == Keys.KEY_PAGE_DOWN
                || key == Keys.KEY_HOME || key == Keys.KEY_END) {
            scrollWithKeyboard(key);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        controlHeld = (event.modifiers() & Keys.MOD_CONTROL) != 0;
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (textFocus == TextFocus.NONE || !event.isAllowedChatCharacter())
            return false;

        String input = event.codepointAsString();
        if (input != null && !input.isEmpty()) {
            if (textFocus == TextFocus.NUMBER)
                appendNumberInput(input);
            else
                appendFocusedText(input);
        }

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        Screen backgroundScreen = getBackgroundScreen();
        return backgroundScreen != null && backgroundScreen.isPauseScreen();
    }

    @Override
    public void tick() {
        super.tick();

        Screen backgroundScreen = getBackgroundScreen();
        if (backgroundScreen != null && minecraft != null && minecraft.gui.screen() == this)
            backgroundScreen.tick();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        Screen backgroundScreen = getBackgroundScreen();
        if (backgroundScreen != null)
            backgroundScreen.resize(width, height);
    }

    public boolean shouldBlockGameMovement() {
        return textFocus != TextFocus.NONE || bindingModule != null || bindingValue != null || shouldBlockMovementForBackgroundScreen();
    }

    public boolean shouldSyncGameMovementKeys() {
        return isInGame() && !closing && !shouldBlockGameMovement();
    }

    @Override
    public void removed() {
        rememberUiState();
        controlHeld = false;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        activeColumnDrag = null;
        bindingModule = null;
        bindingValue = null;
        clearTextFocus();
        syncTextInput();
        resetHoverCursor();
        super.removed();
    }

    @Override
    public void onClose() {
        if (closing)
            return;

        rememberUiState();
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        activeColumnDrag = null;
        bindingModule = null;
        bindingValue = null;
        clearTextFocus();
        resetHoverCursor();
        closing = true;

        if (isInGame() && minecraft != null && minecraft.gui.screen() == this) {
            detachedClosingScreen = this;
            minecraft.gui.setScreen(returnScreen);
        }
    }

    private void restoreUiState() {
        categoryScrolls.clear();
        categoryScrolls.putAll(rememberedCategoryScrolls);
        settingsPanelModule = null;
        settingsPanelRenderModule = null;
        settingsPanelConfig = restoreRememberedConfig(rememberedSettingsPanelConfig);
        settingsPanelRenderConfig = settingsPanelConfig;
        expandedModules.clear();
        expandedModules.addAll(rememberedExpandedModules);
        selectedCategory = rememberedSelectedCategory == null ? ModuleCategory.Combat : rememberedSelectedCategory;
        configView = rememberedConfigView;
        selectedConfigTab = rememberedConfigTab == null ? ConfigTab.MODULE : rememberedConfigTab;
        commandView = rememberedCommandView && !configView;
        selectedCommandTab = rememberedCommandTab == null ? CommandTab.COMMAND : rememberedCommandTab;
        clientSettingsView = rememberedClientSettingsView && !configView && !commandView;
        pvpTab = rememberedPvpTab;
        visibilityTab = rememberedVisibilityTab;
        pvpPins.clear();
        pvpPins.addAll(rememberedPvpPins);
        visibilityPins.clear();
        visibilityPins.addAll(rememberedVisibilityPins);
        openedListModule = rememberedOpenedListModule;
        openedListValue = rememberedOpenedListValue;
        ScrollState selectedScroll = isModuleView() && selectedCategory != null ? categoryScrolls.get(selectedCategory) : null;
        if (selectedScroll != null) {
            listScroll = selectedScroll.listScroll();
            targetListScroll = selectedScroll.targetListScroll();
        } else {
            targetListScroll = rememberedTargetListScroll;
            listScroll = rememberedListScroll;
            if (isModuleView() && selectedCategory != null)
                categoryScrolls.put(selectedCategory, new ScrollState(listScroll, targetListScroll));
        }
        settingsPanelScroll = rememberedSettingsPanelScroll;
        targetSettingsPanelScroll = rememberedTargetSettingsPanelScroll;
        restoreSettingsPanelAnimations();
    }

    private void restoreSettingsPanelAnimations() {
        if (isSettingsDockOpen()) {
            animations.put("settings-panel-open", 1f);
            settingsDockReveal = 1f;
        }

        for (Module module : expandedModules) {
            inlineExpansionAnimations.put(module, 1f);

            for (SettingValue<?> setting : collectSettings(module)) {
                if (setting.canDisplay.canDisplay())
                    settingVisibilityAnimations.put(setting, 1f);
                else
                    settingVisibilityAnimations.remove(setting);
            }
        }

        if (openedListModule != null && openedListValue != null)
            listDropdownAnimations.put(openedListValue, 1f);
    }

    private void rememberUiState() {
        storeCurrentCategoryScroll();
        rememberedSettingsPanelModule = null;
        rememberedSettingsPanelConfig = settingsPanelConfig;
        rememberedExpandedModules.clear();
        rememberedExpandedModules.addAll(expandedModules);
        rememberedSelectedCategory = selectedCategory;
        rememberedConfigView = configView;
        rememberedConfigTab = selectedConfigTab;
        rememberedCommandView = commandView;
        rememberedCommandTab = selectedCommandTab;
        rememberedClientSettingsView = clientSettingsView;
        rememberedPvpTab = pvpTab;
        rememberedVisibilityTab = visibilityTab;
        rememberedPvpPins.clear();
        rememberedPvpPins.addAll(pvpPins);
        rememberedVisibilityPins.clear();
        rememberedVisibilityPins.addAll(visibilityPins);
        rememberedOpenedListModule = openedListModule;
        rememberedOpenedListValue = openedListValue;
        rememberedListScroll = listScroll;
        rememberedTargetListScroll = targetListScroll;
        rememberedSettingsPanelScroll = settingsPanelScroll;
        rememberedTargetSettingsPanelScroll = targetSettingsPanelScroll;
        rememberedCategoryScrolls.clear();
        rememberedCategoryScrolls.putAll(categoryScrolls);
    }

    private void render(NVGU vg) {
        float delta = updateDelta();
        frameDelta = delta;
        animationFrame++;
        clearFrameLayoutCaches();
        openProgress = stepTowards(openProgress, closing ? 0f : 1f, 0.08f * delta);
        updateSmoothScroll(delta);
        updateColumnScrolls(delta);
        updateSettingsPanelSmoothScroll(delta);

        if (closing && openProgress <= 0.001f) {
            finishClose();
            return;
        }

        tabBounds.clear();
        moduleRows.clear();
        moduleExpandButtons.clear();
        controls.clear();
        textFields.clear();
        pendingControlGlass.clear();
        pendingControlForegrounds.clear();
        deferControlChrome = false;
        hoveredModule = null;
        hoveredSetting = null;
        hoveredTooltipTarget = null;
        infoButtonHovered = false;
        tooltipText = "";
        settingsPanelRenderModule = null;
        settingsPanelRenderConfig = settingsPanelConfig;
        settingsDockReveal = settingsPanelReveal();
        if (settingsDockReveal <= 0f)
            settingsPanelRenderConfig = null;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        updateScaledMouse();
        boolean staticFallbackBackdrop = !isInGame() && !hasBackgroundScreen();

        // The ClickGUI blurs the category backdrops, so refresh the captured source every frame.
        Shader2DRenderer.INSTANCE.beginFrame();
        if (staticFallbackBackdrop)
            renderTitleBackdrop(vg, screenWidth, screenHeight);

        vg.globalAlpha(easeOut(openProgress), () -> {
            // Atmosphere keeps a compact search field visible above the centred columns.
            // Hold Ctrl to expose the original Kanade utility bar.
            if (isModuleView()) {
                renderColumns(vg, screenWidth, screenHeight);
                if (controlHeld) {
                    renderTopBar(vg, screenWidth);
                } else {
                    closeRect = new Rect(-1f, -1f, 0f, 0f);
                    hudEditorRect = new Rect(-1f, -1f, 0f, 0f);
                    renderAtmosphereSearch(vg, screenWidth);
                }
            } else {
                renderTopBar(vg, screenWidth);
                renderOverlay(vg, screenWidth, screenHeight);
            }
            renderSettingsPanel(vg, screenWidth, screenHeight);
            renderHoverTooltip(vg);
            applyHoverCursor();
        });
    }
    /** Layout scale shared with the other NanoVG client screens (title screen, HUD). */
    public static float getLayoutScale() {
        return Math.clamp(rememberedLayoutScale, MIN_LAYOUT_SCALE, MAX_LAYOUT_SCALE);
    }

    private float u(float value) {
        return value * LAYOUT * layoutScale;
    }

    private float font(float size) {
        return Math.round(size * LAYOUT * layoutScale);
    }

    private void refreshLayoutMetrics() {
        topBarHeight = u(38f);
        atmosphereTitleBarHeight = u(24f);
        columnGap = u(1.5f);
        columnHeaderHeight = u(12f);
        columnRowHeight = u(11f);
        columnPadding = u(4f);
        columnArrowSize = u(7f);
        inlineSettingRowHeight = u(11f);
        inlineSettingGap = u(0.5f);
        inlineOptionRowHeight = u(10.5f);
        inlineColorRowHeight = u(11f);
        inlineColorSwatchWidth = u(13f);
        inlineColorSwatchHeight = u(9f);
        panelHeaderHeight = u(42f);
        panelFooterHeight = u(22f);
        panelPadding = u(10f);
        categorySidebarWidth = u(126f);
        categoryContentGap = u(10f);
        settingsPanelHeaderHeight = u(44f);
        settingsDockGap = u(8f);
        rowHeight = u(32f);
        settingRowHeight = u(27f);
        settingRowStep = u(34f);
        switchWidth = u(31f);
        switchHeight = u(15f);
        nameLineHeight = u(16f);
    }

    private void setLayoutScale(float scale) {
        float clamped = Math.clamp(scale, MIN_LAYOUT_SCALE, MAX_LAYOUT_SCALE);
        if (Math.abs(clamped - layoutScale) < 0.0001f)
            return;

        layoutScale = clamped;
        rememberedLayoutScale = clamped;
        refreshLayoutMetrics();
        clearFrameLayoutCaches();
    }

    private Rect panelBounds(float screenWidth, float screenHeight) {
        float panelWidth = Math.min(u(860f), Math.max(u(560f), screenWidth - u(36f)));
        float panelHeight = Math.min(u(590f), Math.max(u(390f), screenHeight - u(36f)));
        return new Rect((screenWidth - panelWidth) / 2f, (screenHeight - panelHeight) / 2f, panelWidth, panelHeight);
    }

    private void updateScaledMouse() {
        scaledMouseX = screenMouseX;
        scaledMouseY = screenMouseY;
    }

    private void finishClose() {
        clearInteractionState();
        if (detachedClosingScreen == this)
            detachedClosingScreen = null;
        if (minecraft != null && minecraft.gui.screen() == this)
            minecraft.gui.setScreen(returnScreen);
    }

    private void clearFrameLayoutCaches() {
        expandedSettingsHeightCache.clear();
        inlineSettingsHeightCache.clear();
        renderableSettingsCache.clear();
    }

    private void storeCurrentCategoryScroll() {
        if (!isModuleView() || !searchQuery.isBlank() || selectedCategory == null)
            return;

        categoryScrolls.put(selectedCategory, new ScrollState(listScroll, targetListScroll));
    }

    private void restoreCategoryScroll(ModuleCategory category) {
        if (category == null) {
            resetListScroll();
            return;
        }

        ScrollState state = categoryScrolls.get(category);
        if (state == null) {
            resetListScroll();
            return;
        }

        listScroll = state.listScroll();
        targetListScroll = state.targetListScroll();
        scrollVelocity = 0f;
    }

    private void resetListScroll() {
        targetListScroll = 0f;
        listScroll = 0f;
        scrollVelocity = 0f;
    }

    private float updateDelta() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f : Math.clamp((now - lastFrameNanos) / 16_666_666f, 0.25f, 3f);
        lastFrameNanos = now;
        return delta;
    }

    private void updateSmoothScroll(float delta) {
        if (listViewport.height <= 0f)
            return;

        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);

        if (activeScrollbar != null) {
            scrollVelocity = 0f;
            listScroll = targetListScroll;
            return;
        }

        if (maxListScroll <= 0f) {
            targetListScroll = 0f;
            listScroll = 0f;
            scrollVelocity = 0f;
            return;
        }

        float distance = targetListScroll - listScroll;
        scrollVelocity += distance * 0.045f * delta;
        scrollVelocity *= (float) Math.pow(0.74f, delta);
        scrollVelocity = Math.clamp(scrollVelocity, -42f, 42f);

        listScroll += scrollVelocity * delta;
        listScroll = approach(listScroll, targetListScroll, 1f - (float) Math.pow(0.91f, delta));

        if (listScroll < 0f || listScroll > maxListScroll) {
            listScroll = Math.clamp(listScroll, 0f, maxListScroll);
            scrollVelocity = 0f;
        }

        if (Math.abs(targetListScroll - listScroll) < 0.06f && Math.abs(scrollVelocity) < 0.06f) {
            listScroll = targetListScroll;
            scrollVelocity = 0f;
        }
    }

    private void updateSettingsPanelSmoothScroll(float delta) {
        if (!isSettingsDockOpen() || settingsPanelViewport.height <= 0f)
            return;

        targetSettingsPanelScroll = Math.clamp(targetSettingsPanelScroll, 0f, maxSettingsPanelScroll);

        if (activeSettingsPanelScrollbar != null) {
            settingsPanelScrollVelocity = 0f;
            settingsPanelScroll = targetSettingsPanelScroll;
            return;
        }

        if (maxSettingsPanelScroll <= 0f) {
            targetSettingsPanelScroll = 0f;
            settingsPanelScroll = 0f;
            settingsPanelScrollVelocity = 0f;
            return;
        }

        float distance = targetSettingsPanelScroll - settingsPanelScroll;
        settingsPanelScrollVelocity += distance * 0.045f * delta;
        settingsPanelScrollVelocity *= (float) Math.pow(0.74f, delta);
        settingsPanelScrollVelocity = Math.clamp(settingsPanelScrollVelocity, -42f, 42f);

        settingsPanelScroll += settingsPanelScrollVelocity * delta;
        settingsPanelScroll = approach(
                settingsPanelScroll,
                targetSettingsPanelScroll,
                1f - (float) Math.pow(0.91f, delta)
        );

        if (settingsPanelScroll < 0f || settingsPanelScroll > maxSettingsPanelScroll) {
            settingsPanelScroll = Math.clamp(settingsPanelScroll, 0f, maxSettingsPanelScroll);
            settingsPanelScrollVelocity = 0f;
        }

        if (Math.abs(targetSettingsPanelScroll - settingsPanelScroll) < 0.06f
                && Math.abs(settingsPanelScrollVelocity) < 0.06f) {
            settingsPanelScroll = targetSettingsPanelScroll;
            settingsPanelScrollVelocity = 0f;
        }
    }

    private Screen getBackgroundScreen() {
        return returnScreen != null && returnScreen != this ? returnScreen : null;
    }

    private boolean hasBackgroundScreen() {
        return getBackgroundScreen() != null;
    }

    private boolean shouldBlockMovementForBackgroundScreen() {
        Screen backgroundScreen = getBackgroundScreen();
        return backgroundScreen != null && !InventoryMove.canMoveFreely(backgroundScreen);
    }

    private void renderTitleBackdrop(NVGU vg, float width, float height) {
        if (ensureTexture(vg, "title_screen", "assets/krs/title.png")) {
            CustomTitleScreen.stepBackgroundParallax(screenMouseX, screenMouseY, Math.clamp(0.03f * frameDelta, 0f, 1f));

            vg.texturedRectangle(0f, 0f, width * 2f, height * 2f, "title_screen");
            vg.save();
            try {
                vg.translate(CustomTitleScreen.bgOffsetX, CustomTitleScreen.bgOffsetY);
                vg.texturedRectangle(-width, -height, width * 2f, height * 2f, "title_screen");
            } finally {
                vg.restore();
            }
        }
    }

    private boolean isInGame() {
        return minecraft != null && minecraft.level != null && minecraft.player != null;
    }

    private static boolean ensureTexture(NVGU vg, String identifier, String path) {
        if (vg.hasTexture(identifier))
            return true;

        try (InputStream texture = NanoVGClickGuiScreen.class.getClassLoader().getResourceAsStream(path)) {
            if (texture == null)
                return false;

            vg.createTexture(identifier, texture);
            return vg.hasTexture(identifier);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void renderTopBar(NVGU vg, float screenWidth) {
        float margin = u(14f);
        float barY = u(8f);
        float wordSize = font(15f);
        float wordWidth = NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, "kanade", margin, barY,
                wordSize, u(1.1f), NanoVGTheme.text(245), true);
        NVGFonts.INTER.drawText("v" + Client.clientVersion, margin + wordWidth + u(7f), barY + u(6f),
                font(8f), NanoVGTheme.dim(205), Alignment.LEFT_TOP, true);

        float fieldWidth = u(226f);
        float fieldHeight = u(19f);
        float gearWidth = u(19f);
        float fieldX = (screenWidth - (fieldWidth + u(2f) + gearWidth)) / 2f;
        searchRect = new Rect(fieldX, barY, fieldWidth, fieldHeight);
        clientSettingsRect = new Rect(fieldX + fieldWidth + u(2f), barY, gearWidth, fieldHeight);
        renderSearch(vg, searchRect);

        boolean gearHovered = clientSettingsRect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(clientSettingsRect.x, clientSettingsRect.y, clientSettingsRect.width, clientSettingsRect.height,
                clientSettingsView ? alpha(214, 107, 232, 58) : gearHovered ? alpha(28, 24, 38, 205) : TOP_BAR_BUTTON_BACKGROUND);
        NVGFonts.ICON.drawText(MaterialIcon.SETTING, clientSettingsRect.centerX(), clientSettingsRect.centerY() - u(1f),
                font(10f), clientSettingsView ? NanoVGTheme.ACCENT : gearHovered ? NanoVGTheme.text(235) : NanoVGTheme.muted(215),
                Alignment.CENTER_MIDDLE, true);

        closeRect = new Rect(screenWidth - margin - u(18f), barY + u(0.5f), u(18f), u(18f));
        Rect consoleRect = new Rect(closeRect.x - u(22f), closeRect.y, u(18f), u(18f));
        hudEditorRect = new Rect(consoleRect.x - u(22f), closeRect.y, u(18f), u(18f));
        tabBounds.add(new TabBounds(null, null, CommandTab.COMMAND, consoleRect));

        boolean closeHovered = closeRect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(closeRect.x, closeRect.y, closeRect.width, closeRect.height,
                closeHovered ? alpha(120, 40, 60, 225) : TOP_BAR_BUTTON_BACKGROUND);
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, closeRect.centerX(), closeRect.centerY() - u(1f), font(11f),
                closeHovered ? NanoVGTheme.danger(245) : NanoVGTheme.muted(215), Alignment.CENTER_MIDDLE, true);

        drawTopBarIcon(vg, consoleRect, MaterialIcon.DEBUG, commandView);
        drawTopBarIcon(vg, hudEditorRect, MaterialIcon.MENU, false);
    }

    private void drawTopBarIcon(NVGU vg, Rect rect, String icon, boolean active) {
        boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(rect.x, rect.y, rect.width, rect.height,
                active ? alpha(214, 107, 232, 58) : hovered ? alpha(28, 24, 38, 205) : TOP_BAR_BUTTON_BACKGROUND);
        NVGFonts.ICON.drawText(icon, rect.centerX(), rect.centerY() - u(1f), font(10f),
                active ? NanoVGTheme.ACCENT : hovered ? NanoVGTheme.text(235) : NanoVGTheme.dim(200),
                Alignment.CENTER_MIDDLE, true);
    }

    // Atmosphere's screenshot measurements are expressed in pixels at 1080p.
    // Scale the column UI with the viewport and the existing user-adjustable GUI scale.
    private float cu(float value) {
        return value * atmosphereScale;
    }

    private float columnFont(float value) {
        return Math.max(0.5f, cu(value));
    }

    private void refreshAtmosphereMetrics(float screenHeight) {
        atmosphereScale = Math.max(0.001f, screenHeight / 1080f) * layoutScale;
        columnGap = cu(3f);
        columnHeaderHeight = cu(22f);
        columnRowHeight = cu(19.5f);
        columnPadding = cu(6f);
        columnArrowSize = cu(10f);
        inlineSettingRowHeight = cu(19.5f);
        inlineSettingGap = 0f;
        inlineOptionRowHeight = cu(18f);
        inlineColorRowHeight = cu(19.5f);
        inlineColorSwatchWidth = cu(14f);
        inlineColorSwatchHeight = cu(14f);
    }

    // Morph the triangle smoothly from up (closed) to down (open).
    private void drawAtmosphereArrow(NVGU vg, Rect rect, float openProgress, Color color) {
        float unit = cu(1f);
        float centerX = rect.centerX();
        float top = rect.centerY() - unit * 1.5f;
        float progress = Math.clamp(openProgress, 0f, 1f);
        for (int i = 0; i < 3; i++) {
            float step = (i + 1) * (1f - progress) + (3 - i) * progress;
            float width = step * 2f * unit;
            vg.rectangle(centerX - width / 2f, top + i * unit, width, unit, color);
        }
    }

    private void renderAtmosphereSearch(NVGU vg, float screenWidth) {
        GuiTheme theme = GuiTheme.current();
        float height = cu(18f);
        float fieldWidth = cu(262f);
        float gearWidth = cu(18f);
        float buttonWidth = cu(18f);
        float buttonGap = cu(3f);
        float leftClusterWidth = buttonWidth * 2f + buttonGap;
        float totalWidth = leftClusterWidth + buttonGap + fieldWidth + gearWidth;
        float left = (screenWidth - totalWidth) / 2f;
        float top = cu(16f);
        infoButtonRect = new Rect(left, top, buttonWidth, height);
        configButtonRect = new Rect(left + buttonWidth + buttonGap, top, buttonWidth, height);
        searchRect = new Rect(left + leftClusterWidth + buttonGap, top, fieldWidth, height);
        clientSettingsRect = new Rect(searchRect.x + fieldWidth, top, gearWidth, height);
        drawAtmosphereUtilityButton(vg, infoButtonRect, MaterialIcon.INFO, theme);
        drawAtmosphereUtilityButton(vg, configButtonRect, MaterialIcon.TUNE, theme);
        infoButtonHovered = infoButtonRect.contains(scaledMouseX, scaledMouseY);
        if (infoButtonHovered) {
            hoveredTooltipTarget = INFO_TOOLTIP_TARGET;
            rememberTooltip(INFO_TOOLTIP_TARGET, "Hold Ctrl to reveal more buttons\nCtrl + scroll to scale the interface");
        }
        boolean focused = textFocus == TextFocus.SEARCH;
        boolean searchHovered = searchRect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(searchRect.x, searchRect.y, searchRect.width, searchRect.height, theme.searchBg);
        vg.rectangleBorder(searchRect.x, searchRect.y, searchRect.width, searchRect.height, cu(0.8f),
                focused ? theme.enabled : theme.searchBorder, Border.INSIDE);
        String text = inputText(searchQuery, focused, "Press ENTER to Search..");
        Color textColor = searchQuery.isBlank() && !focused ? theme.searchPlaceholder : theme.searchText;
        float searchSize = columnFont(9.5f);
        float reserve = searchQuery.isBlank() ? cu(8f) : cu(24f);
        float textY = searchRect.y + (searchRect.height - searchSize) / 2f;
        renderInputText(vg, TextFocus.SEARCH, null, focused, text, searchSize,
                searchRect.x + cu(6f), textY, textY, searchRect.width - reserve, searchRect, textColor);
        if (searchQuery.isBlank()) {
            searchClearRect = new Rect(0f, 0f, 0f, 0f);
        } else {
            searchClearRect = new Rect(searchRect.x + searchRect.width - cu(19f),
                    searchRect.y + cu(2f), cu(16f), cu(14f));
            boolean clearHovered = searchClearRect.contains(scaledMouseX, scaledMouseY);
            vg.rectangle(searchClearRect.x, searchClearRect.y, searchClearRect.width, searchClearRect.height,
                    clearHovered ? alpha(255, 255, 255, 28) : alpha(255, 255, 255, 10));
            NVGFonts.ICON.drawText(MaterialIcon.CLOSE, searchClearRect.centerX(), searchClearRect.centerY(),
                    columnFont(11f), clearHovered ? NanoVGTheme.TEXT : NanoVGTheme.muted(210),
                    Alignment.CENTER_MIDDLE, true);
        }
        boolean hovered = clientSettingsRect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(clientSettingsRect.x, clientSettingsRect.y, gearWidth, height,
                clientSettingsView || hovered ? theme.gearHover : theme.searchBg);
        NVGFonts.ICON.drawText(MaterialIcon.SETTING, clientSettingsRect.centerX(),
                clientSettingsRect.centerY(), columnFont(11f),
                clientSettingsView ? theme.enabled : theme.searchText,
                Alignment.CENTER_MIDDLE, true);
    }

    private void drawAtmosphereUtilityButton(NVGU vg, Rect rect, String icon, GuiTheme theme) {
        boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(rect.x, rect.y, rect.width, rect.height,
                hovered ? theme.gearHover : theme.searchBg);
        NVGFonts.ICON.drawText(icon, rect.centerX(), rect.centerY(), columnFont(11f),
                hovered ? theme.enabled : theme.searchText, Alignment.CENTER_MIDDLE, true);
    }

    private void renderColumns(NVGU vg, float screenWidth, float screenHeight) {
        refreshAtmosphereMetrics(screenHeight);
        renderTagTabs(vg, screenWidth);
        ModuleCategory[] categories = ModuleCategory.DISPLAY_ORDER;
        int count = categories.length;
        if (count == 0)
            return;

        float gap = columnGap;
        float margin = cu(8f);
        float width = screenWidth * ATMOSPHERE_COLUMN_SCREEN_RATIO * layoutScale;
        float availableWidth = Math.max(0f, screenWidth - margin * 2f - gap * (count - 1));
        width = Math.min(width, availableWidth / count);
        float totalWidth = width * count + gap * (count - 1);
        float startX = (screenWidth - totalWidth) / 2f;

        float areaTop = Math.max(screenHeight * ATMOSPHERE_TOP_RATIO, tagTabsBottom + cu(6f));
        float availableHeight = Math.max(0f, screenHeight - areaTop - columnHeaderHeight - cu(12f));

        Map<ModuleCategory, List<Module>> moduleLists = new EnumMap<>(ModuleCategory.class);
        Map<ModuleCategory, Float> contentHeights = new EnumMap<>(ModuleCategory.class);
        for (ModuleCategory category : categories) {
            List<Module> modules = visibleModulesFor(category);
            moduleLists.put(category, modules);

            float content = 0f;
            for (Module module : modules)
                content += columnModuleHeight(module, width);
            contentHeights.put(category, content);
        }

        float startY = areaTop;

        for (int i = 0; i < count; i++) {
            ModuleCategory category = categories[i];
            ColumnState column = columns.computeIfAbsent(category, ColumnState::new);
            float x = startX + i * (width + gap);
            float content = contentHeights.get(category);
            float bodyHeight = collapsedCategories.contains(category) ? 0f : Math.min(content, availableHeight);
            column.header = new Rect(x, startY, width, columnHeaderHeight);
            column.body = new Rect(x, startY + columnHeaderHeight, width, bodyHeight);
            column.viewport = new Rect(column.body.x, column.body.y, column.body.width, column.body.height);
        }

        // Blur the world behind the category columns before the translucent panel fills
        // are drawn on top, so the text stays readable while the backdrop stays soft.
        vg.beginEffectBatch();
        for (ModuleCategory category : categories) {
            ColumnState column = columns.get(category);
            if (column != null && column.header.width > 0f) {
                float panelHeight = column.header.height + column.body.height;
                vg.blurRoundedRectangle(column.header.x, column.header.y, column.header.width,
                        panelHeight, 0f, 10f, 0.92f);
            }
        }
        vg.flushEffectBatch();

        for (int i = 0; i < count; i++) {
            ModuleCategory category = categories[i];
            ColumnState column = columns.get(category);
            if (column == null)
                continue;

            List<Module> categoryModules = moduleLists.get(category);
            if (categoryModules == null)
                categoryModules = visibleModulesFor(category);
            final List<Module> modules = categoryModules;
            String label = category.label();
            float labelSize = columnFont(COLUMN_ROW_FONT);
            vg.rectangle(column.header.x, column.header.y, column.header.width, column.header.height,
                    new Color(10, 11, 16, 205));
            NVGFonts.INTER.drawText(label, column.header.centerX(), column.header.centerY(), labelSize,
                    modules.isEmpty() ? NanoVGTheme.dim(150) : NanoVGTheme.text(235),
                    Alignment.CENTER_MIDDLE, true);
            Color accent = NanoVGTheme.ACCENT;
            vg.rectangle(column.header.x, column.header.y + column.header.height - cu(1f),
                    column.header.width, cu(1f),
                    alpha(accent.getRed(), accent.getGreen(), accent.getBlue(), 105));
            Rect headerArrow = new Rect(column.header.x + column.header.width - cu(14f),
                    column.header.y, cu(10f), column.header.height);
            float categoryProgress = animate("category-arrow:" + category.name(),
                    !collapsedCategories.contains(category), 0.18f);
            drawAtmosphereArrow(vg, headerArrow, categoryProgress, NanoVGTheme.dim(205));

            if (collapsedCategories.contains(category)) {
                column.scrollbarTrack = new Rect(0f, 0f, 0f, 0f);
                column.scrollbarThumb = new Rect(0f, 0f, 0f, 0f);
                continue;
            }
            vg.rectangle(column.body.x, column.body.y, column.body.width, column.body.height,
                    new Color(15, 16, 21, 176));

            float contentHeight = contentHeights.get(category);
            column.max = Math.max(0f, contentHeight - column.viewport.height);
            column.target = Math.clamp(column.target, 0f, column.max);
            column.scroll = Math.clamp(column.scroll, 0f, column.max);

            withInputClip(column.viewport, () -> withControlChrome(vg, column.viewport, () -> {
                if (modules.isEmpty()) {
                    String empty = searchQuery.isBlank() && !isTagFilterActive() ? "—" : "no match";
                    NVGFonts.INTER.drawText(empty, column.viewport.centerX(), column.viewport.y + cu(7f),
                            columnFont(9f), NanoVGTheme.dim(140), Alignment.CENTER_TOP, true);
                    return;
                }

                float rowY = column.viewport.y - column.scroll;
                for (Module module : modules) {
                    float rowHeight = columnModuleHeight(module, column.viewport.width);
                    if (rowY + rowHeight > column.viewport.y && rowY < column.viewport.y + column.viewport.height)
                        renderColumnModule(vg, module, column.viewport.x, rowY, column.viewport.width);
                    rowY += rowHeight;
                }
            }));

            renderColumnScrollbar(vg, column, contentHeight);
        }

        renderScrollHint(vg, screenWidth, screenHeight);
    }

    /**
     * Centred down arrow under the columns. It is drawn last so it reads as a scroll hint for the lists,
     * and it is skipped when the columns grow down into that space (the ClickGui intercepts it).
     */
    private void renderScrollHint(NVGU vg, float screenWidth, float screenHeight) {
        float columnBottom = 0f;
        for (ModuleCategory category : ModuleCategory.DISPLAY_ORDER) {
            ColumnState column = columns.get(category);
            if (column == null || column.body.height <= 0f) continue;
            columnBottom = Math.max(columnBottom, column.body.y + column.body.height);
        }

        float size = cu(12f);
        float top = columnBottom + cu(6f);
        boolean fits = columnBottom > 0f && top + size <= screenHeight - cu(10f);

        float progress = animate("column-scroll-hint", fits, 0.16f);
        if (progress <= 0.01f)
            return;

        float bob = (float) Math.sin(animationFrame * 0.12f) * cu(1.6f);
        drawAtmosphereArrow(vg, new Rect(screenWidth / 2f - size / 2f, top + bob, size, size), 1f,
                NanoVGTheme.dim(Math.clamp((int) (215f * progress), 0, 255)));
    }

    private void renderColumnModule(NVGU vg, Module module, float x, float y, float width) {
        Rect row = new Rect(x, y, width, columnRowHeight);
        addModuleRow(module, row);
        boolean hovered = isHovered(row);
        if (hovered)
            rememberHoveredModule(module);

        boolean enabled = module.tempEnabled;
        Color nameColor = enabled
                ? NanoVGTheme.ACCENT
                : hovered ? NanoVGTheme.text(245) : new Color(177, 177, 190, 235);
        NVGFont nameFont = enabled ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        float nameSize = columnFont(COLUMN_ROW_FONT);

        if (enabled) {
            Color accent = NanoVGTheme.ACCENT;
            vg.rectangle(row.x, row.y, row.width, row.height,
                    alpha(accent.getRed(), accent.getGreen(), accent.getBlue(), 24));
        }
        if (hovered)
            vg.rectangle(row.x, row.y, row.width, row.height, alpha(255, 255, 255, 18));
        vg.rectangle(row.x, row.y + row.height - cu(0.75f), row.width, cu(0.75f),
                alpha(255, 255, 255, 22));
        if (enabled)
            vg.rectangle(row.x, row.y + cu(1f), cu(1.5f), Math.max(0f, row.height - cu(2f)), NanoVGTheme.ACCENT);

        boolean expandable = hasInlineSettings(module);
        float arrowSpace = expandable ? columnArrowSize + cu(4f) : 0f;
        float textX = row.x + columnPadding + cu(1f);
        float textWidth = Math.max(cu(16f), row.width - columnPadding * 2f - cu(1f) - arrowSpace);
        String name = truncateText(bindingModule == module ? "Press key..." : module.moduleName,
                nameFont, nameSize, textWidth);
        nameFont.drawText(name, textX, row.y + (row.height - nameSize) / 2f, nameSize, nameColor,
                Alignment.LEFT_TOP, true);

        if (expandable) {
            Rect arrowRect = new Rect(row.x + row.width - columnArrowSize - cu(3f), row.y,
                    columnArrowSize, row.height);
            moduleExpandButtons.add(new ModuleExpandBounds(module, arrowRect));
            boolean expanded = expandedModules.contains(module);
            boolean arrowHovered = arrowRect.contains(scaledMouseX, scaledMouseY);
            Color arrowColor = expanded
                    ? (enabled ? NanoVGTheme.ACCENT : NanoVGTheme.text(240))
                    : arrowHovered ? NanoVGTheme.text(225) : NanoVGTheme.dim(200);
            drawAtmosphereArrow(vg, arrowRect, inlineExpansionProgress(module), arrowColor);
        }

        float expansion = inlineExpansionProgress(module);
        if (expansion <= 0.001f)
            return;

        float settingsHeight = inlineSettingsHeight(module, width);
        Rect clip = new Rect(row.x, row.y + row.height, width, settingsHeight * expansion);
        vg.scissor(clip.x, clip.y, clip.width, clip.height, () ->
                withInputClip(clip, () -> renderInlineSettings(vg, module, row.x, row.y + row.height, width)));
    }

    private float columnModuleHeight(Module module, float width) {
        float height = columnRowHeight;
        float expansion = inlineExpansionProgress(module);
        if (expansion > 0.001f)
            height += inlineSettingsHeight(module, width) * expansion;
        return height;
    }

    private void renderColumnScrollbar(NVGU vg, ColumnState column, float contentHeight) {
        if (contentHeight <= column.viewport.height + 1f) {
            column.scrollbarTrack = new Rect(0f, 0f, 0f, 0f);
            column.scrollbarThumb = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        float thumbHeight = Math.max(cu(16f), column.viewport.height * column.viewport.height / contentHeight);
        float travel = Math.max(1f, column.viewport.height - thumbHeight);
        float thumbY = column.viewport.y + travel * (column.scroll / Math.max(1f, contentHeight - column.viewport.height));
        column.scrollbarTrack = new Rect(column.viewport.x + column.viewport.width - cu(5f),
                column.viewport.y, cu(6f), column.viewport.height);
        column.scrollbarThumb = new Rect(column.viewport.x + column.viewport.width - cu(2.4f),
                thumbY, cu(1.6f), thumbHeight);

        boolean active = column == activeColumnDrag || column.scrollbarTrack.contains(scaledMouseX, scaledMouseY);
        float visibility = animate("column-scrollbar:" + column.category.name(), active, 0.2f);
        if (visibility <= 0.01f)
            return;

        vg.rectangle(column.scrollbarThumb.x, column.scrollbarThumb.y,
                column.scrollbarThumb.width, column.scrollbarThumb.height,
                NanoVGTheme.scrollbarThumb((int) (40 + 160 * visibility)));
    }

    private boolean hasInlineSettings(Module module) {
        for (SettingValue<?> setting : collectSettings(module)) {
            if (setting.canDisplay.canDisplay())
                return true;
        }
        return false;
    }

    private float inlineExpansionProgress(Module module) {
        boolean expanded = expandedModules.contains(module);
        return easeOut(animateIdentity(inlineExpansionAnimations, inlineExpansionFrames, module, expanded, 0.14f));
    }

    private void toggleInlineExpansion(Module module) {
        if (module == null)
            return;

        if (expandedModules.contains(module)) {
            expandedModules.remove(module);
            inlineColorEditors.clear();
            if (openedListModule == module)
                closeListDropdown();
            return;
        }

        expandedModules.add(module);
    }

    private float inlineSettingsHeight(Module module, float width) {
        Float cached = inlineSettingsHeightCache.get(module);
        if (cached != null)
            return cached;

        float rowWidth = width;
        float height = inlineSettingRowHeight; // Bind row
        boolean any = false;

        for (SettingValue<?> setting : collectSettings(module)) {
            float progress = settingVisibilityProgress(setting);
            if (progress <= 0.001f)
                continue;

            any = true;
            height += (inlineSettingRowHeight(setting) + inlineSettingGap
                    + inlineListHeight(module, setting, rowWidth)) * easeOut(progress);
        }

        if (!any)
            height += cu(2f);
        inlineSettingsHeightCache.put(module, height);
        return height;
    }

    private float inlineSettingRowHeight(SettingValue<?> setting) {
        if (setting instanceof ColorValue value && inlineColorEditors.contains(value))
            return inlineSettingRowHeight + inlineColorRowHeight * 3f + cu(3f);
        return inlineSettingRowHeight;
    }

    private float inlineListHeight(Module module, SettingValue<?> setting, float rowWidth) {
        if (!(setting instanceof ListValue list) || list.values.length <= 1)
            return 0f;

        float progress = listDropdownProgress(module, list);
        if (progress <= 0.001f)
            return 0f;

        float optionWidth = Math.max(cu(30f), rowWidth - cu(6f));
        float[] heights = inlineListOptionHeights(list, optionWidth);
        return (cu(1f) + listOptionsTotalHeight(heights)) * easeOut(progress);
    }

    private float[] inlineListOptionHeights(ListValue value, float optionWidth) {
        float[] heights = new float[value.values.length];
        for (int i = 0; i < value.values.length; i++)
            heights[i] = inlineOptionRowHeight;
        return heights;
    }

    private void renderInlineSettings(NVGU vg, Module module, float x, float y, float width) {
        float rowWidth = width;
        float rowX = x;
        Rect bindRow = new Rect(rowX, y, rowWidth, inlineSettingRowHeight);
        addControl(ControlType.MODULE_KEY, bindRow, module, null, 0);
        drawInlineKeyValue("Bind:", bindingModule == module ? "Press key..." : keyName(module.key),
                rowX + cu(7f), bindRow.centerY(), rowWidth - cu(14f));
        vg.rectangle(bindRow.x, bindRow.y + bindRow.height - cu(0.75f), bindRow.width, cu(0.75f),
                alpha(255, 255, 255, 22));
        float rowY = y + inlineSettingRowHeight;

        for (SettingValue<?> setting : collectSettings(module)) {
            float progress = settingVisibilityProgress(setting);
            if (progress <= 0.001f)
                continue;

            float rowHeight = inlineSettingRowHeight(setting);
            float listHeight = inlineListHeight(module, setting, rowWidth);
            float animated = (rowHeight + inlineSettingGap + listHeight) * easeOut(progress);
            float settingY = rowY;
            Rect clip = new Rect(rowX, settingY, rowWidth, animated);
            vg.scissor(clip.x, clip.y, clip.width, clip.height, () ->
                    withInputClip(clip, () -> vg.globalAlpha(easeOut(progress), () ->
                            renderInlineSetting(vg, module, setting, rowX, settingY, rowWidth))));
            rowY += rowHeight + inlineSettingGap + listHeight;
        }
    }

    private void renderInlineSetting(NVGU vg, Module module, SettingValue<?> setting, float x, float y, float width) {
        Rect row = new Rect(x, y, width, inlineSettingRowHeight);

        switch (setting) {
            case BooleanValue value -> {
                addControl(ControlType.BOOLEAN, row, value, null, 0);
                float enabled = animateIdentity(switchAnimations, switchAnimationFrames, value, value.get(), 0.12f);
                float size = columnFont(COLUMN_ROW_FONT);
                String label = truncateText(value.name == null ? "" : value.name, NVGFonts.INTER,
                        size, row.width - cu(25f));
                NVGFonts.INTER.drawText(label, row.x + cu(7f), row.centerY() - size / 2f,
                        size, NanoVGTheme.text(230), Alignment.LEFT_TOP, true);
                float boxSize = cu(13f);
                float boxX = row.x + row.width - boxSize - cu(4f);
                float boxY = row.centerY() - boxSize / 2f;
                vg.rectangle(boxX, boxY, boxSize, boxSize, alpha(5, 6, 10, 205));
                if (enabled > 0.01f) {
                    Color accent = NanoVGTheme.ACCENT;
                    NVGFonts.ICON.drawText(MaterialIcon.CHECK, boxX + boxSize / 2f, boxY + boxSize / 2f,
                            columnFont(10f), alpha(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (245 * enabled)),
                            Alignment.CENTER_MIDDLE, true);
                }
                vg.rectangle(row.x, row.y + row.height - cu(0.75f), row.width, cu(0.75f),
                        alpha(255, 255, 255, 16));
            }
            case ListValue value -> renderInlineListSetting(vg, row, module, value);
            case FloatValue value -> renderInlineNumberSetting(vg, row, value, true);
            case IntValue value -> renderInlineNumberSetting(vg, row, value, false);
            case ColorValue value -> renderInlineColorSetting(vg, row, value);
            case TextValue value -> renderInlineTextSetting(vg, row, module, value);
            case KeyBindValue value -> {
                addControl(ControlType.KEY_VALUE, row, value, null, 0);
                drawInlineKeyValue(value.name, bindingValue == value ? "Press key..." : keyName(value.get()),
                        row.x + cu(3f), row.centerY(), row.width - cu(6f));
            }
            default -> drawInlineRowLabel(setting.name, row.x + cu(3f), row.centerY(), row.width - cu(6f));
        }
    }

    private void drawInlineKeyValue(String label, String value, float x, float centerY, float maxWidth) {
        float size = columnFont(COLUMN_ROW_FONT);
        String labelText = truncateText(label == null ? "" : label, NVGFonts.INTER, size, maxWidth);
        float labelWidth = NVGFonts.INTER.getWidth(labelText, size);
        NVGFonts.INTER.drawText(labelText, x, centerY - size / 2f, size, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);

        if (value == null || value.isEmpty())
            return;

        String valueText = truncateText(value, NVGFonts.INTER, size, Math.max(cu(12f), maxWidth - labelWidth - cu(4f)));
        NVGFonts.INTER.drawText(valueText, x + labelWidth + cu(4f), centerY - size / 2f, size,
                NanoVGTheme.muted(235), Alignment.LEFT_TOP, true);
    }

    private void renderInlineListSetting(NVGU vg, Rect row, Module module, ListValue value) {
        boolean open = openedListModule == module && openedListValue == value;
        float progress = listDropdownProgress(module, value);
        float eased = easeOut(progress);
        float size = columnFont(COLUMN_ROW_FONT);

        String label = value.name == null ? "Value" : value.name;
        String current = value.get() == null ? "" : value.get();
        float labelWidth = NVGFonts.INTER.getWidth(label, size);
        float available = Math.max(cu(20f), row.width - cu(9f) - columnArrowSize - cu(6f));

        String displayLabel = label.endsWith(":") ? label : label + ":";
        vg.rectangle(row.x, row.y, row.width, row.height, alpha(255, 255, 255, 9));
        drawInlineKeyValue(displayLabel, current, row.x + cu(7f), row.centerY(), available);

        Rect arrowRect = new Rect(row.x + row.width - columnArrowSize - cu(3f), row.y,
                columnArrowSize, row.height);
        drawAtmosphereArrow(vg, arrowRect, eased,
                open ? NanoVGTheme.text(235) : NanoVGTheme.dim(190));

        addControl(ControlType.LIST_VALUE, row, value, module, 0);
        if (progress <= 0.01f)
            return;

        float optionX = row.x + cu(1f);
        float optionY = row.y + row.height + cu(1f);
        float optionWidth = Math.max(0f, row.width - cu(2f));
        renderInlineListOptions(vg, value, optionX, optionY, optionWidth, eased, open);
    }

    private void renderInlineListOptions(NVGU vg, ListValue value, float x, float y, float width,
                                         float progress, boolean interactive) {
        float[] heights = inlineListOptionHeights(value, width);
        float listHeight = listOptionsTotalHeight(heights);
        float visibleHeight = Math.max(cu(8f), listHeight * progress);
        Rect listClip = new Rect(x, y, width, visibleHeight);

        withInputClip(listClip, () -> {
            if (!(interactive && progress > 0.62f))
                return;

            float optionY = y;
            for (int i = 0; i < value.values.length; i++) {
                float optionProgress = Math.clamp((progress - i * listOptionStagger(value)) / 0.78f, 0f, 1f);
                if (optionProgress > 0.01f)
                    addControl(ControlType.LIST_OPTION, new Rect(x, optionY, width, heights[i]), value, null, i);
                optionY += heights[i];
            }
        });

        emitControlForeground(vg, () -> vg.scissor(listClip.x, listClip.y, listClip.width, listClip.height, () -> {
            float optionY = y;
            for (int i = 0; i < value.values.length; i++) {
                String option = value.values[i];
                float optionHeight = heights[i];
                float optionProgress = Math.clamp((progress - i * listOptionStagger(value)) / 0.78f, 0f, 1f);
                if (optionProgress > 0.01f) {
                    Rect optionRect = new Rect(x, optionY, width, optionHeight);
                    boolean selected = i == value.getCurrentIndex();
                    boolean hovered = optionRect.contains(scaledMouseX, scaledMouseY);

                    if (selected)
                        vg.rectangle(optionRect.x, optionRect.y + cu(1f), cu(1f),
                                Math.max(0f, optionRect.height - cu(2f)), NanoVGTheme.text(238));
                    else if (hovered)
                        vg.rectangle(optionRect.x, optionRect.y, optionRect.width, optionRect.height,
                                alpha(255, 255, 255, 30));

                    float size = columnFont(COLUMN_ROW_FONT);
                    String text = truncateText(option, NVGFonts.INTER, size, optionRect.width - cu(8f));
                    NVGFont optionFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
                    Color color = selected ? NanoVGTheme.text(245) : NanoVGTheme.muted(215);
                    optionFont.drawText(text, optionRect.x + cu(7f),
                            optionRect.y + (optionRect.height - size) / 2f, size, color, Alignment.LEFT_TOP, true);
                }
                optionY += optionHeight;
            }
        }));
    }

    private void renderInlineNumberSetting(NVGU vg, Rect row, SettingValue<?> value, boolean decimal) {
        boolean active = textFocus == TextFocus.NUMBER && focusedNumberValue == value;
        addControl(active ? (decimal ? ControlType.FLOAT_INPUT : ControlType.INT_INPUT)
                : (decimal ? ControlType.INLINE_FLOAT_SLIDER : ControlType.INLINE_INT_SLIDER), row, value, null, 0);

        float size = columnFont(COLUMN_ROW_FONT);
        String label = value.name == null ? "" : value.name;

        if (active) {
            vg.rectangle(row.x, row.y, row.width, row.height, alpha(214, 107, 232, 38));
            vg.rectangleBorder(row.x, row.y, row.width, row.height, cu(0.8f),
                    NanoVGTheme.inputFocus(120), Border.INSIDE);
            NVGFonts.INTER.drawText(label, row.x + cu(3f), row.centerY() - size / 2f, size,
                    NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);

            float labelWidth = NVGFonts.INTER.getWidth(label, size);
            float inputX = row.x + cu(3f) + labelWidth + cu(4f);
            float inputWidth = Math.max(cu(16f), row.width - labelWidth - cu(10f));
            renderInputText(vg, TextFocus.NUMBER, value, true, numberInput, size, inputX,
                    row.centerY() - size / 2f, row.centerY() - size / 2f, inputWidth,
                    new Rect(inputX, row.y, inputWidth, row.height), NanoVGTheme.text(240));
            return;
        }

        float fill = decimal
                ? normalize(((FloatValue) value).get(), ((FloatValue) value).minimum, ((FloatValue) value).maximum)
                : normalize(((IntValue) value).get(), ((IntValue) value).minimum, ((IntValue) value).maximum);
        Color accent = NanoVGTheme.ACCENT;
        if (fill > 0f)
            vg.rectangle(row.x, row.y + cu(1f), row.width * fill, row.height - cu(2f),
                    alpha(accent.getRed(), accent.getGreen(), accent.getBlue(), 105));
        String text = decimal
                ? String.format(Locale.ROOT, "%.2f%s", ((FloatValue) value).get(),
                ((FloatValue) value).suffix == null ? "" : ((FloatValue) value).suffix)
                : ((IntValue) value).get()
                + (((IntValue) value).suffix == null ? "" : ((IntValue) value).suffix);
        drawInlineKeyValue(label.endsWith(":") ? label : label + ":", text,
                row.x + cu(7f), row.centerY(), row.width - cu(14f));
    }

    private void renderInlineColorSetting(NVGU vg, Rect row, ColorValue value) {
        boolean open = inlineColorEditors.contains(value);
        drawInlineRowLabel(value.name, row.x + cu(3f), row.centerY(), row.width - cu(24f));

        Rect swatch = new Rect(row.x + row.width - inlineColorSwatchWidth - cu(3f),
                row.y + (row.height - inlineColorSwatchHeight) / 2f,
                inlineColorSwatchWidth, inlineColorSwatchHeight);
        addControl(ControlType.COLOR_VALUE, swatch, value, null, 0);
        vg.rectangle(swatch.x, swatch.y, swatch.width, swatch.height, value.get());
        vg.roundedRectangleBorder(swatch.x, swatch.y, swatch.width, swatch.height, cu(2f), cu(0.8f),
                alpha(255, 255, 255, 70), Border.INSIDE);

        if (!open)
            return;

        String[] labels = {"R", "G", "B"};
        Color[] accents = {
                new Color(255, 80, 80, 230),
                new Color(80, 255, 120, 230),
                new Color(80, 175, 255, 230)
        };
        Color color = value.get();
        int[] components = {color.getRed(), color.getGreen(), color.getBlue()};

        for (int i = 0; i < 3; i++) {
            final int index = i;
            float componentY = row.y + row.height + cu(2f) + i * inlineColorRowHeight;
            Rect componentRow = new Rect(row.x, componentY, row.width, inlineColorRowHeight);
            float size = columnFont(COLUMN_ROW_FONT);
            NVGFonts.INTER.drawText(labels[index], componentRow.x + cu(3f),
                    componentRow.y + (componentRow.height - size) / 2f, size,
                    NanoVGTheme.muted(235), Alignment.LEFT_TOP, true);

            Rect track = new Rect(componentRow.x + cu(12f), componentRow.centerY() - cu(3f),
                    Math.max(cu(20f), componentRow.width - cu(16f)), cu(6f));
            addControl(ControlType.INLINE_COLOR_SLIDER, track, value, null, index);
            emitControlForeground(vg, () -> {
                vg.rectangle(track.x, track.y, track.width, track.height, NanoVGTheme.CONTROL_TRACK);
                float filled = track.width * (components[index] / 255f);
                if (filled > 0.5f)
                    vg.rectangle(track.x, track.y, filled, track.height, accents[index]);
                float knobX = track.x + filled - cu(2.5f);
                vg.rectangle(knobX, track.y - cu(2.5f), cu(5f), track.height + cu(5f), NanoVGTheme.KNOB_FILL);
            });
        }
    }

    private void renderInlineTextSetting(NVGU vg, Rect row, Module module, TextValue value) {
        boolean active = textFocus == TextFocus.SETTING && focusedTextValue == value;
        addControl(ControlType.TEXT_VALUE, row, value, module, 0);

        float size = columnFont(COLUMN_ROW_FONT);
        String label = value.name == null ? "" : value.name;

        if (active) {
            vg.rectangle(row.x, row.y, row.width, row.height, alpha(214, 107, 232, 38));
            vg.rectangleBorder(row.x, row.y, row.width, row.height, cu(0.8f),
                    NanoVGTheme.inputFocus(120), Border.INSIDE);
            NVGFonts.INTER.drawText(label, row.x + cu(3f), row.centerY() - size / 2f, size,
                    NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);

            float labelWidth = NVGFonts.INTER.getWidth(label, size);
            float inputX = row.x + cu(3f) + labelWidth + cu(4f);
            float inputWidth = Math.max(cu(16f), row.width - labelWidth - cu(10f));
            renderInputText(vg, TextFocus.SETTING, value, true, value.get(), size, inputX,
                    row.centerY() - size / 2f, row.centerY() - size / 2f, inputWidth,
                    new Rect(inputX, row.y, inputWidth, row.height), NanoVGTheme.text(240));
            return;
        }

        drawInlineKeyValue(label, value.get(), row.x + cu(3f), row.centerY(), row.width - cu(6f));
    }

    private void drawInlineRowLabel(String label, float x, float centerY, float maxWidth) {
        float size = columnFont(COLUMN_ROW_FONT);
        String text = truncateText(label == null ? "" : label, NVGFonts.INTER, size, Math.max(cu(12f), maxWidth));
        NVGFonts.INTER.drawText(text, x, centerY - size / 2f, size, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
    }

    private static String truncateText(String text, NVGFont font, float size, float maxWidth) {
        if (text == null || text.isEmpty())
            return "";
        if (maxWidth <= 0f)
            return "";
        if (font.getWidth(text, size) <= maxWidth)
            return text;

        String ellipsis = "...";
        float ellipsisWidth = font.getWidth(ellipsis, size);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (font.getWidth(builder.toString() + character, size) + ellipsisWidth > maxWidth)
                break;
            builder.append(character);
        }
        builder.append(ellipsis);
        return builder.toString();
    }

    private void renderBottomBar(NVGU vg, float screenWidth, float screenHeight) {
        float y = screenHeight - BOTTOM_BAR_HEIGHT;
        vg.rectangle(0f, y, screenWidth, u(0.8f), new Color(255, 255, 255, 16));
        NVGFonts.INTER.drawText("LMB toggle   RMB settings", u(14f), y + u(7f), font(8f),
                NanoVGTheme.dim(185), Alignment.LEFT_TOP, true);

        float buttonHeight = u(15f);
        float buttonY = y + u(4.5f);
        Rect console = new Rect(screenWidth - u(14f) - u(54f), buttonY, u(54f), buttonHeight);
        Rect configs = new Rect(console.x - u(6f) - u(54f), buttonY, u(54f), buttonHeight);
        tabBounds.add(new TabBounds(null, ConfigTab.MODULE, null, configs));
        tabBounds.add(new TabBounds(null, null, CommandTab.COMMAND, console));
        renderBottomButton(vg, configs, "Configs", configView);
        renderBottomButton(vg, console, "Console", commandView);
    }

    private void renderTagTabs(NVGU vg, float screenWidth) {
        tagTabBounds.clear();

        float height = u(12f);
        float gap = u(3f);
        float width = u(44f);
        float totalWidth = width * 2f + gap;
        float startX = (screenWidth - totalWidth) / 2f;
        float firstRowY = topBarHeight + u(4f);
        float secondRowY = firstRowY + height + gap;

        float panelPaddingX = u(10f);
        float panelPaddingTop = u(4f);
        float panelPaddingBottom = u(6f);
        float panelHeight = secondRowY + height + panelPaddingBottom - (firstRowY - panelPaddingTop);
        vg.rectangle(startX - panelPaddingX, firstRowY - panelPaddingTop,
                totalWidth + panelPaddingX * 2f, panelHeight,
                new Color(7, 8, 12, 218));
        vg.rectangleBorder(startX - panelPaddingX, firstRowY - panelPaddingTop,
                totalWidth + panelPaddingX * 2f, panelHeight, cu(0.7f),
                alpha(255, 255, 255, 20), Border.INSIDE);

        drawTagTab(vg, new Rect(startX, firstRowY, width, height), "Classic",
                pvpTab == ModuleTags.Pvp.CLASSIC, pvpPins.contains(ModuleTags.Pvp.CLASSIC),
                ModuleTags.Pvp.CLASSIC, null);
        drawTagTab(vg, new Rect(startX + width + gap, firstRowY, width, height), "Modern",
                pvpTab == ModuleTags.Pvp.MODERN, pvpPins.contains(ModuleTags.Pvp.MODERN),
                ModuleTags.Pvp.MODERN, null);
        drawTagTab(vg, new Rect(startX, secondRowY, width, height), "Blatant",
                visibilityTab == ModuleTags.Visibility.BLATANT,
                visibilityPins.contains(ModuleTags.Visibility.BLATANT),
                null, ModuleTags.Visibility.BLATANT);
        drawTagTab(vg, new Rect(startX + width + gap, secondRowY, width, height), "Ghost",
                visibilityTab == ModuleTags.Visibility.GHOST,
                visibilityPins.contains(ModuleTags.Visibility.GHOST),
                null, ModuleTags.Visibility.GHOST);

        tagTabsBottom = secondRowY + height + panelPaddingBottom;
    }

    private void drawTagTab(NVGU vg, Rect rect, String label, boolean selected, boolean pinned,
                            ModuleTags.Pvp pvp, ModuleTags.Visibility visibility) {
        boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(rect.x, rect.y, rect.width, rect.height,
                selected ? alpha(214, 107, 232, 62)
                        : hovered ? alpha(255, 255, 255, 26)
                        : pinned ? alpha(214, 107, 232, 30)
                        : alpha(255, 255, 255, 10));
        if (pinned)
            vg.rectangleBorder(rect.x, rect.y, rect.width, rect.height, cu(0.7f), NanoVGTheme.ACCENT, Border.INSIDE);
        NVGFonts.INTER.drawText(label, rect.centerX(), rect.y + (rect.height - font(7f)) / 2f, font(7f),
                selected ? NanoVGTheme.ACCENT
                        : pinned ? NanoVGTheme.text(230)
                        : hovered ? NanoVGTheme.text(235)
                        : NanoVGTheme.muted(210),
                Alignment.CENTER_TOP, true);
        tagTabBounds.add(new TagTabBounds(rect, pvp, visibility));
    }

    private boolean handleTagTabClick(float mouseX, float mouseY, int button) {
        if (!isModuleView())
            return false;
        if (button != Keys.MOUSE_BUTTON_LEFT && button != Keys.MOUSE_BUTTON_RIGHT)
            return false;

        for (TagTabBounds tab : tagTabBounds) {
            if (!tab.rect.contains(mouseX, mouseY))
                continue;

            if (tab.pvp != null) {
                if (button == Keys.MOUSE_BUTTON_LEFT)
                    pvpTab = pvpTab == tab.pvp ? null : tab.pvp;
                else if (!pvpPins.remove(tab.pvp))
                    pvpPins.add(tab.pvp);
            } else if (tab.visibility != null) {
                if (button == Keys.MOUSE_BUTTON_LEFT)
                    visibilityTab = visibilityTab == tab.visibility ? null : tab.visibility;
                else if (!visibilityPins.remove(tab.visibility))
                    visibilityPins.add(tab.visibility);
            }

            closeListDropdown();
            return true;
        }
        return false;
    }

    private Set<ModuleTags.Pvp> activePvpTags() {
        Set<ModuleTags.Pvp> tags = EnumSet.noneOf(ModuleTags.Pvp.class);
        if (pvpTab != null)
            tags.add(pvpTab);
        tags.addAll(pvpPins);
        return tags;
    }

    private Set<ModuleTags.Visibility> activeVisibilityTags() {
        Set<ModuleTags.Visibility> tags = EnumSet.noneOf(ModuleTags.Visibility.class);
        if (visibilityTab != null)
            tags.add(visibilityTab);
        tags.addAll(visibilityPins);
        return tags;
    }

    private boolean isTagFilterActive() {
        return pvpTab != null || !pvpPins.isEmpty() || visibilityTab != null || !visibilityPins.isEmpty();
    }

    private boolean passesTagFilters(Module module) {
        ModuleTags.Tags tags = ModuleTags.of(module);
        Set<ModuleTags.Pvp> pvp = activePvpTags();
        if (!pvp.isEmpty() && Collections.disjoint(pvp, tags.pvp()))
            return false;
        Set<ModuleTags.Visibility> visibility = activeVisibilityTags();
        return visibility.isEmpty() || !Collections.disjoint(visibility, tags.visibility());
    }

    private static boolean tagMatches(Module module, String query) {
        ModuleTags.Tags tags = ModuleTags.of(module);
        for (ModuleTags.Pvp tag : tags.pvp()) {
            if (normalize(tag.name()).contains(query))
                return true;
        }
        for (ModuleTags.Visibility tag : tags.visibility()) {
            if (normalize(tag.name()).contains(query))
                return true;
        }
        return false;
    }

    private static final class TagTabBounds {
        final Rect rect;
        final ModuleTags.Pvp pvp;
        final ModuleTags.Visibility visibility;

        TagTabBounds(Rect rect, ModuleTags.Pvp pvp, ModuleTags.Visibility visibility) {
            this.rect = rect;
            this.pvp = pvp;
            this.visibility = visibility;
        }
    }

    private void renderBottomButton(NVGU vg, Rect rect, String label, boolean active) {
        boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(rect.x, rect.y, rect.width, rect.height,
                active ? alpha(214, 107, 232, 52) : hovered ? alpha(255, 255, 255, 22) : alpha(255, 255, 255, 10));
        NVGFonts.INTER.drawText(label, rect.centerX(), rect.y + u(4f), font(8f),
                active ? NanoVGTheme.ACCENT : hovered ? NanoVGTheme.text(235) : NanoVGTheme.muted(210),
                Alignment.CENTER_TOP, true);
    }

    private void renderOverlay(NVGU vg, float screenWidth, float screenHeight) {
        float width = Math.min(u(560f), screenWidth - u(30f));
        float height = Math.min(u(430f), screenHeight - topBarHeight - BOTTOM_BAR_HEIGHT - u(24f));
        float x = (screenWidth - width) / 2f;
        float y = topBarHeight + u(8f);
        overlayRect = new Rect(x, y, width, height);

        vg.beginEffectBatch();
        drawControlGlass(vg, x, y, width, height, u(NanoVGTheme.RADIUS_LABEL), 0.25f, true, 0f);
        vg.flushEffectBatch();

        overlayCloseRect = new Rect(x + width - u(18f), y + u(4f), u(14f), u(14f));
        float headerHeight = u(22f);
        float footerHeight = u(18f);
        float tabFont = font(10f);
        float tabArea = Math.max(u(60f), width - u(30f));

        vg.rectangle(x, y, width, headerHeight, new Color(10, 11, 16, 235));
        vg.rectangle(x, y + headerHeight - u(0.8f), width, u(0.8f), alpha(255, 255, 255, 18));

        boolean closeHovered = overlayCloseRect.contains(scaledMouseX, scaledMouseY);
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, overlayCloseRect.centerX(), overlayCloseRect.centerY(),
                font(10f), closeHovered ? NanoVGTheme.danger(245) : NanoVGTheme.dim(200),
                Alignment.CENTER_MIDDLE, true);

        if (configView) {
            ConfigTab[] tabs = ConfigTab.values();
            float segment = tabArea / tabs.length;
            for (int i = 0; i < tabs.length; i++) {
                ConfigTab tab = tabs[i];
                Rect rect = new Rect(x + i * segment, y, segment, headerHeight);
                tabBounds.add(new TabBounds(null, tab, null, rect));
                boolean selected = selectedConfigTab == tab;
                boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
                if (selected)
                    NanoVGTheme.renderAccentUnderline(vg, rect.x + u(6f), rect.y + rect.height - u(1.2f),
                            Math.max(1f, rect.width - u(12f)), u(1.2f), 1f);
                NVGFonts.INTER.drawText(tab.sidebarLabel, rect.centerX(), rect.centerY(), tabFont,
                        selected ? NanoVGTheme.text(240) : hovered ? NanoVGTheme.text(235) : NanoVGTheme.muted(200),
                        Alignment.CENTER_MIDDLE, true);
            }
        } else {
            CommandTab[] tabs = CommandTab.values();
            float segment = tabArea / tabs.length;
            for (int i = 0; i < tabs.length; i++) {
                CommandTab tab = tabs[i];
                Rect rect = new Rect(x + i * segment, y, segment, headerHeight);
                tabBounds.add(new TabBounds(null, null, tab, rect));
                boolean selected = selectedCommandTab == tab;
                boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
                if (selected)
                    NanoVGTheme.renderAccentUnderline(vg, rect.x + u(6f), rect.y + rect.height - u(1.2f),
                            Math.max(1f, rect.width - u(12f)), u(1.2f), 1f);
                NVGFonts.INTER.drawText(tab.sidebarLabel, rect.centerX(), rect.centerY(), tabFont,
                        selected ? NanoVGTheme.text(240) : hovered ? NanoVGTheme.text(235) : NanoVGTheme.muted(200),
                        Alignment.CENTER_MIDDLE, true);
            }
        }

        float contentY = y + headerHeight;
        float contentHeight = Math.max(u(40f), height - headerHeight - footerHeight);
        vg.rectangle(x, contentY, width, contentHeight, new Color(15, 16, 21, 211));

        int visibleCount = configView
                ? renderConfigList(vg, x, contentY, width, contentHeight)
                : renderCommandList(vg, x + u(6f), contentY, width - u(12f), contentHeight - u(6f));

        float footerY = y + height - footerHeight;
        vg.rectangle(x, footerY, width, u(0.8f), alpha(255, 255, 255, 18));

        String countLabel = visibleCount + (configView
                ? (visibleCount == 1 ? " config" : " configs")
                : (visibleCount == 1 ? " line" : " lines"));
        NVGFonts.INTER.drawText(countLabel, x + u(8f), footerY + (footerHeight - font(8f)) / 2f,
                font(8f), NanoVGTheme.dim(185), Alignment.LEFT_TOP, true);
    }

    private void updateColumnScrolls(float delta) {
        for (ColumnState column : columns.values()) {
            if (column.viewport.height <= 0f)
                continue;

            column.target = Math.clamp(column.target, 0f, column.max);
            if (column == activeColumnDrag) {
                column.scroll = column.target;
                column.velocity = 0f;
                continue;
            }

            if (column.max <= 0f) {
                column.target = 0f;
                column.scroll = 0f;
                column.velocity = 0f;
                continue;
            }

            float distance = column.target - column.scroll;
            column.velocity += distance * 0.045f * delta;
            column.velocity *= (float) Math.pow(0.74f, delta);
            column.velocity = Math.clamp(column.velocity, -42f, 42f);
            column.scroll += column.velocity * delta;
            column.scroll = approach(column.scroll, column.target, 1f - (float) Math.pow(0.91f, delta));

            if (column.scroll < 0f || column.scroll > column.max) {
                column.scroll = Math.clamp(column.scroll, 0f, column.max);
                column.velocity = 0f;
            }

            if (Math.abs(column.target - column.scroll) < 0.06f && Math.abs(column.velocity) < 0.06f) {
                column.scroll = column.target;
                column.velocity = 0f;
            }
        }
    }

    private void updateColumnScrollbarDrag(float mouseY) {
        if (activeColumnDrag == null || activeColumnDrag.max <= 0f
                || activeColumnDrag.scrollbarTrack.height <= 0f
                || activeColumnDrag.scrollbarThumb.height <= 0f)
            return;

        float travel = Math.max(1f, activeColumnDrag.scrollbarTrack.height - activeColumnDrag.scrollbarThumb.height);
        float thumbY = Math.clamp(mouseY - activeColumnDragOffset, activeColumnDrag.scrollbarTrack.y,
                activeColumnDrag.scrollbarTrack.y + travel);
        float progress = (thumbY - activeColumnDrag.scrollbarTrack.y) / travel;
        activeColumnDrag.target = Math.clamp(progress * activeColumnDrag.max, 0f, activeColumnDrag.max);
        activeColumnDrag.scroll = activeColumnDrag.target;
        activeColumnDrag.velocity = 0f;
    }

    private List<Module> visibleModulesFor(ModuleCategory category) {
        String query = normalize(searchQuery);
        List<Module> modules = new ArrayList<>();
        for (Module module : ModuleManager.allModules) {
            if (module.moduleCategory != category)
                continue;
            if (!passesTagFilters(module))
                continue;
            if (query.isBlank() || moduleMatches(module, query))
                modules.add(module);
        }
        modules.sort(Comparator.comparing(module -> module.moduleName, String.CASE_INSENSITIVE_ORDER));
        return modules;
    }

    private static boolean moduleMatches(Module module, String query) {
        if (tagMatches(module, query))
            return true;
        return normalize(module.moduleName).contains(query)
                || normalize(module.moduleCategory.name()).contains(query)
                || normalize(module.moduleCategory.label()).contains(query)
                || (module.tag() != null && normalize(module.tag()).contains(query))
                || (module.description() != null && normalize(module.description()).contains(query));
    }

    private static final class ColumnState {
        final ModuleCategory category;
        Rect header = new Rect(0f, 0f, 0f, 0f);
        Rect body = new Rect(0f, 0f, 0f, 0f);
        Rect viewport = new Rect(0f, 0f, 0f, 0f);
        Rect scrollbarTrack = new Rect(0f, 0f, 0f, 0f);
        Rect scrollbarThumb = new Rect(0f, 0f, 0f, 0f);
        float scroll;
        float target;
        float velocity;
        float max;

        ColumnState(ModuleCategory category) {
            this.category = category;
        }
    }

    private void renderSettingsPanel(NVGU vg, float screenWidth, float screenHeight) {
        settingsPanelControlStartIndex = controls.size();

        Module module = settingsPanelRenderModule;
        ConfigEntry config = settingsPanelRenderConfig;
        float reveal = settingsDockReveal;
        if ((module == null && config == null) || reveal <= 0f) {
            settingsPanelRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelCloseRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelViewport = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            maxSettingsPanelScroll = 0f;
            return;
        }

        ColumnState anchor = module != null ? columns.get(module.moduleCategory) : null;
        float width = Math.min(u(256f), screenWidth * 0.32f);
        float top = anchor != null ? anchor.header.y : topBarHeight + u(8f);
        float height = Math.min(u(400f), screenHeight - top - BOTTOM_BAR_HEIGHT - u(10f));
        if (height < u(140f))
            height = Math.max(u(140f), screenHeight - top - u(10f));
        float x = anchor != null ? anchor.header.x + anchor.header.width + u(8f) : screenWidth - width - u(14f);
        if (x + width > screenWidth - u(10f))
            x = Math.max(u(10f), (anchor != null ? anchor.header.x : screenWidth * 0.5f) - width - u(8f));
        float slide = (1f - reveal) * u(18f);
        settingsPanelRect = new Rect(x + slide, top, width, height);
        settingsPanelCloseRect = new Rect(
                settingsPanelRect.x + settingsPanelRect.width - u(24f),
                settingsPanelRect.y + u(7f),
                u(15f),
                u(15f)
        );

        Rect dockClip = settingsPanelRect;
        float headerNameX = module != null
                ? settingsPanelRect.x + u(10f) + switchWidth + u(7f)
                : settingsPanelRect.x + u(10f);
        float headerNameWidth = Math.max(u(40f), settingsPanelCloseRect.x - u(6f) - headerNameX);
        String headerName = module != null ? module.moduleName : config.name();
        NVGFont headerNameFont = module != null ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        float headerHeight = u(33f)
                + (wrappedLineCount(headerName, headerNameFont, font(11f), headerNameWidth) - 1) * nameLineHeight;
        float settingsListX = settingsPanelRect.x + u(4f);
        float settingsListY = settingsPanelRect.y + headerHeight;
        float settingsListWidth = settingsPanelRect.width - u(8f);
        float settingsListHeight = settingsPanelRect.height - headerHeight - u(4f);
        settingsPanelViewport = new Rect(
                settingsListX + u(2f),
                settingsListY + u(3f),
                settingsListWidth - u(7f),
                settingsListHeight - u(6f)
        );
        float settingsContentHeight = module != null ? expandedSettingsHeight(module) : configInfoHeight();
        maxSettingsPanelScroll = Math.max(0f, settingsContentHeight - settingsPanelViewport.height);
        targetSettingsPanelScroll = Math.clamp(targetSettingsPanelScroll, 0f, maxSettingsPanelScroll);
        settingsPanelScroll = Math.clamp(settingsPanelScroll, 0f, maxSettingsPanelScroll);
        if (maxSettingsPanelScroll <= 0f)
            settingsPanelScrollVelocity = 0f;

        vg.globalAlpha(reveal, () -> {
            deferControlChrome = true;
            try {
                withInputClip(dockClip, () -> vg.scissor(dockClip.x, dockClip.y, dockClip.width, dockClip.height, () -> {
                    NanoVGTheme.renderCompact(
                            vg,
                            settingsPanelRect.x,
                            settingsPanelRect.y,
                            settingsPanelRect.width,
                            settingsPanelRect.height,
                            u(NanoVGTheme.RADIUS_LABEL),
                            1f,
                            20
                    );
                    vg.rectangle(
                            settingsPanelRect.x + 1f,
                            settingsPanelRect.y + headerHeight,
                            settingsPanelRect.width - 2f,
                            u(0.8f),
                            alpha(255, 255, 255, 18)
                    );

                    float headerFont = font(11f);
                    if (module != null) {
                        Rect enableRect = new Rect(
                                settingsPanelRect.x + u(10f),
                                settingsPanelRect.y + u(9f),
                                switchWidth,
                                switchHeight
                        );
                        drawWrappedText(headerNameFont, headerName, headerNameX, settingsPanelRect.y + u(8f), headerFont,
                                headerNameWidth, nameLineHeight, NanoVGTheme.TEXT, Alignment.LEFT_TOP);
                        if (settingsPanelModule != null)
                            addControl(ControlType.MODULE_ENABLED, enableRect.expand(u(4f), u(6f)), module, null, 0);
                        drawSwitch(
                                vg,
                                enableRect.x,
                                enableRect.y,
                                enableRect.width,
                                enableRect.height,
                                animateIdentity(enabledAnimations, enabledAnimationFrames, module, module.tempEnabled, 0.12f),
                                "enable:" + System.identityHashCode(module),
                                isPressed(ControlType.MODULE_ENABLED, module)
                        );
                    } else {
                        drawWrappedText(headerNameFont, headerName, headerNameX, settingsPanelRect.y + u(8f), headerFont,
                                headerNameWidth, nameLineHeight, NanoVGTheme.TEXT, Alignment.LEFT_TOP);
                    }

                    boolean closeHovered = settingsPanelCloseRect.contains(scaledMouseX, scaledMouseY);
                    float closeProgress = animate("settings-panel-close", closeHovered, 0.18f);
                    vg.rectangle(
                            settingsPanelCloseRect.x,
                            settingsPanelCloseRect.y,
                            settingsPanelCloseRect.width,
                            settingsPanelCloseRect.height,
                            mix(alpha(255, 255, 255, 8), alpha(255, 93, 122, 58), closeProgress)
                    );
                    NVGFonts.ICON.drawText(
                            MaterialIcon.CLOSE,
                            settingsPanelCloseRect.centerX(),
                            settingsPanelCloseRect.centerY() - u(1f),
                            font(10f),
                            mix(alpha(148, 138, 158, 230), alpha(255, 93, 122, 245), closeProgress),
                            Alignment.CENTER_MIDDLE,
                            true
                    );

                    NanoVGTheme.renderCompact(vg, settingsListX, settingsListY, settingsListWidth, settingsListHeight, u(NanoVGTheme.RADIUS_LABEL), 1f, 22);
                    withInputClip(settingsPanelViewport, () -> vg.scissor(
                            settingsPanelViewport.x,
                            settingsPanelViewport.y,
                            settingsPanelViewport.width,
                            settingsPanelViewport.height,
                            () -> {
                                if (module != null)
                                    renderExpandedSettings(
                                            vg,
                                            module,
                                            settingsPanelViewport.x,
                                            settingsPanelViewport.y - settingsPanelScroll,
                                            settingsPanelViewport.width
                                    );
                                else
                                    renderConfigInfo(
                                            vg,
                                            config,
                                            settingsPanelViewport.x,
                                            settingsPanelViewport.y - settingsPanelScroll,
                                            settingsPanelViewport.width
                                    );
                            }
                    ));
                }));
                flushPendingControlGlass(vg);
                vg.scissor(dockClip.x, dockClip.y, dockClip.width, dockClip.height, () -> {
                    drawPendingControlForegrounds(vg);
                    renderSettingsPanelScrollbar(vg, settingsContentHeight);
                });
            } finally {
                deferControlChrome = false;
                pendingControlGlass.clear();
                pendingControlForegrounds.clear();
            }
        });

        if (!isSettingsDockOpen()) {
            while (controls.size() > settingsPanelControlStartIndex)
                controls.remove(controls.size() - 1);
        }
    }

    private float settingsPanelReveal() {
        boolean open = isSettingsDockOpen();
        float raw = animate("settings-panel-open", open, 0.14f);
        if (!open && raw <= 0.02f) {
            animations.put("settings-panel-open", 0f);
            return 0f;
        }

        float reveal = open ? easeOut(raw) : easeIn(raw);
        return reveal <= 0.0005f ? 0f : reveal;
    }

    private float settingsDockWidth(float contentWidth) {
        return Math.clamp(contentWidth * 0.40f, u(248f), u(332f));
    }

    private float settingsDockReservedWidth(float contentWidth, float reveal) {
        return (settingsDockWidth(contentWidth) + settingsDockGap) * Math.clamp(reveal, 0f, 1f);
    }

    private Rect panelContentBounds(Rect panel) {
        return new Rect(
                panel.x + panelPadding,
                panel.y + panelHeaderHeight + panelPadding,
                panel.width - panelPadding * 2f,
                panel.height - panelHeaderHeight - panelPadding * 2f - panelFooterHeight
        );
    }

    private Rect mainContentBounds(Rect content) {
        return new Rect(
                content.x + categorySidebarWidth + categoryContentGap,
                content.y,
                content.width - categorySidebarWidth - categoryContentGap,
                content.height
        );
    }

    private void renderHeader(NVGU vg, float x, float y, float width) {
        closeRect = new Rect(x + width - u(30f), y + u(11f), u(18f), u(18f));
        searchRect = new Rect(x + width - u(242f), y + u(10f), u(196f), u(20f));

        renderSearch(vg, searchRect);

        boolean closeHovered = closeRect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(closeRect.x, closeRect.y, closeRect.width, closeRect.height, closeHovered ? alpha(255, 90, 90, 120) : alpha(255, 255, 255, 18));
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, closeRect.centerX(), closeRect.centerY() - u(1f), font(14f), closeHovered ? NanoVGTheme.TEXT : NanoVGTheme.muted(220), Alignment.CENTER_MIDDLE, true);

        vg.rectangle(x + u(10f), y + panelHeaderHeight - u(1f), width - u(20f), u(1f), alpha(255, 255, 255, 40));
    }

    private void renderSearch(NVGU vg, Rect rect) {
        boolean focused = textFocus == TextFocus.SEARCH;
        boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(rect.x, rect.y, rect.width, rect.height, alpha(14, 14, 16, focused ? 215 : hovered ? 190 : 175));
        vg.rectangleBorder(rect.x, rect.y, rect.width, rect.height, u(0.8f),
                focused ? alpha(214, 107, 232, 160) : alpha(255, 255, 255, 14), Border.INSIDE);

        String text = inputText(searchQuery, focused, "Press ENTER to Search..");
        Color color = searchQuery.isBlank() && !focused ? NanoVGTheme.dim(195) : NanoVGTheme.text(235);
        float textReserve = searchQuery.isBlank() ? u(8f) : u(24f);
        float searchFont = font(9f);
        renderInputText(vg, TextFocus.SEARCH, null, focused, text, searchFont, rect.x + u(6f), rect.y + u(5f), rect.y + u(5f), rect.width - textReserve, rect, color);

        if (searchQuery.isBlank()) {
            searchClearRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        searchClearRect = new Rect(rect.x + rect.width - u(19f), rect.y + u(2f), u(16f), u(16f));
        boolean clearHovered = searchClearRect.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(searchClearRect.x, searchClearRect.y, searchClearRect.width, searchClearRect.height,
                clearHovered ? alpha(255, 255, 255, 28) : alpha(255, 255, 255, 10));
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, searchClearRect.centerX(), searchClearRect.centerY() - u(1f), font(11f),
                clearHovered ? NanoVGTheme.TEXT : NanoVGTheme.muted(210), Alignment.CENTER_MIDDLE, true);
    }

    private void renderFooter(NVGU vg, float x, float y, float width, int visibleCount) {
        vg.rectangle(x, y - u(2f), width, u(1f), alpha(255, 255, 255, 28));

        String countLabel;
        if (!searchQuery.isBlank())
            countLabel = visibleCount + (visibleCount == 1 ? " result" : " results");
        else if (configView)
            countLabel = visibleCount + (visibleCount == 1 ? " config" : " configs");
        else if (commandView && selectedCommandTab == CommandTab.MULTIPLAY)
            countLabel = visibleCount + (visibleCount == 1 ? " player" : " players");
        else if (commandView)
            countLabel = visibleCount + (visibleCount == 1 ? " log" : " logs");
        else
            countLabel = visibleCount + (visibleCount == 1 ? " module" : " modules");
        NVGFonts.INTER.drawText(countLabel, x + width - u(2f), y + u(5f), font(10f),
                searchQuery.isBlank() ? NanoVGTheme.muted(220) : NanoVGTheme.text(230),
                Alignment.RIGHT_TOP, true);
    }

    private void renderTabs(NVGU vg, float x, float y, float width, float height) {
        ModuleCategory[] categories = ModuleCategory.values();
        ConfigTab[] configTabs = ConfigTab.values();
        CommandTab[] commandTabs = CommandTab.values();
        int tabCount = categories.length + configTabs.length + commandTabs.length;
        float naturalHeight = u(5f) + tabCount * u(27f) + u(11f) * 2f;
        float fit = Math.clamp(height / naturalHeight, 0.72f, 1f);
        float gap = u(5f) * fit;
        float tabHeight = u(22f) * fit;
        float tabFont = font(14f);
        float textOffset = (tabHeight - tabFont) / 2f;
        Rect selectedRect = null;

        for (int i = 0; i < categories.length; i++) {
            ModuleCategory category = categories[i];
            Rect rect = new Rect(x, y + u(5f) + i * (tabHeight + gap), width, tabHeight);
            tabBounds.add(new TabBounds(category, null, null, rect));

            boolean selected = isModuleView() && searchQuery.isBlank() && selectedCategory == category;
            if (selected)
                selectedRect = rect;

            NVGFont categoryFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
            categoryFont.drawText(category.name(), rect.x + u(12f), rect.y + textOffset, tabFont, selected ? NanoVGTheme.TEXT : NanoVGTheme.MUTED, Alignment.LEFT_TOP, true);
        }

        float dividerY = y + u(5f) + categories.length * (tabHeight + gap) + u(2f) * fit;
        vg.rectangle(x + u(8f), dividerY, width - u(16f), u(1f), alpha(255, 255, 255, 48));

        float configStartY = dividerY + u(9f) * fit;
        for (int i = 0; i < configTabs.length; i++) {
            ConfigTab tab = configTabs[i];
            Rect rect = new Rect(x, configStartY + i * (tabHeight + gap), width, tabHeight);
            tabBounds.add(new TabBounds(null, tab, null, rect));

            boolean selected = configView && searchQuery.isBlank() && selectedConfigTab == tab;
            if (selected)
                selectedRect = rect;

            NVGFont tabLabelFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
            tabLabelFont.drawText(tab.sidebarLabel, rect.x + u(12f), rect.y + textOffset, tabFont, selected ? NanoVGTheme.TEXT : NanoVGTheme.MUTED, Alignment.LEFT_TOP, true);
        }

        float commandDividerY = configStartY + configTabs.length * (tabHeight + gap) + u(2f) * fit;
        vg.rectangle(x + u(8f), commandDividerY, width - u(16f), u(1f), alpha(255, 255, 255, 48));

        float commandStartY = commandDividerY + u(9f) * fit;
        for (int i = 0; i < commandTabs.length; i++) {
            CommandTab tab = commandTabs[i];
            Rect rect = new Rect(x, commandStartY + i * (tabHeight + gap), width, tabHeight);
            tabBounds.add(new TabBounds(null, null, tab, rect));

            boolean selected = commandView && searchQuery.isBlank() && selectedCommandTab == tab;
            if (selected)
                selectedRect = rect;

            NVGFont tabLabelFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
            tabLabelFont.drawText(tab.sidebarLabel, rect.x + u(12f), rect.y + textOffset, tabFont, selected ? NanoVGTheme.TEXT : NanoVGTheme.MUTED, Alignment.LEFT_TOP, true);
        }

        if (selectedRect == null)
            return;

        tabMarkY = Float.isNaN(tabMarkY)
                ? selectedRect.y
                : approach(tabMarkY, selectedRect.y, Math.clamp(0.16f * frameDelta * 0.9f, 0.08f, 1f));
        renderListRowMark(vg, new Rect(selectedRect.x, tabMarkY, selectedRect.width, selectedRect.height));
    }

    private int renderModuleList(NVGU vg, float x, float y, float width, float height) {
        NanoVGTheme.renderCompact(vg, x, y, width, height, u(NanoVGTheme.RADIUS_HUD), 1f, COMPACT_BACKGROUND_ALPHA_OFFSET);

        List<Module> modules = visibleModules();
        listViewport = new Rect(x + u(5f), y + u(5f), width - u(10f), height - u(10f));
        float contentHeight = contentHeight(modules, listViewport.width);
        maxListScroll = Math.max(0f, contentHeight - listViewport.height);
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        if (maxListScroll <= 0f)
            scrollVelocity = 0f;

        withInputClip(listViewport, () -> withControlChrome(vg, listViewport, () -> {
            float rowY = listViewport.y - listScroll;

            if (modules.isEmpty()) {
                renderEmptyState(
                        vg,
                        listViewport,
                        searchQuery.isBlank() ? MaterialIcon.INFO : MaterialIcon.SEARCH,
                        searchQuery.isBlank() ? "No modules in this category" : "No matching modules",
                        searchQuery.isBlank() ? "Choose another category." : "Try another keyword or clear the search."
                );
                return;
            }

            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                float blockHeight = moduleBlockHeight(module, listViewport.width);
                if (rowY + blockHeight > listViewport.y && rowY < listViewport.y + listViewport.height)
                    renderModuleBlock(vg, module, i, modules.size(), listViewport.x, rowY, listViewport.width);
                rowY += blockHeight;
            }
        }));

        renderScrollbar(vg, listViewport, contentHeight, listScroll);
        return modules.size();
    }

    private int renderConfigList(NVGU vg, float x, float y, float width, float height) {
        float innerX = x + u(8f);
        float innerWidth = width - u(16f);
        float rowTop = y + u(6f);

        if (selectedConfigTab != ConfigTab.ONLINE) {
            renderConfigCreateRow(vg, innerX, rowTop, innerWidth);
            rowTop += u(28f);
        }

        List<ConfigEntry> configs = visibleConfigEntries();
        listViewport = new Rect(innerX, rowTop, innerWidth, Math.max(u(30f), y + height - rowTop - u(5f)));
        float contentHeight = u(2f);
        for (ConfigEntry config : configs)
            contentHeight += configRowHeight(config, listViewport.width);
        contentHeight = Math.max(rowHeight, contentHeight);
        maxListScroll = Math.max(0f, contentHeight - listViewport.height);
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        if (maxListScroll <= 0f)
            scrollVelocity = 0f;

        withInputClip(listViewport, () -> vg.scissor(listViewport.x, listViewport.y, listViewport.width, listViewport.height, () -> {
            float rowY = listViewport.y - listScroll;

            if (configs.isEmpty()) {
                renderEmptyState(
                        vg,
                        listViewport,
                        searchQuery.isBlank() ? MaterialIcon.INFO : MaterialIcon.SEARCH,
                        searchQuery.isBlank() ? "No " + selectedConfigTab.emptyName + " configs" : "No matching configs",
                        searchQuery.isBlank() ? "Create one above or choose another category." : "Try another keyword or clear the search."
                );
                return;
            }

            for (int i = 0; i < configs.size(); i++) {
                ConfigEntry config = configs.get(i);
                float configHeight = configRowHeight(config, listViewport.width);
                if (rowY + configHeight > listViewport.y && rowY < listViewport.y + listViewport.height)
                    renderConfigRow(vg, config, i, configs.size(), listViewport.x, rowY, listViewport.width);
                rowY += configHeight;
            }
        }));

        renderScrollbar(vg, listViewport, contentHeight, listScroll);
        return configs.size();
    }

    private int renderCommandList(NVGU vg, float x, float y, float width, float height) {
        return selectedCommandTab == CommandTab.MULTIPLAY
                ? renderMultiplayList(vg, x, y, width, height)
                : renderCommandConsole(vg, x, y, width, height);
    }

    private int renderCommandConsole(NVGU vg, float x, float y, float width, float height) {
        float rowTop = y + u(8f);
        renderCommandInputRow(vg, x + u(8f), rowTop, width - u(16f));
        rowTop += u(36f);

        List<String> logs = visibleCommandLogs();
        listViewport = new Rect(x + u(5f), rowTop, width - u(10f), Math.max(u(42f), y + height - rowTop - u(6f)));
        float textWidth = Math.max(u(20f), listViewport.width - u(24f));
        if (Math.abs(commandLogWrapWidth - textWidth) > 0.5f || commandLogLineCache.size() > ModuleRenderable.commandLogs.size() * 2 + 8) {
            commandLogLineCache.clear();
            commandLogWrapWidth = textWidth;
        }

        float contentHeight = u(2f);
        for (String log : logs)
            contentHeight += commandLogBlockHeight(log, textWidth);
        contentHeight = Math.max(rowHeight, contentHeight);
        maxListScroll = Math.max(0f, contentHeight - listViewport.height);
        if (followNewCommandLogs()) {
            targetListScroll = maxListScroll;
            scrollVelocity = 0f;
        }
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        if (maxListScroll <= 0f)
            scrollVelocity = 0f;

        withInputClip(listViewport, () -> vg.scissor(listViewport.x, listViewport.y, listViewport.width, listViewport.height, () -> {
            if (logs.isEmpty()) {
                renderEmptyState(
                        vg,
                        listViewport,
                        searchQuery.isBlank() ? MaterialIcon.READER : MaterialIcon.SEARCH,
                        searchQuery.isBlank() ? "Type commands here" : "No matching logs",
                        searchQuery.isBlank() ? "Run \"help\" to show every command." : "Try another keyword or clear the search."
                );
                return;
            }

            float rowY = listViewport.y - listScroll;
            float lineHeight = u(15f);
            float logFont = font(12f);
            for (String log : logs) {
                float blockHeight = commandLogBlockHeight(log, textWidth);
                if (rowY + blockHeight > listViewport.y && rowY < listViewport.y + listViewport.height) {
                    vg.rectangle(listViewport.x + u(6f), rowY + u(1f), listViewport.width - u(12f), blockHeight - u(3f), alpha(255, 255, 255, 12));
                    List<String> lines = commandLogLines(log, textWidth);
                    for (int i = 0; i < lines.size(); i++)
                        NVGFonts.INTER.drawText(lines.get(i), listViewport.x + u(14f), rowY + u(5f) + i * lineHeight, logFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
                }
                rowY += blockHeight;
            }
        }));

        renderScrollbar(vg, listViewport, contentHeight, listScroll);
        renderCommandSuggestions(vg, y + height - u(6f));
        return logs.size();
    }

    private void renderCommandInputRow(NVGU vg, float x, float y, float width) {
        boolean active = textFocus == TextFocus.COMMAND;
        Rect input = new Rect(x, y, width - u(91f), u(24f));
        Rect button = new Rect(x + width - u(83f), y, u(83f), u(24f));
        commandInputRect = input;
        updateCommandSuggestions();
        addControl(ControlType.COMMAND_INPUT, input, null, null, 0);
        addControl(ControlType.COMMAND_RUN, button, null, null, 0);

        boolean inputHovered = input.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(input.x, input.y, input.width, input.height, alpha(255, 255, 255, active ? 29 : inputHovered ? 22 : 16));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(5f), u(1f), active ? NanoVGTheme.inputFocus(110) : alpha(255, 255, 255, 28), Border.INSIDE);

        String text = inputText(commandInput, active, "Enter a command");
        float inputFont = font(11f);
        Color textColor = commandInput.isBlank() && !active ? NanoVGTheme.muted(180) : NanoVGTheme.TEXT;
        NVGFonts.ICON.drawText(MaterialIcon.FORWARD_IOS, input.x + u(8f), input.y + u(5f), font(12f), active ? NanoVGTheme.INPUT_FOCUS : alpha(176, 186, 196, 220), Alignment.LEFT_TOP, true);
        renderInputText(vg, TextFocus.COMMAND, null, active, text, inputFont, input.x + u(26f), input.y + u(6f), input.y + u(6f), input.width - u(32f), input, textColor);
        renderCommandSuggestionGhost(vg, input, text, inputFont);

        renderCommandButton(vg, button, "command-run", "Run", !commandInput.isBlank(), false);
    }

    private int renderMultiplayList(NVGU vg, float x, float y, float width, float height) {
        float rowTop = y + u(8f);
        Rect disconnect = new Rect(x + u(8f), rowTop, width - u(16f), u(24f));
        boolean connected = minecraft != null && minecraft.level != null;
        addControl(ControlType.MULTIPLAY_DISCONNECT, disconnect, null, null, 0);
        renderCommandButton(vg, disconnect, "multiplay-disconnect", "Force disconnect from server", connected, true);
        rowTop += u(36f);

        List<PlayerEntry> players = visibleMultiplayPlayers();
        listViewport = new Rect(x + u(5f), rowTop, width - u(10f), Math.max(u(42f), y + height - rowTop - u(6f)));
        float contentHeight = u(2f);
        for (PlayerEntry player : players)
            contentHeight += playerRowHeight(player, listViewport.width);
        contentHeight = Math.max(rowHeight, contentHeight);
        maxListScroll = Math.max(0f, contentHeight - listViewport.height);
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        if (maxListScroll <= 0f)
            scrollVelocity = 0f;

        withInputClip(listViewport, () -> vg.scissor(listViewport.x, listViewport.y, listViewport.width, listViewport.height, () -> {
            if (players.isEmpty()) {
                boolean inWorld = minecraft != null && minecraft.level != null && minecraft.getConnection() != null;
                renderEmptyState(
                        vg,
                        listViewport,
                        !searchQuery.isBlank() ? MaterialIcon.SEARCH : MaterialIcon.PERSON,
                        !searchQuery.isBlank() ? "No matching players" : inWorld ? "No players" : "Not connected",
                        !searchQuery.isBlank() ? "Try another keyword or clear the search." : inWorld ? "No players are loaded nearby." : "Join a world or server to see players."
                );
                return;
            }

            float rowY = listViewport.y - listScroll;
            for (PlayerEntry player : players) {
                float playerHeight = playerRowHeight(player, listViewport.width);
                if (rowY + playerHeight > listViewport.y && rowY < listViewport.y + listViewport.height)
                    renderPlayerRow(vg, player, listViewport.x, rowY, listViewport.width);
                rowY += playerHeight;
            }
        }));

        renderScrollbar(vg, listViewport, contentHeight, listScroll);
        return players.size();
    }

    private float playerRowHeight(PlayerEntry player, float width) {
        int lines = wrappedLineCount(playerDisplayName(player), playerNameFont(player), font(14f), playerNameWidth(player, width));
        return settingRowStep + (lines - 1) * nameLineHeight;
    }

    private static String playerDisplayName(PlayerEntry player) {
        return player.self() ? player.name() + " (You)" : player.name();
    }

    private static NVGFont playerNameFont(PlayerEntry player) {
        return player.self() ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
    }

    private static String playerPingLabel(PlayerEntry player) {
        return player.ping() >= 0 ? player.ping() + "ms" : "-";
    }

    private float playerNameWidth(PlayerEntry player, float width) {
        float buttonsWidth = u(8f) + u(50f) * 3f + u(5f) * 2f;
        float pingWidth = NVGFonts.INTER.getWidth(playerPingLabel(player), font(10f));
        return Math.max(u(28f), width - buttonsWidth - u(10f) - pingWidth - u(8f) - u(14f));
    }

    private void renderPlayerRow(NVGU vg, PlayerEntry player, float x, float y, float width) {
        Rect row = new Rect(x, y, width, playerRowHeight(player, width) - u(4f));
        if (isHovered(row))
            renderListRowWash(vg, row, 1f);
        if (player.self())
            renderListRowMark(vg, row, 1f);

        float buttonWidth = u(50f);
        float buttonGap = u(5f);
        float buttonHeight = u(20f);
        float buttonY = row.y + (settingRowStep - u(4f) - buttonHeight) / 2f;
        Rect crash = new Rect(row.x + row.width - u(8f) - buttonWidth, buttonY, buttonWidth, buttonHeight);
        Rect teleport = new Rect(crash.x - buttonGap - buttonWidth, buttonY, buttonWidth, buttonHeight);
        Rect kill = new Rect(teleport.x - buttonGap - buttonWidth, buttonY, buttonWidth, buttonHeight);
        addControl(ControlType.PLAYER_KILL, kill, player.name(), null, 0);
        addControl(ControlType.PLAYER_TELEPORT, teleport, player.name(), null, 0);
        addControl(ControlType.PLAYER_CRASH, crash, player.name(), null, 0);

        float nameX = row.x + u(14f);
        NVGFonts.INTER.drawText(playerPingLabel(player), kill.x - u(10f), row.y + u(8f), font(10f), NanoVGTheme.MUTED, Alignment.RIGHT_TOP, true);
        drawWrappedText(playerNameFont(player), playerDisplayName(player), nameX, row.y + u(7f), font(14f),
                playerNameWidth(player, width), nameLineHeight, NanoVGTheme.TEXT, Alignment.LEFT_TOP);

        String key = "player:" + player.name() + ":";
        renderCommandButton(vg, kill, key + "kill", "Kill", true, false);
        renderCommandButton(vg, teleport, key + "tp", "Teleport", true, false);
        renderCommandButton(vg, crash, key + "crash", "Crash", true, true);
    }

    private void renderCommandButton(NVGU vg, Rect button, String animationKey, String label, boolean enabled, boolean danger) {
        boolean hovered = enabled && isHovered(button);
        float progress = animate("command-button:" + animationKey, hovered, 0.18f);
        Color fill = !enabled
                ? alpha(255, 255, 255, 14)
                : danger
                ? mix(alpha(255, 255, 255, 18), alpha(255, 74, 74, 52), progress)
                : alpha(255, 255, 255, (int) (22 + 16 * progress));
        Color border = !enabled
                ? alpha(255, 255, 255, 24)
                : danger
                ? mix(alpha(255, 255, 255, 28), alpha(255, 96, 96, 110), progress)
                : NanoVGTheme.inputFocus(88);
        Color text = !enabled
                ? NanoVGTheme.muted(180)
                : danger && progress > 0.01f
                ? mix(NanoVGTheme.TEXT, alpha(255, 195, 195, 245), progress)
                : NanoVGTheme.TEXT;
        float labelFont = font(11f);
        vg.rectangle(button.x, button.y, button.width, button.height, fill);
        vg.roundedRectangleBorder(button.x, button.y, button.width, button.height, u(5f), u(1f), border, Border.INSIDE);
        float lineHeight = u(13f);
        int lines = wrappedLineCount(label, NVGFonts.INTER, labelFont, button.width - u(8f));
        float labelY = button.centerY() - labelFont / 2f - (lines - 1) * lineHeight / 2f;
        drawWrappedText(NVGFonts.INTER, label, button.centerX(), labelY, labelFont, button.width - u(8f), lineHeight, text, Alignment.CENTER_TOP);
    }

    private List<String> visibleCommandLogs() {
        String query = normalize(searchQuery);
        List<String> logs = new ArrayList<>();
        for (String log : ModuleRenderable.commandLogs) {
            if (log != null && (query.isBlank() || normalize(log).contains(query)))
                logs.add(log);
        }
        return logs;
    }

    private List<String> commandLogLines(String log, float textWidth) {
        return commandLogLineCache.computeIfAbsent(log, ignored -> wrapTextToWidth(log, NVGFonts.INTER, font(12f), textWidth));
    }

    private float commandLogBlockHeight(String log, float textWidth) {
        return commandLogLines(log, textWidth).size() * u(15f) + u(12f);
    }

    private boolean followNewCommandLogs() {
        List<String> logs = ModuleRenderable.commandLogs;
        int count = logs.size();
        String last = count > 0 ? logs.get(count - 1) : null;
        boolean changed = count != lastSeenCommandLogCount || last != lastSeenCommandLog;
        lastSeenCommandLogCount = count;
        lastSeenCommandLog = last;
        return changed;
    }

    private List<PlayerEntry> visibleMultiplayPlayers() {
        List<PlayerEntry> players = new ArrayList<>();
        if (minecraft == null || minecraft.level == null || minecraft.getConnection() == null)
            return players;

        String query = normalize(searchQuery);
        for (AbstractClientPlayer player : minecraft.level.players()) {
            String name = player.getName().getString();
            if (!query.isBlank() && !normalize(name).contains(query))
                continue;

            PlayerInfo info = minecraft.getConnection().getPlayerInfo(player.getUUID());
            players.add(new PlayerEntry(name, info != null ? info.getLatency() : -1, player instanceof LocalPlayer));
        }

        players.sort(Comparator.comparing(PlayerEntry::name, String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private void updateCommandSuggestions() {
        if (textFocus != TextFocus.COMMAND) {
            commandSuggestionsVisible = false;
            commandSuggestionInput = null;
            commandSuggestionAppliedInput = null;
            return;
        }

        int caret = Math.clamp(textCaret, 0, commandInput.length());
        if (commandInput.equals(commandSuggestionInput) && caret == commandSuggestionCaret)
            return;

        commandSuggestionInput = commandInput;
        commandSuggestionCaret = caret;
        if (commandInput.equals(commandSuggestionAppliedInput))
            return;

        commandSuggestionAppliedInput = null;
        commandSuggestionTabCycles = false;
        refreshCommandSuggestions(caret);
        commandSuggestionsVisible = !commandInput.isEmpty()
                && !commandSuggestionList.isEmpty()
                && minecraft != null
                && minecraft.options.autoSuggestions().get();
    }

    private void refreshCommandSuggestions(int caret) {
        CommandDispatcher<ClientSuggestionProvider> commands = ChatCommandTree.dispatcher();
        ClientSuggestionProvider source = minecraft != null && minecraft.player != null
                ? minecraft.player.connection.getSuggestionsProvider()
                : null;
        ParseResults<ClientSuggestionProvider> parse = commands.parse(new StringReader(commandInput), source);
        Suggestions suggestions = commands.getCompletionSuggestions(parse, caret).getNow(null);

        commandSuggestionOriginal = commandInput;
        commandSuggestionIndex = 0;
        commandSuggestionOffset = 0;
        commandSuggestionList = suggestions == null ? List.of() : sortCommandSuggestions(suggestions.getList(), caret);
        commandUsageLines = commandSuggestionList.isEmpty() ? commandUsage(commands, parse, caret, source) : List.of();
    }

    private List<Suggestion> sortCommandSuggestions(List<Suggestion> suggestions, int caret) {
        String partial = commandInput.substring(0, Math.min(caret, commandInput.length()));
        String lastWord = partial.substring(partial.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
        List<Suggestion> matching = new ArrayList<>();
        List<Suggestion> others = new ArrayList<>();
        for (Suggestion suggestion : suggestions) {
            if (suggestion.getText().toLowerCase(Locale.ROOT).startsWith(lastWord))
                matching.add(suggestion);
            else
                others.add(suggestion);
        }
        matching.addAll(others);
        return matching;
    }

    private static List<String> commandUsage(
            CommandDispatcher<ClientSuggestionProvider> commands,
            ParseResults<ClientSuggestionProvider> parse,
            int caret,
            ClientSuggestionProvider source
    ) {
        try {
            SuggestionContext<ClientSuggestionProvider> context = parse.getContext().findSuggestionContext(caret);
            List<String> lines = new ArrayList<>();
            for (Map.Entry<CommandNode<ClientSuggestionProvider>, String> entry : commands.getSmartUsage(context.parent, source).entrySet()) {
                if (!(entry.getKey() instanceof LiteralCommandNode))
                    lines.add(entry.getValue());
            }
            return lines;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private boolean handleCommandSuggestionKey(int key, boolean shift) {
        updateCommandSuggestions();
        if (key == Keys.KEY_TAB) {
            if (commandSuggestionList.isEmpty())
                return true;
            if (!commandSuggestionsVisible) {
                commandSuggestionsVisible = true;
                return true;
            }

            if (commandSuggestionTabCycles)
                cycleCommandSuggestion(shift ? -1 : 1);
            applyCommandSuggestion();
            commandSuggestionTabCycles = true;
            return true;
        }

        if (!commandSuggestionsVisible || commandSuggestionList.isEmpty())
            return false;

        if (key == Keys.KEY_UP || key == Keys.KEY_DOWN) {
            cycleCommandSuggestion(key == Keys.KEY_UP ? -1 : 1);
            commandSuggestionTabCycles = false;
            return true;
        }

        if (key == Keys.KEY_ESCAPE) {
            commandSuggestionsVisible = false;
            return true;
        }

        return false;
    }

    private void cycleCommandSuggestion(int direction) {
        int size = commandSuggestionList.size();
        if (size == 0)
            return;

        commandSuggestionIndex = Math.floorMod(commandSuggestionIndex + direction, size);
        int rows = Math.max(1, commandSuggestionVisibleRows);
        if (commandSuggestionIndex < commandSuggestionOffset)
            commandSuggestionOffset = commandSuggestionIndex;
        else if (commandSuggestionIndex >= commandSuggestionOffset + rows)
            commandSuggestionOffset = commandSuggestionIndex - rows + 1;
    }

    private void scrollCommandSuggestions(int direction) {
        int rows = Math.max(1, commandSuggestionVisibleRows);
        int maxOffset = Math.max(0, commandSuggestionList.size() - rows);
        commandSuggestionOffset = Math.clamp(commandSuggestionOffset + direction, 0, maxOffset);
    }

    private void applyCommandSuggestion() {
        if (commandSuggestionList.isEmpty())
            return;

        Suggestion suggestion = commandSuggestionList.get(Math.clamp(commandSuggestionIndex, 0, commandSuggestionList.size() - 1));
        String applied = suggestion.apply(commandSuggestionOriginal);
        if (applied.length() > MAX_COMMAND_INPUT_LENGTH)
            applied = applied.substring(0, MAX_COMMAND_INPUT_LENGTH);

        commandInput = applied;
        commandHistoryIndex = -1;
        textCaret = Math.min(applied.length(), suggestion.getRange().getStart() + suggestion.getText().length());
        textAnchor = textCaret;
        commandSuggestionAppliedInput = applied;
    }

    private void renderCommandSuggestionGhost(NVGU vg, Rect input, String shownText, float fontSize) {
        if (!commandSuggestionsVisible || commandSuggestionList.isEmpty() || !commandInput.equals(commandSuggestionOriginal)
                || textCaret != commandInput.length())
            return;

        Suggestion suggestion = commandSuggestionList.get(commandSuggestionIndex);
        int start = suggestion.getRange().getStart();
        if (start > commandInput.length() || suggestion.getRange().getEnd() != commandInput.length())
            return;

        String typed = commandInput.substring(start);
        if (!suggestion.getText().startsWith(typed) || suggestion.getText().length() <= typed.length())
            return;

        float textX = input.x + u(26f);
        float maxWidth = input.width - u(32f);
        float scroll = inputScroll(shownText, true, fontSize, maxWidth);
        float ghostX = textX - scroll + NVGFonts.INTER.getWidth(commandInput, fontSize);
        String ghost = suggestion.getText().substring(typed.length());
        vg.scissor(textX - u(2f), input.y, maxWidth + u(4f), input.height, () ->
                NVGFonts.INTER.drawText(ghost, ghostX, input.y + u(6f), fontSize, NanoVGTheme.muted(150), Alignment.LEFT_TOP, true));
    }

    private void renderCommandSuggestions(NVGU vg, float bottomLimit) {
        commandSuggestionRect = new Rect(0f, 0f, 0f, 0f);
        if (textFocus != TextFocus.COMMAND)
            return;

        boolean showList = commandSuggestionsVisible && !commandSuggestionList.isEmpty();
        boolean showUsage = !showList && !commandUsageLines.isEmpty() && textCaret == commandInput.length();
        if (!showList && !showUsage)
            return;

        float fontSize = font(11f);
        float rowHeight = u(15f);
        float paddingX = u(8f);
        Rect input = commandInputRect;
        float top = input.y + input.height + u(3f);
        int available = Math.max(1, (int) ((bottomLimit - top - u(6f)) / rowHeight));
        List<String> lines = new ArrayList<>();
        int rows;
        int rangeStart;
        if (showList) {
            rows = Math.min(Math.min(commandSuggestionList.size(), MAX_COMMAND_SUGGESTION_ROWS), available);
            commandSuggestionVisibleRows = rows;
            commandSuggestionOffset = Math.clamp(commandSuggestionOffset, 0, Math.max(0, commandSuggestionList.size() - rows));
            for (Suggestion suggestion : commandSuggestionList)
                lines.add(suggestion.getText());
            rangeStart = commandSuggestionList.getFirst().getRange().getStart();
        } else {
            rows = Math.min(commandUsageLines.size(), available);
            lines.addAll(commandUsageLines);
            rangeStart = commandInput.length();
        }

        float textWidth = 0f;
        for (String line : lines)
            textWidth = Math.max(textWidth, NVGFonts.INTER.getWidth(line, fontSize));
        float width = textWidth + paddingX * 2f;
        float height = rows * rowHeight + u(6f);
        float textX = input.x + u(26f);
        float maxWidth = input.width - u(32f);
        float scroll = inputScroll(inputText(commandInput, true, ""), true, fontSize, maxWidth);
        String before = commandInput.substring(0, Math.clamp(rangeStart, 0, commandInput.length()));
        float anchorX = textX - scroll + NVGFonts.INTER.getWidth(before, fontSize) - paddingX;
        float x = Math.clamp(anchorX, input.x, Math.max(input.x, input.x + input.width - width));
        Rect popup = new Rect(x, top, width, height);
        commandSuggestionRect = popup;

        vg.roundedRectangle(popup.x, popup.y, popup.width, popup.height, u(NanoVGTheme.RADIUS_HUD), NanoVGTheme.BASE);
        vg.roundedRectangleBorder(popup.x, popup.y, popup.width, popup.height, u(NanoVGTheme.RADIUS_HUD), u(1f), alpha(255, 255, 255, 30), Border.INSIDE);
        float rowY = popup.y + u(3f);
        if (!showList) {
            for (int i = 0; i < rows; i++)
                NVGFonts.INTER.drawText(lines.get(i), popup.x + paddingX, rowY + i * rowHeight + u(2f), fontSize, NanoVGTheme.MUTED, Alignment.LEFT_TOP, true);
            return;
        }

        for (int i = 0; i < rows; i++) {
            int index = commandSuggestionOffset + i;
            if (index >= commandSuggestionList.size())
                break;

            Rect row = new Rect(popup.x + u(2f), rowY + i * rowHeight, popup.width - u(4f), rowHeight);
            boolean selected = index == commandSuggestionIndex;
            if (selected)
                vg.rectangle(row.x, row.y, row.width, row.height, alpha(255, 255, 255, 34));
            else if (isHovered(row))
                vg.rectangle(row.x, row.y, row.width, row.height, alpha(255, 255, 255, 16));
            addControl(ControlType.COMMAND_SUGGESTION, row, null, null, index);
            NVGFont rowFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
            rowFont.drawText(lines.get(index), popup.x + paddingX, row.y + u(2f), fontSize, selected ? NanoVGTheme.TEXT : NanoVGTheme.MUTED, Alignment.LEFT_TOP, true);
        }

    }

    private void runCommandInput() {
        String command = commandInput.trim();
        if (command.isEmpty())
            return;

        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
            commandHistory.add(command);
            while (commandHistory.size() > MAX_COMMAND_HISTORY)
                commandHistory.remove(0);
        }
        commandHistoryIndex = -1;
        commandInput = "";
        textCaret = 0;
        textAnchor = 0;
        ModuleRenderable.executeCommand(command, false);
    }

    private void browseCommandHistory(int direction) {
        if (commandHistory.isEmpty())
            return;

        int index = commandHistoryIndex < 0 ? commandHistory.size() : commandHistoryIndex;
        index += direction;
        if (index >= commandHistory.size()) {
            commandHistoryIndex = -1;
            commandInput = "";
        } else {
            commandHistoryIndex = Math.max(0, index);
            commandInput = commandHistory.get(commandHistoryIndex);
        }
        textCaret = commandInput.length();
        textAnchor = textCaret;
    }

    private void sendPlayerCommand(String command) {
        if (minecraft != null && minecraft.getConnection() != null)
            minecraft.getConnection().sendCommand(command);
    }

    private void resetCommandScroll() {
        resetListScroll();
        lastSeenCommandLogCount = -1;
        lastSeenCommandLog = null;
    }

    private void renderEmptyState(NVGU vg, Rect viewport, String icon, String title, String detail) {
        float centerX = viewport.centerX();
        float centerY = viewport.centerY() - u(7f);
        NVGFonts.ICON.drawText(icon, centerX, centerY - u(20f), font(16f), NanoVGTheme.muted(200), Alignment.CENTER_MIDDLE, true);
        float textWidth = Math.max(u(40f), viewport.width - u(32f));
        float titleHeight = drawWrappedText(NVGFonts.INTER, title, centerX, centerY + u(4f), font(14f), textWidth, u(17f), NanoVGTheme.TEXT, Alignment.CENTER_TOP);
        drawWrappedText(NVGFonts.INTER, detail, centerX, centerY + u(6f) + titleHeight, font(11f), textWidth, u(14f), NanoVGTheme.MUTED, Alignment.CENTER_TOP);
    }

    private void renderConfigCreateRow(NVGU vg, float x, float y, float width) {
        boolean active = textFocus == TextFocus.CONFIG_NAME;
        float fieldHeight = u(22f);
        Rect input = new Rect(x, y, Math.max(u(40f), width - u(70f)), fieldHeight);
        Rect button = new Rect(x + width - u(64f), y, u(64f), fieldHeight);
        addControl(ControlType.CONFIG_NAME, input, null, null, 0);
        addControl(ControlType.CONFIG_CREATE, button, null, null, 0);

        boolean inputHovered = input.contains(scaledMouseX, scaledMouseY);
        vg.rectangle(input.x, input.y, input.width, input.height, alpha(255, 255, 255, active ? 26 : inputHovered ? 18 : 12));
        vg.rectangle(input.x, input.y + input.height - u(0.75f), input.width, u(0.75f),
                active ? NanoVGTheme.ACCENT : alpha(255, 255, 255, 26));

        String placeholder = selectedConfigTab == ConfigTab.MODULE ? "New module config" : "New bind config";
        String text = inputText(newConfigName, active, placeholder);
        float createFont = font(11f);
        Color nameColor = newConfigName.isBlank() && !active ? NanoVGTheme.dim(190) : NanoVGTheme.text(235);
        NVGFonts.ICON.drawText(MaterialIcon.ADD, input.x + u(5f), input.y + (fieldHeight - font(11f)) / 2f,
                font(11f), active ? NanoVGTheme.ACCENT : NanoVGTheme.dim(200), Alignment.LEFT_TOP, true);
        renderInputText(vg, TextFocus.CONFIG_NAME, null, active, text, createFont, input.x + u(20f),
                input.y + (fieldHeight - createFont) / 2f, input.y + (fieldHeight - createFont) / 2f,
                input.width - u(26f), input, nameColor);

        boolean canCreate = !cleanConfigName(newConfigName).isBlank();
        boolean hovered = button.contains(scaledMouseX, scaledMouseY);
        Color accent = NanoVGTheme.ACCENT;
        vg.rectangle(button.x, button.y, button.width, button.height,
                canCreate ? alpha(accent.getRed(), accent.getGreen(), accent.getBlue(), hovered ? 60 : 40)
                        : alpha(255, 255, 255, 10));
        if (canCreate)
            vg.rectangleBorder(button.x, button.y, button.width, button.height, u(0.7f),
                    alpha(accent.getRed(), accent.getGreen(), accent.getBlue(), 150), Border.INSIDE);
        NVGFonts.INTER.drawText("Create", button.centerX(), button.centerY(), font(10f),
                canCreate ? NanoVGTheme.text(240) : NanoVGTheme.dim(180), Alignment.CENTER_MIDDLE, true);
    }

    private boolean isRenamingConfig(ConfigEntry config) {
        return textFocus == TextFocus.CONFIG_RENAME && isSameConfig(renamingConfig, config);
    }

    private float configNameWidth(ConfigEntry config, float width, boolean renaming) {
        float nameRight = width - (config.type != ConfigTab.ONLINE ? u(36f) : u(8f));
        String updatedLabel = renaming ? "" : configListUpdatedLabel(config);
        if (!updatedLabel.isBlank())
            nameRight -= NVGFonts.INTER.getWidth(updatedLabel, font(10f)) + u(8f);
        return Math.max(u(28f), nameRight - u(12f));
    }

    private float configRowHeight(ConfigEntry config, float width) {
        return u(22f);
    }

    private void renderConfigRow(NVGU vg, ConfigEntry config, int index, int visibleCount, float x, float y, float width) {
        Rect row = new Rect(x, y, width, configRowHeight(config, width) - u(0.8f));
        boolean showDelete = config.type != ConfigTab.ONLINE;
        boolean renaming = isRenamingConfig(config);
        boolean deletable = showDelete && !config.current && !renaming;
        Rect delete = showDelete
                ? new Rect(row.x + row.width - u(18f), row.y + (row.height - u(14f)) / 2f, u(14f), u(14f))
                : null;
        Rect load = showDelete
                ? new Rect(row.x, row.y, Math.max(u(1f), row.width - u(22f)), row.height)
                : row;
        addControl(renaming ? ControlType.CONFIG_RENAME : ControlType.CONFIG_LOAD, load, config, null, 0);
        if (deletable)
            addControl(ControlType.CONFIG_DELETE, delete, config, null, 0);

        boolean hovered = isHovered(row);
        if (config.current)
            renderListRowWash(vg, row, 1f);
        else if (hovered)
            vg.rectangle(row.x, row.y, row.width, row.height, alpha(255, 255, 255, 16));
        if (isSameConfig(settingsPanelConfig, config))
            vg.rectangle(row.x, row.y + u(1f), u(1.5f), Math.max(0f, row.height - u(2f)), NanoVGTheme.ACCENT);
        vg.rectangle(row.x, row.y + row.height - u(0.75f), row.width, u(0.75f), alpha(255, 255, 255, 22));

        float nameSize = font(11f);
        float nameX = row.x + u(8f);
        float nameRight = row.x + row.width - (showDelete ? u(24f) : u(8f));
        String updatedLabel = renaming ? "" : configListUpdatedLabel(config);
        if (!updatedLabel.isBlank()) {
            float updatedFont = font(8f);
            float updatedWidth = NVGFonts.INTER.getWidth(updatedLabel, updatedFont);
            NVGFonts.INTER.drawText(updatedLabel, nameRight, row.y + (row.height - updatedFont) / 2f, updatedFont,
                    NanoVGTheme.dim(190), Alignment.RIGHT_TOP, true);
            nameRight -= updatedWidth + u(8f);
        }
        float nameWidth = Math.max(u(24f), nameRight - nameX - u(4f));
        if (renaming) {
            renderConfigRenameField(vg, config, nameX, row.y, nameWidth, row.height);
        } else {
            NVGFont nameFont = config.current ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
            String name = truncateText(config.name, nameFont, nameSize, nameWidth);
            nameFont.drawText(name, nameX, row.y + (row.height - nameSize) / 2f, nameSize,
                    config.current ? NanoVGTheme.ACCENT
                            : hovered ? NanoVGTheme.text(245)
                            : new Color(177, 177, 190, 235),
                    Alignment.LEFT_TOP, true);
        }

        if (delete != null) {
            boolean deleteHovered = deletable && isHovered(delete);
            Color deleteColor = !deletable
                    ? NanoVGTheme.dim(120)
                    : deleteHovered ? NanoVGTheme.danger(245) : NanoVGTheme.dim(190);
            NVGFonts.ICON.drawText(MaterialIcon.DELETE, delete.centerX(), delete.centerY(), font(10f),
                    deleteColor, Alignment.CENTER_MIDDLE, true);
        }
    }

    private void renderConfigRenameField(NVGU vg, ConfigEntry config, float nameX, float rowY, float nameWidth, float rowHeight) {
        Rect input = new Rect(nameX - u(4f), rowY + u(3f), nameWidth + u(4f), rowHeight - u(6f));
        vg.rectangle(input.x, input.y, input.width, input.height, alpha(255, 255, 255, 29));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(5f), u(1f), NanoVGTheme.inputFocus(110), Border.INSIDE);

        float renameFont = font(14f);
        float textX = input.x + u(6f);
        float textY = rowY + u(7f);
        float textMaxWidth = Math.max(u(12f), input.width - u(10f));
        String text = inputText(renameConfigName, true, "");
        rememberTextField(TextFocus.CONFIG_RENAME, config, textX, renameFont, textMaxWidth);
        vg.scissor(input.x, input.y, input.width, input.height, () -> {
            renderSelectionHighlight(vg, true, NVGFonts.INTER, renameFont, textX, textY, textMaxWidth);
            NVGFonts.INTER.drawText(text, textX, textY, renameFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
            renderInlineCaret(vg, true, NVGFonts.INTER, renameFont, textX, textY, NanoVGTheme.TEXT);
        });
    }

    private float moduleNameWidth(Module module, float width) {
        float nameRight = width - u(8f);
        if (module.key != Keys.KEY_UNKNOWN)
            nameRight -= NVGFonts.INTER.getWidth(keyName(module.key), font(10f)) + u(8f);
        return Math.max(u(36f), nameRight - u(14f) - switchWidth - u(8f));
    }

    private void renderModuleBlock(NVGU vg, Module module, int index, int visibleCount, float x, float y, float width) {
        Rect row = new Rect(x, y, width, moduleBlockHeight(module, width) - u(3f));
        addModuleRow(module, row);

        boolean hovered = isHovered(row);
        if (hovered)
            rememberHoveredModule(module);
        float enabled = animateIdentity(enabledAnimations, enabledAnimationFrames, module, module.tempEnabled, 0.12f);
        boolean selected = settingsPanelModule == module;
        float wash = washProgress("module", module, module.tempEnabled);
        if (wash > 0.01f)
            renderListRowWash(vg, row, wash);
        float mark = animate("module-mark:" + System.identityHashCode(module), selected, 0.18f);
        if (mark > 0.01f)
            renderListRowMark(vg, row, mark);

        Rect switchRect = new Rect(row.x + u(14f), row.y + u(7f), switchWidth, switchHeight);
        float nameX = switchRect.x + switchRect.width + u(8f);

        if (module.key != Keys.KEY_UNKNOWN)
            NVGFonts.INTER.drawText(keyName(module.key), row.x + row.width - u(8f), row.y + u(8f), font(10f), NanoVGTheme.MUTED, Alignment.RIGHT_TOP, true);

        NVGFont nameFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        drawWrappedText(nameFont, module.moduleName, nameX, row.y + u(7f), font(14f), moduleNameWidth(module, width), nameLineHeight, NanoVGTheme.TEXT, Alignment.LEFT_TOP);

        drawSwitch(vg, switchRect.x, switchRect.y, switchRect.width, switchRect.height, enabled, "module-list:" + System.identityHashCode(module), pressedModule == module);
    }

    private void renderExpandedSettings(NVGU vg, Module module, float x, float y, float width) {
        float rowY = y + u(2f);
        rowY = renderBaseSettingRows(vg, module, x + u(6f), rowY, width - u(12f));
        List<SettingValue<?>> settings = collectSettings(module);

        if (!hasRenderableSettings(module)) {
            NVGFonts.INTER.drawText("No settings", x + u(10f), rowY + u(7f), font(12f), NanoVGTheme.MUTED, Alignment.LEFT_TOP, true);
            return;
        }

        for (SettingValue<?> setting : settings) {
            float progress = settingVisibilityProgress(setting);
            if (progress <= 0.001f)
                continue;

            float settingHeight = (settingRowHeightFor(setting, width - u(12f)) + settingRowGap() + listDropdownHeight(module, setting)) * easeOut(progress);
            float finalRowY = rowY;
            Rect settingRow = new Rect(x + u(6f), rowY, width - u(12f), settingRowHeightFor(setting, width - u(12f)));
            if (isHovered(settingRow))
                rememberHoveredSetting(setting);
            Rect settingClip = new Rect(x + u(6f), rowY, width - u(12f), settingHeight);
            vg.scissor(settingClip.x, settingClip.y, settingClip.width, settingClip.height, () ->
                    withInputClip(settingClip, () ->
                            vg.globalAlpha(easeOut(progress), () -> renderSetting(vg, module, setting, x + u(6f), finalRowY, width - u(12f)))));
            rowY += settingHeight;
        }
    }

    private void renderConfigInfo(NVGU vg, ConfigEntry config, float x, float y, float width) {
        if (config == null)
            return;

        float rowX = x + u(6f);
        float rowWidth = Math.max(u(40f), width - u(12f));
        String updated = formatConfigUpdatedAt(config);
        Rect row = new Rect(rowX, y + u(2f), rowWidth, configUpdatedCardHeight(rowWidth, updated));
        vg.rectangle(row.x, row.y + u(1f), row.width, row.height - u(2f), alpha(255, 255, 255, 14));
        NVGFonts.INTER.drawText("Last Updated", row.x + u(12f), row.y + u(6f), font(12f), NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);

        List<String> lines = wrapConfigUpdatedLines(updated, rowWidth);
        float textY = row.y + u(24f);
        float lineHeight = u(15f);
        float valueFont = font(11f);
        for (int i = 0; i < lines.size(); i++) {
            NVGFonts.INTER.drawText(lines.get(i), row.x + u(12f), textY + i * lineHeight, valueFont, NanoVGTheme.MUTED, Alignment.LEFT_TOP, true);
        }
    }

    private float configInfoHeight() {
        String updated = formatConfigUpdatedAt(settingsPanelRenderConfig);
        float rowWidth = Math.max(u(40f), settingsPanelViewport.width - u(12f));
        return u(2f) + configUpdatedCardHeight(rowWidth, updated) + u(7f);
    }

    private float configUpdatedCardHeight(float rowWidth, String updated) {
        int lines = Math.max(1, wrapConfigUpdatedLines(updated, rowWidth).size());
        return Math.max(u(42f), u(24f) + lines * u(15f) + u(6f));
    }

    private List<String> wrapConfigUpdatedLines(String updated, float rowWidth) {
        String text = updated == null || updated.isBlank() ? "Unknown" : updated;
        return wrapTextToWidth(text, NVGFonts.INTER, font(11f), Math.max(u(20f), rowWidth - u(24f)));
    }

    private String formatConfigUpdatedAt(ConfigEntry config) {
        Long updated = configUpdatedMillis(config);
        if (updated == null || updated <= 0L)
            return "Unknown";

        return CONFIG_UPDATED_FORMAT.format(Instant.ofEpochMilli(updated));
    }

    private String configListUpdatedLabel(ConfigEntry config) {
        Long updated = configUpdatedMillis(config);
        if (updated == null || updated <= 0L)
            return "";

        return CONFIG_LIST_UPDATED_FORMAT.format(Instant.ofEpochMilli(updated));
    }

    private Long configUpdatedMillis(ConfigEntry config) {
        if (config == null)
            return null;
        if (config.type() == ConfigTab.ONLINE)
            return FileUtil.INSTANCE.cachedOnlineConfigUpdatedAt(config.name());
        if (Client.configManager == null)
            return null;

        return Client.configManager.configUpdatedAt(config.type() == ConfigTab.BIND, config.name());
    }

    private static ConfigEntry restoreRememberedConfig(ConfigEntry remembered) {
        if (remembered == null || remembered.type() == null || remembered.name() == null || remembered.name().isBlank())
            return null;
        if (remembered.type() == ConfigTab.ONLINE)
            return remembered;
        if (Client.configManager == null)
            return remembered;

        File base = new File(
                Client.configManager.BASE_DIR,
                remembered.type() == ConfigTab.BIND ? "bind_configs" : "module_configs"
        );
        File file = new File(base, remembered.name() + ".json");
        return file.isFile() ? remembered : null;
    }

    private float renderBaseSettingRows(NVGU vg, Module module, float x, float y, float width) {
        float arrayHeight = wrappedSettingRowHeight("Show on array", booleanLabelMaxWidth(width));
        Rect array = new Rect(x, y, width, arrayHeight);
        addControl(ControlType.SHOW_ON_ARRAY, array, module, null, 0);
        float shownOnArray = animateIdentity(switchAnimations, switchAnimationFrames, module, module.showOnArray, 0.12f);
        renderBooleanRow(vg, array, "Show on array", "array", module, module.showOnArray, shownOnArray, "array:" + System.identityHashCode(module), isPressed(ControlType.SHOW_ON_ARRAY, module));

        float rowY = renderModuleNote(vg, module, x, y + arrayHeight + settingRowGap(), width);

        Rect key = new Rect(x, rowY, width, settingRowHeight);
        addControl(ControlType.MODULE_KEY, key, module, null, 0);
        renderKeybindRow(vg, key, "Keybind", bindingModule == module ? "Press key..." : keyName(module.key));

        return rowY + settingRowStep;
    }

    private float renderModuleNote(NVGU vg, Module module, float x, float y, float width) {
        String note = moduleNote(module);
        if (note.isEmpty())
            return y;

        List<String> lines = wrapNoteLines(note, width);
        float height = noteBlockHeight(lines);
        Rect row = new Rect(x, y, width, height);
        boolean hovered = isHovered(row);
        vg.rectangle(row.x, row.y + u(1f), row.width, row.height - u(2f), hovered ? alpha(255, 255, 255, 22) : alpha(255, 255, 255, 14));
        float textY = row.y + u(6f);
        float noteFont = font(12f);
        float noteLine = u(15f);
        for (int i = 0; i < lines.size(); i++) {
            NVGFonts.INTER.drawText(lines.get(i), row.x + u(12f), textY + i * noteLine, noteFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
        }
        return y + height + u(7f);
    }

    private float renderSetting(NVGU vg, Module module, SettingValue<?> setting, float x, float y, float width) {
        Rect row = new Rect(x, y, width, settingRowHeightFor(setting, width));

        switch (setting) {
            case BooleanValue value -> {
                float enabled = animateIdentity(switchAnimations, switchAnimationFrames, value, value.get(), 0.12f);
                addControl(ControlType.BOOLEAN, row, value, null, 0);
                renderBooleanRow(vg, row, value.name, "bool", value, value.get(), enabled, "bool:" + System.identityHashCode(value), isPressed(ControlType.BOOLEAN, value));
            }
            case ListValue value -> renderListSetting(vg, row, module, value);
            case FloatValue value -> renderFloatSetting(vg, row, value);
            case IntValue value -> renderIntSetting(vg, row, value);
            case ColorValue value -> renderColorSetting(vg, row, value);
            case TextValue value -> renderTextSetting(vg, row, module, value);
            case KeyBindValue value -> renderKeyBindSetting(vg, row, value);
            default -> renderSettingRow(vg, row, setting, "Unsupported", 0f);
        }

        return settingRowStep + listDropdownHeight(module, setting);
    }

    private void renderBooleanRow(NVGU vg, Rect row, String label, String washScope, Object washKey, boolean washActive, float enabled, String grabId, boolean grabbed) {
        float wash = washProgress(washScope, washKey, washActive);
        if (wash > 0.01f)
            renderListRowWash(vg, row, wash);
        Rect switchRect = new Rect(row.x + u(8f), row.y + u(6f), switchWidth, switchHeight);
        float labelX = switchRect.x + switchRect.width + u(8f);
        drawWrappedSettingLabel(label, labelX, row.y + u(7f), booleanLabelMaxWidth(row.width));
        drawSwitch(vg, switchRect.x, switchRect.y, switchRect.width, switchRect.height, enabled, grabId, grabbed);
    }

    private void renderSettingRow(NVGU vg, Rect row, SettingValue<?> setting, String value, float enabled) {
        if (enabled > 0.01f)
            renderListRowWash(vg, row, enabled);
        drawWrappedSettingLabel(setting.name, row.x + u(12f), row.y + u(7f), settingLabelMaxWidth(setting, row.width));
        if (value != null)
            NVGFonts.INTER.drawText(value, row.x + row.width - u(25f), row.y + u(8f), font(11f), NanoVGTheme.MUTED, Alignment.RIGHT_TOP, true);
    }

    private void renderKeybindRow(NVGU vg, Rect row, String label, String value) {
        drawWrappedSettingLabel(label, row.x + u(12f), row.y + u(7f), keybindLabelMaxWidth(row.width));
        if (value != null)
            NVGFonts.INTER.drawText(value, row.x + row.width - u(25f), row.y + u(8f), font(11f), NanoVGTheme.MUTED, Alignment.RIGHT_TOP, true);
    }

    private void renderListSetting(NVGU vg, Rect row, Module module, ListValue value) {
        boolean open = openedListModule == module && openedListValue == value;
        float dropdownProgress = listDropdownProgress(module, value);
        float easedDropdown = easeOut(dropdownProgress);
        float valueWidth = listValueWidth(row.width);
        float optionX = row.x + row.width - valueWidth - u(8f);
        float optionY = row.y + u(4f);

        if (dropdownProgress > 0.01f) {
            float[] heights = listOptionHeights(value, valueWidth);
            float openHeight = Math.max(row.height, u(4f) + listOptionsTotalHeight(heights) * easedDropdown);
            renderListRowWash(vg, new Rect(row.x, row.y, row.width, openHeight), easedDropdown);
        }

        drawWrappedSettingLabel(value.name, row.x + u(12f), row.y + u(7f), listLabelMaxWidth(row.width));
        if (dropdownProgress <= 0.01f && value.get() != null)
            drawWrappedText(NVGFonts.INTER, value.get(), row.x + row.width - u(25f), row.y + u(8f), font(11f), listCurrentValueWidth(row.width), u(14f), NanoVGTheme.MUTED, Alignment.RIGHT_TOP);
        if (dropdownProgress <= 0.01f) {
            addControl(ControlType.LIST_VALUE, row, value, module, 0);
            return;
        }

        renderListOptions(vg, value, optionX, optionY, valueWidth, easedDropdown, open);
        addControl(ControlType.LIST_VALUE, new Rect(row.x, row.y, Math.max(u(40f), optionX - row.x), row.height), value, module, 0);
    }

    private void renderListOptions(NVGU vg, ListValue value, float x, float y, float width, float progress, boolean interactive) {
        float[] heights = listOptionHeights(value, width);
        float listHeight = listOptionsTotalHeight(heights);
        float visibleHeight = Math.max(u(20f), listHeight * progress);
        Rect listClip = new Rect(x, y, width, visibleHeight);

        withInputClip(listClip, () -> {
            if (!(interactive && progress > 0.62f))
                return;

            float optionY = y;
            for (int i = 0; i < value.values.length; i++) {
                float optionProgress = Math.clamp((progress - i * listOptionStagger(value)) / 0.78f, 0f, 1f);
                if (optionProgress > 0.01f)
                    addControl(ControlType.LIST_OPTION, new Rect(x, optionY, width, heights[i]), value, null, i);
                optionY += heights[i];
            }
        });

        emitControlForeground(vg, () -> vg.scissor(listClip.x, listClip.y, listClip.width, listClip.height, () -> {
            float optionY = y;
            for (int i = 0; i < value.values.length; i++) {
                String option = value.values[i];
                float optionHeight = heights[i];
                float optionProgress = Math.clamp((progress - i * listOptionStagger(value)) / 0.78f, 0f, 1f);
                if (optionProgress > 0.01f) {
                    Rect optionRect = new Rect(x, optionY, width, optionHeight);
                    boolean selected = i == value.getCurrentIndex();
                    float optionGrab = knobGrab("list-option:" + System.identityHashCode(value) + ":" + i, isListOptionPressed(value, i));
                    Rect optionChip = squashedRect(optionRect, optionGrab, 0.04f, 0.16f);
                    float textOffset = u(3f) * (1f - optionProgress);
                    float finalOptionProgress = optionProgress;
                    vg.globalAlpha(finalOptionProgress, () -> {
                        if (selected)
                            renderListRowMark(vg, optionChip);
                        drawWrappedListOption(option, optionChip.x + u(12f), optionChip.y + u(4f) + textOffset, optionChip.width - u(18f), selected);
                    });
                }
                optionY += optionHeight;
            }
        }));
    }

    private static float listOptionStagger(ListValue value) {
        return Math.min(0.035f, 0.22f / Math.max(1, value.values.length));
    }

    private void renderFloatSetting(NVGU vg, Rect row, FloatValue value) {
        boolean active = textFocus == TextFocus.NUMBER && focusedNumberValue == value;
        addControl(ControlType.FLOAT_INPUT, row, value, null, 0);
        renderSettingRow(vg, row, value, active ? null : String.format(Locale.ROOT, "%.2f%s", value.get(), value.suffix == null ? "" : value.suffix), 0f);
        if (active) {
            renderNumberInput(vg, row, value, value.suffix);
            return;
        }

        Rect track = new Rect(row.x + row.width - u(168f), row.y + u(11f), u(102f), u(7f));
        addControl(ControlType.FLOAT_SLIDER, track.expand(u(8f), u(10f)), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum), value, 0);
    }

    private void renderIntSetting(NVGU vg, Rect row, IntValue value) {
        boolean active = textFocus == TextFocus.NUMBER && focusedNumberValue == value;
        addControl(ControlType.INT_INPUT, row, value, null, 0);
        renderSettingRow(vg, row, value, active ? null : value.get() + (value.suffix == null ? "" : value.suffix), 0f);
        if (active) {
            renderNumberInput(vg, row, value, value.suffix);
            return;
        }

        Rect track = new Rect(row.x + row.width - u(168f), row.y + u(11f), u(102f), u(7f));
        addControl(ControlType.INT_SLIDER, track.expand(u(8f), u(10f)), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum), value, 0);
    }

    private void renderNumberInput(NVGU vg, Rect row, SettingValue<?> value, String suffix) {
        float inputWidth = Math.clamp(row.width * 0.44f, u(92f), u(160f));
        Rect input = new Rect(row.x + row.width - inputWidth - u(8f), row.y + u(4f), inputWidth, u(19f));
        boolean valid = parseNumberInput() != null;
        Color borderColor = valid ? NanoVGTheme.inputFocus(120) : alpha(255, 88, 88, 150);
        vg.rectangle(input.x, input.y, input.width, input.height, alpha(255, 255, 255, 28));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(4f), u(1f), borderColor, Border.INSIDE);

        String shownSuffix = suffix == null ? "" : suffix;
        float suffixWidth = shownSuffix.isEmpty() ? 0f : NVGFonts.INTER.getWidth(shownSuffix, font(9f)) + u(7f);
        String text = inputText(numberInput, true, "");
        float numberFont = font(10f);
        Color numberColor = valid ? alpha(255, 255, 255, 240) : alpha(255, 175, 175, 240);
        renderInputText(vg, TextFocus.NUMBER, value, true, text, numberFont, input.x + u(6f), input.y + u(5f), input.y + u(5f), input.width - suffixWidth - u(12f), input, numberColor);
        if (!shownSuffix.isEmpty())
            NVGFonts.INTER.drawText(shownSuffix, input.x + input.width - u(6f), input.y + u(5f), font(9f),
                    alpha(150, 160, 170, 220), Alignment.RIGHT_TOP, true);
    }

    private void renderColorSetting(NVGU vg, Rect row, ColorValue value) {
        renderSettingRow(vg, row, value, value.toHex().substring(0, 7), 0f);
        Color color = value.get();
        float startX = row.x + row.width - u(154f);
        drawColorTrack(vg, value, startX, row.y + u(11f), color.getRed(), 0, alpha(255, 80, 80, 230));
        drawColorTrack(vg, value, startX + u(38f), row.y + u(11f), color.getGreen(), 1, alpha(80, 255, 120, 230));
        drawColorTrack(vg, value, startX + u(76f), row.y + u(11f), color.getBlue(), 2, alpha(80, 175, 255, 230));
        vg.rectangle(row.x + row.width - u(19f), row.y + u(7f), u(12f), u(12f), color);
        vg.roundedRectangleBorder(row.x + row.width - u(19f), row.y + u(7f), u(12f), u(12f), u(3f), u(1f), alpha(255, 255, 255, 80), Border.INSIDE);
    }

    private void drawColorTrack(NVGU vg, ColorValue value, float x, float y, int component, int index, Color accent) {
        Rect track = new Rect(x, y, u(28f), u(7f));
        addControl(ControlType.COLOR_SLIDER, track.expand(u(6f), u(10f)), value, null, index);
        emitControlGlass(vg, track.x, track.y, track.width, track.height, 0f);
        float grab = knobGrab("slider:" + System.identityHashCode(value) + ":" + index, isSliderPressed(value, index));
        Rect knob = sliderKnob(track, component / 255f, grab);
        emitControlGlass(vg, knob.x, knob.y, knob.width, knob.height, 0f);
        Color fill = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220);
        emitControlForeground(vg, () -> {
            vg.rectangle(track.x, track.y, track.width, track.height, NanoVGTheme.CONTROL_TRACK);
            float filled = track.width * component / 255f;
            if (filled > 0.5f)
                vg.rectangle(track.x, track.y, filled, track.height, fill);
            drawKnobFill(vg, knob);
        });
    }

    private void renderTextSetting(NVGU vg, Rect row, Module module, TextValue value) {
        boolean active = textFocus == TextFocus.SETTING && focusedTextValue == value;
        renderSettingRow(vg, row, value, null, 0f);
        Rect input = new Rect(row.x + row.width - u(132f), row.y + u(4f), u(124f), u(19f));
        addControl(ControlType.TEXT_VALUE, input, value, module, 0);
        vg.rectangle(input.x, input.y, input.width, input.height, alpha(255, 255, 255, active ? 28 : 18));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(4f), u(1f), active ? NanoVGTheme.inputFocus(110) : alpha(255, 255, 255, 30), Border.INSIDE);
        String text = inputText(value.get(), active, "");
        float settingFont = font(10f);
        Color textColor = value.get().isEmpty() && !active ? alpha(120, 130, 140, 205) : alpha(255, 255, 255, 235);
        renderInputText(vg, TextFocus.SETTING, value, active, text, settingFont, input.x + u(6f), input.y + u(5f), input.y + u(5f), input.width - u(10f), input, textColor);
    }

    private void renderKeyBindSetting(NVGU vg, Rect row, KeyBindValue value) {
        boolean active = bindingValue == value;
        renderKeybindRow(vg, row, value.name, active ? "Press key..." : keyName(value.get()));
        addControl(ControlType.KEY_VALUE, row, value, null, 0);
    }

    private void withInputClip(Rect clip, Runnable runnable) {
        Rect clipped = clipToCurrentInputClip(clip);
        inputClips.add(clipped != null ? clipped : new Rect(0f, 0f, 0f, 0f));
        try {
            runnable.run();
        } finally {
            inputClips.remove(inputClips.size() - 1);
        }
    }

    private void addControl(ControlType type, Rect rect, Object target, Object owner, int index) {
        Rect clipped = clipToCurrentInputClip(rect);
        if (clipped != null)
            controls.add(new ControlBounds(type, clipped, target, owner, index));
    }

    private void addModuleRow(Module module, Rect rect) {
        Rect clipped = clipToCurrentInputClip(rect);
        if (clipped != null)
            moduleRows.add(new ModuleRowBounds(module, clipped));
    }

    private boolean isHovered(Rect rect) {
        return rect.contains(scaledMouseX, scaledMouseY) && isInsideCurrentInputClip(scaledMouseX, scaledMouseY);
    }

    private boolean isInsideCurrentInputClip(float x, float y) {
        Rect clip = currentInputClip();
        return clip == null || clip.contains(x, y);
    }

    private Rect clipToCurrentInputClip(Rect rect) {
        Rect clip = currentInputClip();
        return clip == null ? rect : rect.intersect(clip);
    }

    private Rect currentInputClip() {
        return inputClips.isEmpty() ? null : inputClips.get(inputClips.size() - 1);
    }

    private boolean handleControlClick(ControlBounds control, float mouseX, int button, boolean doubleClick) {
        if ((control.type == ControlType.INLINE_FLOAT_SLIDER || control.type == ControlType.INLINE_INT_SLIDER)
                && (button == Keys.MOUSE_BUTTON_MIDDLE || (button == Keys.MOUSE_BUTTON_LEFT && doubleClick))) {
            activeSlider = null;
            focusNumberInput((SettingValue<?>) control.target);
            return true;
        }
        if (control.type == ControlType.FLOAT_INPUT || control.type == ControlType.INT_INPUT) {
            if (button == Keys.MOUSE_BUTTON_MIDDLE) {
                focusNumberInput((SettingValue<?>) control.target);
                return true;
            }

            if (button == Keys.MOUSE_BUTTON_LEFT) {
                if (textFocus == TextFocus.NUMBER
                        && focusedNumberValue == control.target) {
                    beginTextSelectionOrSelectAll(mouseX, doubleClick);
                    return true;
                }

                focusNumberInput((SettingValue<?>) control.target);
                return true;
            }

            return false;
        }

        if (control.type == ControlType.COLOR_VALUE) {
            if (button == Keys.MOUSE_BUTTON_LEFT) {
                ColorValue value = (ColorValue) control.target;
                if (inlineColorEditors.contains(value))
                    inlineColorEditors.remove(value);
                else
                    inlineColorEditors.add(value);
                pressedControl = control;
                return true;
            }

            return false;
        }

        if (control.type == ControlType.CONFIG_LOAD || control.type == ControlType.CONFIG_RENAME) {
            ConfigEntry entry = (ConfigEntry) control.target;
            if (button == Keys.MOUSE_BUTTON_MIDDLE)
                return beginConfigRename(entry);

            if (button == Keys.MOUSE_BUTTON_LEFT
                    && textFocus == TextFocus.CONFIG_RENAME
                    && isSameConfig(renamingConfig, entry)) {
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
                return true;
            }

            if (button == Keys.MOUSE_BUTTON_RIGHT
                    && textFocus == TextFocus.CONFIG_RENAME
                    && isSameConfig(renamingConfig, entry)) {
                cancelConfigRename();
                toggleConfigPanel(entry);
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_MIDDLE)
            return false;

        clearTextFocus();
        if (control.type != ControlType.MODULE_KEY) bindingModule = null;
        if (control.type != ControlType.KEY_VALUE) bindingValue = null;

        if (button == Keys.MOUSE_BUTTON_RIGHT) {
            if (control.type == ControlType.CONFIG_LOAD || control.type == ControlType.CONFIG_RENAME) {
                toggleConfigPanel((ConfigEntry) control.target);
                return true;
            }

            if (control.type == ControlType.LIST_VALUE) {
                toggleListDropdown((Module) control.owner, (ListValue) control.target);
                pressedControl = control;
                return true;
            }

            if (control.type == ControlType.MODULE_KEY) {
                ((Module) control.target).key = Keys.KEY_UNKNOWN;
                bindingModule = null;
                return true;
            }

            if (control.type == ControlType.KEY_VALUE) {
                ((KeyBindValue) control.target).set(Keys.KEY_UNKNOWN);
                bindingValue = null;
                return true;
            }

            if (openedListValue != null) {
                closeListDropdown();
                return true;
            }

            return false;
        }

        if (control.type != ControlType.LIST_VALUE && control.type != ControlType.LIST_OPTION)
            closeListDropdown();

        switch (control.type) {
            case CONFIG_NAME -> {
                textFocus = TextFocus.CONFIG_NAME;
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
            }
            case CONFIG_CREATE -> createConfigFromInput();
            case COMMAND_INPUT -> {
                textFocus = TextFocus.COMMAND;
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
            }
            case COMMAND_RUN -> runCommandInput();
            case COMMAND_SUGGESTION -> {
                textFocus = TextFocus.COMMAND;
                commandSuggestionIndex = control.index;
                commandSuggestionTabCycles = false;
                applyCommandSuggestion();
            }
            case MULTIPLAY_DISCONNECT -> {
                if (minecraft != null && minecraft.level != null)
                    minecraft.level.disconnect(Component.literal("Disconnected"));
            }
            case PLAYER_KILL -> sendPlayerCommand("kill " + control.target);
            case PLAYER_TELEPORT -> sendPlayerCommand("tp " + control.target);
            case PLAYER_CRASH -> sendPlayerCommand("execute at " + control.target + " run particle minecraft:explosion ~ ~ ~ 0.1 0.1 0.1 0.01 100000000 force");
            case CONFIG_LOAD -> loadConfigEntry((ConfigEntry) control.target);
            case CONFIG_RENAME -> {
            }
            case CONFIG_DELETE -> deleteConfigEntry((ConfigEntry) control.target);
            case SHOW_ON_ARRAY -> {
                Module module = (Module) control.target;
                module.showOnArray = !module.showOnArray;
                Interface.reloadSortedModules();
                pressedControl = control;
            }
            case MODULE_ENABLED -> {
                ((Module) control.target).toggle();
                pressedControl = control;
            }
            case MODULE_KEY -> bindingModule = (Module) control.target;
            case BOOLEAN -> {
                BooleanValue value = (BooleanValue) control.target;
                value.set(!value.get());
                pressedControl = control;
            }
            case LIST_VALUE -> {
                toggleListDropdown((Module) control.owner, (ListValue) control.target);
                pressedControl = control;
            }
            case LIST_OPTION -> {
                ((ListValue) control.target).setByIndex(control.index);
                pressedControl = control;
                closeListDropdown();
            }
            case FLOAT_SLIDER, INT_SLIDER, COLOR_SLIDER, INLINE_FLOAT_SLIDER, INLINE_INT_SLIDER, INLINE_COLOR_SLIDER -> {
                activeSlider = new SliderDrag(control);
                pressedControl = control;
                updateSlider(activeSlider, mouseX);
            }
            case FLOAT_INPUT, INT_INPUT -> {
            }
            case TEXT_VALUE -> {
                textFocus = TextFocus.SETTING;
                focusedTextValue = (TextValue) control.target;
                focusedTextModule = (Module) control.owner;
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
            }
            case KEY_VALUE -> bindingValue = (KeyBindValue) control.target;
        }

        return true;
    }

    private void updateSlider(SliderDrag drag, float mouseX) {
        ControlBounds control = drag.control;
        Rect track = sliderTrack(control);
        float pct = Math.clamp((mouseX - track.x) / Math.max(1f, track.width), 0f, 1f);

        switch (control.type) {
            case FLOAT_SLIDER, INLINE_FLOAT_SLIDER -> {
                FloatValue value = (FloatValue) control.target;
                float next = value.minimum + (value.maximum - value.minimum) * pct;
                value.set(Math.round(next * 100f) / 100f);
            }
            case INT_SLIDER, INLINE_INT_SLIDER -> {
                IntValue value = (IntValue) control.target;
                int next = Math.round(value.minimum + (value.maximum - value.minimum) * pct);
                value.set(next);
            }
            case COLOR_SLIDER, INLINE_COLOR_SLIDER -> {
                ColorValue value = (ColorValue) control.target;
                Color color = value.get();
                int component = Math.clamp(Math.round(pct * 255f), 0, 255);
                int red = control.index == 0 ? component : color.getRed();
                int green = control.index == 1 ? component : color.getGreen();
                int blue = control.index == 2 ? component : color.getBlue();
                value.set(new Color(red, green, blue, color.getAlpha()));
            }
        }
    }

    private void renderListRowWash(NVGU vg, Rect row, float alphaScale) {
        vg.rectangle(row.x + u(6f), row.y + u(1f), row.width - u(12f), row.height - u(2f), alpha(255, 255, 255, (int) (18 * alphaScale)));
    }

    private void renderListRowMark(NVGU vg, Rect row) {
        renderListRowMark(vg, row, 1f);
    }

    private void renderListRowMark(NVGU vg, Rect row, float alphaScale) {
        int markAlpha = Math.clamp(Math.round(220f * Math.clamp(alphaScale, 0f, 1f)), 0, 255);
        if (markAlpha <= 0)
            return;
        vg.rectangle(row.x + u(6f), row.y + u(7f), u(2f), Math.max(u(8f), row.height - u(14f)), alpha(255, 255, 255, markAlpha));
    }

    private void withControlChrome(NVGU vg, Rect clip, Runnable content) {
        deferControlChrome = true;
        try {
            vg.scissor(clip.x, clip.y, clip.width, clip.height, content);
            flushPendingControlGlass(vg);
            if (!pendingControlForegrounds.isEmpty())
                vg.scissor(clip.x, clip.y, clip.width, clip.height, () -> drawPendingControlForegrounds(vg));
        } finally {
            deferControlChrome = false;
            pendingControlGlass.clear();
            pendingControlForegrounds.clear();
        }
    }

    private void flushPendingControlGlass(NVGU vg) {
        if (pendingControlGlass.isEmpty())
            return;

        flushPendingControlGlass(vg, false);
        flushPendingControlGlass(vg, true);
    }

    private void flushPendingControlGlass(NVGU vg, boolean dark) {
        float current = Math.max(vg.currentAlpha(), 0.001f);
        boolean any = false;
        for (PendingControlGlass glass : pendingControlGlass) {
            if (glass.dark == dark && glass.alphaAtQueue / current > 0.001f) {
                any = true;
                break;
            }
        }
        if (!any)
            return;

        vg.beginEffectBatch();
        for (PendingControlGlass glass : pendingControlGlass) {
            if (glass.dark != dark)
                continue;
            float relative = Math.clamp(glass.alphaAtQueue / current, 0f, 1f);
            if (relative <= 0.001f)
                continue;
            drawControlGlass(vg, glass.x, glass.y, glass.width, glass.height, glass.radius, relative, glass.dark, glass.brightness);
        }
        vg.flushEffectBatch();
    }

    private void drawPendingControlForegrounds(NVGU vg) {
        for (int i = 0, n = pendingControlForegrounds.size(); i < n; i++)
            pendingControlForegrounds.get(i).run();
    }

    private void emitControlGlass(NVGU vg, float x, float y, float width, float height, float radius) {
        emitControlGlass(vg, x, y, width, height, radius, false, 0f);
    }

    private void emitControlGlass(NVGU vg, float x, float y, float width, float height, float radius, boolean dark, float brightness) {
        // Controls are flat; avoid queueing liquid-glass passes for the GUI.
    }

    private void emitControlForeground(NVGU vg, Runnable draw) {
        if (!deferControlChrome) {
            draw.run();
            return;
        }

        Rect clip = currentInputClip();
        float alphaAtQueue = vg.currentAlpha();
        pendingControlForegrounds.add(() -> {
            float relative = Math.clamp(alphaAtQueue / Math.max(vg.currentAlpha(), 0.001f), 0f, 1f);
            if (relative <= 0.001f)
                return;
            vg.globalAlpha(relative, () -> {
                if (clip == null)
                    draw.run();
                else
                    vg.scissor(clip.x, clip.y, clip.width, clip.height, draw);
            });
        });
    }

    private static void drawControlGlass(NVGU vg, float x, float y, float width, float height, float radius, float alpha, boolean dark, float brightness) {
        if (dark)
            NanoVGTheme.renderControlDarkEffects(vg, x, y, width, height, radius, alpha);
        else
            NanoVGTheme.renderControlEffects(vg, x, y, width, height, radius, alpha, brightness);
    }

    private void drawSwitch(NVGU vg, float x, float y, float width, float height, float progress, String grabId, boolean grabbed) {
        float knobProgress = Math.clamp(progress, 0f, 1f);
        float grab = knobGrab(grabId, grabbed);
        Rect knob = switchKnob(x, y, width, height, knobProgress, grab);
        emitControlGlass(vg, x, y, width, height, 0f, false, 0.04f * knobProgress);
        emitControlGlass(vg, knob.x, knob.y, knob.width, knob.height, 0f);
        emitControlForeground(vg, () -> {
            vg.rectangle(x, y, width, height, mix(NanoVGTheme.CONTROL_TRACK, NanoVGTheme.switchOn(240), knobProgress));
            drawKnobFill(vg, knob);
        });
    }

    private void drawSlider(NVGU vg, Rect track, float progress, Object target, int index) {
        float fill = Math.clamp(progress, 0f, 1f);
        float radius = 0f;
        float grab = knobGrab("slider:" + System.identityHashCode(target) + ":" + index, isSliderPressed(target, index));
        Rect knob = sliderKnob(track, fill, grab);
        emitControlGlass(vg, track.x, track.y, track.width, track.height, radius);
        emitControlGlass(vg, knob.x, knob.y, knob.width, knob.height, 0f);
        emitControlForeground(vg, () -> {
            vg.rectangle(track.x, track.y, track.width, track.height, NanoVGTheme.CONTROL_TRACK);
            if (fill > 0.001f)
                vg.rectangle(track.x, track.y, track.width * fill, track.height, NanoVGTheme.sliderFill(235));
            drawKnobFill(vg, knob);
        });
    }

    private static void drawKnobFill(NVGU vg, Rect knob) {
        vg.rectangle(knob.x, knob.y, knob.width, knob.height, NanoVGTheme.KNOB_FILL);
    }

    private static Rect switchKnob(float x, float y, float width, float height, float progress, float grab) {
        float size = height * 0.74f;
        float knobWidth = size * (1f + 0.38f * grab);
        float knobHeight = size * (1f - 0.28f * grab);
        float centerX = x + height / 2f + (width - height) * progress;
        float centerY = y + height / 2f;
        return new Rect(centerX - knobWidth / 2f, centerY - knobHeight / 2f, knobWidth, knobHeight);
    }

    private Rect sliderKnob(Rect track, float progress, float grab) {
        float knobWidth = u(9f) * (1f + 0.46f * grab);
        float knobHeight = u(13f) * (1f - 0.30f * grab);
        float centerX = track.x + track.width * progress;
        float centerY = track.centerY();
        return new Rect(centerX - knobWidth / 2f, centerY - knobHeight / 2f, knobWidth, knobHeight);
    }

    private boolean isPressed(ControlType type, Object target) {
        return pressedControl != null && pressedControl.type == type && pressedControl.target == target;
    }

    private boolean isListOptionPressed(ListValue value, int index) {
        return pressedControl != null
                && pressedControl.type == ControlType.LIST_OPTION
                && pressedControl.target == value
                && pressedControl.index == index;
    }

    private static Rect squashedRect(Rect rect, float grab, float widthScale, float heightScale) {
        float width = rect.width * (1f + widthScale * grab);
        float height = rect.height * (1f - heightScale * grab);
        return new Rect(rect.centerX() - width / 2f, rect.centerY() - height / 2f, width, height);
    }

    private boolean isSliderPressed(Object target, int index) {
        return activeSlider != null && activeSlider.control.target == target && activeSlider.control.index == index;
    }

    private float knobGrab(String id, boolean grabbed) {
        return animate("knob-grab:" + id, grabbed, grabbed ? 0.38f : 0.11f);
    }

    private void renderScrollbar(NVGU vg, Rect viewport, float contentHeight, float scroll) {
        if (contentHeight <= viewport.height + 1f) {
            scrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            scrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        float thumbHeight = Math.max(u(22f), viewport.height * viewport.height / contentHeight);
        float travel = viewport.height - thumbHeight;
        float thumbY = viewport.y + travel * (scroll / Math.max(1f, contentHeight - viewport.height));
        float trackX = viewport.x + viewport.width + u(1f);
        scrollbarTrackRect = new Rect(trackX - u(5f), viewport.y, u(12f), viewport.height);
        scrollbarThumbRect = new Rect(trackX - u(1f), thumbY, u(5f), thumbHeight);

        boolean hovered = scrollbarTrackRect.contains(scaledMouseX, scaledMouseY) || activeScrollbar != null;
        float progress = animate("scrollbar", hovered, 0.18f);
        vg.rectangle(trackX + u(1f), viewport.y + u(4f), u(2f), viewport.height - u(8f), alpha(255, 255, 255, (int) (18 + 18 * progress)));
        vg.rectangle(scrollbarThumbRect.x, scrollbarThumbRect.y, scrollbarThumbRect.width, scrollbarThumbRect.height, NanoVGTheme.scrollbarThumb((int) (112 + 86 * progress)));
    }

    private void renderSettingsPanelScrollbar(NVGU vg, float contentHeight) {
        if (contentHeight <= settingsPanelViewport.height + 1f) {
            settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        float thumbHeight = Math.max(
                u(22f),
                settingsPanelViewport.height * settingsPanelViewport.height / contentHeight
        );
        float travel = settingsPanelViewport.height - thumbHeight;
        float thumbY = settingsPanelViewport.y + travel
                * (settingsPanelScroll / Math.max(1f, contentHeight - settingsPanelViewport.height));
        float trackX = settingsPanelViewport.x + settingsPanelViewport.width + u(2f);
        settingsPanelScrollbarTrackRect = new Rect(
                trackX - u(5f),
                settingsPanelViewport.y,
                u(12f),
                settingsPanelViewport.height
        );
        settingsPanelScrollbarThumbRect = new Rect(trackX - u(1f), thumbY, u(5f), thumbHeight);

        boolean hovered = settingsPanelScrollbarTrackRect.contains(scaledMouseX, scaledMouseY)
                || activeSettingsPanelScrollbar != null;
        float progress = animate("settings-panel-scrollbar", hovered, 0.18f);
        vg.rectangle(
                trackX + u(1f),
                settingsPanelViewport.y + u(4f),
                u(2f),
                settingsPanelViewport.height - u(8f),
                alpha(255, 255, 255, (int) (18 + 18 * progress))
        );
        vg.rectangle(
                settingsPanelScrollbarThumbRect.x,
                settingsPanelScrollbarThumbRect.y,
                settingsPanelScrollbarThumbRect.width,
                settingsPanelScrollbarThumbRect.height,
                NanoVGTheme.scrollbarThumb((int) (112 + 86 * progress))
        );
    }

    private void updateScrollbarDrag(float mouseY) {
        if (activeScrollbar == null || maxListScroll <= 0f || scrollbarTrackRect.height <= 0f || scrollbarThumbRect.height <= 0f)
            return;

        float travel = Math.max(1f, scrollbarTrackRect.height - scrollbarThumbRect.height);
        float thumbY = Math.clamp(mouseY - activeScrollbar.offsetY, scrollbarTrackRect.y, scrollbarTrackRect.y + travel);
        float progress = (thumbY - scrollbarTrackRect.y) / travel;
        targetListScroll = Math.clamp(progress * maxListScroll, 0f, maxListScroll);
        listScroll = targetListScroll;
        scrollVelocity = 0f;
    }

    private void updateSettingsPanelScrollbarDrag(float mouseY) {
        if (activeSettingsPanelScrollbar == null || maxSettingsPanelScroll <= 0f
                || settingsPanelScrollbarTrackRect.height <= 0f
                || settingsPanelScrollbarThumbRect.height <= 0f)
            return;

        float travel = Math.max(
                1f,
                settingsPanelScrollbarTrackRect.height - settingsPanelScrollbarThumbRect.height
        );
        float thumbY = Math.clamp(
                mouseY - activeSettingsPanelScrollbar.offsetY,
                settingsPanelScrollbarTrackRect.y,
                settingsPanelScrollbarTrackRect.y + travel
        );
        float progress = (thumbY - settingsPanelScrollbarTrackRect.y) / travel;
        targetSettingsPanelScroll = Math.clamp(progress * maxSettingsPanelScroll, 0f, maxSettingsPanelScroll);
        settingsPanelScroll = targetSettingsPanelScroll;
        settingsPanelScrollVelocity = 0f;
    }

    private List<Module> visibleModules() {
        String query = normalize(searchQuery);
        List<Module> modules = new ArrayList<>();

        for (Module module : ModuleManager.allModules) {
            if (module.moduleCategory == null) continue;
            if (!query.isBlank() && module.moduleCategory == ModuleCategory.Dev) continue;
            if (!passesTagFilters(module)) continue;

            boolean matches = query.isBlank()
                    ? module.moduleCategory == selectedCategory
                    : normalize(module.moduleName).contains(query)
                    || normalize(module.moduleCategory.name()).contains(query)
                    || (module.tag() != null && normalize(module.tag()).contains(query))
                    || (module.description() != null && normalize(module.description()).contains(query));

            if (matches)
                modules.add(module);
        }

        modules.sort(Comparator.comparing(module -> module.moduleName, String.CASE_INSENSITIVE_ORDER));
        return modules;
    }

    private List<ConfigEntry> visibleConfigEntries() {
        String query = normalize(searchQuery);
        List<ConfigEntry> entries = new ArrayList<>();

        switch (selectedConfigTab) {
            case MODULE -> {
                for (Path path : FileUtil.INSTANCE.getModuleFiles()) {
                    String name = configName(path);
                    if (matchesConfigSearch(name, query))
                        entries.add(new ConfigEntry(name, ConfigTab.MODULE, name.equalsIgnoreCase(Client.configManager.configCurrent)));
                }
            }
            case BIND -> {
                for (Path path : FileUtil.INSTANCE.getBindFiles()) {
                    String name = configName(path);
                    if (matchesConfigSearch(name, query))
                        entries.add(new ConfigEntry(name, ConfigTab.BIND, name.equalsIgnoreCase(Client.configManager.bindCurrent)));
                }
            }
            case ONLINE -> {
                for (String config : FileUtil.INSTANCE.getOnlineCfgs()) {
                    String name = stripJsonExtension(config);
                    if (!name.isBlank() && matchesConfigSearch(name, query))
                        entries.add(new ConfigEntry(name, ConfigTab.ONLINE, ConfigManager.onlineConfigClientName(name).equalsIgnoreCase(Client.configManager.configCurrent)));
                }
            }
        }

        entries.sort(Comparator.comparing(ConfigEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private boolean matchesConfigSearch(String name, String query) {
        return query.isBlank() || normalize(name).contains(query);
    }

    private void createConfigFromInput() {
        if (selectedConfigTab == ConfigTab.ONLINE)
            return;

        String configName = cleanConfigName(newConfigName);
        if (configName.isBlank())
            return;

        if (selectedConfigTab == ConfigTab.MODULE) {
            Client.configManager.saveConfigFile(Client.configManager.configCurrent, false);
            Client.configManager.saveConfigFile(configName, true);
        } else {
            Client.configManager.saveBindFile(Client.configManager.bindCurrent, false);
            Client.configManager.saveBindFile(configName, true);
        }

        newConfigName = "";
        targetListScroll = 0f;
        listScroll = 0f;
        scrollVelocity = 0f;
        clearTextFocus();
    }

    private void loadConfigEntry(ConfigEntry entry) {
        Map<Module, Boolean> previousModuleStates = snapshotModuleStates();

        switch (entry.type) {
            case MODULE -> {
                saveCurrentModuleConfig();
                Client.configManager.loadConfig(entry.name, false);
                saveLoadedModuleConfig(entry.name);
            }
            case BIND -> {
                saveCurrentBindConfig();
                Client.configManager.loadBind(entry.name);
                saveLoadedBindConfig(entry.name);
            }
            case ONLINE -> {
                saveCurrentModuleConfig();
                Client.configManager.loadConfig(entry.name, true);
            }
        }

        if (entry.type != ConfigTab.BIND) {
            prepareConfigToggleAnimations(previousModuleStates);
            Interface.reloadSortedModules();
        }

        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        closeListDropdown();
    }

    private void deleteConfigEntry(ConfigEntry entry) {
        if (entry == null || entry.type == ConfigTab.ONLINE || entry.current)
            return;

        String configName = cleanConfigName(entry.name);
        if (configName.isBlank())
            return;

        if (entry.type == ConfigTab.MODULE && configName.equalsIgnoreCase(Client.configManager.configCurrent))
            return;

        if (entry.type == ConfigTab.BIND && configName.equalsIgnoreCase(Client.configManager.bindCurrent))
            return;

        File base = new File(Client.configManager.BASE_DIR, entry.type == ConfigTab.MODULE ? "module_configs" : "bind_configs");
        File configFile = new File(base, configName + ".json");
        if (!base.exists() || !configFile.isFile() || !configFile.delete())
            return;

        FileUtil.INSTANCE.invalidateLocalConfigCache();
        Client.configManager.forgetConfigUpdated(entry.type == ConfigTab.BIND, configName);
        if (isSameConfig(settingsPanelConfig, entry))
            closeSettingsPanel();
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        scrollVelocity = 0f;
        clearTextFocus();
        closeListDropdown();
    }

    private Map<Module, Boolean> snapshotModuleStates() {
        Map<Module, Boolean> states = new IdentityHashMap<>();
        for (Module module : ModuleManager.allModules) {
            if (module != null)
                states.put(module, module.tempEnabled);
        }
        return states;
    }

    private void prepareConfigToggleAnimations(Map<Module, Boolean> previousStates) {
        for (Module module : ModuleManager.allModules) {
            if (module == null)
                continue;

            Boolean previous = previousStates.get(module);
            if (previous == null || previous == module.tempEnabled)
                continue;

            enabledAnimations.put(module, previous ? 1f : 0f);
            enabledAnimationFrames.remove(module);
        }
    }

    private static String configName(Path config) {
        if (config == null || config.getFileName() == null)
            return "";
        return stripJsonExtension(config.getFileName().toString());
    }

    private static String stripJsonExtension(String config) {
        if (config == null)
            return "";

        String name = config.trim();
        return name.toLowerCase(Locale.ROOT).endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static String cleanConfigName(String input) {
        String stripped = stripJsonExtension(input);
        if (stripped.isBlank())
            return "";

        StringBuilder builder = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|')
                continue;
            builder.append(c);
        }

        return builder.toString().trim();
    }

    private static void saveCurrentModuleConfig() {
        File base = new File(Client.configManager.BASE_DIR, "module_configs");
        File configFile = new File(base, Client.configManager.configCurrent + ".json");
        if (base.exists() && configFile.exists())
            Client.configManager.saveConfigFile(Client.configManager.configCurrent, false);
    }

    private static void saveLoadedModuleConfig(String configName) {
        File base = new File(Client.configManager.BASE_DIR, "module_configs");
        File configFile = new File(base, configName + ".json");
        if (base.exists() && configFile.exists())
            Client.configManager.saveConfigFile(configName, true);
    }

    private static void saveCurrentBindConfig() {
        File base = new File(Client.configManager.BASE_DIR, "bind_configs");
        File bindFile = new File(base, Client.configManager.bindCurrent + ".json");
        if (base.exists() && bindFile.exists())
            Client.configManager.saveBindFile(Client.configManager.bindCurrent, false);
    }

    private static void saveLoadedBindConfig(String configName) {
        File base = new File(Client.configManager.BASE_DIR, "bind_configs");
        File bindFile = new File(base, configName + ".json");
        if (base.exists() && bindFile.exists())
            Client.configManager.saveBindFile(configName, true);
    }

    private float contentHeight(List<Module> modules, float width) {
        float height = 0f;
        for (Module module : modules)
            height += moduleBlockHeight(module, width);
        return Math.max(height, u(30f));
    }

    private float moduleBlockHeight(Module module, float width) {
        NVGFont nameFont = settingsPanelModule == module ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        int lines = wrappedLineCount(module.moduleName, nameFont, font(14f), moduleNameWidth(module, width));
        return rowHeight + (lines - 1) * nameLineHeight;
    }

    private boolean isModuleExpanded(Module module) {
        return expandedModules.contains(module);
    }

    private float expansionProgress(Module module) {
        return animateIdentity(
                expansionAnimations,
                expansionAnimationFrames,
                module,
                expandedModules.contains(module),
                0.08f
        );
    }

    private float expandedSettingsHeight(Module module) {
        Float cached = expandedSettingsHeightCache.get(module);
        if (cached != null)
            return cached;

        List<SettingValue<?>> settings = collectSettings(module);
        float rowWidth = Math.max(u(40f), settingsPanelViewport.width - u(12f));
        float arrayHeight = wrappedSettingRowHeight("Show on array", booleanLabelMaxWidth(rowWidth));
        float height = u(6f) + arrayHeight + settingRowGap() + settingRowStep + moduleNoteHeight(module);

        if (settings.isEmpty()) {
            height += u(30f);
            expandedSettingsHeightCache.put(module, height);
            return height;
        }

        for (SettingValue<?> setting : settings) {
            height += (settingRowHeightFor(setting, rowWidth) + settingRowGap() + listDropdownHeight(module, setting)) * easeOut(settingVisibilityProgress(setting));
        }

        if (!hasRenderableSettings(module))
            height += u(30f);

        expandedSettingsHeightCache.put(module, height);
        return height;
    }

    private float listDropdownHeight(Module module, SettingValue<?> setting) {
        return openedListModule == module && openedListValue == setting && setting instanceof ListValue listValue
                ? listDropdownBaseHeight(listValue) * easeOut(listDropdownProgress(module, listValue))
                : setting instanceof ListValue listValue && listDropdownAnimations.containsKey(listValue)
                ? listDropdownBaseHeight(listValue) * easeOut(listDropdownProgress(module, listValue))
                : 0f;
    }

    private float listDropdownBaseHeight(ListValue value) {
        if (value == null || value.values.length <= 1)
            return 0f;
        float rowWidth = Math.max(u(40f), settingsPanelViewport.width - u(12f));
        float optionWidth = listValueWidth(rowWidth);
        float[] heights = listOptionHeights(value, optionWidth);
        float total = listOptionsTotalHeight(heights);
        float settingHeight = wrappedSettingRowHeight(value.name, listLabelMaxWidth(rowWidth));
        return Math.max(0f, u(4f) + total - settingHeight);
    }

    private float listDropdownProgress(Module module, ListValue value) {
        return animateIdentity(listDropdownAnimations, listDropdownAnimationFrames, value, openedListModule == module && openedListValue == value, 0.08f);
    }

    private List<SettingValue<?>> collectSettings(Module module) {
        if (module == null)
            return Collections.emptyList();

        return moduleSettingsCache.computeIfAbsent(module, this::readSettings);
    }

    private List<SettingValue<?>> readSettings(Module module) {
        List<SettingValue<?>> values = new ArrayList<>();
        for (Field field : ModuleManager.getSettings(module)) {
            try {
                Object setting = field.get(module);
                if (setting instanceof SettingValue<?> value)
                    values.add(value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return values.isEmpty() ? Collections.emptyList() : List.copyOf(values);
    }

    private boolean hasRenderableSettings(Module module) {
        Boolean cached = renderableSettingsCache.get(module);
        if (cached != null)
            return cached;

        for (SettingValue<?> setting : collectSettings(module)) {
            if (setting.canDisplay.canDisplay()) {
                renderableSettingsCache.put(module, true);
                return true;
            }

            Float progress = settingVisibilityAnimations.get(setting);
            if (progress != null && progress > 0.001f) {
                renderableSettingsCache.put(module, true);
                return true;
            }
        }

        renderableSettingsCache.put(module, false);
        return false;
    }

    private float moduleNoteHeight(Module module) {
        String note = moduleNote(module);
        if (note.isEmpty())
            return 0f;

        return noteBlockHeight(wrapNoteLines(note, Math.max(u(40f), settingsPanelViewport.width - u(12f)))) + u(7f);
    }

    private float noteBlockHeight(List<String> lines) {
        return Math.max(u(27f), u(12f) + Math.max(1, lines.size()) * u(15f));
    }

    private List<String> wrapNoteLines(String note, float rowWidth) {
        return wrapTextToWidth(note, NVGFonts.INTER, font(12f), Math.max(u(20f), rowWidth - u(24f)));
    }

    private String moduleNote(Module module) {
        String description = module.description();
        return description == null ? "" : description.trim();
    }

    private float settingVisibilityProgress(SettingValue<?> setting) {
        return animateIdentity(settingVisibilityAnimations, settingVisibilityAnimationFrames, setting, setting.canDisplay.canDisplay(), 0.08f);
    }

    private void toggleConfigPanel(ConfigEntry config) {
        if (config == null)
            return;
        if (isSameConfig(settingsPanelConfig, config))
            closeSettingsPanel();
        else
            openConfigPanel(config);
    }

    private void openConfigPanel(ConfigEntry config) {
        boolean alreadyOpen = isSettingsDockOpen();
        clearInteractionState();
        settingsPanelModule = null;
        settingsPanelRenderModule = null;
        settingsPanelConfig = config;
        settingsPanelRenderConfig = config;
        settingsPanelScroll = 0f;
        targetSettingsPanelScroll = 0f;
        settingsPanelScrollVelocity = 0f;
        activeSettingsPanelScrollbar = null;
        if (!alreadyOpen)
            animations.remove("settings-panel-open");
    }

    private void closeSettingsPanel() {
        clearInteractionState();
        settingsPanelModule = null;
        settingsPanelConfig = null;
        settingsPanelScroll = 0f;
        targetSettingsPanelScroll = 0f;
        settingsPanelScrollVelocity = 0f;
        maxSettingsPanelScroll = 0f;
        activeSettingsPanelScrollbar = null;
    }

    private boolean isModuleView() {
        return !configView && !commandView && !clientSettingsView;
    }

    private boolean isSettingsDockOpen() {
        return settingsPanelModule != null || settingsPanelConfig != null;
    }

    private static boolean isSameConfig(ConfigEntry first, ConfigEntry second) {
        return first != null && second != null && first.type() == second.type() && first.name().equalsIgnoreCase(second.name());
    }

    private void focusSearch() {
        storeCurrentCategoryScroll();
        clearInteractionState();
        textFocus = TextFocus.SEARCH;
    }

    private void focusNumberInput(SettingValue<?> value) {
        if (!(value instanceof FloatValue) && !(value instanceof IntValue))
            return;

        clearTextFocus();
        bindingModule = null;
        bindingValue = null;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        closeListDropdown();

        focusedNumberValue = value;
        numberInput = value instanceof FloatValue floatValue
                ? Float.toString(floatValue.get())
                : Integer.toString(((IntValue) value).get());
        textFocus = TextFocus.NUMBER;
        selectAllFocusedText();
    }

    private boolean beginConfigRename(ConfigEntry entry) {
        if (entry == null || entry.type == ConfigTab.ONLINE)
            return false;

        if (textFocus == TextFocus.CONFIG_RENAME && isSameConfig(renamingConfig, entry)) {
            selectAllFocusedText();
            return true;
        }

        clearTextFocus();
        bindingModule = null;
        bindingValue = null;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        closeListDropdown();

        renamingConfig = entry;
        renameConfigName = entry.name == null ? "" : entry.name;
        textFocus = TextFocus.CONFIG_RENAME;
        selectAllFocusedText();
        return true;
    }

    private void cancelConfigRename() {
        resetTextFocus();
    }

    private boolean commitConfigRename() {
        if (renamingConfig == null || renamingConfig.type == ConfigTab.ONLINE)
            return false;

        String cleaned = cleanConfigName(renameConfigName);
        if (cleaned.isBlank())
            return false;

        boolean bind = renamingConfig.type == ConfigTab.BIND;
        String renamed = Client.configManager.renameConfig(bind, renamingConfig.name, cleaned);
        if (renamed == null)
            return false;

        if (settingsPanelConfig != null
                && settingsPanelConfig.type == renamingConfig.type
                && settingsPanelConfig.name.equalsIgnoreCase(renamingConfig.name)) {
            boolean current = bind
                    ? renamed.equalsIgnoreCase(Client.configManager.bindCurrent)
                    : renamed.equalsIgnoreCase(Client.configManager.configCurrent);
            settingsPanelConfig = new ConfigEntry(renamed, renamingConfig.type, current);
            settingsPanelRenderConfig = settingsPanelConfig;
        }

        return true;
    }

    private void clearSearch() {
        searchQuery = "";
        textCaret = 0;
        textAnchor = 0;
        if (isModuleView() && selectedCategory != null)
            restoreCategoryScroll(selectedCategory);
        else
            resetListScroll();
        closeListDropdown();
    }

    private void clearInteractionState() {
        clearTextFocus();
        bindingModule = null;
        bindingValue = null;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        activeColumnDrag = null;
        closeListDropdown();
    }

    private void closeListDropdown() {
        openedListModule = null;
        openedListValue = null;
    }

    private void toggleListDropdown(Module module, ListValue value) {
        if (module == null || value == null)
            return;
        if (openedListModule == module && openedListValue == value)
            closeListDropdown();
        else {
            openedListModule = module;
            openedListValue = value;
        }
    }

    private void rememberHoveredModule(Module module) {
        hoveredModule = module;
        String note = moduleNote(module);
        if (note.isEmpty())
            return;
        rememberTooltip(module, note);
    }

    private void rememberHoveredSetting(SettingValue<?> setting) {
        hoveredSetting = setting;
        if (setting == null || isSettingInteractionActive(setting))
            return;
        String note = settingNote(setting);
        if (note.isEmpty())
            return;
        rememberTooltip(setting, note);
    }

    private void rememberTooltip(Object target, String note) {
        if (tooltipHoverTarget != target) {
            tooltipHoverTarget = target;
            tooltipHoverStartNanos = System.nanoTime();
        }
        tooltipText = note;
    }

    private String settingNote(SettingValue<?> setting) {
        String note = setting == null ? null : setting.description();
        return note == null ? "" : note.trim();
    }

    private boolean isSettingInteractionActive(SettingValue<?> setting) {
        if (focusedTextValue == setting || bindingValue == setting)
            return true;
        if (activeSlider != null && activeSlider.control().target() == setting)
            return true;
        return setting instanceof ListValue list && openedListValue == list;
    }

    private void renderHoverTooltip(NVGU vg) {
        boolean hasTooltipTarget = hoveredModule != null
                || hoveredSetting != null
                || hoveredTooltipTarget != null
                || infoButtonHovered;
        boolean blockedByModule = hoveredModule != null
                && (settingsPanelModule == hoveredModule || expandedModules.contains(hoveredModule));
        boolean ready = hasTooltipTarget
                && !blockedByModule
                && !tooltipText.isEmpty()
                && System.nanoTime() - tooltipHoverStartNanos >= 280_000_000L;
        if (!ready)
            tooltipHoverTarget = hoveredModule != null ? hoveredModule
                    : hoveredSetting != null ? hoveredSetting
                    : hoveredTooltipTarget != null ? hoveredTooltipTarget
                    : infoButtonHovered ? INFO_TOOLTIP_TARGET : null;
        float progress = animate("module-tooltip", ready, 0.22f);
        if (progress <= 0.01f || tooltipText.isEmpty())
            return;

        float paddingX = u(6f);
        float maxWidth = u(210f);
        float tooltipFont = font(9f);
        float lineHeight = u(11.25f);
        List<String> lines = wrapTextToWidth(tooltipText, NVGFonts.INTER, tooltipFont, maxWidth);
        float textWidth = 0f;
        for (String line : lines)
            textWidth = Math.max(textWidth, NVGFonts.INTER.getWidth(line, tooltipFont));
        float width = textWidth + paddingX * 2f;
        float height = u(18f) + (lines.size() - 1) * lineHeight;
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float preferredX = scaledMouseX + u(10f);
        float drawX = preferredX + width > screenWidth - u(8f)
                ? Math.max(u(8f), screenWidth - u(8f) - width)
                : Math.max(u(8f), preferredX);
        float drawY = scaledMouseY + u(16f) + height > screenHeight - u(8f)
                ? scaledMouseY - height - u(8f)
                : scaledMouseY + u(16f);

        vg.globalAlpha(easeOut(progress), () -> {
            NanoVGTheme.renderPanel(vg, drawX, drawY, width, height, u(NanoVGTheme.RADIUS_HUD), 1f);
            for (int i = 0; i < lines.size(); i++)
                NVGFonts.INTER.drawText(lines.get(i), drawX + paddingX, drawY + u(4.5f) + i * lineHeight, tooltipFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
        });
    }

    private boolean nudgeHoveredColorSlider(float mouseX, float mouseY, double vertical) {
        if (Math.abs(vertical) < 0.01d)
            return false;

        for (ControlBounds control : controls) {
            if (control.type != ControlType.COLOR_SLIDER || !control.rect.contains(mouseX, mouseY))
                continue;

            ColorValue value = (ColorValue) control.target;
            Color color = value.get();
            int delta = (int) Math.round(vertical * 5d);
            int red = control.index == 0 ? Math.clamp(color.getRed() + delta, 0, 255) : color.getRed();
            int green = control.index == 1 ? Math.clamp(color.getGreen() + delta, 0, 255) : color.getGreen();
            int blue = control.index == 2 ? Math.clamp(color.getBlue() + delta, 0, 255) : color.getBlue();
            value.set(new Color(red, green, blue, color.getAlpha()));
            return true;
        }
        return false;
    }

    private void copyFocusedTextToClipboard() {
        if (minecraft == null || textFocus == TextFocus.NONE)
            return;

        String text = getFocusedText();
        if (text == null || text.isEmpty())
            return;

        if (hasTextSelection()) {
            int start = Math.clamp(textSelectionStart(), 0, text.length());
            int end = Math.clamp(textSelectionEnd(), start, text.length());
            if (start >= end)
                return;
            text = text.substring(start, end);
        }

        if (text.isEmpty())
            return;

        minecraft.keyboardHandler.setClipboard(text);
    }

    private void pasteClipboardIntoFocusedText() {
        if (minecraft == null || textFocus == TextFocus.NONE)
            return;

        String clipboard = minecraft.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isEmpty())
            return;

        String sanitized = clipboard.replace('\n', ' ').replace('\r', ' ');
        if (textFocus == TextFocus.NUMBER) {
            appendNumberInput(sanitized.trim());
            return;
        }
        if (textFocus != TextFocus.SETTING)
            sanitized = sanitized.replace('\t', ' ');
        if (sanitized.isEmpty())
            return;

        appendFocusedText(sanitized);
    }

    private void cycleCategory(int delta) {
        storeCurrentCategoryScroll();
        ModuleCategory[] categories = ModuleCategory.values();
        ConfigTab[] configTabs = ConfigTab.values();
        CommandTab[] commandTabs = CommandTab.values();
        int categoryCount = categories.length;
        int configEnd = categoryCount + configTabs.length;
        int total = configEnd + commandTabs.length;
        int current = configView
                ? categoryCount + selectedConfigTab.ordinal()
                : commandView
                ? configEnd + selectedCommandTab.ordinal()
                : selectedCategory.ordinal();
        int next = Math.floorMod(current + delta, total);
        searchQuery = "";
        if (next < categoryCount) {
            configView = false;
            commandView = false;
            selectedCategory = categories[next];
            restoreCategoryScroll(selectedCategory);
        } else if (next < configEnd) {
            configView = true;
            commandView = false;
            selectedConfigTab = configTabs[next - categoryCount];
            resetListScroll();
        } else {
            configView = false;
            commandView = true;
            selectedCommandTab = commandTabs[next - configEnd];
            resetCommandScroll();
        }
        clearInteractionState();
    }

    private void scrollWithKeyboard(int key) {
        boolean settingsFocused = isSettingsDockOpen()
                && settingsPanelRect.contains(scaledMouseX, scaledMouseY);
        if (settingsFocused) {
            float page = Math.max(u(48f), settingsPanelViewport.height * 0.85f);
            if (key == Keys.KEY_HOME)
                targetSettingsPanelScroll = 0f;
            else if (key == Keys.KEY_END)
                targetSettingsPanelScroll = maxSettingsPanelScroll;
            else if (key == Keys.KEY_PAGE_UP)
                targetSettingsPanelScroll = Math.max(0f, targetSettingsPanelScroll - page);
            else
                targetSettingsPanelScroll = Math.min(maxSettingsPanelScroll, targetSettingsPanelScroll + page);
            return;
        }

        float page = Math.max(u(48f), listViewport.height * 0.85f);
        if (key == Keys.KEY_HOME)
            targetListScroll = 0f;
        else if (key == Keys.KEY_END)
            targetListScroll = maxListScroll;
        else if (key == Keys.KEY_PAGE_UP)
            targetListScroll = Math.max(0f, targetListScroll - page);
        else
            targetListScroll = Math.min(maxListScroll, targetListScroll + page);
    }

    private void clearTextFocus() {
        if (textFocus == TextFocus.NUMBER)
            commitNumberInput();
        if (textFocus == TextFocus.CONFIG_RENAME)
            commitConfigRename();
        resetTextFocus();
    }

    private void resetTextFocus() {
        textFocus = TextFocus.NONE;
        focusedTextValue = null;
        focusedTextModule = null;
        focusedNumberValue = null;
        renamingConfig = null;
        renameConfigName = "";
        numberInput = "";
        textCaret = 0;
        textAnchor = 0;
        draggingTextSelection = false;
    }

    private void cancelNumberInput() {
        resetTextFocus();
    }

    private boolean commitNumberInput() {
        Number parsed = parseNumberInput();
        if (parsed == null)
            return false;

        if (focusedNumberValue instanceof FloatValue value) {
            value.set(Math.clamp(parsed.floatValue(), value.minimum, value.maximum));
            return true;
        }

        if (focusedNumberValue instanceof IntValue value) {
            long parsedValue = parsed.longValue();
            long clamped = Math.max(value.minimum, Math.min(value.maximum, parsedValue));
            value.set((int) clamped);
            return true;
        }

        return false;
    }

    private Number parseNumberInput() {
        String input = numberInput.trim();
        if (input.isEmpty())
            return null;

        try {
            if (focusedNumberValue instanceof FloatValue) {
                float parsed = Float.parseFloat(input);
                return Float.isFinite(parsed) ? parsed : null;
            }
            if (focusedNumberValue instanceof IntValue)
                return Long.parseLong(input);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void appendNumberInput(String input) {
        if (textFocus != TextFocus.NUMBER || input == null || input.isEmpty())
            return;

        String text = numberInput == null ? "" : numberInput;
        int start = Math.clamp(textSelectionStart(), 0, text.length());
        int end = Math.clamp(textSelectionEnd(), start, text.length());
        String candidate = text.substring(0, start) + input + text.substring(end);
        if (candidate.length() > 64 || !isPotentialNumberInput(candidate))
            return;

        numberInput = candidate;
        textCaret = start + input.length();
        textAnchor = textCaret;
    }

    private boolean isPotentialNumberInput(String input) {
        if (focusedNumberValue instanceof FloatValue)
            return input.matches("[+-]?(?:\\d*(?:\\.\\d*)?)(?:[eE][+-]?\\d*)?");
        if (focusedNumberValue instanceof IntValue)
            return input.matches("[+-]?\\d*");
        return false;
    }

    private String getFocusedText() {
        if (textFocus == TextFocus.SEARCH) return searchQuery;
        if (textFocus == TextFocus.CONFIG_NAME) return newConfigName;
        if (textFocus == TextFocus.COMMAND) return commandInput;
        if (textFocus == TextFocus.CONFIG_RENAME) return renameConfigName;
        if (textFocus == TextFocus.SETTING && focusedTextValue != null) return focusedTextValue.get();
        if (textFocus == TextFocus.NUMBER) return numberInput;
        return "";
    }

    private void setFocusedText(String text) {
        if (textFocus == TextFocus.SEARCH) {
            searchQuery = text == null ? "" : text;
            if (searchQuery.isBlank() && isModuleView() && selectedCategory != null)
                restoreCategoryScroll(selectedCategory);
            else
                resetListScroll();
            return;
        }

        if (textFocus == TextFocus.CONFIG_NAME) {
            newConfigName = text == null ? "" : text;
            return;
        }

        if (textFocus == TextFocus.COMMAND) {
            String next = text == null ? "" : text;
            if (next.length() > MAX_COMMAND_INPUT_LENGTH)
                next = next.substring(0, MAX_COMMAND_INPUT_LENGTH);
            commandInput = next;
            commandHistoryIndex = -1;
            return;
        }

        if (textFocus == TextFocus.CONFIG_RENAME) {
            String next = text == null ? "" : text;
            if (next.length() > ConfigManager.MAX_CONFIG_NAME_LENGTH)
                next = next.substring(0, ConfigManager.MAX_CONFIG_NAME_LENGTH);
            renameConfigName = next;
            return;
        }

        if (textFocus == TextFocus.SETTING && focusedTextValue != null) {
            focusedTextValue.set(text == null ? "" : text);
            return;
        }

        if (textFocus == TextFocus.NUMBER) {
            numberInput = text == null ? "" : text;
        }
    }

    private void appendFocusedText(String input) {
        if (textFocus == TextFocus.NONE || input == null || input.isEmpty())
            return;

        replaceFocusedSelection(input);
    }

    private void removeLastFocusedCharacter() {
        if (hasTextSelection()) {
            replaceFocusedSelection("");
            return;
        }

        String text = getFocusedText();
        if (text.isEmpty() || textCaret <= 0)
            return;

        int caret = Math.clamp(textCaret, 0, text.length());
        int previous = text.offsetByCodePoints(caret, -1);
        setFocusedText(text.substring(0, previous) + text.substring(caret));
        textCaret = previous;
        textAnchor = previous;
    }

    private void deleteForwardFocusedCharacter() {
        if (hasTextSelection()) {
            replaceFocusedSelection("");
            return;
        }

        String text = getFocusedText();
        if (text.isEmpty() || textCaret >= text.length())
            return;

        int caret = Math.clamp(textCaret, 0, text.length());
        int next = text.offsetByCodePoints(caret, 1);
        setFocusedText(text.substring(0, caret) + text.substring(next));
        textCaret = caret;
        textAnchor = caret;
    }

    private void replaceFocusedSelection(String insertion) {
        String text = getFocusedText();
        int start = Math.clamp(textSelectionStart(), 0, text.length());
        int end = Math.clamp(textSelectionEnd(), start, text.length());
        setFocusedText(text.substring(0, start) + insertion + text.substring(end));
        textCaret = start + insertion.length();
        textAnchor = textCaret;
    }

    private void rememberTextField(TextFocus focus, Object target, float textX, float fontSize, float maxWidth) {
        textFields.add(new TextFieldLayout(focus, target, textX, fontSize, maxWidth));
    }

    private boolean hasTextSelection() {
        return textCaret != textAnchor;
    }

    private int textSelectionStart() {
        return Math.min(textCaret, textAnchor);
    }

    private int textSelectionEnd() {
        return Math.max(textCaret, textAnchor);
    }

    private void selectAllFocusedText() {
        textAnchor = 0;
        textCaret = getFocusedText().length();
        draggingTextSelection = false;
    }

    private void moveFocusedCaret(int direction, boolean extendSelection) {
        String text = getFocusedText();
        if (text.isEmpty()) {
            textCaret = 0;
            textAnchor = 0;
            return;
        }

        if (!extendSelection && hasTextSelection()) {
            textCaret = direction < 0 ? textSelectionStart() : textSelectionEnd();
            textAnchor = textCaret;
            return;
        }

        int caret = Math.clamp(textCaret, 0, text.length());
        if (direction < 0 && caret > 0)
            caret = text.offsetByCodePoints(caret, -1);
        else if (direction > 0 && caret < text.length())
            caret = text.offsetByCodePoints(caret, 1);
        textCaret = caret;
        if (!extendSelection)
            textAnchor = textCaret;
    }

    private void beginTextSelectionOrSelectAll(float mouseX, boolean doubleClick) {
        if (doubleClick) {
            selectAllFocusedText();
            return;
        }
        beginTextSelection(mouseX);
    }

    private void beginTextSelection(float mouseX) {
        int index = caretIndexAt(mouseX);
        textAnchor = index;
        textCaret = index;
        draggingTextSelection = true;
    }

    private void updateTextSelectionDrag(float mouseX) {
        if (textFocus == TextFocus.NONE)
            return;
        textCaret = caretIndexAt(mouseX);
    }

    private int caretIndexAt(float mouseX) {
        String text = getFocusedText();
        TextFieldLayout layout = findFocusedTextField();
        if (text.isEmpty())
            return 0;
        if (layout == null)
            return text.length();

        float local = mouseX - layout.textX;
        if (local <= 0f)
            return 0;

        int index = 0;
        while (index < text.length()) {
            int next = text.offsetByCodePoints(index, 1);
            float left = NVGFonts.INTER.getWidth(text.substring(0, index), layout.fontSize);
            float right = NVGFonts.INTER.getWidth(text.substring(0, next), layout.fontSize);
            if (local < (left + right) / 2f)
                return index;
            index = next;
        }
        return text.length();
    }

    private TextFieldLayout findFocusedTextField() {
        for (int i = 0, n = textFields.size(); i < n; i++) {
            TextFieldLayout layout = textFields.get(i);
            if (layout.focus != textFocus)
                continue;
            if (textFocus == TextFocus.SETTING && layout.target != focusedTextValue)
                continue;
            if (textFocus == TextFocus.NUMBER && layout.target != focusedNumberValue)
                continue;
            if (textFocus == TextFocus.CONFIG_RENAME) {
                if (!(layout.target instanceof ConfigEntry layoutConfig) || !isSameConfig(layoutConfig, renamingConfig))
                    continue;
            }
            return layout;
        }
        return null;
    }

    private void applyHoverCursor() {
        if (minecraft == null)
            return;
        minecraft.getWindow().selectCursor(resolveHoverCursor());
    }

    private void resetHoverCursor() {
        if (minecraft == null)
            return;
        minecraft.getWindow().selectCursor(CursorType.DEFAULT);
    }

    private CursorType resolveHoverCursor() {
        if (closing)
            return CursorType.DEFAULT;

        if (hitRect(searchClearRect))
            return CursorTypes.POINTING_HAND;
        if (hitRect(closeRect))
            return CursorTypes.POINTING_HAND;
        if (isSettingsDockOpen() && hitRect(settingsPanelCloseRect))
            return CursorTypes.POINTING_HAND;
        if (hitRect(scrollbarThumbRect) || hitRect(scrollbarTrackRect))
            return CursorTypes.POINTING_HAND;
        if (hitRect(settingsPanelScrollbarThumbRect) || hitRect(settingsPanelScrollbarTrackRect))
            return CursorTypes.POINTING_HAND;
        if (isModuleView()) {
            for (ColumnState column : columns.values()) {
                if (hitRect(column.header))
                    return CursorTypes.POINTING_HAND;
                if (hitRect(column.scrollbarThumb) || hitRect(column.scrollbarTrack))
                    return CursorTypes.POINTING_HAND;
            }
        }

        if (isSettingsDockOpen() && hitRect(settingsPanelRect)) {
            CursorType settingsCursor = cursorForControls(settingsPanelControlStartIndex, controls.size());
            return settingsCursor == null ? CursorType.DEFAULT : settingsCursor;
        }

        CursorType controlCursor = cursorForControls(0, settingsPanelControlStartIndex > 0 ? settingsPanelControlStartIndex : controls.size());
        if (controlCursor != null)
            return controlCursor;

        if (hitRect(searchRect))
            return CursorTypes.IBEAM;

        for (int i = 0, n = moduleRows.size(); i < n; i++) {
            if (moduleRows.get(i).rect.contains(scaledMouseX, scaledMouseY))
                return CursorTypes.POINTING_HAND;
        }
        for (int i = 0, n = tabBounds.size(); i < n; i++) {
            if (tabBounds.get(i).rect.contains(scaledMouseX, scaledMouseY))
                return CursorTypes.POINTING_HAND;
        }
        return CursorType.DEFAULT;
    }

    private CursorType cursorForControls(int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            ControlBounds control = controls.get(i);
            if (!control.rect.contains(scaledMouseX, scaledMouseY))
                continue;
            return cursorForControl(control);
        }
        return null;
    }

    private CursorType cursorForControl(ControlBounds control) {
        return switch (control.type) {
            case TEXT_VALUE, CONFIG_NAME, CONFIG_RENAME, COMMAND_INPUT -> CursorTypes.IBEAM;
            case FLOAT_SLIDER, INT_SLIDER, COLOR_SLIDER, INLINE_FLOAT_SLIDER, INLINE_INT_SLIDER, INLINE_COLOR_SLIDER -> CursorTypes.POINTING_HAND;
            case FLOAT_INPUT, INT_INPUT ->
                    textFocus == TextFocus.NUMBER && focusedNumberValue == control.target
                            ? CursorTypes.IBEAM
                            : CursorTypes.POINTING_HAND;
            default -> CursorTypes.POINTING_HAND;
        };
    }

    private boolean hitRect(Rect rect) {
        return rect != null && rect.width() > 0f && rect.height() > 0f && rect.contains(scaledMouseX, scaledMouseY);
    }

    private Rect sliderTrack(ControlBounds control) {
        return switch (control.type) {
            case FLOAT_SLIDER, INT_SLIDER -> control.rect.contract(u(8f), u(10f));
            case COLOR_SLIDER -> control.rect.contract(u(6f), u(10f));
            default -> control.rect;
        };
    }

    private float washProgress(String scope, Object key, boolean active) {
        return animations.compute(scope + ":" + System.identityHashCode(key), (ignored, current) -> {
            if (current == null)
                return active ? 1f : 0f;
            return approach(current, active ? 1f : 0f, 0.18f * frameDelta);
        });
    }

    private float animate(String key, boolean active, float speed) {
        return animations.compute(key, (ignored, current) -> approach(current == null ? 0f : current, active ? 1f : 0f, speed * frameDelta));
    }

    private <T> float animateIdentity(Map<T, Float> states, Map<T, Long> frames, T key, boolean active, float speed) {
        Float current = states.get(key);
        if (!active && current == null)
            return 0f;

        Long frame = frames.get(key);
        if (frame != null && frame == animationFrame)
            return current == null ? 0f : current;

        float progress = stepTowards(current == null ? 0f : current, active ? 1f : 0f, speed * frameDelta);
        if (progress <= 0.001f && !active) {
            states.remove(key);
            frames.remove(key);
            return 0f;
        }

        states.put(key, progress);
        frames.put(key, animationFrame);
        return progress;
    }

    private static float approach(float value, float target, float speed) {
        return value + (target - value) * Math.clamp(speed, 0f, 1f);
    }

    private static float stepTowards(float value, float target, float step) {
        if (value < target)
            return Math.min(target, value + Math.max(0f, step));
        if (value > target)
            return Math.max(target, value - Math.max(0f, step));
        return target;
    }

    private static float easeOut(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return 1f - (1f - progress) * (1f - progress);
    }

    private static float easeIn(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return progress * progress;
    }

    private static float normalize(float value, float min, float max) {
        if (Math.abs(max - min) <= 0.0001f) return 0f;
        return Math.clamp((value - min) / (max - min), 0f, 1f);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String inputText(String value, boolean active, String placeholder) {
        String text = value == null ? "" : value;
        if (active) {
            if (hasTextSelection() || !caretVisible())
                return text;
            int caret = Math.clamp(textCaret, 0, text.length());
            return caret >= text.length() ? text + "_" : text;
        }
        return text.isBlank() ? (placeholder == null ? "" : placeholder) : text;
    }

    private void renderInlineCaret(NVGU vg, boolean active, NVGFont font, float fontSize, float textX, float textY, Color color) {
        if (!active || hasTextSelection() || !caretVisible())
            return;

        String text = getFocusedText();
        int caret = Math.clamp(textCaret, 0, text.length());
        if (caret >= text.length())
            return;

        float caretX = textX + font.getWidth(text.substring(0, caret), fontSize);
        font.drawText("|", caretX, textY, fontSize, color, Alignment.CENTER_TOP, true);
    }

    private void renderSelectionHighlight(
            NVGU vg,
            boolean active,
            NVGFont font,
            float fontSize,
            float textX,
            float textY,
            float maxWidth
    ) {
        if (!active || !hasTextSelection())
            return;

        String text = getFocusedText();
        if (text == null || text.isEmpty())
            return;

        int start = Math.clamp(textSelectionStart(), 0, text.length());
        int end = Math.clamp(textSelectionEnd(), start, text.length());
        if (start >= end)
            return;

        float startX = textX + font.getWidth(text.substring(0, start), fontSize);
        float endX = textX + font.getWidth(text.substring(0, end), fontSize);
        float left = Math.max(textX, startX);
        float right = Math.min(textX + Math.max(0f, maxWidth), endX);
        if (right <= left)
            return;

        vg.roundedRectangle(
                left - u(1f),
                textY - u(1f),
                right - left + u(2f),
                fontSize + u(3f),
                u(2f),
                NanoVGTheme.inputFocus(72)
        );
    }

    private boolean caretVisible() {
        return (System.currentTimeMillis() / 480L) % 2L == 0L;
    }

    private static Color alpha(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.clamp(alpha, 0, 255));
    }

    private static Color mix(Color start, Color end, float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return new Color(
                Math.clamp(Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress), 0, 255),
                Math.clamp(Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress), 0, 255),
                Math.clamp(Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress), 0, 255),
                Math.clamp(Math.round(start.getAlpha() + (end.getAlpha() - start.getAlpha()) * progress), 0, 255)
        );
    }

    private float settingRowGap() {
        return settingRowStep - settingRowHeight;
    }

    private float settingRowHeightFor(SettingValue<?> setting, float rowWidth) {
        if (setting instanceof BooleanValue value)
            return wrappedSettingRowHeight(value.name, booleanLabelMaxWidth(rowWidth));
        if (setting instanceof ListValue value) {
            float labelHeight = wrappedSettingRowHeight(value.name, listLabelMaxWidth(rowWidth));
            if (value.get() == null)
                return labelHeight;
            int valueLines = wrappedLineCount(value.get(), NVGFonts.INTER, font(11f), listCurrentValueWidth(rowWidth));
            return Math.max(labelHeight, u(8f) + valueLines * u(14f) + u(5f));
        }
        if (setting instanceof KeyBindValue)
            return wrappedSettingRowHeight(setting.name, keybindLabelMaxWidth(rowWidth));
        return wrappedSettingRowHeight(setting.name, settingLabelMaxWidth(setting, rowWidth));
    }

    private float settingLabelMaxWidth(SettingValue<?> setting, float rowWidth) {
        float reserve = switch (setting) {
            case FloatValue ignored -> u(168f);
            case IntValue ignored -> u(168f);
            case ColorValue ignored -> u(154f);
            case TextValue ignored -> u(132f);
            default -> NVGFonts.INTER.getWidth("Unsupported", font(11f)) + u(25f);
        };
        return Math.max(u(36f), rowWidth - reserve - u(20f));
    }

    private float keybindLabelMaxWidth(float rowWidth) {
        float valueWidth = NVGFonts.INTER.getWidth("Press key...", font(11f));
        return Math.max(u(36f), rowWidth - valueWidth - u(25f) - u(20f));
    }

    private float listCurrentValueWidth(float rowWidth) {
        return Math.max(u(20f), listValueWidth(rowWidth) - u(17f));
    }

    private float booleanLabelMaxWidth(float rowWidth) {
        return Math.max(u(36f), rowWidth - switchWidth - u(24f));
    }

    private float listValueWidth(float rowWidth) {
        return Math.max(u(88f), Math.min(u(180f), rowWidth * 0.38f));
    }

    private float listLabelMaxWidth(float rowWidth) {
        return Math.max(u(36f), rowWidth - listValueWidth(rowWidth) - u(28f));
    }

    private float[] listOptionHeights(ListValue value, float optionWidth) {
        float[] heights = new float[value.values.length];
        for (int i = 0; i < value.values.length; i++)
            heights[i] = listOptionRowHeight(value.values[i], optionWidth);
        return heights;
    }

    private float listOptionsTotalHeight(float[] heights) {
        float total = 0f;
        for (float height : heights)
            total += height;
        return total;
    }

    private float listOptionRowHeight(String option, float optionWidth) {
        int lines = wrapListOption(option, optionWidth - u(18f)).size();
        return Math.max(u(20f), u(4f) + lines * u(14f) + u(3f));
    }

    private void drawWrappedListOption(String option, float x, float y, float maxWidth, boolean selected) {
        float size = font(11f);
        float lineHeight = u(14f);
        NVGFont optionFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        List<String> lines = wrapListOption(option, maxWidth);
        for (int i = 0; i < lines.size(); i++)
            optionFont.drawText(lines.get(i), x, y + i * lineHeight, size, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
    }

    private List<String> wrapListOption(String option, float maxWidth) {
        return wrapTextToWidth(option == null ? "" : option, NVGFonts.INTER_MEDIUM, font(11f), Math.max(u(20f), maxWidth));
    }

    private float wrappedSettingRowHeight(String label, float maxWidth) {
        int lines = wrapSettingLabel(label, maxWidth).size();
        return Math.max(settingRowHeight, u(6f) + lines * u(15f) + u(4f));
    }

    private void drawWrappedSettingLabel(String label, float x, float y, float maxWidth) {
        float size = font(12f);
        float lineHeight = u(15f);
        List<String> lines = wrapSettingLabel(label, maxWidth);
        for (int i = 0; i < lines.size(); i++)
            NVGFonts.INTER.drawText(lines.get(i), x, y + i * lineHeight, size, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
    }

    private List<String> wrapSettingLabel(String label, float maxWidth) {
        return wrapTextToWidth(label == null ? "" : label, NVGFonts.INTER, font(12f), Math.max(u(20f), maxWidth));
    }

    private static List<String> wrapTextToWidth(String text, NVGFont font, float size, float maxWidth) {
        WrapKey key = new WrapKey(text, font, size, maxWidth);
        List<String> cached = WRAP_CACHE.get(key);
        if (cached != null)
            return cached;

        List<String> lines = Collections.unmodifiableList(computeWrappedLines(text, font, size, maxWidth));
        if (WRAP_CACHE.size() >= WRAP_CACHE_LIMIT)
            WRAP_CACHE.clear();
        WRAP_CACHE.put(key, lines);
        return lines;
    }

    private static List<String> computeWrappedLines(String text, NVGFont font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int index = 0;
        while (index <= normalized.length()) {
            int lineBreak = normalized.indexOf('\n', index);
            if (lineBreak < 0)
                lineBreak = normalized.length();
            wrapParagraph(normalized.substring(index, lineBreak), font, size, maxWidth, lines);
            if (lineBreak >= normalized.length())
                break;
            index = lineBreak + 1;
        }

        return lines.isEmpty() ? Collections.singletonList("") : lines;
    }

    private static void wrapParagraph(String paragraph, NVGFont font, float size, float maxWidth, List<String> lines) {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }
        if (font.getWidth(paragraph, size) <= maxWidth) {
            lines.add(paragraph);
            return;
        }

        int start = 0;
        while (start < paragraph.length()) {
            int end = findWrapEnd(paragraph, start, font, size, maxWidth);
            lines.add(paragraph.substring(start, end).strip());
            start = end;
            while (start < paragraph.length() && Character.isWhitespace(paragraph.charAt(start)))
                start++;
        }
    }

    private static int findWrapEnd(String text, int start, NVGFont font, float size, float maxWidth) {
        int bestFit = start + 1;
        int lastWhitespace = -1;

        for (int end = start + 1; end <= text.length(); end++) {
            if (font.getWidth(text.substring(start, end), size) > maxWidth)
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

    private float drawWrappedText(NVGFont font, String text, float x, float y, float size, float maxWidth, float lineHeight, Color color, Alignment alignment) {
        List<String> lines = wrapTextToWidth(text == null ? "" : text, font, size, Math.max(u(20f), maxWidth));
        for (int i = 0; i < lines.size(); i++)
            font.drawText(lines.get(i), x, y + i * lineHeight, size, color, alignment, true);
        return lines.size() * lineHeight;
    }

    private int wrappedLineCount(String text, NVGFont font, float size, float maxWidth) {
        return wrapTextToWidth(text == null ? "" : text, font, size, Math.max(u(20f), maxWidth)).size();
    }

    private void renderInputText(NVGU vg, TextFocus focus, Object target, boolean active, String text, float fontSize, float textX, float textY, float selectionY, float maxWidth, Rect box, Color color) {
        float scroll = inputScroll(text, active, fontSize, maxWidth);
        float shiftedX = textX - scroll;
        rememberTextField(focus, target, shiftedX, fontSize, maxWidth + scroll);
        vg.scissor(textX - u(2f), box.y, maxWidth + u(4f), box.height, () -> {
            renderSelectionHighlight(vg, active, NVGFonts.INTER, fontSize, shiftedX, selectionY, maxWidth + scroll);
            NVGFonts.INTER.drawText(text, shiftedX, textY, fontSize, color, Alignment.LEFT_TOP, true);
            renderInlineCaret(vg, active, NVGFonts.INTER, fontSize, shiftedX, textY, color);
        });
    }

    private float inputScroll(String text, boolean active, float fontSize, float maxWidth) {
        float total = NVGFonts.INTER.getWidth(text, fontSize);
        if (!active || total <= maxWidth)
            return 0f;

        String focused = getFocusedText();
        int caret = Math.clamp(textCaret, 0, focused.length());
        float caretX = NVGFonts.INTER.getWidth(focused.substring(0, caret), fontSize) + u(6f);
        return Math.clamp(caretX - maxWidth, 0f, total - maxWidth);
    }

    private static int normalizeKey(int key) {
        return key == Keys.KEY_ESCAPE || key == Keys.KEY_BACKSPACE || key == Keys.KEY_DELETE
                ? Keys.KEY_UNKNOWN
                : key;
    }

    private static String keyName(int key) {
        if (key == Keys.KEY_UNKNOWN)
            return "NONE";
        if (Keys.isMouseKey(key))
            return Keys.mouseKeyName(key);

        String name = Keys.printableKeyName(key);
        if (name != null)
            return name.toUpperCase(Locale.ROOT);

        return switch (key) {
            case Keys.KEY_SPACE -> "SPACE";
            case Keys.KEY_ESCAPE -> "ESCAPE";
            case Keys.KEY_ENTER -> "ENTER";
            case Keys.KEY_TAB -> "TAB";
            case Keys.KEY_BACKSPACE -> "BACKSPACE";
            case Keys.KEY_INSERT -> "INSERT";
            case Keys.KEY_DELETE -> "DELETE";
            case Keys.KEY_RIGHT -> "RIGHT";
            case Keys.KEY_LEFT -> "LEFT";
            case Keys.KEY_DOWN -> "DOWN";
            case Keys.KEY_UP -> "UP";
            case Keys.KEY_PAGE_UP -> "PAGE UP";
            case Keys.KEY_PAGE_DOWN -> "PAGE DOWN";
            case Keys.KEY_HOME -> "HOME";
            case Keys.KEY_END -> "END";
            case Keys.KEY_LEFT_SHIFT -> "LEFT SHIFT";
            case Keys.KEY_LEFT_CONTROL -> "LEFT CTRL";
            case Keys.KEY_LEFT_ALT -> "LEFT ALT";
            case Keys.KEY_RIGHT_SHIFT -> "RIGHT SHIFT";
            case Keys.KEY_RIGHT_CONTROL -> "RIGHT CTRL";
            case Keys.KEY_RIGHT_ALT -> "RIGHT ALT";
            case Keys.KEY_F1 -> "F1";
            case Keys.KEY_F2 -> "F2";
            case Keys.KEY_F3 -> "F3";
            case Keys.KEY_F4 -> "F4";
            case Keys.KEY_F5 -> "F5";
            case Keys.KEY_F6 -> "F6";
            case Keys.KEY_F7 -> "F7";
            case Keys.KEY_F8 -> "F8";
            case Keys.KEY_F9 -> "F9";
            case Keys.KEY_F10 -> "F10";
            case Keys.KEY_F11 -> "F11";
            case Keys.KEY_F12 -> "F12";
            default -> String.valueOf(key);
        };
    }

    private enum ConfigTab {
        MODULE("Module Configs", "module"),
        BIND("Bind Configs", "bind"),
        ONLINE("Online Configs", "online");

        private final String sidebarLabel;
        private final String emptyName;

        ConfigTab(String sidebarLabel, String emptyName) {
            this.sidebarLabel = sidebarLabel;
            this.emptyName = emptyName;
        }
    }

    private enum CommandTab {
        COMMAND("Command"),
        MULTIPLAY("Multiplay");

        private final String sidebarLabel;

        CommandTab(String sidebarLabel) {
            this.sidebarLabel = sidebarLabel;
        }
    }

    private enum TextFocus {
        NONE,
        SEARCH,
        SETTING,
        NUMBER,
        CONFIG_NAME,
        CONFIG_RENAME,
        COMMAND
    }

    private enum ControlType {
        CONFIG_NAME,
        CONFIG_CREATE,
        CONFIG_LOAD,
        CONFIG_RENAME,
        CONFIG_DELETE,
        COMMAND_INPUT,
        COMMAND_RUN,
        COMMAND_SUGGESTION,
        MULTIPLAY_DISCONNECT,
        PLAYER_KILL,
        PLAYER_TELEPORT,
        PLAYER_CRASH,
        SHOW_ON_ARRAY,
        MODULE_ENABLED,
        MODULE_KEY,
        BOOLEAN,
        LIST_VALUE,
        LIST_OPTION,
        FLOAT_INPUT,
        INT_INPUT,
        FLOAT_SLIDER,
        INT_SLIDER,
        INLINE_FLOAT_SLIDER,
        INLINE_INT_SLIDER,
        INLINE_COLOR_SLIDER,
        COLOR_SLIDER,
        COLOR_VALUE,
        TEXT_VALUE,
        KEY_VALUE
    }

    private record TabBounds(ModuleCategory category, ConfigTab configTab, CommandTab commandTab, Rect rect) {
        private boolean isConfig() {
            return configTab != null;
        }

        private boolean isCommand() {
            return commandTab != null;
        }
    }

    private record PlayerEntry(String name, int ping, boolean self) {
    }

    private record ModuleRowBounds(Module module, Rect rect) {
    }

    private record ModuleExpandBounds(Module module, Rect rect) {
    }

    private record ConfigEntry(String name, ConfigTab type, boolean current) {
    }

    private record ScrollState(float listScroll, float targetListScroll) {
    }

    private record ControlBounds(ControlType type, Rect rect, Object target, Object owner, int index) {
    }

    private record SliderDrag(ControlBounds control) {
    }

    private record ScrollbarDrag(float offsetY) {
    }

    private record PendingControlGlass(float x, float y, float width, float height, float radius, float alphaAtQueue, boolean dark, float brightness) {
    }

    private record WrapKey(String text, NVGFont font, float size, float maxWidth) {
    }

    private record TextFieldLayout(TextFocus focus, Object target, float textX, float fontSize, float maxWidth) {
    }

    private record Rect(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        float centerX() {
            return x + width / 2f;
        }

        float centerY() {
            return y + height / 2f;
        }

        Rect expand(float horizontal, float vertical) {
            return new Rect(x - horizontal, y - vertical, width + horizontal * 2f, height + vertical * 2f);
        }

        Rect contract(float horizontal, float vertical) {
            return new Rect(x + horizontal, y + vertical, Math.max(1f, width - horizontal * 2f), Math.max(1f, height - vertical * 2f));
        }

        Rect intersect(Rect other) {
            float left = Math.max(x, other.x);
            float top = Math.max(y, other.y);
            float right = Math.min(x + width, other.x + other.width);
            float bottom = Math.min(y + height, other.y + other.height);
            if (right <= left || bottom <= top)
                return null;
            return new Rect(left, top, right - left, bottom - top);
        }
    }
}
