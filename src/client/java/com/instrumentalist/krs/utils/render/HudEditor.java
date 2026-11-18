package com.instrumentalist.krs.utils.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.screen.HudEditorScreen;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Named HUD module layout, shared by the in-chat editor and the dedicated editor screen.
 * Offsets are stored relative to each module's normal anchor so existing layouts keep working,
 * and an optional per-module scale resizes the module around its top-left corner.
 */
public final class HudEditor {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "hud_layout.json";
    private static final float GRID_SPACING = 40f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3f;
    private static final float HANDLE_SIZE = 9f;
    private static final int STALE_FRAMES = 45;
    private static final float PANEL_ROW_HEIGHT = 18f;
    private static final float PANEL_CAPTION_HEIGHT = 16f;
    private static final List<PanelRow> PANEL_ROWS = new ArrayList<>();
    private static boolean panelRowsDirty = true;
    private static float panelRowsHeight;
    private static boolean arrayListPanelOpen;
    private static float arrayListScroll;
    private static boolean notificationPanelOpen;

    public enum Element {
        WATERMARK("Watermark"),
        TAB_GUI("Tab GUI"),
        SESSION("Session Info"),
        PLAYER_LIST("Players"),
        MINIMAP("Minimap"),
        MODULE_LIST("Module List"),
        TARGET_HUD("Target HUD"),
        NOTIFICATIONS("Notifications"),
        CHAT("Chat"),
        SOME_INFO("Info");

        private final String label;

        Element(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** What the pointer is currently over, used for cursor feedback. */
    public enum Pointer {
        NONE,
        MOVE,
        RESIZE
    }

    private record Bounds(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return px >= x && py >= y && px <= x + width && py <= y + height;
        }

        float centerX() {
            return x + width / 2f;
        }

        float centerY() {
            return y + height / 2f;
        }
    }

    private static final Map<Element, Float> OFFSET_X = new EnumMap<>(Element.class);
    private static final Map<Element, Float> OFFSET_Y = new EnumMap<>(Element.class);
    private static final Map<Element, Float> SCALE = new EnumMap<>(Element.class);
    private static final Map<Element, Bounds> BOUNDS = new EnumMap<>(Element.class);
    private static final Map<Element, Integer> LAST_SEEN_FRAME = new EnumMap<>(Element.class);
    private static final List<Element> DRAW_ORDER = new ArrayList<>();

    private static Element dragging;
    private static boolean resizing;
    private static float dragMouseX;
    private static float dragMouseY;
    private static float dragStartOffsetX;
    private static float dragStartOffsetY;
    private static float dragBaseX;
    private static float dragBaseY;
    private static float dragWidth;
    private static float dragHeight;
    private static float resizeStartScale;
    private static float resizePivotX;
    private static float resizePivotY;
    private static float resizeStartDistance;
    private static int frameCounter;
    private static boolean loaded;

    private HudEditor() {
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        clearOffsets();
        SCALE.clear();

        File file = layoutFile();
        if (!file.isFile()) return;

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            for (Element element : Element.values()) {
                JsonElement entry = root.get(element.name().toLowerCase());
                if (entry == null || !entry.isJsonObject()) continue;
                JsonObject object = entry.getAsJsonObject();
                if (object.has("x")) OFFSET_X.put(element, object.get("x").getAsFloat());
                if (object.has("y")) OFFSET_Y.put(element, object.get("y").getAsFloat());
                if (object.has("scale")) SCALE.put(element, clampScale(object.get("scale").getAsFloat()));
            }
        } catch (RuntimeException | IOException ignored) {
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Element element : Element.values()) {
            float x = offsetX(element);
            float y = offsetY(element);
            float scale = scale(element);
            if (Math.abs(x) < 0.01f && Math.abs(y) < 0.01f && Math.abs(scale - 1f) < 0.001f) continue;

            JsonObject entry = new JsonObject();
            entry.addProperty("x", x);
            entry.addProperty("y", y);
            if (Math.abs(scale - 1f) >= 0.001f) entry.addProperty("scale", scale);
            root.add(element.name().toLowerCase(), entry);
        }

        File file = layoutFile();
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();

