package smilerryan.ryanware.modules_essentials;

import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.List;

import smilerryan.ryanware.RyanWare;

public class SoundBlocker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<SoundEvent>> sounds = sgGeneral.add(new SoundEventListSetting.Builder()
        .name("sounds")
        .description("Sounds to block.")
        .build()
    );

    public SoundBlocker() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix_essentials + "sound-blocker", "Blocks out specific selected sounds.");
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        Identifier playedId = event.sound.getId(); // SoundInstance → Identifier

        for (SoundEvent sound : sounds.get()) {
            Identifier soundId = Registries.SOUND_EVENT.getId(sound);
            if (playedId.equals(soundId)) {
                event.cancel();
                break;
            }
        }
    }
}
