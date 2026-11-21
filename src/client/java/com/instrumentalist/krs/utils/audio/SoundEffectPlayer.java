package com.instrumentalist.krs.utils.audio;

import com.mojang.logging.LogUtils;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Plays short MP3 effects once, letting several of them overlap. */
public final class SoundEffectPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Delay every clip starts with, so no clip begins later than another. */
    private static final float START_DELAY_SECONDS = 0.064f;
    private static final int SILENCE_THRESHOLD = 600;
    private static final Map<String, LeadScan> LEAD_SCANS = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "krs-sound-effect");
        thread.setDaemon(true);
        return thread;
    });

    private SoundEffectPlayer() {
    }

    public static void play(String resourcePath, float volume) {
        if (resourcePath == null || resourcePath.isBlank())
            return;

        float clampedVolume = Math.clamp(volume, 0f, 1f);
        if (clampedVolume <= 0f)
            return;

        EXECUTOR.execute(() -> playBlocking(resourcePath, clampedVolume));
    }

    private static void playBlocking(String resourcePath, float volume) {
        AdvancedPlayer player = null;
        try (InputStream stream = openResourceStream(resourcePath)) {
            if (stream == null) {
                LOGGER.warn("Could not find sound effect resource: {}", resourcePath);
                return;
            }

            player = new AdvancedPlayer(
                    new BufferedInputStream(stream),
                    new VolumeAudioDevice(volume, leadingSkip(resourcePath))
            );
            player.play();
        } catch (JavaLayerException | IOException | RuntimeException exception) {
            LOGGER.warn("Failed to play sound effect: {}", resourcePath, exception);
        } finally {
            if (player != null) {
                try {
                    player.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static InputStream openResourceStream(String resourcePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream stream = contextClassLoader.getResourceAsStream(resourcePath);
            if (stream != null)
                return stream;
        }

        return SoundEffectPlayer.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    /**
     * How much leading silence a clip carries. Clips come with different amounts of it,
     * which makes some of them start noticeably later than others.
     */
    private record LeadScan(int leadSamples, int sampleRate) {
    }

    private static int leadingSkip(String resourcePath) {
        LeadScan scan;
        try {
            scan = LEAD_SCANS.computeIfAbsent(resourcePath, SoundEffectPlayer::scanLeadSilence);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to scan sound effect lead: {}", resourcePath, exception);
            return 0;
        }

        if (scan == null || scan.sampleRate() <= 0)
            return 0;

        int startDelay = Math.round(START_DELAY_SECONDS * scan.sampleRate());
        return Math.max(0, scan.leadSamples() - startDelay);
    }

    /**
     * Counts the silent samples at the start of a clip so it can be trimmed down to the
     * shared start delay, making every sound begin at the same offset.
     */
    private static LeadScan scanLeadSilence(String resourcePath) {
        int leadSamples = 0;
        int sampleRate = 44100;
        Bitstream bitstream = null;
        try (InputStream stream = openResourceStream(resourcePath)) {
            if (stream == null)
                return new LeadScan(0, sampleRate);

            bitstream = new Bitstream(new BufferedInputStream(stream));
            Decoder decoder = new Decoder();
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                sampleRate = header.frequency();
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] samples = buffer.getBuffer();
                int length = buffer.getBufferLength();
                for (int i = 0; i < length; i++) {
                    if (Math.abs(samples[i]) > SILENCE_THRESHOLD) {
                        bitstream.closeFrame();
                        return new LeadScan(leadSamples, sampleRate);
                    }
                    leadSamples++;
                }
                bitstream.closeFrame();
            }
        } catch (JavaLayerException | IOException | RuntimeException exception) {
            LOGGER.warn("Failed to scan sound effect lead: {}", resourcePath, exception);
        } finally {
            if (bitstream != null) {
                try {
                    bitstream.close();
                } catch (BitstreamException | RuntimeException ignored) {
                }
            }
        }

        return new LeadScan(0, sampleRate);
    }

    private static final class VolumeAudioDevice extends JavaSoundAudioDevice {

        private final float volume;
        private int skipRemaining;
        private short[] scaledSamples = new short[0];

        private VolumeAudioDevice(float volume, int skipSamples) {
            this.volume = volume;
            this.skipRemaining = skipSamples;
        }

        @Override
        protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
            if (skipRemaining > 0) {
                int dropped = Math.min(skipRemaining, len);
                skipRemaining -= dropped;
                offs += dropped;
                len -= dropped;
                if (len <= 0)
                    return;
            }

            if (volume >= 0.999f) {
                super.writeImpl(samples, offs, len);
                return;
            }

            if (scaledSamples.length < len)
                scaledSamples = new short[len];

            for (int i = 0; i < len; i++)
                scaledSamples[i] = (short) Math.round(samples[offs + i] * volume);

            super.writeImpl(scaledSamples, 0, len);
        }
    }
}
