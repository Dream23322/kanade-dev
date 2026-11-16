package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.audio.Mp3MusicPlayer;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.realmsclient.RealmsMainScreen;
import com.instrumentalist.krs.utils.input.Keys;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.NVGFont;

import java.awt.*;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

public class CustomTitleScreen extends Screen implements IMinecraft {

    private static final float LAYOUT = 1.35f;
    private static long cachedTimeSecond = -1L;
    private static String cachedTime = "";
    private static final Mp3MusicPlayer MAIN_MENU_MUSIC = new Mp3MusicPlayer("assets/krs/musics/mainmenu.mp3");
    private static final float STARTUP_LOAD_BAR_FADE_SECONDS = 0.25f;
    private static float startupLoadBarAlpha;
    private static long startupLoadBarFadeNanos;
    private static final String RELEASES_URL = "https://github.com/Aspw-w/Krs/releases";
    private static final Color TOP_BAR_BUTTON_HOVER = new Color(28, 24, 38, 205);

    private boolean musicVolumeSliderDragging;
    private boolean musicVolumeDirty;
    private float musicSliderGrab;
    private long lastMusicSliderNanos;

    private final MenuButton singlePlayerButton = new MenuButton("Single Player", MaterialIcon.HOME);
    private final MenuButton multiPlayerButton = new MenuButton("Multi Player", MaterialIcon.PUBLIC);
    private final MenuButton realmsButton = new MenuButton("Realms", MaterialIcon.LAN);
    private final MenuButton friendsButton = new MenuButton("Friends", MaterialIcon.PERSON);
    private final MenuButton optionsButton = new MenuButton("Options", MaterialIcon.SETTING);
    private final MenuButton updateAvailableButton = new MenuButton("Update", MaterialIcon.RELOAD, true);
    private final MenuButton exitButton = new MenuButton("Exit", MaterialIcon.CLOSE);
    private final List<MenuButton> menuButtons = List.of(
            singlePlayerButton,
            multiPlayerButton,
            realmsButton,
            friendsButton,
            optionsButton,
            exitButton
    );
    private final List<MenuButton> outdatedClientMenuButtons = List.of(
            singlePlayerButton,
            multiPlayerButton,
            realmsButton,
            friendsButton,
            optionsButton,
            updateAvailableButton,
            exitButton
    );

    private float menuX;
    private float menuY;
    private float menuWidth;
    private float menuHeight;

    public static float bgOffsetX = 0;
    public static float bgOffsetY = 0;
    private static float bgParallaxScreenWidth = -1f;
    private static float bgParallaxScreenHeight = -1f;
    private static boolean bgParallaxHoldCenter;
    private static float bgParallaxHoldMouseX;
    private static float bgParallaxHoldMouseY;

    public static void stepBackgroundParallax(float targetX, float targetY, float amount) {
        if (!Float.isFinite(targetX) || !Float.isFinite(targetY) || !Float.isFinite(amount))
            return;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        if (!(screenWidth > 0f) || !(screenHeight > 0f))
            return;

        if (screenWidth != bgParallaxScreenWidth || screenHeight != bgParallaxScreenHeight
                || !Float.isFinite(bgOffsetX) || !Float.isFinite(bgOffsetY)) {
            bgParallaxScreenWidth = screenWidth;
            bgParallaxScreenHeight = screenHeight;
            bgOffsetX = screenWidth / 2f;
            bgOffsetY = screenHeight / 2f;
            bgParallaxHoldCenter = true;
            bgParallaxHoldMouseX = targetX;
            bgParallaxHoldMouseY = targetY;
            return;
        }

        if (bgParallaxHoldCenter) {
            if (Math.abs(targetX - bgParallaxHoldMouseX) < 0.5f && Math.abs(targetY - bgParallaxHoldMouseY) < 0.5f) {
                targetX = screenWidth / 2f;
                targetY = screenHeight / 2f;
            } else {
                bgParallaxHoldCenter = false;
            }
        }

        bgOffsetX += (targetX - bgOffsetX) * amount;
        bgOffsetY += (targetY - bgOffsetY) * amount;
    }

    public CustomTitleScreen(Component title) {
        super(title);
    }

    public static void stopMainMenuMusic() {
        int frame = MAIN_MENU_MUSIC.stopAndGetFramePosition();
        if (Client.configManager != null)
            Client.configManager.setMainMenuMusicFrame(frame, true);
    }

