package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.TextValue;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Queue helper that watches the pre-game countdown and requeues the current
 * gamemode when the lobby contains no real players.
 *
 * <p>On the target server real players are marked with a {@code *} in the tab
 * list. The module treats every other tab-list entry as a bot and excludes the
 * local player when deciding whether a lobby is empty.</p>
 */
public class Rewound extends Module {
    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)");
    private static final Pattern CLOCK = Pattern.compile("(?<!\\d)(\\d{1,2}):([0-5]\\d)(?!\\d)");
    private static final Pattern BARE_NUMBER = Pattern.compile("^(\\d{1,3})(?:\\s*(?:s|sec|secs|second|seconds|秒|초))?$");
    private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern MINUTES = Pattern.compile(".*\\b\\d{1,3}\\s*(?:m|min|mins|minute|minutes)\\b.*");

    private static final long RECENT_COUNTDOWN_NANOS = 2_000_000_000L;
    private static final long COUNTDOWN_GAP_NANOS = 3_000_000_000L;
    private static final long REQUEUE_COOLDOWN_NANOS = 1_500_000_000L;
    private static final long COMMAND_MODE_GRACE_NANOS = 30_000_000_000L;

    // The /play gamemodes listed by the server. Keeping this list explicit
    // avoids mistaking unrelated tab-list text for a mode name.
    private static final List<String> KNOWN_MODES = List.of(
            "bedwars_eight_one",
            "bedwars_eight_two",
            "bedwars_four_three",
            "bedwars_four_four",
            "bedwars_castle",
            "solo_normal",
            "solo_insane",
            "teams_normal",
            "teams_insane",
            "mega",
            "duels_uhc_duel",
            "duels_uhc_doubles",
            "duels_uhc_four",
            "duels_uhc_meetup",
            "duels_sw_duel",
            "duels_sw_doubles",
            "duels_bridge_duel",
            "duels_bridge_doubles",
            "duels_bridge_teams",
            "duels_bridge_2v2v2v2",
            "duels_bridge_3v3v3v3",
            "duels_classic_duel",
            "duels_op_duel",
            "duels_op_doubles",
            "duels_combo_duel",
            "duels_potion_duel",
            "duels_bow_duel",
            "duels_sumo_duel",
            "duels_bowspleef_duel",
            "blitz_solo_normal",
            "blitz_teams_normal",
            "duels_challenge",
            "replay",
            "pit"
    );
    @Setting
    private final BooleanValue requeue = new BooleanValue(
            "ReQueue",
            true
    ).describe("Requeues the current gamemode when no real players are present.");

    @Setting
    private final IntValue triggerSeconds = new IntValue(
            "Trigger Seconds",
            5,
            1,
            30,
            "s"
    ).describe("How many seconds before the game starts the player check runs.");

    @Setting
    private final TextValue modeOverride = new TextValue(
            "Game Mode",
            ""
    ).describe("Optional /play mode override. Leave empty to use auto-detection.");

    @Setting
    private final BooleanValue notifications = new BooleanValue(
            "Notifications",
            true
    ).describe("Shows a notification whenever a requeue command is sent.");

    private String currentMode;
    private String commandedMode;
    private long commandedModeNanos;
    private String tabHeader = "";
    private String tabFooter = "";
    private Integer recentCountdown;
    private long recentCountdownNanos;
    private long lastCountdownSeenNanos;
    private boolean countdownHandled;
    private boolean missingModeNotified;
    private long lastRequeueNanos;

    public Rewound() {
        super("Rewound", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Requeues the current gamemode when a lobby has no real players";
    }

    @Override
    public String tag() {
        return currentMode == null ? "ReQueue" : currentMode;
    }

    /**
     * Finds the current play mode for external queue commands. If Rewound is
     * enabled its live detection is used; otherwise the scoreboard is scanned
     * and its Game Mode override is still respected.
     */
    public static String detectQueueMode() {
        Rewound module = ModuleManager.getModule(Rewound.class);
        Rewound scanner = module == null ? new Rewound() : module;

        String mode = scanner.resolveMode();
        if (mode != null && !mode.isBlank())
            return mode;

        return findModeIn(scanner.collectBoardLines());
    }

    @Override
    public void onEnable() {
        resetCountdownState();
        currentMode = null;
        commandedMode = null;
        commandedModeNanos = 0L;
        tabHeader = "";
        tabFooter = "";
        recentCountdown = null;
        recentCountdownNanos = 0L;
        lastCountdownSeenNanos = 0L;
        missingModeNotified = false;
        lastRequeueNanos = 0L;
    }

    @Override
    public void onDisable() {
        resetCountdownState();
        currentMode = null;
        commandedMode = null;
        commandedModeNanos = 0L;
        recentCountdown = null;
        recentCountdownNanos = 0L;
        missingModeNotified = false;
        lastRequeueNanos = 0L;
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetCountdownState();
        commandedMode = null;
        commandedModeNanos = 0L;
        tabHeader = "";
        tabFooter = "";
        recentCountdown = null;
        recentCountdownNanos = 0L;
        missingModeNotified = false;
        lastRequeueNanos = 0L;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null || mc.level == null) {
            resetCountdownState();
            return;
        }

        List<String> boardLines = collectBoardLines();
        List<String> tabLines = tabLines();

        // The tab list is the authoritative text source. Sidebars can keep the
        // previous game's mode around while a new queue/lobby is loading.
        String tabMode = findModeIn(tabLines);
        if (tabMode != null) {
            rememberMode(tabMode, true);
        } else {
            for (String line : boardLines)
                rememberModeFromText(line, false);
        }

        Integer seconds = parseCountdown(boardLines);
        if (seconds == null)
            seconds = parseCountdown(tabLines);
        long now = System.nanoTime();
        if (seconds == null && recentCountdown != null
                && now - recentCountdownNanos <= RECENT_COUNTDOWN_NANOS)
            seconds = recentCountdown;

        if (seconds == null || seconds <= 0) {
            // Countdown displays can blink for a tick or two; only reset after a real gap.
            if (lastCountdownSeenNanos == 0 || now - lastCountdownSeenNanos > COUNTDOWN_GAP_NANOS)
                resetCountdownState();
            return;
        }

        lastCountdownSeenNanos = now;
        int trigger = Math.max(1, triggerSeconds.get());
        if (seconds > trigger) {
            countdownHandled = false;
            return;
        }

        if (countdownHandled)
            return;

        if (!requeue.get())
            return;

        if (hasRealPlayer(player)) {
            countdownHandled = true;
            return;
        }

        String mode = resolveMode();
        if (mode == null) {
            notifyMissingMode();
            return;
        }

        if (now - lastRequeueNanos < REQUEUE_COOLDOWN_NANOS)
            return;

        countdownHandled = true;
        lastRequeueNanos = now;
        player.connection.sendCommand("play " + mode);
        if (notifications.get() && Client.notificationManager != null)
            Client.notificationManager.addNotification(
                    "Rewound",
                    "No real players at " + seconds + "s - requeueing " + mode
            );
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (event == null || event.packet == null)
            return;

        String command = null;
        if (event.packet instanceof ServerboundChatCommandPacket packet)
            command = packet.command();
        else if (event.packet instanceof ServerboundChatCommandSignedPacket packet)
            command = packet.command();

        if (command != null)
            rememberPlayCommand(command);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event == null || event.packet == null)
            return;

        Packet<?> packet = event.packet;
        if (packet instanceof ClientboundTabListPacket tabList) {
            tabHeader = text(tabList.header());
            tabFooter = text(tabList.footer());
            observeText(tabHeader, true, true);
            observeText(tabFooter, true, true);
        } else if (packet instanceof ClientboundSetTitleTextPacket title) {
            observeText(text(title.text()), true, false);
        } else if (packet instanceof ClientboundSetSubtitleTextPacket subtitle) {
            observeText(text(subtitle.text()), true, false);
        } else if (packet instanceof ClientboundSetActionBarTextPacket actionBar) {
            observeText(text(actionBar.text()), true, false);
        } else if (packet instanceof ClientboundSystemChatPacket chat && !chat.overlay()) {
            observeText(text(chat.content()), false, false);
        }
    }

    private void observeText(String text, boolean detectMode, boolean trustedModeSource) {
        if (text == null || text.isBlank())
            return;

        if (detectMode)
            rememberModeFromText(text, trustedModeSource);
        Integer seconds = parseCountdownLine(text);
        if (seconds != null) {
            recentCountdown = seconds;
            recentCountdownNanos = System.nanoTime();
        }
    }

    private List<String> collectBoardLines() {
        ArrayList<String> lines = new ArrayList<>();
        if (mc.level == null)
            return lines;

        Scoreboard board = mc.level.getScoreboard();
        for (DisplaySlot slot : new DisplaySlot[]{DisplaySlot.SIDEBAR, DisplaySlot.LIST}) {
            Objective objective = board.getDisplayObjective(slot);
            if (objective == null)
                continue;

            addLine(lines, text(objective.getDisplayName()));
            for (PlayerScoreEntry entry : board.listPlayerScores(objective)) {
                if (entry == null || entry.isHidden())
                    continue;

                String display = text(entry.display());
                String owner = entry.owner();
                String line = display;
                if (line == null || line.isBlank())
                    line = owner;
                if (line == null || line.isBlank())
                    continue;

                lines.add(line + " " + entry.value());
            }
        }

        return lines;
    }

    private List<String> tabLines() {
        ArrayList<String> lines = new ArrayList<>();
        addLine(lines, tabHeader);
        addLine(lines, tabFooter);
        return lines;
    }

    private Integer parseCountdown(List<String> lines) {
        if (lines == null || lines.isEmpty())
            return null;

        for (String line : lines) {
            Integer value = parseCountdownLine(line);
            if (value != null)
                return value;
        }

        // Some servers put the label and the number on separate scoreboard rows.
        for (int index = 0; index < lines.size(); index++) {
            if (!hasCountdownContext(normalize(lines.get(index))))
                continue;

            for (int offset = -1; offset <= 1; offset++) {
                if (offset == 0)
                    continue;

                int neighbour = index + offset;
                if (neighbour < 0 || neighbour >= lines.size())
                    continue;

                Integer value = parseBareNumber(lines.get(neighbour));
                if (value != null)
                    return value;
            }
        }

        boolean hasContext = false;
        for (String line : lines) {
            if (hasCountdownContext(normalize(line))) {
                hasContext = true;
                break;
            }
        }

        if (!hasContext)
            return null;

        for (String line : lines) {
            Integer value = parseBareNumber(line);
            if (value != null)
                return value;
        }

        return null;
    }

    private Integer parseCountdownLine(String line) {
        String normalized = normalize(line);
        if (!hasCountdownContext(normalized))
            return null;

        if (hasMinuteLabel(normalized))
            return null;

        Matcher clock = CLOCK.matcher(normalized);
        if (clock.find()) {
            int seconds = Integer.parseInt(clock.group(1)) * 60
                    + Integer.parseInt(clock.group(2));
            return seconds > 0 && seconds <= 30 ? seconds : null;
        }

        Matcher matcher = NUMBER.matcher(normalized);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value > 0 && value <= 30)
                return value;
        }

        return null;
    }

    private static boolean hasMinuteLabel(String normalized) {
        return normalized.contains("minute")
                || normalized.contains("分")
                || normalized.contains("분")
                || MINUTES.matcher(normalized).matches();
    }

    private Integer parseBareNumber(String line) {
        String normalized = normalize(line);
        if (normalized.isBlank())
            return null;

        Matcher clock = CLOCK.matcher(normalized);
        if (clock.matches()) {
            int seconds = Integer.parseInt(clock.group(1)) * 60
                    + Integer.parseInt(clock.group(2));
            return seconds > 0 && seconds <= 30 ? seconds : null;
        }

        Matcher matcher = BARE_NUMBER.matcher(normalized);
        if (!matcher.matches())
            return null;

        int value = Integer.parseInt(matcher.group(1));
        return value > 0 && value <= 30 ? value : null;
    }

    private static boolean hasCountdownContext(String normalized) {
        if (normalized.isBlank())
            return false;

        return normalized.contains("start")
                || normalized.contains("begin")
                || normalized.contains("countdown")
                || normalized.contains("launch")
                || normalized.contains("ゲーム開始")
                || normalized.contains("開始")
                || normalized.contains("スタート")
                || normalized.contains("カウントダウン")
                || normalized.contains("시작")
                || normalized.contains("开始")
                || normalized.contains("秒") && normalized.contains("まで");
    }

    private void rememberPlayCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/"))
            normalized = normalized.substring(1).trim();

        String lower = normalized.toLowerCase(Locale.ROOT);
        String prefix = null;
        if (lower.startsWith("play "))
            prefix = "play ";
        else if (lower.startsWith("queue "))
            prefix = "queue ";

        if (prefix == null)
            return;

        String mode = normalized.substring(prefix.length()).trim();
        int space = mode.indexOf(' ');
        if (space >= 0)
            mode = mode.substring(0, space);

        mode = mode.toLowerCase(Locale.ROOT);
        if (mode.matches("[a-z0-9_]+")) {
            commandedMode = mode;
            commandedModeNanos = System.nanoTime();
            rememberMode(mode, true);
        }
    }

    private void rememberModeFromText(String text, boolean trusted) {
        String mode = findModeInText(text);
        if (mode != null)
            rememberMode(mode, trusted);
    }

    private static String findModeIn(List<String> lines) {
        if (lines == null)
            return null;

        for (String line : lines) {
            String mode = findModeInText(line);
            if (mode != null)
                return mode;
        }

        return null;
    }

    private static String findModeInText(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank() || normalized.length() > 96)
            return null;

        for (String mode : KNOWN_MODES) {
            if (containsToken(normalized, mode))
                return mode;
        }

        return null;
    }

    private static boolean containsToken(String text, String token) {
        int index = text.indexOf(token);
        while (index >= 0) {
            int end = index + token.length();
            boolean leftBoundary = index == 0 || !isModeCharacter(text.charAt(index - 1));
            boolean rightBoundary = end >= text.length() || !isModeCharacter(text.charAt(end));
            if (leftBoundary && rightBoundary)
                return true;

            index = text.indexOf(token, index + 1);
        }
        return false;
    }

    private static boolean isModeCharacter(char character) {
        return character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_';
    }

    private void rememberMode(String mode, boolean trusted) {
        if (mode == null || mode.isBlank())
            return;

        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!trusted && commandedMode != null
                && !normalized.equals(commandedMode)
                && System.nanoTime() - commandedModeNanos < COMMAND_MODE_GRACE_NANOS)
            return;

        if (normalized.equals(currentMode))
            return;

        currentMode = normalized;
        missingModeNotified = false;
    }

    private String resolveMode() {
        String override = modeOverride.get();
        if (override != null && !override.isBlank()) {
            String normalized = override.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("/play "))
                normalized = normalized.substring(6).trim();
            else if (normalized.startsWith("play "))
                normalized = normalized.substring(5).trim();
            else if (normalized.startsWith("/queue "))
                normalized = normalized.substring(7).trim();
            else if (normalized.startsWith("queue "))
                normalized = normalized.substring(6).trim();
            else if (normalized.startsWith("/"))
                normalized = normalized.substring(1).trim();

            return normalized.matches("[a-z0-9_]+") ? normalized : null;
        }

        return currentMode;
    }

    private boolean hasRealPlayer(LocalPlayer player) {
        var connection = mc.getConnection();
        if (connection == null)
            return false;

        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            if (info == null || info.getProfile() == null)
                continue;

            var profile = info.getProfile();
            if (profile.id() != null && profile.id().equals(player.getUUID()))
                continue;

            String name = profile.name();
            if (name == null || name.isBlank())
                continue;

            if (hasRealMarker(info, name))
                return true;
        }

        return false;
    }

    private boolean hasRealMarker(PlayerInfo info, String profileName) {
        String display = "";
        if (mc.gui != null && mc.gui.hud != null) {
            var tabList = mc.gui.hud.getTabList();
            if (tabList != null)
                display = text(tabList.getNameForDisplay(info));
        }

        if (display.isBlank())
            display = text(info.getTabListDisplayName());
        if (display.isBlank())
            display = profileName;

        if (isStarred(display))
            return true;

        PlayerTeam team = info.getTeam();
        if (team != null) {
            String decorated = text(team.getPlayerPrefix()) + display + text(team.getPlayerSuffix());
            return isStarred(decorated);
        }

        return false;
    }

    private static boolean isStarred(String display) {
        String compact = FORMATTING.matcher(display).replaceAll("").replaceAll("\\s+", "");
        return compact.contains("*");
    }

    private void notifyMissingMode() {
        if (missingModeNotified)
            return;

        missingModeNotified = true;
        if (notifications.get() && Client.notificationManager != null)
            Client.notificationManager.addNotification(
                    "Rewound",
                    "Could not detect the current gamemode. Set Game Mode manually."
            );
    }

    private void resetCountdownState() {
        lastCountdownSeenNanos = 0L;
        countdownHandled = false;
    }

    private static void addLine(List<String> lines, String line) {
        if (line != null && !line.isBlank())
            lines.add(line);
    }

    private static String text(Component component) {
        return component == null ? "" : component.getString();
    }

    private static String normalize(String text) {
        if (text == null)
            return "";
        return FORMATTING.matcher(text).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }
}
