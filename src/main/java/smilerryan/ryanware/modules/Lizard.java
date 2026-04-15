package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Lizard extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum ColorMode { Green, Rainbow }

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("Chance (percent) each tick to trigger lizard event")
        .defaultValue(100) // default full chance
        .min(1)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> minSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("min-seconds-between-triggers")
        .description("Minimum seconds between lizard event triggers")
        .defaultValue(10)
        .min(0)
        .sliderMax(60)
        .build()
    );

    // === TEXT ===
    private final Setting<Boolean> showText = sgGeneral.add(new BoolSetting.Builder()
        .name("show-text")
        .description("Show the LIZARD text on screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ColorMode> colorMode = sgGeneral.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode")
        .description("Whether the text is rainbow or just green shades")
        .defaultValue(ColorMode.Green)
        .build()
    );

    // === SOUND ===
    private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play the lizard.wav sound.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
        .name("volume")
        .description("Volume of the lizard sound")
        .defaultValue(1.0)
        .min(0.0)
        .max(1000.0)
        .sliderMax(5.0)
        .build()
    );

    // === CHAT ===
    private final Setting<Boolean> autoChat = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-chat")
        .description("Automatically send a chat message when triggered.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> chatMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("chat-messages")
        .description("Messages to randomly send when triggered.")
        .defaultValue(List.of("Lizard.", "LIZARD.", "\"Lizard.\"", "\"LIZARD.\""))
        .build()
    );

    private final Random random = new Random();
    private final List<LizardFlash> activeFlashes = new ArrayList<>();
    private long nextCheck = 0;
    private long lastTrigger = 0;

    private static class LizardFlash {
        int x, y;
        float scale;
        Color color;
        long startTime, endTime;

        LizardFlash(int x, int y, long duration, Color color, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.color = color;
            this.startTime = System.currentTimeMillis();
            this.endTime = this.startTime + duration;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > endTime;
        }

        float getAlpha() {
            long now = System.currentTimeMillis();
            float life = (endTime - startTime);
            float progress = (now - startTime) / (float) life;

            if (progress < 0.1f) return progress / 0.1f;
            else if (progress > 0.8f) return (1f - progress) / 0.2f;
            else return 1f;
        }
    }

    public Lizard() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Lizard", "Randomly flashes LIZARD text, plays lizard.wav, and/or sends messages.");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        long now = System.currentTimeMillis();

        if (now > nextCheck) {
            if (random.nextInt(100) < chance.get()) {
                boolean canTrigger = (now - lastTrigger) >= (minSeconds.get() * 1000L);
                if (canTrigger) {
                    // === TEXT ===
                    if (showText.get()) spawnText(event);

                    // === SOUND ===
                    if (playSound.get()) playLizardSound();

                    // === CHAT ===
                    if (autoChat.get() && !chatMessages.get().isEmpty()) {
                        String msg = chatMessages.get().get(random.nextInt(chatMessages.get().size()));
                        MinecraftClient.getInstance().player.networkHandler.sendChatMessage(msg);
                    }

                    lastTrigger = now;
                }
            }
            nextCheck = now + 50;
        }

        // Draw active text flashes
        if (showText.get() && !activeFlashes.isEmpty()) {
            activeFlashes.removeIf(flash -> {
                if (flash.isExpired()) return true;
                drawText(flash.x, flash.y, flash.color, flash.scale, flash.getAlpha());
                return false;
            });
        }
    }

    private void spawnText(Render2DEvent event) {
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            float scale = 1.2f + random.nextFloat() * 2.0f;
            int x = random.nextInt(Math.max(1, event.screenWidth - 60));
            int y = random.nextInt(Math.max(1, event.screenHeight - 20));

            Color color = (colorMode.get() == ColorMode.Green)
                ? new Color(0, 150 + random.nextInt(106), 0)
                : new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));

            long duration = 1000 + random.nextInt(2000);
            activeFlashes.add(new LizardFlash(x, y, duration, color, scale));
        }
    }

    private void drawText(int x, int y, Color baseColor, float scale, float alpha) {
        int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
        Color c = new Color(baseColor.r, baseColor.g, baseColor.b, a);

        TextRenderer.get().begin(scale, false, true);
        TextRenderer.get().render("LIZARD", x, y, c);
        TextRenderer.get().end();
    }

    private void playLizardSound() {
        try {
            var rawStream = Lizard.class.getResourceAsStream("/lizard.wav");
            if (rawStream != null) {
                var buffered = new java.io.BufferedInputStream(rawStream);
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(buffered);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);

                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (20.0 * Math.log10(Math.max(0.0001, volume.get())));
                gainControl.setValue(Math.min(gainControl.getMaximum(), Math.max(gainControl.getMinimum(), dB)));

                clip.start();
            } else {
                System.err.println("Could not find lizard.wav in resources!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