        try {
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void reset() {
        clearOffsets();
        SCALE.clear();
        save();
    }

    public static void resetElement(Element element) {
        if (element == null) return;
        OFFSET_X.remove(element);
        OFFSET_Y.remove(element);
        SCALE.remove(element);
        save();
    }

    private static void clearOffsets() {
        OFFSET_X.clear();
        OFFSET_Y.clear();
    }

    private static File layoutFile() {
        return new File(new File(Minecraft.getInstance().gameDirectory, Client.configLocation), FILE_NAME);
    }

    public static float offsetX(Element element) {
        return OFFSET_X.getOrDefault(element, 0f);
    }

    public static float offsetY(Element element) {
        return OFFSET_Y.getOrDefault(element, 0f);
    }

    private static float scale(Element element) {
        return SCALE.getOrDefault(element, 1f);
    }

    /** True while the dedicated editor is open or chat is being used as the in-game HUD editor. */
    public static boolean isEditing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return false;
        Screen screen = minecraft.gui.screen();
        return screen instanceof HudEditorScreen || screen instanceof ChatScreen;
    }

    public static void beginFrame() {
        frameCounter++;
        pruneStaleBounds();
    }

    private static void pruneStaleBounds() {
        DRAW_ORDER.removeIf(element -> frameCounter - LAST_SEEN_FRAME.getOrDefault(element, frameCounter) > STALE_FRAMES);
        BOUNDS.keySet().removeIf(element -> !DRAW_ORDER.contains(element));
    }

    /**
     * Registers the module's current on-screen rectangle. Recorded on every HUD frame, not just while
     * editing, so the editor always has geometry to hit-test against. The rectangle is reported in the
     * module's unscaled layout space and stored scaled, matching how the module is drawn.
     */
    public static void register(Element element, float x, float y, float width, float height) {
        if (width <= 1f || height <= 1f) return;

        float scale = scale(element);
        BOUNDS.put(element, new Bounds(x, y, width * scale, height * scale));
        LAST_SEEN_FRAME.put(element, frameCounter);
        if (!DRAW_ORDER.contains(element)) DRAW_ORDER.add(element);
    }

    /** Applies the module's stored scale around its own top-left corner while {@code draw} runs. */
    public static void withScale(NVGU vg, Element element, Runnable draw) {
        Bounds bounds = BOUNDS.get(element);
        if (bounds == null) {
            draw.run();
            return;
        }
        withScaleAt(vg, element, bounds.x(), bounds.y(), draw);
    }

    /** Applies the module's stored scale around an explicit anchor, for modules drawn at several places. */
    public static void withScaleAt(NVGU vg, Element element, float anchorX, float anchorY, Runnable draw) {
        float scale = scale(element);
        if (Math.abs(scale - 1f) < 0.001f) {
            draw.run();
            return;
        }

        vg.save();
        try {
            vg.scale(anchorX, anchorY, scale);
            draw.run();
        } finally {
            vg.restore();
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isEditing()) return false;

        float x = NanoVGManager.toScaledMouseX(mouseX);
        float y = NanoVGManager.toScaledMouseY(mouseY);

        if (notificationPanelOpen) {
            if (button == Keys.MOUSE_BUTTON_RIGHT) {
                notificationPanelOpen = false;
                return true;
            }
            if (button == Keys.MOUSE_BUTTON_LEFT) return handleNotificationPanelClick(x, y);
            return true;
        }

        if (arrayListPanelOpen) {
            if (button == Keys.MOUSE_BUTTON_RIGHT) {
                arrayListPanelOpen = false;
                return true;
            }
            if (button == Keys.MOUSE_BUTTON_LEFT) return handlePanelClick(x, y);
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_RIGHT) {
            Bounds notifications = BOUNDS.get(Element.NOTIFICATIONS);
            if (notifications != null && notifications.contains(x, y)) {
                notificationPanelOpen = true;
                arrayListPanelOpen = false;
                return true;
            }

            Bounds list = BOUNDS.get(Element.MODULE_LIST);
            if (list != null && list.contains(x, y)) {
                arrayListPanelOpen = true;
                arrayListScroll = 0f;
                panelRowsDirty = true;
                notificationPanelOpen = false;
                return true;
            }
            return false;
        }

        if (button != Keys.MOUSE_BUTTON_LEFT) return false;

        for (int i = DRAW_ORDER.size() - 1; i >= 0; i--) {
            Element element = DRAW_ORDER.get(i);
            Bounds bounds = BOUNDS.get(element);
            if (bounds == null) continue;

            if (isOverHandle(bounds, x, y)) {
                beginResize(element, bounds, x, y);
                return true;
            }

            if (bounds.contains(x, y)) {
                beginMove(element, bounds, x, y);
                return true;
            }
        }

        return false;
    }

    private static void beginMove(Element element, Bounds bounds, float mouseX, float mouseY) {
        dragging = element;
        resizing = false;
        dragMouseX = mouseX;
        dragMouseY = mouseY;
        dragStartOffsetX = offsetX(element);
        dragStartOffsetY = offsetY(element);
        dragBaseX = bounds.x() - dragStartOffsetX;
        dragBaseY = bounds.y() - dragStartOffsetY;
        dragWidth = bounds.width();
        dragHeight = bounds.height();
    }

