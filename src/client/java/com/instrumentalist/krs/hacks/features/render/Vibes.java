package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.audio.SoundEffectPlayer;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class Vibes extends Module {

    private static final String NONE = "None";
    private static final String RANDOM = "Random";
    private static final String[] SOUND_NAMES = {
            NONE, RANDOM, "Blud Bath", "Castlevania Hello",
            "Hor Lr", "Nightmare", "Okay", "One"
    };
    private static final String[] SOUND_RESOURCES = {
            "assets/krs/sound/blud bath.mp3",
            "assets/krs/sound/castlevania_hello.mp3",
            "assets/krs/sound/hor lr.mp3",
            "assets/krs/sound/nightmare.mp3",
            "assets/krs/sound/okay.mp3",
            "assets/krs/sound/one.mp3"
    };
    private static final String DETECTION_ALL = "All";
    private static final String DETECTION_VANILLA = "Vanilla";
    private static final String DETECTION_ATTACK = "Attack";
    private static final String DETECTION_DAMAGE = "Damage Packet";
    private static final String DETECTION_CHAT = "Chat";
    private static final String[] KILL_METHODS = {
            DETECTION_ALL, DETECTION_VANILLA, DETECTION_ATTACK, DETECTION_DAMAGE, DETECTION_CHAT
    };
    private static final String[] WIN_PHRASES = {
            "you won", "you win", "victory", "winner:", "you are the winner", "won the game", "game won"
    };
    /** Death message wording used by modern servers and by 1.8.9 servers. */
    private static final String[] KILL_PHRASES = {
            "was slain by", "was killed by", "was shot by", "was blown up by",
            "was knocked into the void by", "was burnt to a crisp whilst fighting",
            "was struck by lightning whilst fighting", "was doomed to fall by",
            "was impaled by", "was pummeled by", "was fireballed by",
            "slain by", "you killed", "you have killed"
    };
    private static final Pattern YOU_PATTERN = Pattern.compile("\\byou\\b");
    /** How long a player we damaged stays a candidate for a kill. */
    private static final long VICTIM_MEMORY_NANOS = 12_000_000_000L;
    private static final long WIN_COOLDOWN_NANOS = 3_000_000_000L;
    private static final long HELLO_COOLDOWN_NANOS = 5_000_000_000L;

    @Setting
    private static final FloatValue volume = new FloatValue(
            "Volume",
            0.3f,
            0f,
            1f
    ).describe("Volume of the Vibes sounds.");

    @Setting
    private static final ListValue killSound = new ListValue(
            "Kill Sound",
            SOUND_NAMES,
            "Okay"
    ).describe("Sound played when you kill a player. Random picks a different clip each time.");

    @Setting
    private static final ListValue killDetection = new ListValue(
            "Kill Detection",
            KILL_METHODS,
            DETECTION_ALL
    ).describe("How kills are detected. All combines every method, which is what makes kills work on 1.8.9 and other servers that never send last-hurt info.");

    @Setting
    private static final ListValue winSound = new ListValue(
            "Win Sound",
            SOUND_NAMES,
            "One"
    ).describe("Sound played when you win a game. Random picks a different clip each time.");

    @Setting
    private static final ListValue helloSound = new ListValue(
            "Hello Sound",
            SOUND_NAMES,
            "Castlevania Hello"
    ).describe("Herro sound played when the game opens and when you join a world. Random picks a different clip each time.");

    private final Set<UUID> countedKills = new HashSet<>();
    /** Players we recently damaged, so death signals can be attributed to us. */
    private final Map<UUID, TrackedVictim> suspectedVictims = new HashMap<>();
    private final Map<Integer, UUID> suspectedVictimEntityIds = new HashMap<>();
    private long lastWinNanos;
    private long lastHelloNanos;

    public Vibes() {
        super("Vibes", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    @Override
    public String description() {
        return "Plays sounds on kills, game wins, game open and world join";
    }

    @Override
    public void onEnable() {
        clearKills();
        sanitizeSounds();
        lastWinNanos = 0L;

        // Client.loaded turns true once the startup config has been applied, so this
        // only fires for the initial launch instead of every manual toggle.
        if (!Client.loaded)
            playHello();
    }

    @Override
    public void onDisable() {
        clearKills();
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearKills();
        sanitizeSounds();
        lastWinNanos = 0L;

        if (event == null || event.world == null)
            return;

        playHello();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event == null || mc.player == null)
            return;
        if (!usesDetection(DETECTION_ATTACK))
            return;

        if (event.entity instanceof Player victim && victim != mc.player)
            trackVictim(victim);
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            clearKills();
            return;
        }

        pruneVictims(System.nanoTime());

        if (usesDetection(DETECTION_VANILLA)) {
            for (Player player : mc.level.players()) {
                if (player == mc.player || !player.isDeadOrDying())
                    continue;
                if (player.getLastHurtByMob() != mc.player)
                    continue;

                countKill(player.getUUID());
                break;
            }
        }

        if (!usesDetection(DETECTION_ATTACK) && !usesDetection(DETECTION_DAMAGE))
            return;

        // Dead players are often removed from the world before the death animation is
        // visible, so any tracked victim that is now dying counts as our kill.
        if (suspectedVictims.isEmpty())
            return;

        for (Map.Entry<UUID, TrackedVictim> entry : new ArrayList<>(suspectedVictims.entrySet())) {
            Player victim = findPlayer(entry.getKey());
            if (victim != null && victim.isDeadOrDying())
                countKill(entry.getKey());
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event == null || event.packet == null)
            return;

        Packet<?> packet = event.packet;
        if (packet instanceof ClientboundGameEventPacket gameEvent
                && gameEvent.getEvent() == ClientboundGameEventPacket.WIN_GAME) {
            playWin();
            return;
        }

        if (packet instanceof ClientboundSystemChatPacket chat && !chat.overlay()) {
            String message = chat.content().getString();
            if (isWinMessage(message))
                playWin();
            if (usesDetection(DETECTION_CHAT))
                handleChatKill(message);
            return;
        }

        if (packet instanceof ClientboundDamageEventPacket damage) {
            runOnClientThread(() -> handleDamage(damage));
        } else if (packet instanceof ClientboundEntityEventPacket entityEvent
                && entityEvent.getEventId() == EntityEvent.DEATH) {
            runOnClientThread(() -> handleDeathEvent(entityEvent));
        } else if (packet instanceof ClientboundRemoveEntitiesPacket removal) {
            runOnClientThread(() -> handleRemoval(removal));
        }
    }

    private void runOnClientThread(Runnable action) {
        if (mc.isSameThread())
            action.run();
        else
            mc.execute(action);
    }

    /** Server-side damage feed: the most accurate signal and it also covers projectile hits. */
    private void handleDamage(ClientboundDamageEventPacket packet) {
        if (!usesDetection(DETECTION_DAMAGE) || mc.player == null || mc.level == null)
            return;

        int self = mc.player.getId();
        if (packet.sourceCauseId() != self && packet.sourceDirectId() != self)
            return;

        if (mc.level.getEntity(packet.entityId()) instanceof Player victim && victim != mc.player)
            trackVictim(victim);
    }

    private void handleDeathEvent(ClientboundEntityEventPacket packet) {
        if (mc.level == null)
            return;
        if (!usesDetection(DETECTION_ATTACK) && !usesDetection(DETECTION_DAMAGE))
            return;

        if (!(packet.getEntity(mc.level) instanceof Player victim) || victim == mc.player)
            return;
        if (!suspectedVictims.containsKey(victim.getUUID()))
            return;

        countKill(victim.getUUID());
    }

    private void handleRemoval(ClientboundRemoveEntitiesPacket packet) {
        if (mc.level == null)
            return;
        if (!usesDetection(DETECTION_ATTACK) && !usesDetection(DETECTION_DAMAGE))
            return;

        for (int entityId : packet.entityIds()) {
            UUID uuid = suspectedVictimEntityIds.get(entityId);
            if (uuid == null)
                continue;

            Entity entity = mc.level.getEntity(entityId);
            boolean died = entity == null
                    || entity.getRemovalReason() == Entity.RemovalReason.KILLED
                    || (entity instanceof LivingEntity living && (living.isDeadOrDying() || living.getHealth() <= 0f));

            if (died)
                countKill(uuid);
            else
                untrackVictim(uuid);
        }
    }

    private void handleChatKill(String message) {
        if (mc.player == null || suspectedVictims.isEmpty())
            return;

        String text = message.toLowerCase(Locale.ROOT);
        if (!isDeathMessage(text) || !mentionsLocalPlayer(text))
            return;

        boolean matched = false;
        for (Map.Entry<UUID, TrackedVictim> entry : new ArrayList<>(suspectedVictims.entrySet())) {
            String name = entry.getValue().name;
            if (name.isEmpty() || !text.contains(name))
                continue;

            countKill(entry.getKey());
            matched = true;
        }

        // Hypixel style messages that name the killer without the victim being tracked.
        if (!matched && (text.contains("you killed") || text.contains("you have killed")))
            play(killSound);
    }

    private static boolean isDeathMessage(String text) {
        for (String phrase : KILL_PHRASES) {
            if (text.contains(phrase))
                return true;
        }

        return false;
    }

    private static boolean mentionsLocalPlayer(String text) {
        if (YOU_PATTERN.matcher(text).find())
            return true;

        String ownName = mc.player.getName().getString().toLowerCase(Locale.ROOT);
        return !ownName.isEmpty() && text.contains(ownName);
    }

    private void trackVictim(Player victim) {
        suspectedVictims.put(victim.getUUID(), new TrackedVictim(
                victim.getName().getString().toLowerCase(Locale.ROOT),
                System.nanoTime() + VICTIM_MEMORY_NANOS
        ));
        suspectedVictimEntityIds.put(victim.getId(), victim.getUUID());
    }

    private void untrackVictim(UUID uuid) {
        suspectedVictims.remove(uuid);
        suspectedVictimEntityIds.values().removeIf(uuid::equals);
    }

    private void pruneVictims(long now) {
        if (suspectedVictims.isEmpty())
            return;

        Iterator<Map.Entry<UUID, TrackedVictim>> iterator = suspectedVictims.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedVictim> entry = iterator.next();
            if (entry.getValue().expiresAt >= now)
                continue;

            iterator.remove();
            suspectedVictimEntityIds.values().removeIf(entry.getKey()::equals);
        }
    }

    private void countKill(UUID victim) {
        untrackVictim(victim);
        if (!countedKills.add(victim))
            return;

        play(killSound);
    }

    /** Falls back to a valid clip when a config still names a sound that no longer exists. */
    private static void sanitizeSounds() {
        if (!killSound.contains(killSound.get()))
            killSound.set("Okay");
        if (!winSound.contains(winSound.get()))
            winSound.set("One");
        if (!helloSound.contains(helloSound.get()))
            helloSound.set("Castlevania Hello");
    }

    private void clearKills() {
        countedKills.clear();
        suspectedVictims.clear();
        suspectedVictimEntityIds.clear();
    }

    private static Player findPlayer(UUID uuid) {
        if (mc.level == null || uuid == null)
            return null;

        for (Player player : mc.level.players()) {
            if (uuid.equals(player.getUUID()))
                return player;
        }
        return null;
    }

    private static boolean usesDetection(String method) {
        String mode = killDetection.get();
        return DETECTION_ALL.equals(mode) || method.equals(mode);
    }

    private void playHello() {
        long now = System.nanoTime();
        if (now - lastHelloNanos < HELLO_COOLDOWN_NANOS)
            return;

        lastHelloNanos = now;
        play(helloSound);
    }

    private void playWin() {
        long now = System.nanoTime();
        if (now - lastWinNanos < WIN_COOLDOWN_NANOS)
            return;

        lastWinNanos = now;
        play(winSound);
    }

    private static boolean isWinMessage(String message) {
        if (message == null || message.isEmpty())
            return false;

        String text = message.toLowerCase(Locale.ROOT);
        for (String phrase : WIN_PHRASES) {
            if (text.contains(phrase))
                return true;
        }

        return false;
    }

    private static void play(ListValue sound) {
        float currentVolume = volume.get();
        if (currentVolume <= 0f)
            return;

        SoundEffectPlayer.play(resourceFor(sound.get()), currentVolume);
    }

    private static String resourceFor(String soundName) {
        if (RANDOM.equals(soundName))
            return SOUND_RESOURCES[ThreadLocalRandom.current().nextInt(SOUND_RESOURCES.length)];

        return switch (soundName) {
            case "Blud Bath" -> "assets/krs/sound/blud bath.mp3";
            case "Castlevania Hello" -> "assets/krs/sound/castlevania_hello.mp3";
            case "Hor Lr" -> "assets/krs/sound/hor lr.mp3";
            case "Nightmare" -> "assets/krs/sound/nightmare.mp3";
            case "Okay" -> "assets/krs/sound/okay.mp3";
            case "One" -> "assets/krs/sound/one.mp3";
            default -> null;
        };
    }

    private static final class TrackedVictim {
        final String name;
        final long expiresAt;

        TrackedVictim(String name, long expiresAt) {
            this.name = name;
            this.expiresAt = expiresAt;
        }
    }
}
