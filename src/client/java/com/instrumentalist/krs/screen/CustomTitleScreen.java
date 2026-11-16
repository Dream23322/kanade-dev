package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.audio.Mp3MusicPlayer;
import com.instrumentalist.krs.hacks.features.render.Interface;
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

    private static long cachedTimeSecond = -1L;
    private static String cachedTime = "";
    private static final Mp3MusicPlayer MAIN_MENU_MUSIC = new Mp3MusicPlayer("assets/krs/musics/mainmenu.mp3");
    private static final float MUSIC_SLIDER_WIDTH = 180f;
    private static final float MUSIC_SLIDER_TRACK_HEIGHT = 7f;
    private static final Color BUTTON_IDLE = new Color(12, 9, 18, 162);
    private static final Color BUTTON_HOVERED = new Color(22, 14, 33, 205);
    private static final Color HOVER_LINE = new Color(214, 107, 232, 220);
    private static final float STARTUP_LOAD_BAR_FADE_SECONDS = 0.25f;
    private static float startupLoadBarAlpha;
    private static long startupLoadBarFadeNanos;
    private static final String RELEASES_URL = "https://github.com/Aspw-w/Krs/releases";

    private boolean musicVolumeSliderDragging;
    private boolean musicVolumeDirty;
    private float musicSliderGrab;
    private long lastMusicSliderNanos;

    private final MenuButton singlePlayerButton = new MenuButton("Single Player");
    private final MenuButton multiPlayerButton = new MenuButton("Multi Player");
    private final MenuButton realmsButton = new MenuButton("Realms");
    private final MenuButton friendsButton = new MenuButton("Friends");
    private final MenuButton optionsButton = new MenuButton("Options");
    private final MenuButton updateAvailableButton = new MenuButton("Update");
    private final MenuButton exitButton = new MenuButton("Exit");
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

    private MenuButton updateUiButtonState(MenuButton button, float x, float y, float mouseX, float mouseY) {
        boolean hovered = isHovered(mouseX, mouseY, x - 110f, y - 34f, 220f, 42f);
        float target = hovered ? 24f : 21f;
        button.fontSize = button.fontSize * 0.85f + target * 0.15f;
        button.animation = hovered ? button.animation * 0.8f + 10f : button.animation * 0.8f;
        button.x = x;
        button.y = y;
        button.hovered = hovered;
        return button;
    }

    private void renderUiButton(NVGU vg, MenuButton state) {
        Color baseColor = state.hovered ? BUTTON_HOVERED : BUTTON_IDLE;
        vg.roundedRectangle(state.x - 110f, state.y - 34f, 220f, 38f, NanoVGTheme.RADIUS_LABEL, baseColor);
        NanoVGTheme.renderEdge(vg, state.x - 110f, state.y - 34f, 220f, 38f, NanoVGTheme.RADIUS_LABEL, 1f);

        if (state.hovered)
            vg.rectangle(state.x - 109f, state.y - 30f, 2f, 30f, NanoVGTheme.ACCENT);

        NVGFont font = state.hovered ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        font.drawText(state.label, state.x, state.y - 15f, 13f,
                state.hovered ? NanoVGTheme.text(246) : NanoVGTheme.muted(235), Alignment.CENTER_MIDDLE, true);
    }

    private void renderMusicVolumeSlider(NVGU vg, float centerX, float mouseX, float mouseY) {
        float sliderX = getMusicSliderX(centerX);
        float sliderY = getMusicSliderY();
        float volume = getMainMenuMusicVolume();
        float fill = Math.clamp(volume, 0f, 1f);
        float radius = 0f;
        float grab = updateMusicSliderGrab();
        float knobWidth = 9f * (1f + 0.46f * grab);
        float knobHeight = 13f * (1f - 0.30f * grab);
        float knobX = sliderX + MUSIC_SLIDER_WIDTH * fill - knobWidth / 2f;
        float knobY = sliderY + MUSIC_SLIDER_TRACK_HEIGHT / 2f - knobHeight / 2f;
        String sliderText = volume == 0f ? "music off" : "volume " + Math.round(volume * 100f) + "%";
        NVGFonts.INTER.drawText(sliderText, centerX, sliderY - 10f, 9f, NanoVGTheme.muted(215), Alignment.CENTER_MIDDLE, true);
        vg.rectangle(sliderX, sliderY, MUSIC_SLIDER_WIDTH, MUSIC_SLIDER_TRACK_HEIGHT, NanoVGTheme.CONTROL_TRACK);
        if (fill > 0.001f)
            vg.rectangle(sliderX, sliderY, MUSIC_SLIDER_WIDTH * fill, MUSIC_SLIDER_TRACK_HEIGHT, NanoVGTheme.sliderFill(235));
        vg.rectangle(knobX, knobY, knobWidth, knobHeight, NanoVGTheme.KNOB_FILL);
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

        for (MenuButton button : getVisibleMenuButtons()) {
            if (isHovered(mouseX, mouseY, button.x - 110f, button.y - 34f, 220f, 42f))
                return CursorTypes.POINTING_HAND;
        }

        return CursorType.DEFAULT;
    }

    private boolean isMusicSliderHovered(double mouseX, double mouseY) {
        float centerX = NanoVGManager.getScaledScreenWidth() / 2f;
        float sliderX = getMusicSliderX(centerX);
        float sliderY = getMusicSliderY();

        return isHovered(mouseX, mouseY, sliderX - 8f, sliderY - 22f, MUSIC_SLIDER_WIDTH + 16f, 40f);
    }

    private void updateMainMenuMusicVolumeFromMouse(double mouseX, boolean save) {
        float sliderX = getMusicSliderX(NanoVGManager.getScaledScreenWidth() / 2f);
        float scaledMouseX = NanoVGManager.toScaledMouseX(mouseX);
        float volume = (scaledMouseX - sliderX) / MUSIC_SLIDER_WIDTH;
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

    private static float getMusicSliderX(float centerX) {
        return centerX - MUSIC_SLIDER_WIDTH / 2f;
    }

    private static float getMusicSliderY() {
        return 62f;
    }

    private static class MenuButton {
        final String label;
        float x;
        float y;
        boolean hovered;
        float animation;
        float fontSize = 21f;

        MenuButton(String label) {
            this.label = label;
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
                vg.rectangle(markerX, trackY, markerWidth, trackHeight, HOVER_LINE)
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Client.nanoVgManager == null)
            return;

        context.requestCursor(resolveMenuCursor(mouseX, mouseY));

        Consumer<NVGU> renderer = vg -> {
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float screenHeight = NanoVGManager.getScaledScreenHeight();
            float centerX = screenWidth / 2f;
            float centerY = screenHeight / 2f;

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

            float menuWidth = Math.min(300f, Math.max(240f, screenWidth - 40f));
            float menuX = centerX - menuWidth / 2f;
            float menuY = centerY - 150f;
            float firstButtonY = menuY + 46f;
            List<MenuButton> visibleMenuButtons = getVisibleMenuButtons();
            float menuHeight = 18f + (45f * visibleMenuButtons.size());

            NanoVGTheme.renderPanel(vg, menuX, menuY, menuWidth, menuHeight, NanoVGTheme.RADIUS_LABEL, 1f);

            float wordSize = 30f;
            float wordTracking = 1.8f;
            String wordmark = "kanade";
            float wordWidth = NanoVGTheme.trackedTextWidth(NVGFonts.INTER_MEDIUM, wordmark, wordSize, wordTracking);
            float wordY = menuY - 52f;
            NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, wordmark, centerX - wordWidth / 2f, wordY,
                    wordSize, wordTracking, NanoVGTheme.text(246), true);
            NVGFonts.INTER.drawText("v" + Client.clientVersion, centerX, wordY + wordSize + 5f, 9f,
                    NanoVGTheme.dim(215), Alignment.CENTER_TOP, true);
            NanoVGTheme.renderAccentUnderline(vg, centerX - wordWidth * 0.28f, wordY + wordSize + 1f,
                    wordWidth * 0.56f, 1f, 0.9f);

            NanoVGTheme.drawTrackedText(NVGFonts.INTER, "KANADE  " + Client.clientVersion, 10f, screenHeight - 15f,
                    8f, 1.3f, NanoVGTheme.muted(190), true);
            NVGFonts.INTER.drawText(getCurrentTime(), screenWidth - 12f, 10f, 11f,
                    NanoVGTheme.muted(210), Alignment.RIGHT_TOP, true);
            NVGFonts.INTER.drawText("Logged in as " + mc.getUser().getName(), centerX, 20f, 10f,
                    NanoVGTheme.dim(210), Alignment.CENTER_TOP, true);
            renderMusicVolumeSlider(vg, centerX, mouseX, mouseY);

            if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
                NVGFonts.INTER.drawText("Vulkan support is not yet complete, so performance will be significantly lower than when using OpenGL. Please use OpenGL unless there is a specific reason not to.", centerX, 80f, 21f, NanoVGTheme.ACCENT, Alignment.CENTER_TOP, true);
            }

            for (int i = 0; i < visibleMenuButtons.size(); i++)
                updateUiButtonState(visibleMenuButtons.get(i), centerX, firstButtonY + (45f * i), mouseX, mouseY);

            for (MenuButton state : visibleMenuButtons) {
                renderUiButton(vg, state);
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
        float centerX = NanoVGManager.getScaledScreenWidth() / 2f;
        float firstButtonY = NanoVGManager.getScaledScreenHeight() / 2f - 104f;

        if (button == Keys.MOUSE_BUTTON_LEFT) {
            if (isMusicSliderHovered(mouseX, mouseY)) {
                musicVolumeSliderDragging = true;
                updateMainMenuMusicVolumeFromMouse(mouseX, true);
                return true;
            }

            List<MenuButton> visibleMenuButtons = getVisibleMenuButtons();
            for (int i = 0; i < visibleMenuButtons.size(); i++) {
                MenuButton menuButton = visibleMenuButtons.get(i);
                String label = menuButton.label;
                float buttonTop = firstButtonY + (45f * i) - 34f;

                if (isHovered(mouseX, mouseY, centerX - 110f, buttonTop, 220f, 38f)) {
                    switch (label) {
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