    private static void beginResize(Element element, Bounds bounds, float mouseX, float mouseY) {
        dragging = element;
        resizing = true;
        resizeStartScale = scale(element);
        resizePivotX = bounds.centerX();
        resizePivotY = bounds.centerY();
        resizeStartDistance = Math.max(6f, (float) Math.hypot(mouseX - resizePivotX, mouseY - resizePivotY));
    }

    /** The single drag entry point, driven from the editor's own render frame in {@link #queueOverlay()}. */
    private static void applyDrag(double mouseX, double mouseY) {
        float x = NanoVGManager.toScaledMouseX(mouseX);
        float y = NanoVGManager.toScaledMouseY(mouseY);

        if (resizing) {
            float distance = (float) Math.hypot(x - resizePivotX, y - resizePivotY);
            applyScale(dragging, clampScale(resizeStartScale * (distance / resizeStartDistance)));
            return;
        }

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();

        float nextX = Math.clamp(dragStartOffsetX + x - dragMouseX,
                -dragBaseX, Math.max(-dragBaseX, screenWidth - dragBaseX - dragWidth));
        float nextY = Math.clamp(dragStartOffsetY + y - dragMouseY,
                -dragBaseY, Math.max(-dragBaseY, screenHeight - dragBaseY - dragHeight));

        OFFSET_X.put(dragging, nextX);
        OFFSET_Y.put(dragging, nextY);

        Bounds bounds = BOUNDS.get(dragging);
        if (bounds != null)
            BOUNDS.put(dragging, new Bounds(nextX + dragBaseX, nextY + dragBaseY, bounds.width(), bounds.height()));
    }

    public static boolean mouseReleased() {
        if (dragging == null) return false;

        dragging = null;
        resizing = false;
        save();
        return true;
    }

    /** Reads the pointer position back after a drag so a missed release can never leave the editor stuck. */
    public static void abortDrag() {
        dragging = null;
        resizing = false;
        arrayListPanelOpen = false;
        notificationPanelOpen = false;
    }

    /** Scrolls the module picker while it is open. */
    public static boolean mouseScrolled(double vertical) {
        if (!isEditing()) return false;
        if (notificationPanelOpen) return true;
        if (!arrayListPanelOpen) return false;

        ensurePanelRows();
        arrayListScroll = Math.clamp(arrayListScroll - (float) vertical * PANEL_ROW_HEIGHT,
                0f, panelMaxScroll(panelLayout()));
        return true;
    }

    /** Closes the module picker if it is open, so Escape can back out one layer at a time. */
    public static boolean dismissPanel() {
        if (!arrayListPanelOpen && !notificationPanelOpen) return false;

        arrayListPanelOpen = false;
        notificationPanelOpen = false;
        return true;
    }

    private record Panel(float x, float y, float width, float height) {
        float contentTop() {
            return y + 30f;
        }

        float contentBottom() {
            return y + height - 18f;
        }

        float contentHeight() {
            return contentBottom() - contentTop();
        }

        boolean contains(float px, float py) {
            return px >= x && py >= y && px <= x + width && py <= y + height;
        }
    }

    /** One picker line: a category caption, or a module that can be toggled on the module list. */
    private record PanelRow(Module module, String caption, float height) {
    }

    /** Builds the grouped, sorted picker rows once per open instead of on every frame. */
    private static void ensurePanelRows() {
        if (!panelRowsDirty) return;
        panelRowsDirty = false;

        PANEL_ROWS.clear();
        for (ModuleCategory category : ModuleCategory.DISPLAY_ORDER) {
            List<Module> group = new ArrayList<>();
            for (Module module : ModuleManager.allModules)
                if (!module.hidden && module.moduleCategory == category) group.add(module);
            if (group.isEmpty()) continue;

            group.sort(Comparator.comparing(module -> module.moduleName, String.CASE_INSENSITIVE_ORDER));
            PANEL_ROWS.add(new PanelRow(null, category.label().toUpperCase(Locale.ROOT), PANEL_CAPTION_HEIGHT));
            for (Module module : group)
                PANEL_ROWS.add(new PanelRow(module, null, PANEL_ROW_HEIGHT));
        }

        panelRowsHeight = 0f;
        for (PanelRow row : PANEL_ROWS)
            panelRowsHeight += row.height();
    }

