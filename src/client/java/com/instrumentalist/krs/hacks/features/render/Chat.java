package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.chat.ChatRenderer;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ColorValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.TextValue;

import java.awt.Color;

/**
 * Replaces the vanilla chat HUD with a themed renderer, tags client output as
 * {@code kanade | message} and turns mentions into glowing alerts.
 */
public class Chat extends Module {
    public static final String CLIENT_TAG_DEFAULT = "kanade";

    @Setting
    public static final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Vanilla", "Modern", "Compact"},
            "Modern"
    ).describe("Vanilla leaves the normal chat alone; Modern and Compact draw the custom chat HUD.");

    @Setting
    public static final FloatValue width = new FloatValue(
            "Width",
            260f,
            140f,
            620f,
            "px"
    ).describe("Width of the custom chat panel.");

    @Setting
    public static final IntValue maxLines = new IntValue(
            "Max Lines",
            12,
            3,
            30
    ).describe("How many chat lines stay on screen.");

    @Setting
    public static final FloatValue lineSpacing = new FloatValue(
            "Line Spacing",
            2f,
            0f,
            8f,
            "px"
    ).describe("Extra vertical space between chat lines.");

    @Setting
    public static final FloatValue opacity = new FloatValue(
            "Opacity",
            0.9f,
            0.15f,
            1f
    ).describe("Opacity of the custom chat panel.");

    @Setting
    public static final BooleanValue background = new BooleanValue(
            "Background",
            true
    ).describe("Draws the chat panel background.");

    @Setting
    public static final FloatValue radius = new FloatValue(
            "Corner Radius",
            4f,
            0f,
            12f,
            "px"
    ).describe("Corner radius of the chat panel.");

    @Setting
    public static final ListValue position = new ListValue(
            "Position",
            new String[]{"Bottom Left", "Top Left"},
            "Bottom Left"
    ).describe("Screen corner the chat panel anchors to.");

    @Setting
    public static final ListValue animation = new ListValue(
            "Animation",
            new String[]{"Fade", "None"},
            "Fade"
    ).describe("How new chat lines appear.");

    @Setting
    public static final ListValue timestamps = new ListValue(
            "Timestamps",
            new String[]{"Off", "HH:mm", "HH:mm:ss"},
            "Off"
    ).describe("Shows the local time in front of each chat line.");

    @Setting
    public static final BooleanValue icons = new BooleanValue(
            "Prefix Icons",
            true
    ).describe("Shows a small type icon before each chat line.");

    @Setting
    public static final ListValue theme = new ListValue(
            "Theme",
            new String[]{"Client", "Midnight", "Accent Glass", "CS2", "Light", "Custom"},
            "Client"
    ).describe("Chat palette. Pick Custom to use the colour pickers below.");

    @Setting
    public static final ColorValue customBackground = new ColorValue(
            "Chat Background",
            new Color(0x0D, 0x09, 0x14, 220),
            () -> "Custom".equals(theme.get())
    ).describe("Panel background when the chat theme is Custom.");

    @Setting
    public static final ColorValue customText = new ColorValue(
            "Chat Text",
            new Color(0xE9, 0xE2, 0xF0),
            () -> "Custom".equals(theme.get())
    ).describe("Message colour when the chat theme is Custom.");

    @Setting
    public static final ColorValue customMention = new ColorValue(
            "Mention Colour",
            new Color(0xFF, 0xD8, 0xFF),
            () -> "Custom".equals(theme.get())
    ).describe("Highlight colour for mentions when the chat theme is Custom.");

    @Setting
    public static final ColorValue customGlow = new ColorValue(
            "Glow Colour",
            new Color(0xFF, 0xFF, 0xFF),
            () -> "Custom".equals(theme.get())
    ).describe("Glow colour for mention alerts when the chat theme is Custom.");

    @Setting
    public static final TextValue tagText = new TextValue(
            "Tag Text",
            CLIENT_TAG_DEFAULT
    ).describe("Name shown in front of client messages and alerts.");

    @Setting
    public static final ListValue tagStyle = new ListValue(
            "Tag Style",
            new String[]{"Theme", "Gradient", "White", "Custom"},
            "Gradient"
    ).describe("Colour style of the client tag.");

    @Setting
    public static final ColorValue tagColor = new ColorValue(
            "Tag Colour",
            new Color(0xD6, 0x6B, 0xE8),
            () -> "Custom".equals(tagStyle.get())
    ).describe("Tag colour when the tag style is Custom.");

    @Setting
    public static final BooleanValue tagGlow = new BooleanValue(
            "Tag Glow",
            true
    ).describe("Draws a soft white glow behind the client tag.");

    @Setting
    public static final BooleanValue mentionPing = new BooleanValue(
            "Mention Ping",
            true
    ).describe("Plays a sound and raises an alert when your name appears in chat.");

    @Setting
    public static final BooleanValue mentionOwnName = new BooleanValue(
            "Mention Own Name",
            true,
            mentionPing::get
    ).describe("Treat your Minecraft name as a mention keyword.");

    @Setting
    public static final TextValue keywords = new TextValue(
            "Mention Keywords",
            "",
            mentionPing::get
    ).describe("Extra mention words, separated by commas.");

    @Setting
    public static final BooleanValue caseSensitive = new BooleanValue(
            "Case Sensitive",
            false,
            mentionPing::get
    ).describe("Requires mentions to match the keyword casing.");

    @Setting
    public static final BooleanValue onlyWhenUnfocused = new BooleanValue(
            "Only When Unfocused",
            true,
            mentionPing::get
    ).describe("Skips the ping while the chat screen is open.");

    @Setting
    public static final BooleanValue highlight = new BooleanValue(
            "Mention Highlight",
            true
    ).describe("Draws an accent bar and glow on mentioning lines.");

    @Setting
    public static final BooleanValue flashScreen = new BooleanValue(
            "Mention Flash",
            true,
            mentionPing::get
    ).describe("Briefly flashes the screen edges when you are mentioned.");

    @Setting
    public static final ListValue sound = new ListValue(
            "Mention Sound",
            new String[]{"Orb", "Pling", "Click", "None"},
            "Orb",
            mentionPing::get
    ).describe("Sound played for a mention.");

    @Setting
    public static final BooleanValue notify = new BooleanValue(
            "Mention Notification",
            true,
            mentionPing::get
    ).describe("Shows a glowing alert card for mentions.");

    @Setting
    public static final BooleanValue glow = new BooleanValue(
            "Notification Glow",
            true
    ).describe("Adds the white glow pass to chat alert cards.");

    @Setting
    public static final FloatValue glowIntensity = new FloatValue(
            "Glow Intensity",
            1f,
            0.1f,
            2.5f,
            () -> glow.get()
    ).describe("Strength of the alert card glow.");

    @Setting
    public static final FloatValue pulseSpeed = new FloatValue(
            "Glow Pulse Speed",
            1f,
            0f,
            3f,
            () -> glow.get()
    ).describe("Speed of the glowing pulse. 0 keeps the glow steady.");

    @Setting
    public static final BooleanValue showSender = new BooleanValue(
            "Show Sender",
            true
    ).describe("Prefixes mention cards with the sender name.");

    @Setting
    public static final IntValue historySize = new IntValue(
            "History Size",
            250,
            50,
            1000
    ).describe("How many chat messages are kept for the custom renderer.");

    @Setting
    public static final BooleanValue antiClear = new BooleanValue(
            "Anti Clear",
            false
    ).describe("Ignores server and plugin requests that wipe the chat.");

    @Setting
    public static final BooleanValue ignoreOwn = new BooleanValue(
            "Ignore Own Messages",
            true
    ).describe("Never treats your own messages as a mention.");

    public Chat() {
        super("Chat", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public String description() {
        return "Custom chat HUD, client tag, mention pings and glowing alerts";
    }

    /** True when the module is enabled at all; pings and anti-clear work in Vanilla mode too. */
    public static boolean featuresEnabled() {
        return ModuleManager.getModuleState(Chat.class);
    }

    /** True when the module is enabled and the custom renderer should replace vanilla chat. */
    public static boolean customEnabled() {
        return featuresEnabled() && !"Vanilla".equalsIgnoreCase(mode.get());
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (!customEnabled() || Client.nanoVgManager == null)
            return;
        if (mc.player == null || mc.level == null)
            return;

        Client.nanoVgManager.load(ChatRenderer::render);
    }
}
