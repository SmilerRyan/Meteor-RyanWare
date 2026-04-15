package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import smilerryan.ryanware.RyanWare;

import javax.sound.sampled.*;
import java.io.*;

public class SpeechToText extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOutput  = settings.createGroup("Output");
    private final SettingGroup sgOverlay = settings.createGroup("Overlay");

    // === Paths ===
    private final Setting<String> recordingsFolder = sgGeneral.add(new StringSetting.Builder()
        .name("recordings-folder")
        .defaultValue("./stt")
        .build()
    );

    private final Setting<String> sttExePath = sgGeneral.add(new StringSetting.Builder()
        .name("stt-processor")
        .defaultValue("./stt/_process.bat")
        .build()
    );

    // === Keep/Delete recordings ===
    private final Setting<Boolean> keepRecordings = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-recordings")
        .defaultValue(true)
        .build()
    );

    // === Debug ===
    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .defaultValue(false)
        .build()
    );

    // === Output ===
    private final Setting<Boolean> sendToChat = sgOutput.add(new BoolSetting.Builder()
        .name("output-to-chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> prefix = sgOutput.add(new StringSetting.Builder()
        .name("chat-prefix")
        .defaultValue("")
        .visible(sendToChat::get)
        .build()
    );

    // === Overlay ===
    private final Setting<Boolean> showOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("show-overlay")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> overlayX = sgOverlay.add(new IntSetting.Builder()
        .name("x")
        .defaultValue(10)
        .min(0)
        .max(3000)
        .build()
    );

    private final Setting<Integer> overlayY = sgOverlay.add(new IntSetting.Builder()
        .name("y")
        .defaultValue(10)
        .min(0)
        .max(3000)
        .build()
    );

    private final Setting<Double> overlayScale = sgOverlay.add(new DoubleSetting.Builder()
        .name("scale")
        .defaultValue(1.0)
        .min(0.2)
        .max(5.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<SettingColor> overlayColor = sgOverlay.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private TargetDataLine micLine;
    private Thread recordingThread;
    private File outputFile;

    private String overlayText = "";

    // used to cancel old STT results
    private long generationId = 0;

    public SpeechToText() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Speech-To-Text", "Records mic audio and processes it.");
    }

    @Override
    public void onActivate() {
        long myGen = ++generationId;

        overlayText = "Recording…";
        try {
            File folder = new File(recordingsFolder.get());
            folder.mkdirs();
            outputFile = new File(folder, "rec_" + System.currentTimeMillis() + ".wav");

            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            micLine = (TargetDataLine) AudioSystem.getLine(info);

            micLine.open(format);
            micLine.start();

            recordingThread = new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(micLine)) {
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
                } catch (Exception ignored) {}
            }, "STT-Recorder");

            recordingThread.start();
        }
        catch (Exception e) {
            overlayText = "Mic Error";
            error("Failed to start microphone.");
        }
    }

    @Override
    public void onDeactivate() {
        long myGen = generationId;

        try {
            if (micLine != null) {
                micLine.stop();
                micLine.close();
            }
            if (recordingThread != null) recordingThread.join(200);
        }
        catch (Exception ignored) {}

        new Thread(() -> runSTT(myGen), "STT-Thread").start();
    }

    private void runSTT(long myGen) {
        File exe = new File(sttExePath.get());
        if (!exe.exists()) {
            if (myGen == generationId) overlayText = "Processor Missing";
            return;
        }

        if (myGen == generationId) overlayText = "Processing…";

        try {
            ProcessBuilder pb = new ProcessBuilder(exe.getAbsolutePath(), outputFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder textOut = new StringBuilder();

            String line;
            while ((line = br.readLine()) != null) textOut.append(line).append(" ");
            proc.waitFor();

            String finalText = textOut.toString().trim();

            if (outputFile.exists() && !keepRecordings.get()) outputFile.delete();

            if (myGen != generationId) return; // cancelled silently

            // sanitize — remove newlines
            finalText = finalText.replace("\n", " ").replace("\r", " ");

            // replace invalid chars with '?'
            finalText = finalText.replaceAll("[^\\x20-\\x7E]", "?");

            // trim and collapse whitespace
            finalText = finalText.trim().replaceAll("\\s+", " ");

            // too long or empty → don't send, clear overlay
            if (finalText.isEmpty() || finalText.length() > 200) {
                overlayText = "";
                return;
            }

            sendOutput(finalText);
            overlayText = "";
        }
        catch (Exception e) {
            if (myGen == generationId) overlayText = "Processing Error";
        }
    }

    private void sendOutput(String text) {
        if (!sendToChat.get()) return;

        String msg = prefix.get() + text;

        if (msg.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(msg.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(msg);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showOverlay.get()) return;
        if (overlayText == null || overlayText.isEmpty()) return;

        SettingColor sc = overlayColor.get();
        Color c = new Color(sc.r, sc.g, sc.b, sc.a);

        TextRenderer.get().begin(overlayScale.get().floatValue(), false, true);
        TextRenderer.get().render(overlayText, overlayX.get(), overlayY.get(), c);
        TextRenderer.get().end();
    }
}
