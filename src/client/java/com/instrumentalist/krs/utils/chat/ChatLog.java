package com.instrumentalist.krs.utils.chat;

import com.instrumentalist.krs.hacks.features.render.Chat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side copy of the chat feed. Vanilla keeps its own message list for scrolling and
 * signatures; this store keeps the plain text and timestamp needed by the custom renderer.
 */
public final class ChatLog {
    public enum Kind {
        PLAYER,
        SERVER,
        CLIENT
    }

    public record Entry(Component text, String plain, Kind kind, long timeMs, boolean mention,
                        MessageSignature signature) {
    }

    private static final Object LOCK = new Object();
    private static final int HARD_LIMIT = 1000;
    private static final ArrayList<Entry> entries = new ArrayList<>();
    private static volatile long version;

    private ChatLog() {
    }

    /** Records a message in the custom chat log. */
    public static void record(Kind kind, Component text, MessageSignature signature) {
        if (text == null || kind == null)
            return;

        String plain = text.getString();
        if (kind == Kind.CLIENT) {
            plain = stripClientPrefix(plain);
            plain = stripTagPrefix(plain);
        }
        if (plain.isBlank())
            return;

        String sender = kind == Kind.PLAYER ? senderOf(plain) : null;
        boolean mention = kind != Kind.CLIENT && MentionMatcher.matches(plain, sender);

        synchronized (LOCK) {
            entries.add(new Entry(text, plain, kind, System.currentTimeMillis(), mention, signature));
            int limit = Math.clamp(Chat.historySize.get(), 50, HARD_LIMIT);
            while (entries.size() > limit)
                entries.removeFirst();
            version++;
        }

        if (mention)
            ChatRenderer.ping(sender, plain);
    }

    /** Flags printed while custom chat is off carry the tag in the text; the renderer adds its own. */
    public static String stripTagPrefix(String plain) {
        String prefix = ClientTag.label() + " | ";
        return plain != null && plain.startsWith(prefix) ? plain.substring(prefix.length()) : plain;
    }

    /** ChatUtil prefixes client output with "> "; the tag replaces that marker. */
    public static String stripClientPrefix(String plain) {
        String trimmed = plain == null ? "" : plain;
        while (trimmed.startsWith("> ") || trimmed.startsWith(">"))
            trimmed = trimmed.substring(trimmed.startsWith("> ") ? 2 : 1).stripLeading();
        return trimmed;
    }

    /** Extracts {@code Name} from a vanilla chat line, or null when the format is unknown. */
    public static String senderOf(String plain) {
        if (plain == null || !plain.startsWith("<"))
            return null;

        int end = plain.indexOf('>');
        if (end <= 1)
            return null;

        return plain.substring(1, end).strip();
    }

    public static void remove(MessageSignature signature) {
        if (signature == null)
            return;

        synchronized (LOCK) {
            for (int index = 0; index < entries.size(); index++) {
                MessageSignature stored = entries.get(index).signature();
                if (stored != null && stored.equals(signature)) {
                    entries.remove(index);
                    version++;
                    return;
                }
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            entries.clear();
            version++;
        }
    }

    public static List<Entry> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(entries);
        }
    }

    public static long version() {
        return version;
    }
}
