package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import smilerryan.ryanware.RyanWare;

public class Radio extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<PlaybackMode> playbackMode = sgGeneral.add(new EnumSetting.Builder<PlaybackMode>()
        .name("Playback Mode")
        .description("How the radio audio should be played.")
        .defaultValue(PlaybackMode.FFPLAY)
        .build()
    );

    private final Setting<Radios> radioChannel = sgGeneral.add(new EnumSetting.Builder<Radios>()
        .name("Radio Channel")
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
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    // ffplay
    private Process ffplayProcess;

    // ffmpeg
    private Process ffmpegProcess;
    private CompletableFuture<Void> radioThread;
    private volatile SourceDataLine dataLine;

    // shared
    private volatile boolean stopRequested = false;
    private String currentUrl = "";
    private int lastVolume = -1;
    private PlaybackMode lastMode = null;

    public Radio() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Radio", "It's a fucking in-game radio!");
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
        String newUrl = getActiveUrl();
        int vol = volume.get();
        PlaybackMode mode = playbackMode.get();

        if (!newUrl.equals(currentUrl) || vol != lastVolume || mode != lastMode) {
            currentUrl = newUrl;
            lastVolume = vol;
            lastMode = mode;

            stopRequested = true;
            stopRadio();

            stopRequested = false;
            startRadio();

            return;
        }

        if (mode == PlaybackMode.FFPLAY) {
            if (ffplayProcess != null && !ffplayProcess.isAlive()) {
                stopRadio();
                startRadio();
            }
        } else {
            setVolume(vol);

            if (ffmpegProcess != null && !ffmpegProcess.isAlive()) {
                stopRequested = true;
                stopRadio();

                stopRequested = false;
                startRadio();
            }
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

        if (currentUrl.isEmpty()) return;

        if (playbackMode.get() == PlaybackMode.FFPLAY) {
            startFFPlay();
        } else {
            startFFmpeg();
        }
    }

    private void startFFPlay() {
        try {
            ffplayProcess = new ProcessBuilder(
                "ffplay",
                "-nodisp",
                "-autoexit",
                "-loglevel", "quiet",
                "-volume", String.valueOf(volume.get()),
                currentUrl
            ).start();
        } catch (Exception e) {
            error("Failed to start ffplay. Make sure ffplay is installed and in your PATH.");
            toggle();
        }
    }

    private void startFFmpeg() {
        if (radioThread != null && !radioThread.isDone()) return;

        radioThread = CompletableFuture.runAsync(() -> {
            byte[] previousGoodFrame = null;
            int silentFrames = 0;

            final int MIN_GOOD_FRAMES = 5;
            final float NOISE_THRESHOLD = 0.02f;

            while (!stopRequested && isActive()) {
                try {
                    boolean localFile =
                        !currentUrl.startsWith("http://") &&
                        !currentUrl.startsWith("https://");

                    ProcessBuilder pb;

                    if (localFile) {
                        pb = new ProcessBuilder(
                            "ffmpeg",
                            "-re",
                            "-i", currentUrl,
                            "-f", "s16le",
                            "-acodec", "pcm_s16le",
                            "-ac", "2",
                            "-ar", "48000",
                            "-"
                        );
                    } else {
                        pb = new ProcessBuilder(
                            "ffmpeg",
                            "-reconnect", "1",
                            "-reconnect_streamed", "1",
                            "-reconnect_delay_max", "5",
                            "-i", currentUrl,
                            "-f", "s16le",
                            "-acodec", "pcm_s16le",
                            "-ac", "2",
                            "-ar", "48000",
                            "-"
                        );
                    }

                    pb.redirectErrorStream(true);

                    ffmpegProcess = pb.start();

                    AudioFormat format = new AudioFormat(
                        48000,
                        16,
                        2,
                        true,
                        false
                    );

                    DataLine.Info info = new DataLine.Info(
                        SourceDataLine.class,
                        format
                    );

                    SourceDataLine newLine = (SourceDataLine) AudioSystem.getLine(info);

                    newLine.open(format, 500 * 1024);
                    newLine.start();

                    dataLine = newLine;

                    setVolume(volume.get());

                    InputStream audioStream = ffmpegProcess.getInputStream();

                    byte[] buffer = new byte[500 * 1024];

                    int frameSize = format.getFrameSize();
                    int bytesRead;

                    while (!stopRequested && isActive() && (bytesRead = audioStream.read(buffer)) != -1) {
                        if (bytesRead <= 0) continue;

                        int frameCount = bytesRead / frameSize;

                        if (frameCount == 0) continue;

                        for (int i = 0; i < frameCount; i++) {
                            int offset = i * frameSize;

                            float frameVolume = calculateFrameVolume(
                                buffer,
                                offset,
                                frameSize
                            );

                            boolean isNoise =
                                frameVolume > 0 &&
                                frameVolume < NOISE_THRESHOLD;

                            boolean isSilent = frameVolume == 0;

                            if (isNoise || isSilent) {
                                silentFrames++;

                                if (previousGoodFrame != null) {
                                    System.arraycopy(
                                        previousGoodFrame,
                                        0,
                                        buffer,
                                        offset,
                                        frameSize
                                    );
                                }
                            } else {
                                if (previousGoodFrame == null) {
                                    previousGoodFrame = new byte[frameSize];
                                }

                                System.arraycopy(
                                    buffer,
                                    offset,
                                    previousGoodFrame,
                                    0,
                                    frameSize
                                );

                                silentFrames = 0;
                            }
                        }

                        if (silentFrames < MIN_GOOD_FRAMES) {
                            SourceDataLine line = dataLine;

                            if (line != null) {
                                line.write(buffer, 0, frameCount * frameSize);
                            }
                        }
                    }

                    stopRadio();
                } catch (Exception e) {
                    if (!stopRequested) {
                        error("FFmpeg playback failed: " + e.getMessage());
                    }

                    stopRadio();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        });
    }

    private void setVolume(int volume) {
        SourceDataLine line = dataLine;

        if (line != null && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            try {
                FloatControl gainControl =
                    (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);

                if (volume <= 0) {
                    gainControl.setValue(gainControl.getMinimum());
                    return;
                }

                float dB = (float) (Math.log(volume / 100.0) * 20.0);

                gainControl.setValue(
                    Math.max(
                        gainControl.getMinimum(),
                        Math.min(gainControl.getMaximum(), dB)
                    )
                );
            } catch (Exception ignored) {
            }
        }
    }

    private float calculateFrameVolume(byte[] audioData, int offset, int length) {
        float sum = 0;
        int sampleCount = 0;

        for (int i = 0; i < length; i += 2) {
            if (offset + i + 1 >= audioData.length) break;

            short sample = (short) (
                (audioData[offset + i + 1] << 8) |
                (audioData[offset + i] & 0xff)
            );

            float sampleValue = sample / 32768.0f;

            sum += sampleValue * sampleValue;

            sampleCount++;
        }

        if (sampleCount == 0) return 0;

        return (float) Math.sqrt(sum / sampleCount);
    }

    private synchronized void stopRadio() {
        SourceDataLine line = dataLine;
        dataLine = null;

        if (line != null) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }

            try {
                line.flush();
            } catch (Exception ignored) {
            }

            try {
                line.close();
            } catch (Exception ignored) {
            }
        }

        Process ffmpeg = ffmpegProcess;
        ffmpegProcess = null;

        if (ffmpeg != null) {
            try {
                ffmpeg.destroy();
            } catch (Exception ignored) {
            }
        }

        Process ffplay = ffplayProcess;
        ffplayProcess = null;

        if (ffplay != null) {
            try {
                ffplay.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    public enum PlaybackMode {
        FFPLAY,
        FFMPEG
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