package com.instrumentalist.krs.utils;

import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.render.HudEditor;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationManager {
    private static final int MAX_NOTIFICATIONS = 32;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 512;

    private static final float SCREEN_MARGIN = 4f;
    private static final float CARD_GAP = 3f;
    private static final float CARD_PADDING_X = 8f;
    private static final float CARD_PADDING_Y = 6f;
    private static final float TITLE_SIZE = 8.5f;
    private static final float TITLE_TRACKING = 0.35f;
    private static final float MESSAGE_SIZE = 9.5f;
    private static final float MESSAGE_GAP = 2f;
    private static final float ICON_SIZE = 11f;
    private static final float ICON_GAP = 6f;
    private static final float PROGRESS_HEIGHT = 2f;
    private static final float IN_DURATION_MS = 180f;
    private static final float OUT_DURATION_MS = 220f;
    private static final float STACK_RESPONSE_MS = 60f;
    private static final int MAX_MESSAGE_LINES = 3;

    private static final Object NOTIFICATION_LOCK = new Object();
    private static final ArrayList<Notification> notifications = new ArrayList<>(MAX_NOTIFICATIONS);

    private final ArrayList<Notification> renderEntries = new ArrayList<>(MAX_NOTIFICATIONS);
    private Notification editorPreview;
    private long lastPrepareNanos;

    /** Visual weight of a notification; inferred from the title unless the caller picks one. */
    public enum Severity {
        ACCENT(MaterialIcon.INFO),
        SUCCESS(MaterialIcon.DONE),
        WARNING(MaterialIcon.WARNING),
        DANGER(MaterialIcon.SYNC_PROBLEM);

        private final String icon;

        Severity(String icon) {
            this.icon = icon;
        }

        private String icon() {
            return icon;
        }

        private Color color(int alpha) {
            return switch (this) {
                case SUCCESS -> NanoVGTheme.success(alpha);
                case WARNING -> NanoVGTheme.warning(alpha);
                case DANGER -> NanoVGTheme.danger(alpha);
                default -> NanoVGTheme.accent(alpha);
            };
        }
    }

    public void addNotification(String title, String message) {
        addNotification(title, message, inferSeverity(title));
    }

    public void addNotification(String title, String message, Severity severity) {
        String cleanTitle = sanitizeText(title, MAX_TITLE_LENGTH);
        String cleanMessage = sanitizeText(message, MAX_MESSAGE_LENGTH);
        Notification notification = new Notification(
                cleanTitle,
                cleanMessage,
                severity == null ? Severity.ACCENT : severity
        );
        long now = System.nanoTime();
        synchronized (NOTIFICATION_LOCK) {
            if (Interface.notificationCombine.get()) {
                Notification existing = findCombineCandidate(cleanTitle, cleanMessage, now);
                if (existing != null) {
                    existing.merge(cleanMessage, now);
                    return;
                }
            }

            while (notifications.size() >= MAX_NOTIFICATIONS)
                notifications.removeFirst();
            notifications.add(notification);
        }
    }

    private static Notification findCombineCandidate(String title, String message, long now) {
        long windowNanos = Math.max(0L, Interface.notificationCombineWindow.get()) * 1_000_000L;
        for (int index = notifications.size() - 1; index >= 0; index--) {
            Notification existing = notifications.get(index);
            if (!existing.title.equalsIgnoreCase(title))
                continue;
            if (now - existing.startTimeNanos > windowNanos)
                continue;
            if (existing.isExpired(now, Interface.notificationDuration.get()))
                continue;
            if (!canCombineMessages(existing.message, message))
                continue;
            return existing;
        }
        return null;
    }

    private static boolean canCombineMessages(String first, String second) {
        if (first.equals(second))
            return true;
        return commonPrefix(first, second).stripTrailing().length() >= 8;
    }

    private static String combineMessages(String first, String second) {
        if (first.equals(second) || first.contains(second))
            return first;
        if (second.contains(first))
            return second;

        String prefix = commonPrefix(first, second);
        if (prefix.stripTrailing().length() >= 8) {
            String firstSuffix = first.substring(prefix.length()).strip();
            String secondSuffix = second.substring(prefix.length()).strip();
            if (!firstSuffix.isEmpty() && !secondSuffix.isEmpty()) {
                return sanitizeText(prefix.stripTrailing() + " " + firstSuffix + ", " + secondSuffix,
                        MAX_MESSAGE_LENGTH);
            }
        }

        return sanitizeText(first + " · " + second, MAX_MESSAGE_LENGTH);
    }

    private static String commonPrefix(String first, String second) {
        int length = Math.min(first.length(), second.length());
        int index = 0;
        while (index < length && first.charAt(index) == second.charAt(index))
            index++;
        return first.substring(0, index);
    }

    public void prepareNotifications() {
        renderEntries.clear();

        long now = System.nanoTime();
        float deltaMillis = lastPrepareNanos == 0L ? 16f : (now - lastPrepareNanos) / 1_000_000.0f;
        lastPrepareNanos = now;
        deltaMillis = Math.clamp(deltaMillis, 0f, 100f);

        float durationMillis = Interface.notificationDuration.get();
        float maxWidth = Interface.notificationWidth.get();
        int maxVisible = Interface.notificationMax.get();
        boolean progress = Interface.notificationProgress.get();
        boolean icons = Interface.notificationIcons.get();
        String corner = Interface.notificationCorner.get().toLowerCase(Locale.ROOT);

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        boolean right = !corner.contains("left");
        boolean bottom = corner.contains("bottom");
        String stackMode = Interface.notificationStack.get().toLowerCase(Locale.ROOT);
        boolean stackUp = switch (stackMode) {
            case "up" -> true;
            case "down" -> false;
            default -> bottom;
        };

        float offsetX = HudEditor.offsetX(HudEditor.Element.NOTIFICATIONS);
        float offsetY = HudEditor.offsetY(HudEditor.Element.NOTIFICATIONS);
        float cardWidth = Math.max(60f, Math.min(maxWidth, screenWidth - SCREEN_MARGIN * 2f));
        float baseX = right ? screenWidth - SCREEN_MARGIN + offsetX : SCREEN_MARGIN + offsetX;
        float baseY = bottom ? screenHeight - SCREEN_MARGIN + offsetY : SCREEN_MARGIN + offsetY;
        float smoothing = 1f - (float) Math.exp(-deltaMillis / STACK_RESPONSE_MS);

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float stackOffset = 0f;
        int visible = 0;

        synchronized (NOTIFICATION_LOCK) {
            for (int index = 0; index < notifications.size(); ) {
                if (notifications.get(index).isExpired(now, durationMillis)) {
                    notifications.remove(index);
                    continue;
                }
                index++;
            }

            for (Notification notification : notifications) {
                if (visible >= maxVisible) break;

                float alpha = notification.alpha(now, durationMillis);
                if (alpha <= 0.002f) continue;

                notification.measure(cardWidth, icons);
                float height = notification.height(progress);
                float x = right ? baseX - cardWidth : baseX;
                float targetY = stackUp ? baseY - stackOffset - height : baseY + stackOffset;
                targetY = Math.clamp(targetY, SCREEN_MARGIN,
                        Math.max(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN));

                notification.setTargetY(targetY, smoothing);

                float slide = (1f - easeOutCubic(notification.inProgress(now))) * 4f;
                float y = notification.animatedY() + (stackUp ? -slide : slide);

                notification.setFrame(x, y, cardWidth, height, alpha,
                        inProgressOf(notification, now, durationMillis), icons, progress);
                renderEntries.add(notification);

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + cardWidth);
                maxY = Math.max(maxY, y + height);
                stackOffset += height + CARD_GAP;
                visible++;
            }
        }

        if (visible == 0 && HudEditor.isEditing()) {
            if (editorPreview == null)
                editorPreview = new Notification("Preview", "Right click to customize notifications", Severity.ACCENT);

            editorPreview.measure(cardWidth, icons);
            float height = editorPreview.height(progress);
            float x = right ? baseX - cardWidth : baseX;
            float y = Math.clamp(stackUp ? baseY - height : baseY, SCREEN_MARGIN,
                    Math.max(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN));
            editorPreview.setFrame(x, y, cardWidth, height, 1f, 1f, icons, progress);
            renderEntries.add(editorPreview);
            minX = x;
            minY = y;
            maxX = x + cardWidth;
            maxY = y + height;
            visible = 1;
        }

        if (visible > 0)
            HudEditor.register(HudEditor.Element.NOTIFICATIONS, minX, minY, maxX - minX, maxY - minY);
    }

    public void clear() {
        synchronized (NOTIFICATION_LOCK) {
            notifications.clear();
        }
        renderEntries.clear();
    }

    public void renderNotificationEffects(NVGU vg) {
        for (Notification notification : renderEntries)
            notification.renderEffects(vg);
    }

    public void renderNotificationBodies(NVGU vg) {
        for (Notification notification : renderEntries)
            notification.renderBody(vg);
    }

    /** Draw-only entry point; the HUD calls {@link #prepareNotifications()} once per frame. */
    public void renderNotifications(NVGU vg) {
        if (renderEntries.isEmpty()) return;

        vg.beginEffectBatch();
        renderNotificationEffects(vg);
        vg.flushEffectBatch();
        renderNotificationBodies(vg);
    }

    private static float inProgressOf(Notification notification, long now, float durationMillis) {
        return Math.clamp(1f - notification.outProgress(now, durationMillis), 0f, 1f);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1f - Math.clamp(value, 0f, 1f);
        return 1f - inverse * inverse * inverse;
    }

    private static Severity inferSeverity(String title) {
        if (title == null || title.isEmpty())
            return Severity.ACCENT;

        String key = title.toLowerCase(Locale.ROOT);
        if (key.contains("fail") || key.contains("error") || key.contains("disabled")
                || key.contains("blocked") || key.contains("crash") || key.contains("denied"))
            return Severity.DANGER;

        if (key.contains("alert") || key.contains("detect") || key.contains("warn") || key.contains("flag"))
            return Severity.WARNING;

        if (key.contains("success") || key.contains("done") || key.contains("enabled") || key.contains("saved"))
            return Severity.SUCCESS;

        return Severity.ACCENT;
    }

    private static String sanitizeText(String value, int maxLength) {
        if (value == null || value.isEmpty())
            return "";
        if (value.length() <= maxLength)
            return value;

        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1)))
            end--;
        return value.substring(0, end);
    }

    private static String ellipsize(NVGFont font, String text, float size, float maxWidth) {
        if (text.isEmpty() || font.getWidth(text, size) <= maxWidth)
            return text;

        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end).stripTrailing() + ellipsis, size) > maxWidth)
            end--;

        return end <= 0 ? ellipsis : text.substring(0, end).stripTrailing() + ellipsis;
    }

    private static String ellipsizeTracked(String text, float size, float tracking, float maxWidth) {
        if (text.isEmpty() || NanoVGTheme.trackedTextWidth(NVGFonts.INTER_MEDIUM, text, size, tracking) <= maxWidth)
            return text;

        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && NanoVGTheme.trackedTextWidth(NVGFonts.INTER_MEDIUM,
                text.substring(0, end).stripTrailing() + ellipsis, size, tracking) > maxWidth)
            end--;

        return end <= 0 ? ellipsis : text.substring(0, end).stripTrailing() + ellipsis;
    }

    private static List<String> wrapMessage(NVGFont font, String text, float size, float maxWidth, int maxLines) {
        if (text == null || text.isEmpty())
            return List.of();

        String[] words = text.split("\\s+");
        List<String> lines = new ArrayList<>(maxLines);
        StringBuilder current = new StringBuilder();
        int index = 0;

        while (index < words.length) {
            String word = words[index];
            String candidate = current.length() == 0 ? word : current + " " + word;

            if (current.length() == 0 || font.getWidth(candidate, size) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                index++;
                continue;
            }

            lines.add(current.toString());
            current.setLength(0);
            if (lines.size() == maxLines)
                break;
        }

        if (lines.size() < maxLines) {
            if (current.length() > 0)
                lines.add(current.toString());
            return lines;
        }

        if (index < words.length) {
            String last = lines.get(lines.size() - 1);
            lines.set(lines.size() - 1, ellipsize(font, last + " " + words[index], size, maxWidth));
        }

        return lines;
    }

    private static final class Notification {
        private final String title;
        private String message;
        private final Severity severity;
        private long startTimeNanos;

        private String displayTitle = "";
        private List<String> messageLines = List.of();
        private float measuredWidth = -1f;
        private boolean measuredIcons;

        private float animatedY = Float.NaN;
        private float x;
        private float y;
        private float width;
        private float height;
        private float alpha;
        private float lifetime;
        private boolean icons;
        private boolean progressBar;

        private Notification(String title, String message, Severity severity) {
            this.title = title;
            this.message = message;
            this.severity = severity;
            this.startTimeNanos = System.nanoTime();
        }

        private void merge(String newMessage, long now) {
            this.message = combineMessages(this.message, newMessage);
            this.startTimeNanos = now;
            this.measuredWidth = -1f;
        }

        private float elapsedMillis(long now) {
            return Math.max(0f, (now - startTimeNanos) / 1_000_000.0f);
        }

        private float inProgress(long now) {
            return Math.clamp(elapsedMillis(now) / IN_DURATION_MS, 0f, 1f);
        }

        private float outProgress(long now, float durationMillis) {
            return Math.clamp((elapsedMillis(now) - durationMillis) / OUT_DURATION_MS, 0f, 1f);
        }

        private float alpha(long now, float durationMillis) {
            return easeOutCubic(inProgress(now)) * (1f - easeOutCubic(outProgress(now, durationMillis)));
        }

        private boolean isExpired(long now, float durationMillis) {
            return elapsedMillis(now) > durationMillis + OUT_DURATION_MS;
        }

        private void measure(float cardWidth, boolean icons) {
            if (Float.compare(cardWidth, measuredWidth) == 0 && icons == measuredIcons)
                return;

            measuredWidth = cardWidth;
            measuredIcons = icons;

            float textWidth = Math.max(16f, cardWidth - CARD_PADDING_X * 2f - (icons ? ICON_SIZE + ICON_GAP : 0f));
            displayTitle = ellipsizeTracked(title.toUpperCase(Locale.ROOT), TITLE_SIZE, TITLE_TRACKING, textWidth);
            messageLines = wrapMessage(NVGFonts.INTER, message, MESSAGE_SIZE, textWidth, MAX_MESSAGE_LINES);
        }

        private float height(boolean progress) {
            float height = CARD_PADDING_Y * 2f + NVGFonts.INTER_MEDIUM.getHeight(TITLE_SIZE);
            if (!messageLines.isEmpty())
                height += MESSAGE_GAP + messageLines.size() * NVGFonts.INTER.getHeight(MESSAGE_SIZE);
            if (progress)
                height += PROGRESS_HEIGHT;
            return height;
        }

        private void setTargetY(float targetY, float smoothing) {
            if (Float.isNaN(animatedY)) {
                animatedY = targetY;
                return;
            }

            animatedY += (targetY - animatedY) * smoothing;
        }

        private float animatedY() {
            return Float.isNaN(animatedY) ? 0f : animatedY;
        }

        private void setFrame(float x, float y, float width, float height, float alpha,
                              float lifetime, boolean icons, boolean progressBar) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.alpha = Math.clamp(alpha, 0f, 1f);
            this.lifetime = Math.clamp(lifetime, 0f, 1f);
            this.icons = icons;
            this.progressBar = progressBar;
        }

        private void renderEffects(NVGU vg) {
            // Flat cards, like the rest of the HUD: the client avoids liquid-glass passes outside the menu.
        }

        private void renderBody(NVGU vg) {
            int alphaInt = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
            if (alphaInt <= 0 || width <= 0f || height <= 0f)
                return;

            float radius = NanoVGTheme.RADIUS_LABEL;
            NanoVGTheme.renderCompact(vg, x, y, width, height, radius, alpha, 40);
            NanoVGTheme.renderEdge(vg, x, y, width, height, radius, alpha * 0.8f);

            vg.rectangle(x, y, 2f, height, severity.color(Math.min(255, Math.round(alphaInt * 0.9f))));

            float titleY = y + CARD_PADDING_Y - 0.5f;
            float textX = x + CARD_PADDING_X;

            if (icons) {
                float iconY = titleY + (NVGFonts.INTER_MEDIUM.getHeight(TITLE_SIZE) - NVGFonts.ICON.getHeight(ICON_SIZE)) / 2f;
                NVGFonts.ICON.drawText(severity.icon(), x + CARD_PADDING_X, iconY, ICON_SIZE,
                        severity.color(alphaInt), Alignment.LEFT_TOP, true);
                textX += ICON_SIZE + ICON_GAP;
            }

            NanoVGTheme.drawTrackedText(NVGFonts.INTER_MEDIUM, displayTitle, textX, titleY,
                    TITLE_SIZE, TITLE_TRACKING, NanoVGTheme.text(alphaInt), true);

            float lineY = y + CARD_PADDING_Y + NVGFonts.INTER_MEDIUM.getHeight(TITLE_SIZE) + MESSAGE_GAP;
            float lineHeight = NVGFonts.INTER.getHeight(MESSAGE_SIZE);
            int messageAlpha = Math.min(255, Math.round(alphaInt * 0.85f));

            for (int line = 0; line < messageLines.size(); line++) {
                NVGFonts.INTER.drawText(messageLines.get(line), textX, lineY + line * lineHeight,
                        MESSAGE_SIZE, NanoVGTheme.muted(messageAlpha), Alignment.LEFT_TOP, true);
            }

            if (progressBar && lifetime > 0f) {
                float barWidth = Math.max(0f, (width - 2f) * lifetime);
                if (barWidth > 0.5f)
                    NanoVGTheme.renderAccentUnderline(vg, x + 2f, y + height - PROGRESS_HEIGHT,
                            barWidth, PROGRESS_HEIGHT, alpha);
            }
        }
    }
}