    private static Panel panelLayout() {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float width = Math.max(260f, Math.min(380f, screenWidth - 40f));
        float height = Math.max(160f, Math.min(420f, screenHeight - 60f));
        return new Panel((screenWidth - width) / 2f, (screenHeight - height) / 2f, width, height);
    }

    private static float panelMaxScroll(Panel panel) {
        return Math.max(0f, panelRowsHeight - panel.contentHeight());
    }

    private static boolean handlePanelClick(float x, float y) {
        Panel panel = panelLayout();
        ensurePanelRows();

        float rowLeft = panel.x() + 8f;
        float rowRight = panel.x() + panel.width() - 16f;
        boolean overRows = x >= rowLeft && x <= rowRight
                && y >= panel.contentTop() && y <= panel.contentBottom();

        if (overRows) {
            float top = panel.contentTop() - arrayListScroll;
            for (PanelRow row : PANEL_ROWS) {
                if (y >= top && y <= top + row.height()) {
                    if (row.module() != null) {
                        row.module().showOnArray = !row.module().showOnArray;
                        Interface.reloadSortedModules();
                    }
                    return true;
                }
                top += row.height();
            }
        }

        if (!panel.contains(x, y))
            arrayListPanelOpen = false;

        return true;
    }

    private static String bindLabel(int key) {
        if (key == Keys.KEY_UNKNOWN) return "-";
        if (Keys.isMouseKey(key)) return Keys.mouseKeyName(key);

        String name = Keys.printableKeyName(key);
        if (name != null) return name.toUpperCase(Locale.ROOT);

        return switch (key) {
            case Keys.KEY_SPACE -> "SPACE";
            case Keys.KEY_ESCAPE -> "ESC";
            case Keys.KEY_ENTER -> "ENTER";
            case Keys.KEY_TAB -> "TAB";
            case Keys.KEY_BACKSPACE -> "BACKSPACE";
            case Keys.KEY_INSERT -> "INSERT";
            case Keys.KEY_DELETE -> "DELETE";
            case Keys.KEY_UP -> "UP";
            case Keys.KEY_DOWN -> "DOWN";
            case Keys.KEY_LEFT -> "LEFT";
            case Keys.KEY_RIGHT -> "RIGHT";
            default -> "KEY " + key;
        };
    }

    private static void applyScale(Element element, float nextScale) {
        float current = scale(element);
        if (Math.abs(nextScale - current) < 0.0001f) return;

        Bounds bounds = BOUNDS.get(element);
        if (bounds != null) {
            float ratio = nextScale / current;
            BOUNDS.put(element, new Bounds(bounds.x(), bounds.y(),
                    Math.max(2f, bounds.width() * ratio), Math.max(2f, bounds.height() * ratio)));
        }
        SCALE.put(element, nextScale);
    }

    private static float clampScale(float value) {
        if (!Float.isFinite(value)) return 1f;
        return Math.clamp(value, MIN_SCALE, MAX_SCALE);
    }

    public static Pointer pointerAt(double mouseX, double mouseY) {
        if (!isEditing() || arrayListPanelOpen || notificationPanelOpen) return Pointer.NONE;

        float x = NanoVGManager.toScaledMouseX(mouseX);
        float y = NanoVGManager.toScaledMouseY(mouseY);

        for (int i = DRAW_ORDER.size() - 1; i >= 0; i--) {
            Bounds bounds = BOUNDS.get(DRAW_ORDER.get(i));
            if (bounds == null) continue;
            if (isOverHandle(bounds, x, y)) return Pointer.RESIZE;
            if (bounds.contains(x, y)) return Pointer.MOVE;
        }

        return Pointer.NONE;
    }

    private static boolean isOverHandle(Bounds bounds, float px, float py) {
        float half = HANDLE_SIZE / 2f;
        float left = bounds.x();
        float right = bounds.x() + bounds.width();
        float top = bounds.y();
        float bottom = bounds.y() + bounds.height();
        float middleX = bounds.centerX();
        float middleY = bounds.centerY();

        return near(px, py, left, top, half)
                || near(px, py, middleX, top, half)
                || near(px, py, right, top, half)
                || near(px, py, left, middleY, half)
                || near(px, py, right, middleY, half)
                || near(px, py, left, bottom, half)
                || near(px, py, middleX, bottom, half)
                || near(px, py, right, bottom, half);
    }

    private static boolean near(float px, float py, float cx, float cy, float half) {
        return Math.abs(px - cx) <= half && Math.abs(py - cy) <= half;
    }