    public static void resetBackgroundState() {
        bgParallaxScreenWidth = -1f;
        bgParallaxScreenHeight = -1f;
        bgParallaxHoldCenter = false;
        mc.mouseHandler.resyncMousePosition();
        mc.mouseHandler.setIgnoreFirstMove();
        Shader2DRenderer.INSTANCE.invalidateBackdrop();
    }

    @Override
    public void added() {
        super.added();
        resetBackgroundState();
        syncMainMenuMusicVolume();
        MAIN_MENU_MUSIC.playLoop();
    }

    @Override
    protected void init() {
        super.init();
        syncMainMenuMusicVolume();
        MAIN_MENU_MUSIC.playLoop();
    }

    @Override
    public void removed() {
        saveMainMenuMusicVolumeIfDirty();
        stopMainMenuMusic();
        super.removed();
    }

    private void syncMainMenuMusicVolume() {
        if (Client.configManager != null) {
            MAIN_MENU_MUSIC.setVolume(Client.configManager.getMainMenuMusicVolume());
            MAIN_MENU_MUSIC.setStartFrame(Client.configManager.getMainMenuMusicFrame());
        }
    }

    private boolean isMinecraftMouseBlocked() {
        return GuiInputBlocker.shouldBlockMinecraftMouse();
    }

    private boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        if (isMinecraftMouseBlocked()) return false;

