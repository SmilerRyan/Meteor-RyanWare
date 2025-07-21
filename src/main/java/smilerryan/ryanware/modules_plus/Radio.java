package smilerryan.ryanware.modules_plus;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.*;

import smilerryan.ryanware.RyanWare;

public class Radio extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Radios> radioChannel = sgGeneral.add(new EnumSetting.Builder<Radios>()
        .name("Radio Channel")
        .defaultValue(Radios.Radio1)
        .build()
    );

    private final Setting<String> customUrl = sgGeneral.add(new StringSetting.Builder()
        .name("Custom URL")
        .description("Only used if Custom is selected.")
        .defaultValue("")
        .visible(() -> radioChannel.get() == Radios.Custom)
        .build()
    );

    private final Setting<Integer> volumeInt = sgGeneral.add(new IntSetting.Builder()
        .name("Volume")
        .description("Adjusts the volume of the radio.")
        .defaultValue(50)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private Process ffmpegProcess;
    private CompletableFuture<Void> radioThread;
    private SourceDataLine dataLine;
    private volatile boolean stopRequested = false;
    private String currentUrl = "";

    public Radio() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "Radio", "It's a fucking in-game radio!");
    }

    @Override
    public void onActivate() {
        stopRequested = false;
        startRadio();
    }

    @Override
    public void onDeactivate() {
        stopRequested = true;
        stopRadio();
    }

    @EventHandler
    private void onTick(TickEvent.Pre e) {
        setVolume(volumeInt.get());

        String newUrl = getActiveUrl();
        if (!newUrl.equals(currentUrl)) {
            currentUrl = newUrl;
            stopRequested = true;
            stopRadio();
            stopRequested = false;
            startRadio();
        }
    }

    private void setVolume(int volume) {
        if (dataLine != null && dataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) dataLine.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log(volume / 100.0) * 10.0);
            gainControl.setValue(dB);
        }
    }

    private String getActiveUrl() {
        if (radioChannel.get() == Radios.Custom) {
            String custom = customUrl.get().trim();
            return custom.isEmpty() ? "" : custom;
        }
        return radioChannel.get().URL;
    }

    private void startRadio() {
        currentUrl = getActiveUrl();
        if (currentUrl.isEmpty()) {
            error("Custom URL is empty.");
            return;
        }
        
        if (radioThread != null && !radioThread.isDone()) {
            return; // Prevent multiple starts
        }

        radioThread = CompletableFuture.runAsync(() -> {
            while (!stopRequested) {
                info("Radio started: " + currentUrl);
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",
                        "-reconnect", "1",
                        "-reconnect_streamed", "1",
                        "-reconnect_delay_max", "5",
                        "-i", currentUrl,
                        "-f", "s16le",
                        "-acodec", "pcm_s16le",
                        "-ac", "2",
                        "-ar", "44100",
                        "-fflags", "+discardcorrupt",
                        "-"
                    );
                    pb.redirectErrorStream(true);
                    ffmpegProcess = pb.start();
                    InputStream audioStream = ffmpegProcess.getInputStream();

                    AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                    dataLine = (SourceDataLine) AudioSystem.getLine(info);
                    dataLine.open(format, 44100 * 4);
                    dataLine.start();

                    byte[] readBuffer = new byte[4096];
                    int bytesRead;
                    int frameSize = format.getFrameSize();

                    while (!stopRequested && (bytesRead = audioStream.read(readBuffer)) != -1 && ffmpegProcess.isAlive()) {
                        if (bytesRead > 0) {
                            int framesToWrite = bytesRead / frameSize;
                            if (framesToWrite > 0) {
                                dataLine.write(readBuffer, 0, framesToWrite * frameSize);
                            }
                        }
                    }

                    if (!stopRequested) {
                        error("Stream interrupted, reconnecting...");
                        stopRadio();
                    }
                } catch (Exception e) {
                    if (!stopRequested) {
                        error("Error: " + e.getMessage());
                        stopRadio();
                    }
                }
            }
        });
    }

    private void stopRadio() {
        if (dataLine != null) {
            dataLine.stop();
            dataLine.flush();
            dataLine.close();
            dataLine = null;
        }
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }
        info("Radio stopped.");
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

        public final String URL;

        Radios(String url) {
            this.URL = url;
        }
    }
}