package com.instrumentalist.krs.utils.chat;

import com.instrumentalist.krs.hacks.features.render.Chat;
import com.instrumentalist.krs.utils.IMinecraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Word-boundary mention matching with a short per-sender cooldown. */
public final class MentionMatcher implements IMinecraft {
    private static final long PING_COOLDOWN_MS = 500L;
    private static final Map<String, Long> lastPing = new HashMap<>();

    private MentionMatcher() {
    }

    public static boolean matches(String plain, String sender) {
        if (plain == null || plain.isEmpty() || !Chat.featuresEnabled() || !Chat.mentionPing.get())
            return false;
        if (Chat.onlyWhenUnfocused.get() && mc.gui != null && mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)
            return false;

        String ownName = ownName();
        if (Chat.ignoreOwn.get() && sender != null && ownName != null && ownName.equalsIgnoreCase(sender))
            return false;

        boolean caseSensitive = Chat.caseSensitive.get();
        String haystack = caseSensitive ? plain : plain.toLowerCase(Locale.ROOT);

        if (Chat.mentionOwnName.get() && ownName != null && !ownName.isBlank()
                && containsWord(haystack, caseSensitive ? ownName : ownName.toLowerCase(Locale.ROOT), caseSensitive))
            return cooldown(sender, ownName);

        String keywords = Chat.keywords.get();
        if (keywords == null || keywords.isBlank())
            return false;

        for (String raw : keywords.split(",")) {
            String keyword = raw.strip();
            if (keyword.isEmpty())
                continue;
            if (containsWord(haystack, caseSensitive ? keyword : keyword.toLowerCase(Locale.ROOT), caseSensitive))
                return cooldown(sender, keyword);
        }

        if (Chat.mentionOwnName.get() && ownName != null && !ownName.isBlank())
            return false;

        return false;
    }

    private static boolean cooldown(String sender, String keyword) {
        long now = System.currentTimeMillis();
        String key = (sender == null ? "" : sender.toLowerCase(Locale.ROOT)) + "|" + keyword.toLowerCase(Locale.ROOT);
        Long previous = lastPing.get(key);
        if (previous != null && now - previous < PING_COOLDOWN_MS)
            return false;

        lastPing.put(key, now);
        if (lastPing.size() > 256)
            lastPing.values().removeIf(time -> now - time > PING_COOLDOWN_MS * 4);
        return true;
    }

    /** Matches only when the keyword is not glued to other letters or digits. */
    static boolean containsWord(String haystack, String needle, boolean caseSensitive) {
        if (needle.isEmpty() || haystack.length() < needle.length())
            return false;

        String target = caseSensitive ? haystack : haystack.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = target.indexOf(needle, from);
            if (index < 0)
                return false;

            int before = index - 1;
            int after = index + needle.length();
            boolean leftOk = before < 0 || !isWordChar(target.charAt(before));
            boolean rightOk = after >= target.length() || !isWordChar(target.charAt(after));
            if (leftOk && rightOk)
                return true;

            from = index + 1;
        }
    }

    private static boolean isWordChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static String ownName() {
        if (mc.getUser() != null)
            return mc.getUser().getName();
        return null;
    }
}