        float scaledMouseX = NanoVGManager.toScaledMouseX(mouseX);
        float scaledMouseY = NanoVGManager.toScaledMouseY(mouseY);
        return scaledMouseX >= x && scaledMouseX - width <= x && scaledMouseY >= y && scaledMouseY - height <= y;
    }

    private static float layoutScale() {
        return NanoVGClickGuiScreen.getLayoutScale();
    }

    private float u(float value) {
        return value * LAYOUT * layoutScale();
    }

    private float font(float size) {
        return Math.max(1f, Math.round(size * LAYOUT * layoutScale()));
    }

    private float topBarY() {
        return u(8f);
    }

    private float iconButtonSize() {
        return u(18f);
    }

    private float closeButtonX(float screenWidth) {
        return screenWidth - u(14f) - iconButtonSize();
    }

    private float settingsButtonX(float screenWidth) {
        return closeButtonX(screenWidth) - iconButtonSize() - u(2f);
    }

    private float musicPanelWidth() {
        return u(190f);
    }

    private float musicPanelHeight() {
        return u(18f);
    }

    private float musicPanelX(float screenWidth) {
        return (screenWidth - musicPanelWidth()) / 2f;
    }

    private float musicPanelY() {
        return topBarY();
    }

    private float musicTrackX(float screenWidth) {
        return musicPanelX(screenWidth) + u(42f);
    }

    private float musicTrackWidth() {
        return musicPanelWidth() - u(42f) - u(34f);
    }

    private float musicTrackY() {
        return musicPanelY() + musicPanelHeight() / 2f - u(2.5f);
    }

    private float musicTrackHeight() {
        return u(5f);
    }

    private float menuRowHeight() {
        return u(24f);
    }

    private float menuRowGap() {
        return u(2f);
    }

    private float menuHeaderHeight() {
        return u(22f);
    }

    private float menuPadding() {
        return u(6f);
    }

    private void layoutMenu(List<MenuButton> buttons, float screenWidth, float screenHeight) {
        menuWidth = Math.max(u(160f), Math.min(u(232f), screenWidth - u(32f)));

        float rowHeight = menuRowHeight();
        float rowGap = menuRowGap();
        float headerHeight = menuHeaderHeight();
        float padding = menuPadding();
        int count = buttons.size();

        menuHeight = headerHeight + u(4f) + count * rowHeight + Math.max(0, count - 1) * rowGap + padding;
        menuX = (screenWidth - menuWidth) / 2f;

        float centeredY = (screenHeight - menuHeight) / 2f + u(4f);
        float topLimit = u(40f);
        float bottomLimit = screenHeight - menuHeight - u(24f);
        menuY = topLimit <= bottomLimit ? Math.clamp(centeredY, topLimit, bottomLimit) : centeredY;

        float rowX = menuX + padding;
        float rowWidth = menuWidth - padding * 2f;
        float rowY = menuY + headerHeight + u(4f);
        for (MenuButton button : buttons) {
            button.x = rowX;
            button.y = rowY;
            button.width = rowWidth;
            button.height = rowHeight;
            rowY += rowHeight + rowGap;
        }
    }

    private void updateMenuButton(MenuButton button, double mouseX, double mouseY) {
        button.hovered = isHovered(mouseX, mouseY, button.x, button.y, button.width, button.height);
        float target = button.hovered ? 1f : 0f;
        button.hover += (target - button.hover) * 0.22f;
    }

    private void renderTopBar(NVGU vg, float screenWidth, double mouseX, double mouseY) {
        float barY = topBarY();
        float wordSize = u(15f);
        float wordWidth = NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, "kanade", u(14f), barY,
                wordSize, u(1.1f), NanoVGTheme.text(245), true);

        String version = "v" + Client.clientVersion;
        float versionX = u(14f) + wordWidth + u(7f);
        float versionWidth = NVGFonts.INTER.getWidth(version, font(8f));
        NVGFonts.INTER.drawText(version, versionX, barY + u(6f), font(8f),
                NanoVGTheme.dim(205), Alignment.LEFT_TOP, true);

        if (screenWidth >= u(640f)) {
            float chipX = versionX + versionWidth + u(10f);
            NVGFonts.ICON.drawText(MaterialIcon.PERSON, chipX, barY + u(4.5f), font(9f),
                    NanoVGTheme.dim(185), Alignment.LEFT_TOP, true);
            NVGFonts.INTER.drawText(mc.getUser().getName(), chipX + u(11f), barY + u(6f), font(8f),
                    NanoVGTheme.dim(210), Alignment.LEFT_TOP, true);
        }

        float settingsX = settingsButtonX(screenWidth);
        boolean settingsHovered = isHovered(mouseX, mouseY, settingsX, barY, iconButtonSize(), iconButtonSize());
        renderTopBarButton(vg, settingsX, barY, MaterialIcon.SETTING, false, settingsHovered);

        float closeX = closeButtonX(screenWidth);
        boolean closeHovered = isHovered(mouseX, mouseY, closeX, barY, iconButtonSize(), iconButtonSize());
        renderTopBarButton(vg, closeX, barY, MaterialIcon.CLOSE, true, closeHovered);

        String time = getCurrentTime();
        float clockWidth = NVGFonts.INTER.getWidth(time, font(11f));
        NVGFonts.INTER.drawText(time, settingsX - u(10f) - clockWidth, barY + u(2f), font(11f),
                NanoVGTheme.muted(210), Alignment.LEFT_TOP, true);
    }

    private void renderTopBarButton(NVGU vg, float x, float y, String icon, boolean danger, boolean hovered) {
        float size = iconButtonSize();
        Color background;
        if (hovered)
            background = danger ? NanoVGTheme.scaledAlpha(NanoVGTheme.DANGER, 0.45f) : TOP_BAR_BUTTON_HOVER;
        else
            background = NanoVGTheme.scaledAlpha(NanoVGTheme.HUD_BACKGROUND, 0.55f);

        vg.rectangle(x, y, size, size, background);
        Color iconColor = danger && hovered ? NanoVGTheme.danger(245)
                : hovered ? NanoVGTheme.text(235) : NanoVGTheme.dim(200);
        NVGFonts.ICON.drawText(icon, x + size / 2f, y + size / 2f - u(1f), font(10f),
                iconColor, Alignment.CENTER_MIDDLE, true);
    }

    private void renderMusicControl(NVGU vg, float screenWidth, double mouseX, double mouseY) {
        float panelX = musicPanelX(screenWidth);
        float panelY = musicPanelY();
        boolean hovered = musicVolumeSliderDragging || isMusicSliderHovered(mouseX, mouseY);

        NanoVGTheme.renderCompact(vg, panelX, panelY, musicPanelWidth(), musicPanelHeight(),
                NanoVGTheme.RADIUS_LABEL, 1f);

        NVGFonts.INTER.drawText("MUSIC", panelX + u(7f), panelY + u(5.5f), font(8f),
                hovered ? NanoVGTheme.text(235) : NanoVGTheme.dim(200), Alignment.LEFT_TOP, true);

        float volume = Math.clamp(getMainMenuMusicVolume(), 0f, 1f);
        float grab = updateMusicSliderGrab();
        float trackX = musicTrackX(screenWidth);
        float trackY = musicTrackY();
        float trackWidth = musicTrackWidth();
        float trackHeight = musicTrackHeight();

        vg.rectangle(trackX, trackY, trackWidth, trackHeight, NanoVGTheme.CONTROL_TRACK);
        if (volume > 0.001f)
            vg.rectangle(trackX, trackY, trackWidth * volume, trackHeight, NanoVGTheme.sliderFill(235));

        float knobWidth = u(4f) * (1f + 0.5f * grab);
        float knobHeight = trackHeight + u(4f) * (1f - 0.35f * grab);
        float knobX = trackX + trackWidth * volume - knobWidth / 2f;
        float knobY = trackY + trackHeight / 2f - knobHeight / 2f;
        vg.rectangle(knobX, knobY, knobWidth, knobHeight, NanoVGTheme.KNOB_FILL);

        String percent = volume <= 0.001f ? "OFF" : Math.round(volume * 100f) + "%";
        float percentWidth = NVGFonts.INTER.getWidth(percent, font(8f));
        float percentX = panelX + musicPanelWidth() - u(7f) - percentWidth;
        NVGFonts.INTER.drawText(percent, percentX, panelY + u(5.5f), font(8f),
                hovered ? NanoVGTheme.text(230) : NanoVGTheme.muted(215), Alignment.LEFT_TOP, true);
    }

    private void renderMenuPanel(NVGU vg, List<MenuButton> buttons) {
        NanoVGTheme.renderPanel(vg, menuX, menuY, menuWidth, menuHeight, NanoVGTheme.RADIUS_MENU, 1f);

        float headerHeight = menuHeaderHeight();
        NanoVGTheme.drawTrackedText(NVGFonts.INTER, "MAIN MENU", menuX + u(7f), menuY + u(7f),
                font(8f), u(1.1f), NanoVGTheme.dim(190), true);

        String version = "v" + Client.clientVersion;
        float versionWidth = NVGFonts.INTER.getWidth(version, font(8f));
        NVGFonts.INTER.drawText(version, menuX + menuWidth - u(7f) - versionWidth, menuY + u(7f),
                font(8f), NanoVGTheme.dim(190), Alignment.LEFT_TOP, true);

        vg.rectangle(menuX, menuY + headerHeight, menuWidth, 1f, NanoVGTheme.PANEL_EDGE);

        for (MenuButton button : buttons)
            renderMenuRow(vg, button);
    }

    private void renderMenuRow(NVGU vg, MenuButton state) {
        float hover = state.hover;

        if (state.alert)
            vg.rectangle(state.x, state.y, state.width, state.height,
                    NanoVGTheme.scaledAlpha(NanoVGTheme.ACCENT, 0.22f));
        else if (hover > 0.01f)
            vg.rectangle(state.x, state.y, state.width, state.height,
                    new Color(28, 24, 38, Math.clamp(Math.round(205f * hover), 0, 255)));

        float accentWidth = u(1.5f) * (state.alert ? 1f : hover);
        if (accentWidth > 0.05f)
            vg.rectangle(state.x, state.y + u(3f), accentWidth, state.height - u(6f),
                    NanoVGTheme.accent(255));

        float centerY = state.y + state.height / 2f;

        Color iconColor = state.alert ? NanoVGTheme.ACCENT
                : NanoVGTheme.blend(NanoVGTheme.muted(215), NanoVGTheme.text(246), hover);
        NVGFonts.ICON.drawText(state.icon, state.x + u(8f), centerY - font(10f) / 2f, font(10f),
                iconColor, Alignment.LEFT_TOP, true);

        NVGFont labelFont = hover > 0.5f ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        Color labelColor = state.alert ? NanoVGTheme.accent(240)
                : NanoVGTheme.blend(NanoVGTheme.muted(225), NanoVGTheme.text(246), hover);
        labelFont.drawText(state.label, state.x + u(26f), centerY - u(5.5f), u(11f) * (1f + 0.05f * hover),
                labelColor, Alignment.LEFT_TOP, true);

        float chevronX = state.x + state.width - u(12f) + u(2f) * hover;
        Color chevronColor = NanoVGTheme.blend(NanoVGTheme.dim(170), NanoVGTheme.accent(235), hover);
        NVGFonts.ICON.drawText(MaterialIcon.FORWARD_IOS, chevronX, centerY - u(1f), font(9f),
                chevronColor, Alignment.CENTER_MIDDLE, true);
    }

    private float updateMusicSliderGrab() {
        long now = System.nanoTime();
        float delta = lastMusicSliderNanos == 0L ? 1f : Math.clamp((now - lastMusicSliderNanos) / 16_666_666f, 0.25f, 3f);
        lastMusicSliderNanos = now;
        float target = musicVolumeSliderDragging ? 1f : 0f;
        float speed = musicVolumeSliderDragging ? 0.38f : 0.11f;
        musicSliderGrab += (target - musicSliderGrab) * Math.clamp(speed * delta, 0f, 1f);
        return musicSliderGrab;
    }

    private CursorType resolveMenuCursor(double mouseX, double mouseY) {
        if (isMinecraftMouseBlocked() || shouldRenderStartupLoadBar())
            return CursorType.DEFAULT;

        if (musicVolumeSliderDragging || isMusicSliderHovered(mouseX, mouseY))
            return CursorTypes.POINTING_HAND;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        if (isHovered(mouseX, mouseY, settingsButtonX(screenWidth), topBarY(), iconButtonSize(), iconButtonSize())
                || isHovered(mouseX, mouseY, closeButtonX(screenWidth), topBarY(), iconButtonSize(), iconButtonSize()))
            return CursorTypes.POINTING_HAND;

        for (MenuButton button : getVisibleMenuButtons()) {
            if (isHovered(mouseX, mouseY, button.x, button.y, button.width, button.height))
                return CursorTypes.POINTING_HAND;
        }

        return CursorType.DEFAULT;
    }

    private boolean isMusicSliderHovered(double mouseX, double mouseY) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        return isHovered(mouseX, mouseY, musicPanelX(screenWidth), musicPanelY(), musicPanelWidth(), musicPanelHeight());
    }

    private void updateMainMenuMusicVolumeFromMouse(double mouseX, boolean save) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float scaledMouseX = NanoVGManager.toScaledMouseX(mouseX);
        float volume = (scaledMouseX - musicTrackX(screenWidth)) / Math.max(1f, musicTrackWidth());
        volume = Math.max(0f, Math.min(1f, volume));

        MAIN_MENU_MUSIC.setVolume(volume);
        if (Client.configManager != null)
            Client.configManager.setMainMenuMusicVolume(volume, save);

        musicVolumeDirty = !save;
    }

    private void saveMainMenuMusicVolumeIfDirty() {
        if (!musicVolumeDirty || Client.configManager == null)
            return;

        Client.configManager.setMainMenuMusicVolume(MAIN_MENU_MUSIC.getVolume(), true);
        musicVolumeDirty = false;
    }

    private float getMainMenuMusicVolume() {
        return Client.configManager == null ? MAIN_MENU_MUSIC.getVolume() : Client.configManager.getMainMenuMusicVolume();
    }

    private List<MenuButton> getVisibleMenuButtons() {
        return FileUtil.INSTANCE.isLatestClient() ? menuButtons : outdatedClientMenuButtons;
    }

    private static class MenuButton {
        final String label;
        final String icon;
        final boolean alert;
        float x;
        float y;
        float width;
        float height;
        boolean hovered;
        float hover;

        MenuButton(String label, String icon) {
            this(label, icon, false);
        }

        MenuButton(String label, String icon, boolean alert) {
            this.label = label;
            this.icon = icon;
            this.alert = alert;
        }
    }

    public static String getCurrentTime() {
        long currentSecond = System.currentTimeMillis() / 1000L;
        if (currentSecond != cachedTimeSecond) {
            cachedTimeSecond = currentSecond;
            cachedTime = formatTime(LocalTime.now());
        }

        return cachedTime;
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

    private static boolean ensureTexture(NVGU vg, String identifier, String path) {
        if (vg.hasTexture(identifier))
            return true;

        try (InputStream texture = CustomTitleScreen.class.getClassLoader().getResourceAsStream(path)) {
            if (texture == null)
                return false;

            vg.createTexture(identifier, texture);
            return vg.hasTexture(identifier);
        } catch (RuntimeException | java.io.IOException ignored) {
            return false;
        }
    }

    public static void renderStartupLoadBarIfNeeded(NanoVGManager manager) {
        if (manager == null)
            return;

        float alpha = updateStartupLoadBarFade();
        if (alpha <= 0.001f)
            return;

        manager.renderImmediate(vg -> vg.globalAlpha(alpha, () -> renderStartupLoadBar(vg)));
    }

    public static boolean shouldRenderStartupLoadBar() {
        return mc.gui.screen() instanceof CustomTitleScreen && mc.gui.overlay() != null;
    }

    public static boolean isStartupLoadBarVisible() {
        return shouldRenderStartupLoadBar()
                || startupLoadBarAlpha > 0.001f && mc.gui.screen() instanceof CustomTitleScreen;
    }

    private static float updateStartupLoadBarFade() {
        long now = System.nanoTime();
        if (!(mc.gui.screen() instanceof CustomTitleScreen)) {
            startupLoadBarAlpha = 0f;
            startupLoadBarFadeNanos = now;
            return 0f;
        }

        float target = shouldRenderStartupLoadBar() ? 1f : 0f;
        float seconds = Math.clamp((now - startupLoadBarFadeNanos) / 1_000_000_000f, 0f, 0.05f);
        startupLoadBarFadeNanos = now;
        float step = seconds / STARTUP_LOAD_BAR_FADE_SECONDS;
        startupLoadBarAlpha = target > startupLoadBarAlpha
                ? Math.min(target, startupLoadBarAlpha + step)
                : Math.max(target, startupLoadBarAlpha - step);
        return startupLoadBarAlpha * startupLoadBarAlpha * (3f - 2f * startupLoadBarAlpha);
    }

    private static void renderStartupLoadBar(NVGU vg) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float panelWidth = Math.min(320f, Math.max(240f, screenWidth - 48f));
        float panelHeight = 54f;
        float panelX = (screenWidth - panelWidth) / 2f;
        float panelY = Math.min(screenHeight - panelHeight - 28f, screenHeight * 0.72f);
        float trackX = panelX + 22f;
        float trackY = panelY + 33f;
        float trackWidth = panelWidth - 44f;
        float trackHeight = 5f;

        NanoVGTheme.renderPanel(vg, panelX, panelY, panelWidth, panelHeight, NanoVGTheme.RADIUS_PANEL, 1f);
        NVGFonts.INTER_MEDIUM.drawText("Loading", panelX + 22f, panelY + 12f, 12f, NanoVGTheme.text(235), Alignment.LEFT_TOP, true);
        vg.rectangle(trackX, trackY, trackWidth, trackHeight, NanoVGTheme.base(90));

        float phase = (System.currentTimeMillis() % 1350L) / 1350f;
        float markerWidth = Math.max(54f, trackWidth * 0.28f);
        float markerX = trackX - markerWidth + (trackWidth + markerWidth * 2f) * phase;

        vg.scissor(trackX, trackY, trackWidth, trackHeight, () ->
                vg.rectangle(markerX, trackY, markerWidth, trackHeight, NanoVGTheme.accent(220))
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Client.nanoVgManager == null)
            return;

        // Keep hit boxes valid for the cursor before the (possibly deferred) renderer runs.
        layoutMenu(getVisibleMenuButtons(), NanoVGManager.getScaledScreenWidth(), NanoVGManager.getScaledScreenHeight());
        context.requestCursor(resolveMenuCursor(mouseX, mouseY));

        Consumer<NVGU> renderer = vg -> {
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float screenHeight = NanoVGManager.getScaledScreenHeight();
            float centerX = screenWidth / 2f;

            if (!isMinecraftMouseBlocked()) {
                float targetBgOffsetX = NanoVGManager.toScaledMouseX(mc.mouseHandler.getScaledXPos(mc.getWindow()));
                float targetBgOffsetY = NanoVGManager.toScaledMouseY(mc.mouseHandler.getScaledYPos(mc.getWindow()));

                stepBackgroundParallax(targetBgOffsetX, targetBgOffsetY, 0.03f);
            }

            if (ensureTexture(vg, "title_screen", "assets/krs/title.png")) {
                vg.texturedRectangle(0f, 0f, screenWidth * 2, screenHeight * 2, "title_screen");

                vg.save();
                try {
                    vg.translate(bgOffsetX, bgOffsetY);
                    vg.texturedRectangle(-screenWidth, -screenHeight, screenWidth * 2, screenHeight * 2, "title_screen");
                } finally {
                    vg.restore();
                }
            }

            vg.rectangle(0f, 0f, screenWidth, screenHeight, new Color(8, 6, 11, 142));
            vg.rectangle(
                    0f, 0f, screenWidth, screenHeight,
                    vg.linearGradient(
                            0f, 0f, screenWidth, screenHeight, Math.max(1f, screenHeight),
                            new Color(10, 6, 16, 46),
                            new Color(6, 4, 9, 208),
                            org.nvgu.util.LinearGradientDirection.TOP_TO_BOTTOM
                    )
            );

            List<MenuButton> visibleMenuButtons = getVisibleMenuButtons();
            layoutMenu(visibleMenuButtons, screenWidth, screenHeight);
            for (MenuButton button : visibleMenuButtons)
                updateMenuButton(button, mouseX, mouseY);

            renderTopBar(vg, screenWidth, mouseX, mouseY);
            renderMusicControl(vg, screenWidth, mouseX, mouseY);
            renderMenuPanel(vg, visibleMenuButtons);

            NanoVGTheme.drawTrackedText(NVGFonts.INTER, "KANADE  " + Client.clientVersion, u(10f), screenHeight - u(15f),
                    font(8f), u(1.3f), NanoVGTheme.muted(190), true);

            if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
                NVGFonts.INTER.drawText("Vulkan support is not yet complete, so performance will be significantly lower than when using OpenGL. Please use OpenGL unless there is a specific reason not to.", centerX, u(40f), font(9f), NanoVGTheme.ACCENT, Alignment.CENTER_TOP, true);
            }

            Client.notificationManager.renderNotifications(vg);
        };

        if (mc.gui.screen() == this || mc.gui.screen() instanceof NanoVGClickGuiScreen)
            Client.nanoVgManager.load(renderer);
        else
            Client.nanoVgManager.loadBeforeGui(renderer);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (shouldRenderStartupLoadBar())
            return true;

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        List<MenuButton> visibleMenuButtons = getVisibleMenuButtons();
        layoutMenu(visibleMenuButtons, screenWidth, screenHeight);

        if (button == Keys.MOUSE_BUTTON_LEFT) {
            if (isMusicSliderHovered(mouseX, mouseY)) {
                musicVolumeSliderDragging = true;
                updateMainMenuMusicVolumeFromMouse(mouseX, true);
                return true;
            }

            if (isHovered(mouseX, mouseY, settingsButtonX(screenWidth), topBarY(), iconButtonSize(), iconButtonSize())) {
                mc.gui.setScreen(new OptionsScreen(this, mc.options));
                return true;
            }

            if (isHovered(mouseX, mouseY, closeButtonX(screenWidth), topBarY(), iconButtonSize(), iconButtonSize())) {
                stopMainMenuMusic();
                mc.stop();
                return true;
            }

            for (MenuButton menuButton : visibleMenuButtons) {
                if (!isHovered(mouseX, mouseY, menuButton.x, menuButton.y, menuButton.width, menuButton.height))
                    continue;

                switch (menuButton.label) {
                    case "Single Player":
                        mc.gui.setScreen(new SelectWorldScreen(this));
                        return true;

                    case "Multi Player":
                        mc.gui.setScreen(new JoinMultiplayerScreen(this));
                        return true;

                    case "Realms":
                        mc.gui.setScreen(new RealmsMainScreen(this));
                        return true;

                    case "Friends":
                        OnlineOptionsScreen.confirmFriendsListEnabled(
                                mc,
                                () -> mc.gui.setScreen(new FriendsOverlayScreen(this)),
                                this
                        );
                        return true;

                    case "Options":
                        mc.gui.setScreen(new OptionsScreen(this, mc.options));
                        return true;

                    case "Update":
                        FileUtil.INSTANCE.openInBrowser(RELEASES_URL);
                        return true;

                    case "Exit":
                        stopMainMenuMusic();
                        mc.stop();
                        return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (musicVolumeSliderDragging && event.button() == Keys.MOUSE_BUTTON_LEFT) {
            updateMainMenuMusicVolumeFromMouse(event.x(), false);
            return true;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (musicVolumeSliderDragging && event.button() == Keys.MOUSE_BUTTON_LEFT) {
            updateMainMenuMusicVolumeFromMouse(event.x(), true);
            musicVolumeSliderDragging = false;
            musicVolumeDirty = false;
            return true;
        }

        return super.mouseReleased(event);
    }
}
