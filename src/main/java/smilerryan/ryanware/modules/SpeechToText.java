package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import smilerryan.ryanware.RyanWare;

import javax.sound.sampled.*;
import java.io.*;

public class SpeechToText extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOutput  = settings.createGroup("Output");

    // === Full Paths ===
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

    private TargetDataLine micLine;
    private Thread recordingThread;
    private File outputFile;

    public SpeechToText() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Speech-To-Text", "Records and processes mic audio.");
    }

    @Override
    public void onActivate() {
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
            if (debug.get()) info("Recording started → " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            error("Failed to start microphone.");
            e.printStackTrace();
        }
    }

    @Override
    public void onDeactivate() {
        try {
            if (micLine != null) {
                micLine.stop();
                micLine.close();
            }
            if (recordingThread != null) recordingThread.join(200);
            if (debug.get()) info("Recording stopped.");
        } catch (Exception e) {
            error("Error stopping mic.");
        }
        new Thread(this::runSTT, "STT-Thread").start();
    }

    private void runSTT() {
        try {
            File exe = new File(sttExePath.get());
            if (!exe.exists()) {
                error("Processor not found at:\n" + sttExePath.get());
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(exe.getAbsolutePath(), outputFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder textOut = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (debug.get()) info(line);
                textOut.append(line).append("\n");
            }
            proc.waitFor();
            String finalText = textOut.toString().trim();
            if (!finalText.isEmpty()) sendOutput(finalText);
            if (!keepRecordings.get() && outputFile.exists()) {
                if (debug.get()) info("Deleting recording: " + outputFile.getAbsolutePath());
                outputFile.delete();
            } else {
                if (debug.get()) info("Keeping recording: " + outputFile.getAbsolutePath());
            }
        } catch (Exception e) {
            error("Failed to run STT Processing.");
            e.printStackTrace();
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
}