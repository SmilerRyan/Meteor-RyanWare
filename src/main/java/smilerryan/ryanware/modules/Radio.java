package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import smilerryan.ryanware.RyanWare;

public class Radio extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<PlaybackMode> playbackMode = sgGeneral.add(new EnumSetting.Builder<PlaybackMode>()
        .name("Playback Mode")
        .description("How the radio audio should be played.")
        .defaultValue(PlaybackMode.FFMPEG_STREAM)
        .build()
    );

    private final Setting<Radios> radioChannel = sgGeneral.add(new EnumSetting.Builder<Radios>()
        .name("Radio Channel")
        .description("The radio channel to play.")
        .defaultValue(Radios.Radio1)
        .build()
    );

    private final Setting<String> customUrl = sgGeneral.add(new StringSetting.Builder()
        .name("Custom URL")
        .description("URL or local audio file path.")
        .defaultValue("")
        .visible(() -> radioChannel.get() == Radios.Custom)
        .build()
    );

    private final Setting<Integer> volume = sgGeneral.add(new IntSetting.Builder()
        .name("Volume")
        .description("Adjusts the volume of the radio.")
        .defaultValue(50)
        // .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private Process ffplayProcess;
    private Process ffmpegProcess;
    private CompletableFuture<Void> radioThread;
    private volatile SourceDataLine dataLine;
    private volatile boolean stopRequested = false;
    private String currentUrl = "";
    private int lastVolume = -1;
    private PlaybackMode lastMode = null;

    public Radio() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Radio", "It's an in-game radio!");
    }

    @Override
    public void onActivate() {

        String ffmpegPath = "./meteor-client/ryanware/ffmpeg";

        File ffmpegFile = new File(ffmpegPath);
        if (!ffmpegFile.exists()) {
            info("ffmpeg distribution not found at: " + ffmpegPath);
            if (this.isActive()) this.toggle();
            return;
        }
        
        stopRequested = false;
        currentUrl = getActiveUrl();
        lastVolume = volume.get();
        lastMode = playbackMode.get();
        startRadio();
    }

    @Override
    public void onDeactivate() {
        stopRequested = true;
        stopRadio();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;
        String newUrl = getActiveUrl();
        int newVolume = volume.get();
        PlaybackMode newMode = playbackMode.get();
        boolean urlChanged = !newUrl.equals(currentUrl);
        boolean modeChanged = newMode != lastMode;
        boolean volumeChanged = newVolume != lastVolume;

        // FFPLAY volume changes require restarting the process, FFMPEG_STREAM volume changes can be handled live.
        boolean shouldRestart = urlChanged || modeChanged || (newMode == PlaybackMode.FFPLAY && volumeChanged);

        if (shouldRestart) {
            currentUrl = newUrl;
            lastVolume = newVolume;
            lastMode = newMode;
            stopRadio();
            startRadio();
            return;
        }
        lastVolume = newVolume;
    }

    private void startRadio() {
        if (currentUrl == null || currentUrl.trim().isEmpty()) {return;}
        stopRequested = false;
        switch (playbackMode.get()) {
            case FFPLAY -> startWithFfplay(currentUrl);
            case FFMPEG_STREAM -> startWithFfmpegStream(currentUrl);
        }
    }

    private void stopRadio() {
        stopRequested = true;
        closeDataLine();
        if (ffplayProcess != null) {
            ffplayProcess.destroy();
            ffplayProcess = null;
        }
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }
        if (radioThread != null) {
            radioThread.cancel(true);
            radioThread = null;
        }
    }

    private String getActiveUrl() {
        if (radioChannel.get() == Radios.Custom) {
            return customUrl.get().trim();
        }
        return radioChannel.get().url;
    }

    private void startWithFfplay(String url) {
        try {
            closeDataLine();
            if (ffmpegProcess != null) {
                ffmpegProcess.destroy();
                ffmpegProcess = null;
            }
            if (radioThread != null) {
                radioThread.cancel(true);
                radioThread = null;
            }
            ProcessBuilder pb = new ProcessBuilder(
                "ffplay",
                "-nodisp",
                "-autoexit",
                "-loglevel", "error",
                "-volume", String.valueOf(volume.get()),
                url
            );
            pb.redirectErrorStream(true);
            ffplayProcess = pb.start();
        } catch (Exception e) {
            error("Failed to start ffplay. Make sure ffplay is installed and added to PATH.");
            e.printStackTrace();
        }
    }

    private void startWithFfmpegStream(String url) {
        if (ffplayProcess != null) {
            ffplayProcess.destroy();
            ffplayProcess = null;
        }
        radioThread = CompletableFuture.runAsync(() -> {
            AudioFormat format = new AudioFormat(44100f, 16, 2, true, false);
            try {
                ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-i", url, "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2", "pipe:1");
                pb.redirectErrorStream(false);
                ffmpegProcess = pb.start();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                dataLine = (SourceDataLine) AudioSystem.getLine(info);
                int bufferSize = 44100 * 4;
                dataLine.open(format, bufferSize);
                dataLine.start();
                try (InputStream audioStream = new BufferedInputStream(ffmpegProcess.getInputStream())) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (!stopRequested && !Thread.currentThread().isInterrupted() && (bytesRead = audioStream.read(buffer)) != -1) {
                        applyVolume(buffer, bytesRead);
                        dataLine.write(buffer, 0, bytesRead);
                    }
                }
            } catch (Exception e) {
                if (!stopRequested) {
                    error("Failed to start radio using ffmpeg stream. Make sure ffmpeg is installed and added to PATH.");
                    e.printStackTrace();
                }
            } finally {
                closeDataLine();
                if (ffmpegProcess != null) {
                    ffmpegProcess.destroy();
                    ffmpegProcess = null;
                }
            }
        });
    }

    private void applyVolume(byte[] buffer, int length) {
        float gain = volume.get() / 100.0f;
        for (int i = 0; i < length - 1; i += 2) {
            int low = buffer[i] & 0xff;
            int high = buffer[i + 1];
            int sample = (high << 8) | low;
            int adjusted = Math.round(sample * gain);
            if (adjusted > Short.MAX_VALUE) adjusted = Short.MAX_VALUE;
            if (adjusted < Short.MIN_VALUE) adjusted = Short.MIN_VALUE;
            buffer[i] = (byte) (adjusted & 0xff);
            buffer[i + 1] = (byte) ((adjusted >> 8) & 0xff);
        }
    }

    private void closeDataLine() {
        SourceDataLine line = dataLine;
        if (line != null) {
            try {line.drain();} catch (Exception ignored) {}
            try {line.stop();} catch (Exception ignored) {}
            try {line.close();} catch (Exception ignored) {}
            dataLine = null;
        }
    }

    public enum PlaybackMode {
        FFPLAY("Play externally with ffplay"),
        FFMPEG_STREAM("Convert with ffmpeg and stream in game");

        private final String title;
        PlaybackMode(String title) {this.title = title;}

        @Override
        public String toString() {return title;}
    }

    public enum Radios {
        Radio1("https://icast.connectmedia.hu/5202/live.mp3"),
        SlagerFM("https://slagerfm.netregator.hu:7813/slagerfm128.mp3"),
        RetroRadio("https://icast.connectmedia.hu/5002/live.mp3"),
        BestFM("https://icast.connectmedia.hu/5102/live.mp3"),
        RockFM("https://icast.connectmedia.hu/5301/live.mp3"),
        KossuthRadio("https://icast.connectmedia.hu/4736/mr1.mp3"),
        CapitalUK("https://icecast.thisisdax.com/CapitalUK"),
        HeartLondon("https://icecast.thisisdax.com/HeartLondonMP3"),
        SmoothLondon("https://icecast.thisisdax.com/SmoothLondonMP3"),
        Custom("");
        private final String url;
        Radios(String url) {this.url = url;}
    }

}