    /**
     * Queues this frame's editor chrome and advances any in-progress drag. The editing screens call this
     * from their own render pass, so the editor never depends on a HUD frame arriving.
     */
    public static void queueOverlay() {
        if (!isEditing()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (dragging != null && minecraft != null && minecraft.mouseHandler != null)
            applyDrag(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                    minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));

        if (Client.nanoVgManager != null)
            Client.nanoVgManager.load(HudEditor::renderOverlay);
    }

    private static void renderOverlay(NVGU vg) {
        if (!isEditing()) return;

        Minecraft minecraft = Minecraft.getInstance();
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();

        float mouseX = NanoVGManager.toScaledMouseX(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()));
        float mouseY = NanoVGManager.toScaledMouseY(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));

        renderGrid(vg, screenWidth, screenHeight);

        for (Element element : DRAW_ORDER) {
            Bounds bounds = BOUNDS.get(element);
            if (bounds == null) continue;

            boolean active = element == dragging || bounds.contains(mouseX, mouseY);
            Color edge = active ? NanoVGTheme.accent(235) : new Color(255, 255, 255, 62);
            vg.roundedRectangleBorder(bounds.x() - 1f, bounds.y() - 1f,
                    bounds.width() + 2f, bounds.height() + 2f, 0f, 1f, edge, Border.INSIDE);

            renderHandles(vg, bounds, edge, active);

            String label = element.label();
            float scale = scale(element);
            if (Math.abs(scale - 1f) >= 0.005f)
                label = label + "  " + Math.round(scale * 100f) + "%";

            float labelWidth = NVGFonts.INTER.getWidth(label, 7f) + 10f;
            float labelY = bounds.y() >= 13f ? bounds.y() - 11f : bounds.y() + 1f;
            vg.rectangle(bounds.x(), labelY, labelWidth, 10f, new Color(5, 3, 8, 225));
            vg.roundedRectangleBorder(bounds.x(), labelY, labelWidth, 10f, 0f, 1f,
                    active ? edge : new Color(255, 255, 255, 35), Border.INSIDE);
            NVGFonts.INTER.drawText(label, bounds.x() + 5f, labelY + 1.5f, 7f,
                    active ? NanoVGTheme.text(245) : NanoVGTheme.muted(210), Alignment.LEFT_TOP, true);
        }

        renderBanner(vg, screenWidth);

        if (!DRAW_ORDER.isEmpty()) {
            if (notificationPanelOpen)
                renderNotificationPanel(vg, screenWidth, screenHeight, mouseX, mouseY);
            else if (arrayListPanelOpen)
                renderArrayListPanel(vg, screenWidth, screenHeight, mouseX, mouseY);
            return;
        }

