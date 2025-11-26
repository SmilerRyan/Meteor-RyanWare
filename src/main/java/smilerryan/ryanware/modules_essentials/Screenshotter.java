package smilerryan.ryanware.modules_essentials;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import smilerryan.ryanware.RyanWare;

import java.io.File;
import javax.sound.sampled.*;

public class Screenshotter extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<String> saveTo = sgGeneral.add(new StringSetting.Builder()
        .name("Save To")
        .description("Full path including filename. No assumptions, no fallback extension. Supports {UNIX}.")
        .defaultValue("screenshots/screenshot_{UNIX}.jpg")
        .build()
    );

    private final Setting<Integer> intervalMs = sgGeneral.add(new IntSetting.Builder()
        .name("Repeat Every (ms)")
        .defaultValue(0)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    public enum BeepMode { NEVER, BEFORE, AFTER, BOTH }
    private final Setting<BeepMode> beepMode = sgGeneral.add(new EnumSetting.Builder<BeepMode>()
        .name("Beep Mode")
        .defaultValue(BeepMode.AFTER)
        .build()
    );

    private volatile boolean running = false;
    private Thread screenshotThread;

    public Screenshotter() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix_essentials + "Screenshotter",
            "Takes screenshots of ALL monitors using ffmpeg with an optional interval and beep sound.");
    }

    @Override
    public void onActivate() {
        running = true;

        screenshotThread = new Thread(() -> {
            boolean singleShot = intervalMs.get() <= 0;
            long interval = intervalMs.get();

            while (running) {
                long iterationStart = System.currentTimeMillis();
                try {
                    long unix = System.currentTimeMillis();
                    String finalPath = saveTo.get().replace("{UNIX}", Long.toString(unix));

                    File output = new File(finalPath);
                    File parent = output.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    if (beepMode.get() == BeepMode.BEFORE || beepMode.get() == BeepMode.BOTH) {
                        playBeep();
                    }

                    ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",
                        "-f", "gdigrab",
                        "-i", "desktop",
                        "-vframes", "1",
                        "-y",
                        output.getAbsolutePath()
                    );

                    try {
                        Process proc = pb.start();

                        // Single-shot and no AFTER beep → disable immediately
                        if (singleShot && !(beepMode.get() == BeepMode.AFTER || beepMode.get() == BeepMode.BOTH)) {
                            if (this.isActive()) this.toggle();
                            // Let ffmpeg run in background without waiting
                        } else {
                            proc.waitFor();
                        }
                    } catch (Exception ignored) {}

                    if (beepMode.get() == BeepMode.AFTER || beepMode.get() == BeepMode.BOTH) {
                        playBeep();
                    }

                    // Stop if single-shot
                    if (singleShot) break;

                    // Fixed interval: sleep only the remaining time
                    long elapsed = System.currentTimeMillis() - iterationStart;
                    long sleep = interval - elapsed;
                    if (sleep > 0) Thread.sleep(sleep);

                } catch (Exception ignored) {
                    break;
                }
            }

            if (this.isActive()) this.toggle();
        });

        screenshotThread.setDaemon(true);
        screenshotThread.start();
    }

    @Override
    public void onDeactivate() {
        running = false;
        if (screenshotThread != null) {
            screenshotThread.interrupt();
            screenshotThread = null;
        }
    }

    private void playBeep() {
        try {
            float sampleRate = 44100f;
            int durationMs = 100;
            int numSamples = (int) (durationMs * (sampleRate / 1000));
            byte[] data = new byte[numSamples];

            double freq = 2000; // 2kHz beep
            for (int i = 0; i < numSamples; i++) {
                double angle = i / (sampleRate / freq) * 2.0 * Math.PI;
                data[i] = (byte) (Math.sin(angle) * 127f);
            }

            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
            javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
            clip.open(format, data, 0, data.length);
            clip.start();
        } catch (Exception ignored) {}
    }
}
