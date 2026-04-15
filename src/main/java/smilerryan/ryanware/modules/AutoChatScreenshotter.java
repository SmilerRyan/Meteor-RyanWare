package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.ScreenshotRecorder;

import smilerryan.ryanware.RyanWare;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoChatScreenshotter extends Module {

    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<List<String>> triggers = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-words")
        .description("Messages containing any of these words will trigger a screenshot.")
        .defaultValue("WHAT")
        .build()
    );

    private final Setting<String> responseMessage = sgGeneral.add(new StringSetting.Builder()
        .name("response-message")
        .description("Optional chat message to send when triggered.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> responseDelay = sgGeneral.add(new StringSetting.Builder()
        .name("response-delay-ms")
        .description("Delay before sending response message. Supports '500', '200-800', or empty.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> screenshotDelay = sgGeneral.add(new StringSetting.Builder()
        .name("screenshot-delay-ms")
        .description("Delay before taking the screenshot. Supports '50', '100-500', or empty.")
        .defaultValue("")
        .build()
    );

    public AutoChatScreenshotter() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Auto-Chat-Screenshotter",
            "Takes a screenshot when specific chat messages appear."
        );
    }

    private boolean matchesTrigger(String msg) {
        String lower = msg.toLowerCase();

        for (String trigger : triggers.get()) {
            if (trigger == null || trigger.isEmpty()) continue;

            if (lower.contains(trigger.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        if (mc.player == null || mc.world == null) return;

        String message = e.getMessage().getString();

        if (!matchesTrigger(message)) return;

        boolean wasChatOpen = mc.currentScreen instanceof ChatScreen;

        new Thread(() -> {

            mc.execute(() -> {
                ensureUnpaused();
                if (!wasChatOpen) openChatKey();
            });

            int delay = parseDelay(responseDelay.get());
            if (delay > 0) sleep(delay);

            sendResponse();

            int delay2 = parseDelay(screenshotDelay.get());
            if (delay2 > 0) sleep(delay2);

            playBeep();

            mc.execute(() -> {
                try {
                    ScreenshotRecorder.saveScreenshot(
                        mc.runDirectory,
                        mc.getFramebuffer(),
                        text -> {}
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                if (!wasChatOpen) closeChatKey();
            });

        }, "AutoChatScreenshotter").start();
    }

    private void sendResponse() {
        if (responseMessage.get().isEmpty()) return;

        mc.execute(() -> {
            if (mc.player == null) return;

            if (responseMessage.get().startsWith("/")) {
                mc.player.networkHandler.sendChatCommand(responseMessage.get().substring(1));
            } else {
                mc.player.networkHandler.sendChatMessage(responseMessage.get());
            }
        });
    }

    private void ensureUnpaused() {
        if (mc.currentScreen instanceof GameMenuScreen) {
            mc.setScreen(null);
        }
    }

    private void openChatKey() {
        mc.options.chatKey.setPressed(true);
        mc.options.chatKey.setPressed(false);
    }

    private void closeChatKey() {
        mc.options.chatKey.setPressed(true);
        mc.options.chatKey.setPressed(false);
    }

    private int parseDelay(String input) {
        if (input == null || input.isEmpty()) return 0;

        try {
            if (input.contains("-")) {
                String[] parts = input.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (max < min) return min;
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            }

            return Integer.parseInt(input.trim());
        } catch (Exception ignored) {}

        return 0;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    private void playBeep() {
        try {
            float sampleRate = 44100f;
            int durationMs = 100;
            int samples = (int) (durationMs * (sampleRate / 1000));

            byte[] data = new byte[samples];
            double freq = 2000;

            for (int i = 0; i < samples; i++) {
                double angle = i / (sampleRate / freq) * 2 * Math.PI;
                data[i] = (byte) (Math.sin(angle) * 127);
            }

            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
            Clip clip = AudioSystem.getClip();
            clip.open(format, data, 0, data.length);
            clip.start();

        } catch (Exception ignored) {}
    }
}