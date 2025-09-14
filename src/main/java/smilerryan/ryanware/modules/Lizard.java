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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Lizard extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("Chance (percent) each tick to spawn a LIZARD flash")
        .defaultValue(5)
        .min(1)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Random random = new Random();
    private final List<LizardFlash> activeFlashes = new ArrayList<>();
    private long nextCheck = 0;

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

            if (progress < 0.1f) {
                // Faster fade in (first 10% of life)
                return progress / 0.1f;
            }
            else if (progress > 0.8f) {
                // Fade out (last 20% of life)
                return (1f - progress) / 0.2f;
            }
            else {
                return 1f;
            }
        }
    }

    public Lizard() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Lizard", "Randomly flashes LIZARD text and plays lizard.wav");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        long now = System.currentTimeMillis();

        // Chance trigger
        if (now > nextCheck) {
            if (random.nextInt(100) < chance.get()) {
                int count = 1 + random.nextInt(3); // 1–3 flashes
                for (int i = 0; i < count; i++) {
                    float scale = 1.0f + random.nextFloat() * 2.0f; // 1.0x – 3.0x

                    // Pick position across the full screen (not shrunk by scale)
                    int x = random.nextInt(Math.max(1, event.screenWidth - 60));
                    int y = random.nextInt(Math.max(1, event.screenHeight - 20));

                    Color color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
                    long duration = 1000 + random.nextInt(2000);

                    activeFlashes.add(new LizardFlash(x, y, duration, color, scale));
                }
                playLizardSound();
            }
            nextCheck = now + 50;
        }

        // Draw all active flashes
        if (!activeFlashes.isEmpty()) {
            activeFlashes.removeIf(flash -> {
                if (flash.isExpired()) return true;
                drawText(flash.x, flash.y, flash.color, flash.scale, flash.getAlpha());
                return false;
            });
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
            File soundFile = new File(MinecraftClient.getInstance().runDirectory, "lizard.wav");
            if (soundFile.exists()) {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                clip.start();
            }
        } catch (Exception ignored) {}
    }
}