        renderNoGeometryHint(vg, minecraft, screenWidth, screenHeight);
    }

    /**
     * Explains the empty editor when no HUD module has reported geometry (no world, HUD hidden, or every
     * module disabled) so the screen does not silently ignore input.
     */
    private static void renderNoGeometryHint(NVGU vg, Minecraft minecraft, float screenWidth, float screenHeight) {
        String message = minecraft.player == null || minecraft.level == null
                ? "Join a world to edit the HUD"
                : "Nothing to edit: enable HUD elements in Interface";
        float width = NVGFonts.INTER.getWidth(message, 10f) + 24f;
        float x = (screenWidth - width) / 2f;
        float y = screenHeight / 2f - 12f;
        vg.rectangle(x, y, width, 24f, new Color(6, 5, 9, 225));
        vg.roundedRectangleBorder(x, y, width, 24f, 0f, 1f, new Color(255, 255, 255, 45), Border.INSIDE);
        NVGFonts.INTER.drawText(message, screenWidth / 2f, y + 8f, 10f,
                NanoVGTheme.dim(225), Alignment.CENTER_TOP, true);
    }

    private static void renderGrid(NVGU vg, float screenWidth, float screenHeight) {
        Color grid = new Color(255, 255, 255, 9);
        for (float x = GRID_SPACING; x < screenWidth; x += GRID_SPACING)
            vg.rectangle(x, 0f, 1f, screenHeight, grid);
        for (float y = GRID_SPACING; y < screenHeight; y += GRID_SPACING)
            vg.rectangle(0f, y, screenWidth, 1f, grid);
    }

    private static void renderHandles(NVGU vg, Bounds bounds, Color edge, boolean active) {
        float size = active ? 5f : 4f;
        float half = size / 2f;
        float left = bounds.x();
        float right = bounds.x() + bounds.width();
        float top = bounds.y();
        float bottom = bounds.y() + bounds.height();
        float middleX = bounds.centerX();
        float middleY = bounds.centerY();

        renderHandle(vg, left, top, half, edge);
        renderHandle(vg, middleX, top, half, edge);
        renderHandle(vg, right, top, half, edge);
        renderHandle(vg, left, middleY, half, edge);
        renderHandle(vg, right, middleY, half, edge);
        renderHandle(vg, left, bottom, half, edge);
        renderHandle(vg, middleX, bottom, half, edge);
        renderHandle(vg, right, bottom, half, edge);
    }

    private static void renderHandle(NVGU vg, float centerX, float centerY, float half, Color color) {
        vg.rectangle(centerX - half, centerY - half, half * 2f, half * 2f, color);
    }

    private static final int NOTIFICATION_PANEL_ROWS = 12;

    private static boolean handleNotificationPanelClick(float x, float y) {
        Panel panel = panelLayout();
        if (!panel.contains(x, y)) {
            notificationPanelOpen = false;
            return true;
        }

        float rowLeft = panel.x() + 8f;
        float rowWidth = panel.width() - 16f;
        float contentTop = panel.contentTop();
        if (x < rowLeft || x > rowLeft + rowWidth
                || y < contentTop || y > panel.contentBottom())
            return true;

        int row = (int) ((y - contentTop) / PANEL_ROW_HEIGHT);
        if (row < 0 || row >= NOTIFICATION_PANEL_ROWS)
            return true;

        boolean increase = x >= rowLeft + rowWidth * 0.65f;
        switch (row) {
            case 0 -> Interface.notifications.set(!Interface.notifications.get());
            case 1 -> Interface.notificationStack.nextValue();
            case 2 -> Interface.notificationCombine.set(!Interface.notificationCombine.get());
            case 3 -> Interface.notificationCombineWindow.set(Math.clamp(
                    Interface.notificationCombineWindow.get() + (increase ? 250 : -250), 250, 5000));
            case 4 -> Interface.notificationCorner.nextValue();
            case 5 -> Interface.notificationWidth.set(Math.clamp(
                    Interface.notificationWidth.get() + (increase ? 10f : -10f), 120f, 280f));
            case 6 -> Interface.notificationDuration.set(Math.clamp(
                    Interface.notificationDuration.get() + (increase ? 500 : -500), 2000, 12000));
            case 7 -> Interface.notificationMax.set(Math.clamp(
                    Interface.notificationMax.get() + (increase ? 1 : -1), 1, 10));
            case 8 -> Interface.notificationProgress.set(!Interface.notificationProgress.get());
            case 9 -> Interface.notificationIcons.set(!Interface.notificationIcons.get());
            case 10 -> Interface.notificationModuleToggle.set(!Interface.notificationModuleToggle.get());
            case 11 -> resetElement(Element.NOTIFICATIONS);
            default -> {
            }
        }
        return true;
    }

    private static String notificationRowLabel(int row) {
        return switch (row) {
            case 0 -> "Notifications";
            case 1 -> "Stack Direction";
            case 2 -> "Combine Similar";
            case 3 -> "Combine Window";
            case 4 -> "Corner";
            case 5 -> "Width";
            case 6 -> "Duration";
            case 7 -> "Max Visible";
            case 8 -> "Progress Bar";
            case 9 -> "Icons";
            case 10 -> "Module Toggles";
            case 11 -> "Reset Position";
            default -> "";
        };
    }

    private static String notificationRowValue(int row) {
        return switch (row) {
            case 0 -> Interface.notifications.get() ? "ON" : "OFF";
            case 1 -> Interface.notificationStack.get();
            case 2 -> Interface.notificationCombine.get() ? "ON" : "OFF";
            case 3 -> Interface.notificationCombineWindow.get() + "ms";
            case 4 -> Interface.notificationCorner.get();
            case 5 -> Math.round(Interface.notificationWidth.get()) + "px";
            case 6 -> String.format(Locale.ROOT, "%.1fs", Interface.notificationDuration.get() / 1000f);
            case 7 -> Integer.toString(Interface.notificationMax.get());
            case 8 -> Interface.notificationProgress.get() ? "ON" : "OFF";
            case 9 -> Interface.notificationIcons.get() ? "ON" : "OFF";
            case 10 -> Interface.notificationModuleToggle.get() ? "ON" : "OFF";
            case 11 -> "Click";
            default -> "";
        };
    }

    private static void renderNotificationPanel(NVGU vg, float screenWidth, float screenHeight,
                                                float mouseX, float mouseY) {
        Panel panel = panelLayout();
        vg.rectangle(0f, 0f, screenWidth, screenHeight, new Color(0, 0, 0, 110));
        vg.rectangle(panel.x(), panel.y(), panel.width(), panel.height(), new Color(8, 6, 12, 246));

        float rowLeft = panel.x() + 8f;
        float rowWidth = panel.width() - 16f;
        float contentTop = panel.contentTop();
        for (int row = 0; row < NOTIFICATION_PANEL_ROWS; row++) {
            float top = contentTop + row * PANEL_ROW_HEIGHT;
            boolean hovered = mouseX >= rowLeft && mouseX <= rowLeft + rowWidth
                    && mouseY >= top && mouseY <= top + PANEL_ROW_HEIGHT;
            if (hovered)
                vg.rectangle(rowLeft, top, rowWidth, PANEL_ROW_HEIGHT - 1f, new Color(255, 255, 255, 16));

            String label = notificationRowLabel(row);
            String value = notificationRowValue(row);
            boolean enabled = switch (row) {
                case 0 -> Interface.notifications.get();
                case 2 -> Interface.notificationCombine.get();
                case 8 -> Interface.notificationProgress.get();
                case 9 -> Interface.notificationIcons.get();
                case 10 -> Interface.notificationModuleToggle.get();
                default -> true;
            };
            Color labelColor = row == 11 ? NanoVGTheme.accent(220)
                    : hovered ? NanoVGTheme.text(245) : NanoVGTheme.text(220);
            NVGFonts.INTER.drawText(label, rowLeft + 5f, top + 4.5f, 8f,
                    labelColor, Alignment.LEFT_TOP, true);
            NVGFonts.INTER.drawText(value, rowLeft + rowWidth - 5f, top + 4.5f, 8f,
                    enabled ? NanoVGTheme.muted(230) : NanoVGTheme.dim(190),
                    Alignment.RIGHT_TOP, true);
        }

        vg.rectangle(panel.x(), panel.y(), panel.width(), contentTop - panel.y(), new Color(8, 6, 12, 246));
        vg.rectangle(panel.x() + 1f, contentTop - 4f, panel.width() - 2f, 1f, new Color(255, 255, 255, 22));
        NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, "NOTIFICATIONS", panel.x() + 12f, panel.y() + 9f,
                10f, 0.9f, NanoVGTheme.text(245), true);
        NVGFonts.INTER.drawText("Right click closes", panel.x() + panel.width() - 12f,
                panel.y() + 10f, 7.5f, NanoVGTheme.dim(215), Alignment.RIGHT_TOP, true);

        vg.rectangle(panel.x(), panel.contentBottom(), panel.width(),
                panel.y() + panel.height() - panel.contentBottom(), new Color(8, 6, 12, 246));
        NVGFonts.INTER.drawText("Left click toggles  ·  side click adjusts values",
                panel.x() + panel.width() / 2f, panel.y() + panel.height() - 14f, 7f,
                NanoVGTheme.dim(195), Alignment.CENTER_TOP, true);

        vg.roundedRectangleBorder(panel.x(), panel.y(), panel.width(), panel.height(), 0f, 1f,
                new Color(214, 107, 232, 140), Border.INSIDE);
    }

    private static void renderArrayListPanel(NVGU vg, float screenWidth, float screenHeight,
                                             float mouseX, float mouseY) {
        Panel panel = panelLayout();
        ensurePanelRows();
        arrayListScroll = Math.clamp(arrayListScroll, 0f, panelMaxScroll(panel));

        int shown = 0;
        int total = 0;
        for (PanelRow row : PANEL_ROWS) {
            if (row.module() == null) continue;
            total++;
            if (row.module().showOnArray) shown++;
        }

        vg.rectangle(0f, 0f, screenWidth, screenHeight, new Color(0, 0, 0, 110));
        vg.rectangle(panel.x(), panel.y(), panel.width(), panel.height(), new Color(8, 6, 12, 246));

        float rowLeft = panel.x() + 8f;
        float rowWidth = panel.width() - 16f;
        float top = panel.contentTop() - arrayListScroll;
        for (PanelRow row : PANEL_ROWS) {
            // Partly scrolled rows still draw; the header and footer masks below trim their overflow.
            boolean visible = top + row.height() > panel.contentTop() && top < panel.contentBottom();
            if (visible && row.caption() != null) {
                NVGFonts.INTER_MEDIUM.drawText(row.caption(), rowLeft + 3f, top + 4f, 7f,
                        NanoVGTheme.accent(200), Alignment.LEFT_TOP, true);
            } else if (visible) {
                Module module = row.module();
                boolean onList = module.showOnArray;
                boolean hovered = mouseX >= rowLeft && mouseX <= rowLeft + rowWidth
                        && mouseY >= top && mouseY <= top + row.height();

                if (hovered)
                    vg.rectangle(rowLeft, top, rowWidth, row.height() - 1f, new Color(255, 255, 255, 16));
                else if (onList)
                    vg.rectangle(rowLeft, top, rowWidth, row.height() - 1f, NanoVGTheme.accent(16));

                float checkX = rowLeft + 5f;
                float checkY = top + (row.height() - 10f) / 2f;
                vg.roundedRectangleBorder(checkX, checkY, 10f, 10f, 2f, 1f,
                        onList ? NanoVGTheme.accent(225) : new Color(255, 255, 255, 70), Border.INSIDE);
                if (onList)
                    vg.rectangle(checkX + 3f, checkY + 3f, 4f, 4f, NanoVGTheme.accent(240));

                String bind = bindLabel(module.key);
                if (!"-".equals(bind))
                    NVGFonts.INTER.drawText(bind, rowLeft + rowWidth - NVGFonts.INTER.getWidth(bind, 7f) - 6f,
                            top + 5.5f, 7f, NanoVGTheme.muted(190), Alignment.LEFT_TOP, true);

                NVGFonts.INTER.drawText(module.moduleName, rowLeft + 22f, top + 4.5f, 8f,
                        module.tempEnabled ? NanoVGTheme.text(240) : NanoVGTheme.muted(190),
                        Alignment.LEFT_TOP, true);
            }

            top += row.height();
        }

        // Header and footer go on last so partly scrolled rows never paint over them.
        vg.rectangle(panel.x(), panel.y(), panel.width(), panel.contentTop() - panel.y(), new Color(8, 6, 12, 246));
        vg.rectangle(panel.x() + 1f, panel.contentTop() - 4f, panel.width() - 2f, 1f, new Color(255, 255, 255, 22));
        NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, "MODULE LIST", panel.x() + 12f, panel.y() + 9f,
                10f, 0.9f, NanoVGTheme.text(245), true);
        NVGFonts.INTER.drawText(shown + " / " + total + " shown", panel.x() + panel.width() - 12f,
                panel.y() + 10f, 7.5f, NanoVGTheme.dim(215), Alignment.RIGHT_TOP, true);

        vg.rectangle(panel.x(), panel.contentBottom(), panel.width(),
                panel.y() + panel.height() - panel.contentBottom(), new Color(8, 6, 12, 246));
        NVGFonts.INTER.drawText("Left click toggles  ·  Right click or Esc closes",
                panel.x() + panel.width() / 2f, panel.y() + panel.height() - 14f, 7f,
                NanoVGTheme.dim(195), Alignment.CENTER_TOP, true);

        vg.roundedRectangleBorder(panel.x(), panel.y(), panel.width(), panel.height(), 0f, 1f,
                new Color(214, 107, 232, 140), Border.INSIDE);

        float maxScroll = panelMaxScroll(panel);
        if (maxScroll > 0f) {
            float trackX = panel.x() + panel.width() - 6f;
            float trackHeight = panel.contentHeight();
            float thumbHeight = Math.max(24f, trackHeight * (trackHeight / panelRowsHeight));
            float thumbTop = panel.contentTop() + (trackHeight - thumbHeight) * (arrayListScroll / maxScroll);
            vg.rectangle(trackX, panel.contentTop(), 2f, trackHeight, new Color(255, 255, 255, 20));
            vg.rectangle(trackX, thumbTop, 2f, thumbHeight, NanoVGTheme.accent(170));
        }
    }

    private static void renderBanner(NVGU vg, float screenWidth) {
        float bannerWidth = Math.min(340f, screenWidth - 20f);
        float bannerHeight = 30f;
        float bannerX = (screenWidth - bannerWidth) / 2f;
        float bannerY = 8f;
        vg.rectangle(bannerX, bannerY, bannerWidth, bannerHeight, new Color(6, 5, 9, 225));
        vg.roundedRectangleBorder(bannerX, bannerY, bannerWidth, bannerHeight, 0f, 1f,
                new Color(214, 107, 232, 120), Border.INSIDE);
        NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, "HUD EDITOR", bannerX + 10f,
                bannerY + 6f, 10f, 0.9f, NanoVGTheme.text(245), true);
        boolean inChat = Minecraft.getInstance().gui.screen() instanceof ChatScreen;
        String hint = inChat
                ? "Drag / resize  ·  Right click HUD  ·  Esc done"
                : "Drag / resize  ·  Right click HUD  ·  R reset  ·  Esc done";
        NVGFonts.INTER.drawText(hint, bannerX + bannerWidth - 10f, bannerY + 8f, 7.5f,
                NanoVGTheme.dim(220), Alignment.RIGHT_TOP, true);
    }
}
