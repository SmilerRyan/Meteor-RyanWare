package smilerryan.ryanware.modules_3;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class ChatPlinger extends Module {

    private final Setting<List<String>> keywords = settings.getDefaultGroup().add(
        new StringListSetting.Builder()
            .name("keywords")
            .description("Keywords to trigger the sound. Empty = all messages.")
            .defaultValue( List.of("->", "Ryan", "Balls") )
            .build()
    );

    // Dropdown list (single selectable sound)
    private final Setting<List<SoundEvent>> sound = settings.getDefaultGroup().add(
        new SoundEventListSetting.Builder()
            .name("sound")
            .description("Sound to play.")
            .defaultValue(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value())
            .build()
    );

    private final Setting<Double> volume = settings.getDefaultGroup().add(
        new DoubleSetting.Builder().name("volume").defaultValue(1).min(0).sliderMax(2).build()
    );

    private final Setting<Double> pitch = settings.getDefaultGroup().add(
        new DoubleSetting.Builder().name("pitch").defaultValue(1).min(0).sliderMax(2).build()
    );

    public ChatPlinger() {
        super(RyanWare.CATEGORY3, RyanWare.modulePrefix3 + "Chat-Plinger",
            "Plays a sound when chat messages match keywords.");
    }

    @EventHandler
    private void onMsg(ReceiveMessageEvent e) {
        if (mc.player == null) return;
        String m = e.getMessage().getString();

        if (keywords.get().isEmpty()) {
            play();
            return;
        }

        for (String k : keywords.get()) {
            if (m.toLowerCase().contains(k.toLowerCase())) {
                play();
                return;
            }
        }
    }

    private void play() {
        mc.player.playSound(
            sound.get().get(0),
            volume.get().floatValue(),
            pitch.get().floatValue()
        );
    }
}
